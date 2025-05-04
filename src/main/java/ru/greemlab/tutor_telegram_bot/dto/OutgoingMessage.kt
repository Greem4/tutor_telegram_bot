package ru.greemlab.tutor_telegram_bot.dto

import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard

data class OutgoingMessage(val chatId: Long, val text: String, val markup: ReplyKeyboard?)
