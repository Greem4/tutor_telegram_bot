package ru.greemlab.tutor_telegram_bot.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard
import ru.greemlab.tutor_telegram_bot.dto.OutgoingMessage
import java.io.File

@Service
class SenderService(private val events: ApplicationEventPublisher) {

    private val scope = CoroutineScope(Dispatchers.IO)

    fun send(chat: Long, text: String, kb: ReplyKeyboard? = null) =
        scope.launch { events.publishEvent(OutgoingMessage(chat, text, kb)) }

    fun photo(chat: Long, url: String) =
        scope.launch { events.publishEvent(SendPhoto(chat.toString(), InputFile(url))) }

    fun document(chat: Long, file: File, caption: String) =
        scope.launch { events.publishEvent(SendDocument().apply {
            chatId   = chat.toString(); this.document = InputFile(file)
            this.caption = caption; parseMode = "HTML"
        }) }
}