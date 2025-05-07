package ru.greemlab.tutor_telegram_bot.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ru.greemlab.tutor_telegram_bot.catalog.CaseCatalog
import ru.greemlab.tutor_telegram_bot.entity.CaseAnswer
import ru.greemlab.tutor_telegram_bot.entity.TelegramUser
import ru.greemlab.tutor_telegram_bot.enums.SurveyQuestion
import ru.greemlab.tutor_telegram_bot.repository.CaseAnswerRepository
import ru.greemlab.tutor_telegram_bot.repository.SurveyAnswerRepository
import ru.greemlab.tutor_telegram_bot.session.CaseSession
import java.util.concurrent.ConcurrentHashMap

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
) {
    private val sessions = ConcurrentHashMap<Long, CaseSession>()

    /**
     * Старт второго этапа — кейсов.
     * _userId и _nick нужны только для сигнатуры, профиль берём из SurveyService
     */
    suspend fun start(chatId: Long) {
        // Получаем пользователя из кэша первого этапа
        val user: TelegramUser = surveyService.takeProfile(chatId)
            ?: run {
                sender.send(chatId, "Сначала пройдите опросник.", kb.start())
                return
            }

        if (user.casesCompleted) {
            sender.send(chatId, "Вы уже завершили кейсы ранее.", kb.remove())
            return
        }

        sessions[chatId] = CaseSession(user, catalog)
        askNext(chatId)
    }

    fun active(chatId: Long): Boolean =
        sessions.containsKey(chatId)

    fun cancel(chatId: Long) {
        sessions.remove(chatId)
    }

    suspend fun answer(chatId: Long, text: String) {
        val session = sessions[chatId] ?: return
        session.answer(text)

        if (session.next()) {
            askNext(chatId)
        } else {
            finish(chatId, session)
        }
    }

    private suspend fun askNext(chatId: Long) {
        val kase = sessions[chatId]?.current ?: return

        sender.photo(chatId, kase.image)

        val tasksText = if (kase.tasks.isNotEmpty()) {
            kase.tasks.joinToString("\n", prefix = "\n") { "• $it" }
        } else ""
        val prompt = "${kase.description}$tasksText\n\nВаш ответ:"
        sender.send(chatId, prompt, kb.cancel())
    }

    private suspend fun finish(chatId: Long, session: CaseSession) {
        // 1) сохраняем ответы кейсов
        session.dump().forEach { (idx, answer) ->
            caseAnswerRepo.save(
                CaseAnswer(
                    user = session.user,
                    caseIndex = idx,
                    answer = answer
                )
            )
        }
        // 2) ставим флаг и сохраняем пользователя
        session.user.apply { casesCompleted = true }
            .also { surveyService.updateUser(it) }
        sessions.remove(chatId)

        // 3) собираем ответы опроса первого этапа правильно
        val surveyAnswers: Map<SurveyQuestion, String> =
            surveyAnswerRepo.findByUser(session.user)
                .associate { it.question to it.answer }

        // 4) собираем ответы кейсов
        val caseAnswers: Map<Int, String> = session.dump()

        // 5) генерируем PDF
        val nick = session.user.username
        val pdfFile = pdf.build(
            chatId    = chatId,
            nike      = nick,
            surveyAns = surveyAnswers,  // теперь тип совпадает
            caseAns   = caseAnswers,
            cat       = catalog
        )

        /* кандидату */
        sender.send(
            chatId, """
            Вы ответили на все вопросы! 🏁
            Мы свяжемся с Вами в ближайшее время. 
            Спасибо.
        """.trimIndent()
        )
        sender.send(
            chatId, """
            Можете ознакомиться с тем, кто такой тьютор в ОАНО 
            Школа "НИКА" 👇
        """.trimIndent(), kb.abortTutor()
        )

        adminId?.let { admin ->
            sender.document(admin, pdfFile, "📥 Ответы кандидата @${nick ?: chatId}")
        }
    }
}
