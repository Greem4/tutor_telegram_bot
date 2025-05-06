package ru.greemlab.tutor_telegram_bot.session

import ru.greemlab.tutor_telegram_bot.enums.SurveyQuestion

class SurveySession(
    val userId : Long,
    val nick   : String?,
) {
    private var i = 0
    private val answers = linkedMapOf<SurveyQuestion, String>()
    val current get() = SurveyQuestion.entries[i]
    fun answer(txt: String) {
        answers[current] = txt
    }

    fun next(): Boolean = ++i < SurveyQuestion.entries.size
    fun dump() = answers.toMap()
}