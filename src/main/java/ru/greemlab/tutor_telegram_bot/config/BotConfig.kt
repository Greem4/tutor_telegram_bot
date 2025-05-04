package ru.greemlab.tutor_telegram_bot.config

import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import ru.greemlab.tutor_telegram_bot.bot.TutorBot

@Component
class BotConfig(private val bot: TutorBot) {

    @Bean @Throws(TelegramApiException::class)
    fun telegramBotsApi(): TelegramBotsApi =
        TelegramBotsApi(DefaultBotSession::class.java).apply { registerBot(bot) }
}