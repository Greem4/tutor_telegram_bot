package ru.greemlab.tutor_telegram_bot.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ru.greemlab.tutor_telegram_bot.catalog.CaseCatalog
import ru.greemlab.tutor_telegram_bot.entity.CaseAnswer
import ru.greemlab.tutor_telegram_bot.entity.TelegramUser
import ru.greemlab.tutor_telegram_bot.enums.SurveyQuestion
import ru.greemlab.tutor_telegram_bot.repository.CaseAnswerRepository
import ru.greemlab.tutor_telegram_bot.repository.SurveyAnswerRepository
import ru.greemlab.tutor_telegram_bot.repository.TelegramUserRepository
import ru.greemlab.tutor_telegram_bot.session.CaseSession
import java.util.concurrent.ConcurrentHashMap

@Service
class CaseService(
    @Value("\${app.bot.admin_id}") private val adminId: Long?, // ID администратора из конфигурации
    private val catalog: CaseCatalog, // Каталог кейсов
    private val sender: SenderService, // Сервис отправки сообщений и медиа
    private val kb: KeyboardService, // Сервис клавиатур
    private val pdf: PdfService, // Сервис генерации PDF
    private val surveyService: SurveyService, // Сервис, управляющий анкетой
    private val surveyAnswerRepo: SurveyAnswerRepository, // Репозиторий ответов анкеты
    private val caseAnswerRepo: CaseAnswerRepository, // Репозиторий ответов на кейсы
    private val userRepo: TelegramUserRepository //TODO к проду удалить
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val sessions = ConcurrentHashMap<Long, CaseSession>()

    /**
     * Запуск 2-го этапа — кейсов.
     */
    suspend fun start(chatId: Long) {
        log.debug("Start cases for chatId={}", chatId)
        val user: TelegramUser = surveyService.takeProfile(chatId)
            ?: run {
                log.warn(
                    "Profile not found for chatId={}, asking to complete survey first",
                    chatId
                )
                sender.send(chatId, "Сначала пройдите опросник.", kb.start())
                return
            }

        if (user.casesCompleted) {
            log.debug(
                "User {} has already completed cases, skipping start",
                user.telegramId
            )
            sender.send(chatId, "Вы уже завершили кейсы ранее.", kb.remove())
            return
        }

        sessions[chatId] = CaseSession(user, catalog)
        log.debug(
            "Created CaseSession for chatId={}, total sessions={}",
            chatId,
            sessions.size
        )
        askNext(chatId)
    }

    fun active(chatId: Long): Boolean =
        sessions.containsKey(chatId)

    fun cancel(chatId: Long) {
        log.debug("Canceling cases for chatId={}", chatId)
        sessions.remove(chatId)
    }

    suspend fun answer(chatId: Long, text: String) {
        log.debug("Received answer for chatId={}: {}", chatId, text)
        val session = sessions[chatId] ?: run {
            log.warn("No active CaseSession for chatId={}, ignoring answer", chatId)
            return
        }

        session.answer(text)
        if (session.next()) {
            log.debug("Moving to next case for chatId={}", chatId)
            askNext(chatId)
        } else {
            log.debug("All cases answered for chatId={}, finishing", chatId)
            finish(chatId, session)
        }
    }

    private suspend fun askNext(chatId: Long) {
        val kase = sessions[chatId]?.current ?: return
        log.debug("Asking case #{} to chatId={}", kase.id, chatId)
        sender.photo(chatId, kase.image, kb.cancel())
    }

    /**
     * Сбрасывает все данные кейсов и флаг в БД.
     */   //TODO к проду удалить 
    fun reset(chatId: Long) {
        log.debug("Resetting survey for chatId={}", chatId)
        sessions.remove(chatId)
        // 2) Загружаем пользователя из БД
        userRepo.findByTelegramId(chatId)
            .ifPresent { user ->
                user.casesCompleted = false
                userRepo.save(user)
                log.debug("surveyCompleted flag reset in DB for telegramId={}", user.telegramId)
            }
    }

    private suspend fun finish(chatId: Long, session: CaseSession) {
        log.debug("Finishing cases for chatId={}", chatId)
        // 1) сохраняем ответы
        session.dump().forEach { (idx, answer) ->
            caseAnswerRepo.save(
                CaseAnswer(
                    user = session.user,
                    caseIndex = idx,
                    answer = answer
                )
            )
            log.debug("Saved CaseAnswer idx={} for user={}", idx, session.user.telegramId)
        }

        // 2) отмечаем
        session.user.apply { casesCompleted = true }
            .also { surveyService.updateUser(it) }
        log.debug("casesCompleted flag set to true for user={}", session.user.telegramId)

        sessions.remove(chatId)

        // 3) собираем ответы 1-го этапа
        val surveyAnswers: Map<SurveyQuestion, String> =
            surveyAnswerRepo.findByUser(session.user)
                .associate { it.question to it.answer }
        log.debug(
            "Loaded {} survey answers for user={}",
            surveyAnswers.size,
            session.user.telegramId
        )

        // 4) ответы по кейсам
        val caseAnswers = session.dump()

        // 5) генерируем PDF
        val nick = session.user.username
        val pdfFile = pdf.build(
            chatId = chatId,
            nike = nick,
            surveyAns = surveyAnswers,
            caseAns = caseAnswers,
            cat = catalog
        )
        log.debug("Generated PDF for chatId={}", chatId)

        // 6) отправляем пользователю
        sender.send(
            chatId,
            "Вы ответили на все вопросы! 🏁\nСпасибо, мы свяжемся с вами скоро."
        )
        sender.send(chatId, "Кто такой тьютор в Школе «НИКА» 👇", kb.abortTutor())

        // 7) админу
        adminId?.let { admin ->
            sender.document(admin, pdfFile, "📥 Ответы кандидата @${nick ?: chatId}")
            log.debug("Sent PDF to admin={}", admin)
        }
    }
}
