package ru.greemlab.tutor_telegram_bot.handler

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import ru.greemlab.tutor_telegram_bot.enums.CallbackType
import ru.greemlab.tutor_telegram_bot.service.CaseService
import ru.greemlab.tutor_telegram_bot.service.SurveyService

@Component
class CallbackHandler(
    private val cases : CaseService,
    private val survey: SurveyService,
) {

    suspend fun handle(q: CallbackQuery) = when (CallbackType.from(q.data)) {
        CallbackType.START_SURVEY ->
            survey.start(
                chat  = q.message.chatId,
                userId = q.from.id,
                nick   = q.from.userName,
            )

        CallbackType.START_CASES  ->
            cases.start(
                chat   = q.message.chatId,
                userId = q.from.id,
                nick   = q.from.userName,
            )

        null -> Unit
    }
}
