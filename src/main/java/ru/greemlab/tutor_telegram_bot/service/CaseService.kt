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
    private val sender : SenderService,
    private val kb     : KeyboardService,
    private val pdf    : PdfService,
    private val survey : SurveyService,
) {
    private val sessions = ConcurrentHashMap<Long, CaseSession>()

    fun active(chat: Long) = sessions.containsKey(chat)
    fun cancel(chat: Long) { sessions.remove(chat) }

    suspend fun start(chat: Long, userId: Long, nick: String?, phone: String?) {
        sessions[chat] = CaseSession(catalog)
        survey.cacheProfile(chat, userId, nick, phone)
        ask(chat)
    }

    suspend fun answer(chat: Long, txt: String) {
        val s = sessions[chat] ?: return
        s.answer(txt)
        if (!s.next()) finish(chat) else ask(chat)
    }

    private suspend fun ask(chat: Long) =
        sessions[chat]?.current?.let { sender.photo(chat, it.image) }

    private suspend fun finish(chat: Long) {
        val cs = sessions.remove(chat) ?: return

        /* профиль кандидата */
        val (id, nick, phone) = survey.profile(chat) ?: Triple(chat, null, null)

        /* -------- PDF -------- */
        val pdfFile = pdf.build(
            chat,
            nick,                               // ник в шапке
            phone,
            survey.answers(chat),               // ответы анкеты
            cs.dump(),                          // ответы кейсов
            catalog
        )

        /* кандидату */
        sender.send(chat, """
            👏 Благодарим за ответы!
            Мы свяжемся с вами после проверки.
        """.trimIndent(), kb.remove())

        /* админу */
        adminId?.takeIf { it != chat }?.let { admin ->
            sender.send(admin, buildResume(id, nick, phone), null)
            sender.document(admin, pdfFile, "📥 Ответы кандидата @$nick")
        }
        sender.send(chat, buildResume(id, nick, phone), null)
        sender.document(chat,pdfFile, "📥 Ответы кандидата @$nick")
    }

    private fun buildResume(id: Long, nick: String?, phone: String?) = """
        📝 Кандидат
        Ник: ${nick?.let { "@$it" } ?: "—"}
        Телефон: ${phone ?: "—"}
    """.trimIndent()
}
