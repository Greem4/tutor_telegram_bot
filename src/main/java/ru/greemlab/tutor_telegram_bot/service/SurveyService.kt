package ru.greemlab.tutor_telegram_bot.service

import org.springframework.stereotype.Service
import ru.greemlab.tutor_telegram_bot.session.SurveySession
import java.util.concurrent.ConcurrentHashMap

@Service
class SurveyService(
    private val sender: SenderService,
    private val kb: KeyboardService,
) {
    private val sessions = ConcurrentHashMap<Long, SurveySession>()

    suspend fun start(chat: Long, userId: Long, nick: String?, phone: String?) {
        sessions[chat] = SurveySession(userId, nick, phone)
        ask(chat)
    }


    fun cancel(chat: Long) {
        sessions -= chat
    }

    fun active(chat: Long): Boolean {
        return sessions.containsKey(chat)
    }

    fun answers(chat: Long) = sessions[chat]?.dump() ?: emptyMap()

    suspend fun answer(chat: Long, txt: String) {
        val s = sessions[chat] ?: return
        s.answer(txt)
        if (s.next()) ask(chat) else finish(chat)
    }

    private suspend fun ask(chat: Long) {
        sessions[chat]?.let { sender.send(chat, it.current.prompt, kb.cancel()) }
    }

    private suspend fun finish(chat: Long) {
        sessions -= chat
        sender.send(
            chat, """
            👏 Вы прошли 1‑й этап!
            ➡ Впереди 2‑й этап — кейсы (3шт, ≈30мин).
            Нажмите кнопку ниже, чтобы продолжить.
        """.trimIndent(), kb.beginCases()
        )
    }

    fun cacheProfile(chat: Long, id: Long, nick: String?, phone: String?) {
        profile[chat] = Triple(id, nick, phone)
    }

    fun profile(chat: Long) = profile[chat]          // геттер
    private val profile = ConcurrentHashMap<Long, Triple<Long, String?, String?>>()


}