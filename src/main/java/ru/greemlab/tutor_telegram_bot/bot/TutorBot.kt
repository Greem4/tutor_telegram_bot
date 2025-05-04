package ru.greemlab.tutor_telegram_bot.bot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.Update
import ru.greemlab.tutor_telegram_bot.dto.OutgoingMessage
import ru.greemlab.tutor_telegram_bot.handler.CallbackHandler
import ru.greemlab.tutor_telegram_bot.handler.MessageHandler

@Component
class TutorBot(
    private val messageHandler: MessageHandler,
    private val callbackHandler: CallbackHandler,
    @Value("\${app.bot.botUsername}") private val username: String,
    @Value("\${app.bot.botToken}")    token: String,
) : TelegramLongPollingBot(token) {

    private val log   = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun getBotUsername() = username

    /** Метод TelegramAPI → не suspend, поэтому сами уходим в корутину */
    override fun onUpdateReceived(update: Update) {
        scope.launch {
            when {
                update.hasMessage() && update.message.hasText() ->
                    messageHandler.handle(update.message.chatId, update.message.text)
                update.hasCallbackQuery() ->
                    callbackHandler.handle(update.callbackQuery)
            }
        }
    }

    /* ---------- события от приложения ---------- */

    @EventListener
    fun on(out: OutgoingMessage) = scope.launch {
        runCatching {
            execute(SendMessage(out.chatId.toString(), out.text).apply {
                parseMode   = "HTML"
                replyMarkup = out.markup
            })
        }.onFailure { log.error("send failed", it) }
    }

    @EventListener
    fun on(photo: SendPhoto) = scope.launch {
        runCatching { execute(photo) }
            .onFailure { log.error("send photo failed", it) }
    }

    @EventListener
    fun on(doc: SendDocument) = scope.launch {
        runCatching { execute(doc) }
            .onFailure { log.error("send document failed", it) }
    }
}