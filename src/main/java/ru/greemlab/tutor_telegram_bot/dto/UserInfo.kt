package ru.greemlab.tutor_telegram_bot.dto

import java.io.Serializable

/**
 * Лёгкая «выжимка» из TelegramUser, пригодная для кладки в Redis.
 */
data class UserInfo(
    val id: Long,
    val username: String?
) : Serializable