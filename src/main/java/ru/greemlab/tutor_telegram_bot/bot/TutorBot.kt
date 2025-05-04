package ru.greemlab.tutor_telegram_bot.bot

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class TutorBot(
    @Value("\${app.bot.botUsername}")private val botUsername: String,
    @Value("\${app.bot.botToken}") private val botToken: String,
) : TelegramLongPollingBot(botToken) {

    override fun getBotUsername(): String? {
        return botUsername
    }

    override fun onUpdateReceived(update: Update?) {
    }
}