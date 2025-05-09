package ru.greemlab.tutor_telegram_bot.session

import ru.greemlab.tutor_telegram_bot.dto.UserInfo
import java.io.Serializable

/**
 * Сессия кейсов, тоже сохраняется в Redis
 */
class CaseSession(
    val user: UserInfo,
    var index: Int = 0,
    private val answers: LinkedHashMap<Int, String> = linkedMapOf()
) : Serializable {

    fun answer(txt: String) {
        answers[index] = txt
    }

    fun next(total : Int): Boolean =
        ++index < total

    fun dump(): Map<Int, String> = answers.toMap()
}
