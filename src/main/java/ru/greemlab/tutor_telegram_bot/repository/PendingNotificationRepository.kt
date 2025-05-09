package ru.greemlab.tutor_telegram_bot.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.greemlab.tutor_telegram_bot.entity.PendingNotification

interface PendingNotificationRepository : JpaRepository<PendingNotification, Long> {
    fun findBySentFalse(): List<PendingNotification>
}
