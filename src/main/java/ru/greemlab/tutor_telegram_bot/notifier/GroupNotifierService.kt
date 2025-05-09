package ru.greemlab.tutor_telegram_bot.notifier

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ru.greemlab.tutor_telegram_bot.entity.PendingNotification
import ru.greemlab.tutor_telegram_bot.enums.SurveyQuestion
import ru.greemlab.tutor_telegram_bot.repository.PendingNotificationRepository
import ru.greemlab.tutor_telegram_bot.service.PdfService
import ru.greemlab.tutor_telegram_bot.service.SenderService
import java.io.File
import java.time.LocalTime
import java.time.ZoneId

@Component
class GroupNotifierService(
    @Value("\${app.bot.group_id}") private val groupId: Long?,
    private val sender: SenderService,
    private val pdfService: PdfService,
    private val pendingRepo: PendingNotificationRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val zone      = ZoneId.of("Europe/Moscow")
    private val startTime = LocalTime.of(20, 42)
    private val endTime   = LocalTime.of(22, 0)

    /**
     * Собирает PDF (анкета или кейсы) и:
     * - если сейчас в окне 10–22 по Мск — шлёт в группу;
     * - иначе сохраняет в pending_notifications.
     */
    fun notifyOrDefer(
        chatId: Long,
        username: String?,
        surveyAns: Map<SurveyQuestion, String>,
        caseAns: Map<Int, String>,
        catalog: ru.greemlab.tutor_telegram_bot.catalog.CaseCatalog
    ) {
        // 1) собрать PDF единым методом
        val pdfFile: File = pdfService.build(
            chatId = chatId,
            username = username,
            surveyAns = surveyAns,
            caseAns = caseAns,
            cat = catalog
        )

        // 2) проверить время
        val now = LocalTime.now(zone)
        if (!now.isBefore(startTime) && now.isBefore(endTime)) {
            doSend(pdfFile, username, chatId)
        } else {
            pendingRepo.save(
                PendingNotification(
                    telegramId = groupId ?: chatId,
                    username   = username ?: chatId.toString(),
                )
            )
            log.info("GroupNotifier: отложена отправка для ${username ?: chatId} ($now не в ${startTime}–${endTime})")
        }
    }

    /** Отправка без проверки окна */
    private fun doSend(pdf: File, username: String?, chatId: Long) {
        val who = username?.let { "@$it" } ?: chatId.toString()
        val caption = "📥 PDF от $who"

        groupId?.let { gid ->
            sender.document(gid, pdf, caption)
        } ?: log.warn("GroupNotifier: groupId == null, пропускаем отправку")
    }

}
