package ru.greemlab.tutor_telegram_bot.handler

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ru.greemlab.tutor_telegram_bot.service.CaseService
import ru.greemlab.tutor_telegram_bot.service.KeyboardService
import ru.greemlab.tutor_telegram_bot.service.SenderService
import ru.greemlab.tutor_telegram_bot.service.SurveyService
import ru.greemlab.tutor_telegram_bot.text.BotMessages

@Component
class MessageHandler(
    @Value("\${app.bot.admin_id}") private val adminId: Long?,
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

        // 2) /start — только показывает инструкцию, не продолжает
        if (text.equals(UserCommand.START.text, ignoreCase = true)) {
            when {
                survey.active(chatId) -> {
                    // на этапе опроса — показываем инструкцию по опросу
                    sender.send(chatId, BotMessages.WELCOME_MESSAGE, kb.start())
                }

                cases.active(chatId) -> {
                    // на этапе кейсов — показываем инструкцию по кейсам
                    sender.send(
                        chatId,
                        BotMessages.CASES_WELCOME_MESSAGE,
                        kb.beginCases()
                    )
                }

                else -> {
                    // ничего не запущено — общий главный экран
                    sender.send(chatId, BotMessages.WELCOME_MESSAGE, kb.start())
                }
            }
            return
        }

        // 3) BEGIN_SURVEY — реальный старт или продолжение опроса
        if (text.equals(UserCommand.BEGIN_SURVEY.text, ignoreCase = true)) {
            survey.start(chatId, chatId /* telegramId */, null /* nick */)
            return
        }

        // 4) BEGIN_CASES — реальный старт или продолжение кейсов
        if (text.equals(UserCommand.BEGIN_CASES.text, ignoreCase = true)) {
            cases.start(chatId)
            return
        }

        // 5) /cancel — отмена текущего этапа
        if (text.equals(UserCommand.CANCEL.text, ignoreCase = true)) {
            when {
                survey.active(chatId) -> {
                    survey.cancel(chatId)
                    sender.send(chatId, BotMessages.WELCOME_MESSAGE, kb.start())
                }

                cases.active(chatId) -> {
                    cases.cancel(chatId)
                    sender.send(chatId, BotMessages.WELCOME_MESSAGE, kb.start())
                }

                else -> {
                    sender.send(chatId, BotMessages.WELCOME_MESSAGE, kb.remove())
                }
            }
            return
        }

        // 6) Обработка ответов на опрос или на кейсы
        when {
            survey.active(chatId) -> handleSurvey(chatId, text)
            cases.active(chatId) -> handleCases(chatId, text)
            else -> handleRoot(chatId, text)
        }
    }

    private fun handleSurvey(chatId: Long, text: String) {
        if (text.equals(UserCommand.CANCEL.text, ignoreCase = true)) {
            survey.cancel(chatId)
            sender.send(chatId, BotMessages.WELCOME_MESSAGE, kb.start())
        } else {
            survey.answer(chatId, text)
        }
    }

    private suspend fun handleCases(chatId: Long, text: String) {
        if (text.equals(UserCommand.CANCEL.text, ignoreCase = true)) {
            cases.cancel(chatId)
            sender.send(chatId, BotMessages.CASES_WELCOME_MESSAGE, kb.beginCases())
        } else {
            cases.answer(chatId, text)
        }
    }

    private fun handleRoot(chatId: Long, text: String) = when (UserCommand.parse(text)) {
        UserCommand.START -> sender.send(chatId, BotMessages.WELCOME_MESSAGE, kb.start())
        UserCommand.CANCEL -> sender.send(
            chatId,
            BotMessages.WELCOME_MESSAGE,
            kb.remove()
        )

        null -> sender.send(chatId, BotMessages.UNKNOWN_COMMAND, kb.remove())
        else -> sender.send(chatId, BotMessages.UNKNOWN_COMMAND, kb.remove())
    }
}