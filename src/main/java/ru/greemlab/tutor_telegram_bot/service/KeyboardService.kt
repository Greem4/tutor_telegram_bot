package ru.greemlab.tutor_telegram_bot.service

import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import ru.greemlab.tutor_telegram_bot.enums.CallbackType
import ru.greemlab.tutor_telegram_bot.enums.UserCommand

@Service
class KeyboardService {

    fun start() = InlineKeyboardMarkup(
        listOf(
            listOf(
                InlineKeyboardButton("НАЧАТЬ 🚀").apply {
                    callbackData = CallbackType.START_SURVEY.id
                }
            )))

    fun beginCases() = InlineKeyboardMarkup(
        listOf(
            listOf(
        InlineKeyboardButton("НАЧАТЬ 2‑й ЭТАП ➡️").apply {
            callbackData = CallbackType.START_CASES.id
        }
    )))

    fun cancel(): ReplyKeyboardMarkup =
        ReplyKeyboardMarkup(listOf(KeyboardRow().apply { add(UserCommand.CANCEL.text) })).apply {
            resizeKeyboard = true
        }

    fun remove() = ReplyKeyboardRemove(true)
}