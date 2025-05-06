package ru.greemlab.tutor_telegram_bot.service

import org.springframework.stereotype.Service
import ru.greemlab.tutor_telegram_bot.enums.SurveyQuestion
import ru.greemlab.tutor_telegram_bot.session.SurveySession
import java.util.concurrent.ConcurrentHashMap

@Service
class SurveyService(
    private val sender: SenderService,
    private val kb: KeyboardService,
) {

    /* активные опросы */
    private val sessions = ConcurrentHashMap<Long, SurveySession>()

    /* профиль пользователя (id / nick / phone) хранится отдельно -> нужен CaseService */
    private val profile = ConcurrentHashMap<Long, Pair<Long, String?>>()

    private val completed = ConcurrentHashMap<Long, Map<SurveyQuestion, String>>()

    fun cacheProfile(chat: Long, id: Long, nick: String?) {
        profile[chat] = Pair(id, nick)
    }

    fun profile(chat: Long): Pair<Long, String?>? = profile[chat]

    fun start(chat: Long, userId: Long, nick: String?) {
        sessions[chat] = SurveySession(userId, nick)
        cacheProfile(chat, userId, nick)
        ask(chat)
    }

    fun cancel(chat: Long) {
        sessions.remove(chat)
    }

    fun active(chat: Long): Boolean {
        return sessions.containsKey(chat)
    }

    fun answers(chat: Long): Map<SurveyQuestion, String> =
        sessions[chat]?.dump() ?: completed[chat] ?: emptyMap()

    /* ------------ логика заданий ------------ */

    private fun ask(chat: Long) {
        sessions[chat]?.let {                      //TODO вкл кнопку отмены
            sender.send(chat, it.current.prompt /*kb.cancel()*/)
        }
    }

    fun answer(chat: Long, txt: String) {
        val s = sessions[chat] ?: return
        s.answer(txt)
        if (s.next()) ask(chat) else finish(chat)
    }

    private fun finish(chat: Long) {
        val session = sessions[chat] ?: return

        completed[chat] = session.dump()

        sessions.remove(chat)
        sender.send(
            chat, """
            👏 Вы прошли 1 этап опросника на должность тьютора.
            ➡Впереди 2 этап - кейсы.
            Всего будет 3 кейса. 
            ⏱Примерное время ответа на кейсы - 30 мин. 
            Для продолжения нажмите ниже👇
        """.trimIndent(), kb.beginCases()
        )
    }
}