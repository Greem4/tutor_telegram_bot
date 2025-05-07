package ru.greemlab.tutor_telegram_bot.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.greemlab.tutor_telegram_bot.entity.SurveyAnswer
import ru.greemlab.tutor_telegram_bot.entity.TelegramUser

interface SurveyAnswerRepository : JpaRepository<SurveyAnswer, Long> {
    fun findAllByUser(user: TelegramUser): List<SurveyAnswer>
    fun findByUser(user: TelegramUser): List<SurveyAnswer>
}
