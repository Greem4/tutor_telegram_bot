package ru.greemlab.tutor_telegram_bot.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.greemlab.tutor_telegram_bot.entity.CaseAnswer

interface CaseAnswerRepository : JpaRepository<CaseAnswer, Long> {
}
