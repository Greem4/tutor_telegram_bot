package ru.greemlab.tutor_telegram_bot.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "pending_notification")
data class PendingNotification(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "telegram_id", nullable = false)
    val telegramId: Long,

    @Column(nullable = false)
    val username: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var sent: Boolean = false
)
