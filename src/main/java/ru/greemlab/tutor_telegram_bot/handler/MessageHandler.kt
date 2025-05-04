package ru.greemlab.tutor_telegram_bot.handler

import org.springframework.stereotype.Component
import ru.greemlab.tutor_telegram_bot.enums.UserCommand
import ru.greemlab.tutor_telegram_bot.service.CaseService
import ru.greemlab.tutor_telegram_bot.service.KeyboardService
import ru.greemlab.tutor_telegram_bot.service.SenderService
import ru.greemlab.tutor_telegram_bot.service.SurveyService
import ru.greemlab.tutor_telegram_bot.text.BotMessages

@Component
class MessageHandler(
    private val survey: SurveyService,
    private val cases : CaseService,
    private val sender: SenderService,
    private val kb    : KeyboardService,
) {

    suspend fun handle(chat: Long, text: String) = when {
        survey.active(chat) -> handleSurvey(chat, text)
        cases .active(chat) -> handleCases (chat, text)
        else                -> handleRoot  (chat, text)
    }

    private fun handleSurvey(chat: Long, text: String) =
        if (text.equals(UserCommand.CANCEL.text, true)) {
            survey.cancel(chat); sender.send(chat, BotMessages.WELCOME_MESSAGE, kb.start())
        } else survey.answer(chat, text)

    private suspend fun handleCases(chat: Long, text: String) =
        if (text.equals(UserCommand.CANCEL.text, true)) {
            cases.cancel(chat); sender.send(chat, BotMessages.WELCOME_MESSAGE, kb.beginCases())
        } else cases.answer(chat, text)

    private fun handleRoot(chat: Long, text: String) = when (UserCommand.parse(text)) {
        UserCommand.START  -> sender.send(chat, BotMessages.WELCOME_MESSAGE, kb.start())
        UserCommand.CANCEL -> sender.send(chat, BotMessages.WELCOME_MESSAGE, kb.remove())
        null               -> sender.send(chat, BotMessages.UNKNOWN_COMMAND, kb.remove())
    }
}