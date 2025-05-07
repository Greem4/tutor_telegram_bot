package ru.greemlab.tutor_telegram_bot.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(name = "telegram_user", uniqueConstraints = [
    UniqueConstraint(columnNames = ["telegram_id"])
])
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
)
