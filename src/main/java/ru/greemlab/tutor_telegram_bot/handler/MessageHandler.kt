package ru.greemlab.tutor_telegram_bot.handler

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ru.greemlab.tutor_telegram_bot.enums.UserCommand
import ru.greemlab.tutor_telegram_bot.service.CaseService
import ru.greemlab.tutor_telegram_bot.service.KeyboardService
import ru.greemlab.tutor_telegram_bot.service.SenderService
import ru.greemlab.tutor_telegram_bot.service.SurveyService
import ru.greemlab.tutor_telegram_bot.text.BotMessages

@Component
class MessageHandler(
    @Value("\${app.bot.admin_id}") val adminId: Long?,
    private val survey: SurveyService,
    private val cases: CaseService,
    private val sender: SenderService,
    private val kb: KeyboardService,
) {

    suspend fun handle(chatId: Long, text: String) {
        // Команда /reset доступна только администратору
        if (text.trim() == "/reset" && chatId == adminId) {
            survey.reset(chatId)
            cases.reset(chatId)
            sender.send(chatId, "Сессии сброшены. /start")
            return
        }
        when {
            survey.active(chatId) -> handleSurvey(chatId, text)
            cases.active(chatId) -> handleCases(chatId, text)
            else -> handleRoot(chatId, text)
        }

    }

    private fun handleSurvey(chatId: Long, text: String) =
        if (text.equals(UserCommand.CANCEL.text, true)) {
            survey.cancel(chatId); sender.send(
                chatId, BotMessages.WELCOME_MESSAGE, kb.start()
            )
        } else survey.answer(chatId, text)

    private suspend fun handleCases(chatId: Long, text: String) =
        if (text.equals(UserCommand.CANCEL.text, true)) {
            cases.cancel(chatId); sender.send(
                chatId, BotMessages.WELCOME_MESSAGE, kb.beginCases()
            )
        } else cases.answer(chatId, text)

    private fun handleRoot(chatId: Long, text: String) = when (UserCommand.parse(text)) {
        UserCommand.START -> sender.send(chatId, BotMessages.WELCOME_MESSAGE, kb.start())
        UserCommand.CANCEL -> sender.send(
            chatId,
            BotMessages.WELCOME_MESSAGE,
            kb.remove()
        )

        null -> sender.send(chatId, BotMessages.UNKNOWN_COMMAND, kb.remove())
    }
}