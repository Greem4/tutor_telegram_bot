package ru.greemlab.tutor_telegram_bot.session

import ru.greemlab.tutor_telegram_bot.catalog.CaseCatalog

class CaseSession(private val cat: CaseCatalog) {
    private var i = 0
    private val answers = linkedMapOf<Int, String>()
    val current get() = cat.byIndex(i)
    fun answer(txt: String) {
        answers[i] = txt
    }

    fun next() = ++i < cat.size()
    val done get() = answers.size == cat.size()
    fun dump() = answers.toMap()
}