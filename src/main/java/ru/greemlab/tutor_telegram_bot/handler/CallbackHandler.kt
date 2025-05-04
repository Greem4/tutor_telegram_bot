package ru.greemlab.tutor_telegram_bot.handler

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import ru.greemlab.tutor_telegram_bot.enums.CallbackType
import ru.greemlab.tutor_telegram_bot.service.CaseService
import ru.greemlab.tutor_telegram_bot.service.SurveyService

@Component
class CallbackHandler(
    private val cases: CaseService,
    private val survey: SurveyService
) {
    suspend fun handle(q: CallbackQuery) = when (CallbackType.from(q.data)) {
        CallbackType.START_SURVEY ->
            survey.start(q.message.chatId, q.from.id, q.from.userName, null)
        CallbackType.START_CASES  ->
            cases.start (q.message.chatId, q.from.id, q.from.userName, null)
        null -> Unit
    }

}