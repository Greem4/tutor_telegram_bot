package ru.greemlab.tutor_telegram_bot.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.greemlab.tutor_telegram_bot.entity.TelegramUser
import java.util.*

interface TelegramUserRepository : JpaRepository<TelegramUser, Long> {
    fun findByTelegramId(telegramId: Long): Optional<TelegramUser>
}
