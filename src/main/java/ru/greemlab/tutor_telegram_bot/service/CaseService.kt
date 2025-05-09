package ru.greemlab.tutor_telegram_bot.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import ru.greemlab.tutor_telegram_bot.catalog.CaseCatalog
import ru.greemlab.tutor_telegram_bot.dto.UserInfo
import ru.greemlab.tutor_telegram_bot.entity.CaseAnswer
import ru.greemlab.tutor_telegram_bot.repository.CaseAnswerRepository
import ru.greemlab.tutor_telegram_bot.repository.SurveyAnswerRepository
import ru.greemlab.tutor_telegram_bot.repository.TelegramUserRepository
import ru.greemlab.tutor_telegram_bot.session.CaseSession
import java.io.Serializable

@Service
class CaseService(
    @Value("\${app.bot.admin_id}") private val adminId: Long?,
    private val catalog: CaseCatalog,
    private val sender: SenderService,
    private val kb: KeyboardService,
    private val pdf: PdfService,
    private val surveyService: SurveyService,
    private val surveyAnswerRepo: SurveyAnswerRepository,
    private val caseAnswerRepo: CaseAnswerRepository,
    private val userRepo: TelegramUserRepository,
    private val cacheManager: CacheManager          // ← внедряем CacheManager
) : Serializable {

    private val log = LoggerFactory.getLogger(javaClass)
    private val cache get() = cacheManager.getCache("caseSessions")!!

    suspend fun start(chatId: Long) {
        log.debug("Start cases for chatId={}", chatId)

        // 1) если в кэше уже есть CaseSession — возобновляем
        cache.get(chatId, CaseSession::class.java)?.let {
            log.debug("Resuming existing case session for chatId={}", chatId)
            askNext(chatId)
            return
        }

        // 2) проверяем профиль
        val user = surveyService.takeProfile(chatId) ?: run {
            log.warn(
                "Profile not found for chatId={}, asking to complete survey first", chatId
            )
            sender.send(chatId, "Сначала пройдите опросник.", kb.start())
            return
        }

        if (user.casesCompleted) {
            log.debug(
                "User {} has already completed cases, skipping start", user.telegramId
            )
            sender.send(chatId, "Вы уже завершили кейсы ранее.", kb.remove())
            return
        }

        // 3) создаём новую сессию и кладём в кэш
        val session = CaseSession(
            user = UserInfo(
                id = user.telegramId,
                username = user.username,
            )
        )
        cache.put(chatId, session)
        log.debug("Created CaseSession for chatId={}", chatId)
        askNext(chatId)
    }

    fun active(chatId: Long): Boolean = cache.get(chatId, CaseSession::class.java) != null

    fun cancel(chatId: Long) {
        log.debug("Canceling cases for chatId={}", chatId)
        cache.evict(chatId)
    }

    suspend fun answer(chatId: Long, text: String) {
        val session = cache.get(chatId, CaseSession::class.java) ?: run {
            log.warn("No active CaseSession for chatId={}, ignoring answer", chatId)
            return
        }

        session.answer(text)
        val hasNext = session.next(catalog.size())
        // обновляем кэш после изменения
        cache.put(chatId, session)

        if (hasNext) {
            askNext(chatId)
        } else {
            finish(chatId, session)
        }
    }

    private suspend fun askNext(chatId: Long) {
        val session = cache.get(chatId, CaseSession::class.java) ?: return
        val kase = catalog.byIndex(session.index)
        val num = session.index + 1
        log.debug("Asking case #{} to chatId={}", kase.id, chatId)
        sender.photo(chatId, kase.image, kb.cancel())
    }

    /**
     * Сбрасывает все данные кейсов и флаг в БД.
     */   //TODO к проду удалить 
    fun reset(chatId: Long) {
        log.debug("Resetting survey for chatId={}", chatId)
        cache.evict(chatId)
        // 2) Загружаем пользователя из БД
        userRepo.findByTelegramId(chatId).ifPresent { user ->
            user.casesCompleted = false
            userRepo.save(user)
            log.debug(
                "surveyCompleted flag reset in DB for telegramId={}", user.telegramId
            )
        }
    }

    private suspend fun finish(chatId: Long, session: CaseSession) {
        log.debug("Finishing cases for chatId={}", chatId)
        // 1. достаём «настоящего» пользователя из БД
        val user = userRepo.findByTelegramId(chatId)
            .orElseThrow { IllegalStateException("User ${session.user.id} not found") }

        // 2. сохраняем ответы
        session.dump().forEach { (idx, answer) ->
            caseAnswerRepo.save(CaseAnswer(
                user = user,
                caseIndex = idx,
                answer = answer,
            ))
        }

        // 3. ставим флаг «завершил кейсы»
        user.casesCompleted = true
        surveyService.updateUser(user)

        // 4. вычищаем сессию из кэша
        cache.evict(chatId)

        // 5. собираем PDF
        val surveyAnswers = surveyAnswerRepo.findByUser(user)
            .associate { it.question to it.answer }
        val caseAnswers = session.dump()
        val pdfFile = pdf.build(
            chatId = chatId,
            username = user.username,
            surveyAns = surveyAnswers,
            caseAns = caseAnswers,
            cat = catalog
        )

        // 6) отправляем пользователю
        sender.send(
            chatId, "Вы ответили на все вопросы! 🏁\nСпасибо, мы свяжемся с вами скоро."
        )
        sender.send(chatId, "Кто такой тьютор в Школе «НИКА» 👇", kb.abortTutor())

        // 7) админу
        adminId?.let { admin ->
            sender.document(
                admin, pdfFile, "📥 Ответы кандидата @${session.user.username ?: chatId}"
            )
            log.debug("Sent PDF to admin={}", admin)
        }
    }
}
