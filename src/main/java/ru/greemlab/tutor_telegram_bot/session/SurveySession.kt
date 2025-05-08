package ru.greemlab.tutor_telegram_bot.session

import ru.greemlab.tutor_telegram_bot.entity.TelegramUser
import ru.greemlab.tutor_telegram_bot.enums.SurveyQuestion

class SurveySession(
    /** Сущность пользователя, чтобы сохранить ответы в БД позже */
    val user: TelegramUser
) {
    private var index = 0
    private val answers = linkedMapOf<SurveyQuestion, String>()

    /** Текущий вопрос */
    val current: SurveyQuestion
        get() = SurveyQuestion.entries[index]

    /** Записываем ответ на текущий вопрос */
    fun answer(txt: String) {
        answers[current] = txt
    }

    /** Переводим на следующий вопрос.
     * @return true, если ещё есть вопросы */
    fun next(): Boolean =
        ++index < SurveyQuestion.entries.size

    /** Сливаем все ответы для сохранения */
    fun dump(): Map<SurveyQuestion, String> =
        answers.toMap()
}
