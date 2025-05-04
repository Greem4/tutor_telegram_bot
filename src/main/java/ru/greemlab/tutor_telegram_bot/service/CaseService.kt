package ru.greemlab.tutor_telegram_bot.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ru.greemlab.tutor_telegram_bot.catalog.CaseCatalog
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
        sessions[chat] = CaseSession(catalog)
        survey.cacheProfile(chat, userId, nick, phone)
        ask(chat)
    }

    suspend fun answer(chat: Long, txt: String) {
        val s = sessions[chat] ?: return
        s.answer(txt)
        if (!s.next()) finish(chat) else ask(chat)
    }

    private suspend fun ask(chatId: Long) =
        sessions[chatId]?.current?.let { sender.photo(chatId, it.image) }

    private suspend fun finish(chatId: Long) {
        val cs = sessions.remove(chatId) ?: return

        /* профиль кандидата */
        val (id, nickName, phone) = survey.profile(chatId) ?: Triple(chatId, null, null)

        /* -------- PDF -------- */
        val pdfFile = pdf.build(
            chatId,
            nickName,                               // ник в шапке
            survey.answers(chatId),               // ответы анкеты
            cs.dump(),                          // ответы кейсов
            catalog
        )

        /* кандидату */
        sender.send(
            chatId, """
            👏 Благодарим за ответы!
            Мы свяжемся с вами после проверки.
        """.trimIndent(), kb.remove()
        )

        /* админу */
        adminId?.takeIf { it != chatId }?.let { admin ->
            sender.document(admin, pdfFile, "📥 Ответы кандидата @${nickName ?: chatId}")
        }
        sender.document(chatId, pdfFile, "📥 Ответы кандидата @${nickName ?: chatId}")
    }
}
