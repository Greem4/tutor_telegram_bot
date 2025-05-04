package ru.greemlab.tutor_telegram_bot.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ru.greemlab.tutor_telegram_bot.catalog.CaseCatalog
import ru.greemlab.tutor_telegram_bot.enums.SurveyQuestion
import ru.greemlab.tutor_telegram_bot.session.CaseSession
import java.util.concurrent.ConcurrentHashMap

@Service
class CaseService(
    @Value("\${app.bot.admin_id}") private val adminId: Long?,
    private val catalog: CaseCatalog,
    private val sender: SenderService,
    private val kb: KeyboardService,
    private val pdf: PdfService,
    private val survey: SurveyService,
) {
    private val sessions = ConcurrentHashMap<Long, CaseSession>()

    fun active(chat: Long) = sessions.containsKey(chat)
    fun cancel(chat: Long) {
        sessions.remove(chat)
    }

    suspend fun start(chat: Long, userId: Long, nick: String?, phone: String?) {
        sessions[chat] = CaseSession(catalog).also {
            // сохраняем ID/ник/телефон в SurveyService, чтобы потом взять
            survey.cacheProfile(chat, userId, nick, phone)
        }
        ask(chat)
    }


     fun answer(chat: Long, txt: String) {
        val s = sessions[chat] ?: return
        s.answer(txt)
        if (!s.next()) finish(chat) else ask(chat)
    }

    private  fun ask(chat: Long) =
        sessions[chat]?.current?.let { sender.photo(chat, it.image) }

    private fun finish(chat: Long) {
        val cs = sessions.remove(chat) ?: return
        val pdfFile = pdf.build(
            chat,
            survey.answers(chat)[SurveyQuestion.FULL_NAME],
            null,
            survey.answers(chat),
            cs.dump(),
            catalog
        )

        sender.document(chat, pdfFile, "📄 Ваши ответы приняты, спасибо!")
        sender.send(
            chat, """
            👏 Благодарим за ответы!
            Мы свяжемся с вами после проверки.
        """.trimIndent(), kb.remove()
        )
        sender.document(chat, pdfFile, "")

        adminId?.takeIf { it != chat }?.let {
            sender.document(it, pdfFile, "📥 Ответы кандидата ID $chat")
        }
    }

    private fun buildResume(id: Long, nick: String?, phone: String?) = """
    📝 Кандидат
    ID: <code>$id</code>
    Ник: ${nick?.let { "@$it" } ?: "—"}
    Телефон: ${phone ?: "—"}
""".trimIndent()

}