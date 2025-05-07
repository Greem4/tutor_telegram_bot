package ru.greemlab.tutor_telegram_bot.session

import ru.greemlab.tutor_telegram_bot.catalog.CaseCatalog
import ru.greemlab.tutor_telegram_bot.entity.TelegramUser

class CaseSession(
    val user: TelegramUser,
    private val cat: CaseCatalog
) {
    private var i = 0
    private val answers = linkedMapOf<Int, String>()
    val current get() = cat.byIndex(i)

    fun answer(txt: String) {
        answers[i] = txt
    }

    fun next(): Boolean = ++i < cat.size()
    fun dump(): Map<Int, String> = answers.toMap()
}
