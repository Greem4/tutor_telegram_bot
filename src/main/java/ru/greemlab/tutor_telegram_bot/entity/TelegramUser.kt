package ru.greemlab.tutor_telegram_bot.entity

import jakarta.persistence.*
import java.io.Serializable

@Entity
@Table(
    name = "telegram_user",
    uniqueConstraints = [UniqueConstraint(columnNames = ["telegram_id"])]
)
data class TelegramUser(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "telegram_id", nullable = false)
    val telegramId: Long,

    @Column
    val username: String? = null,

    @Column(nullable = false)
    var surveyCompleted: Boolean = false,

    @Column(nullable = false)
    var casesCompleted: Boolean = false
) : Serializable
