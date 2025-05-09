package ru.greemlab.tutor_telegram_bot.notifier

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ru.greemlab.tutor_telegram_bot.catalog.CaseCatalog
import ru.greemlab.tutor_telegram_bot.entity.PendingNotification
import ru.greemlab.tutor_telegram_bot.enums.SurveyQuestion
import ru.greemlab.tutor_telegram_bot.repository.PendingNotificationRepository
import ru.greemlab.tutor_telegram_bot.service.PdfService
import ru.greemlab.tutor_telegram_bot.service.SenderService
import java.io.File
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@Component
class GroupNotifierService(
    @Value("\${app.bot.group_id}") private val groupId: Long?,      // ID целевой группы
    private val sender: SenderService,
    private val pdfService: PdfService,
    private val pendingRepo: PendingNotificationRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Часовой пояс для окна рассылки
    private val zone      = ZoneId.of("Europe/Moscow")
    // Разрешённый интервал: [10:00, 22:00)
    private val startTime = LocalTime.of(10, 0)
    private val endTime   = LocalTime.of(22, 0)

    /**
     * Сначала строим PDF, затем:
     * — если сейчас в [10:00,22:00) по МСК, шлём сразу в группу;
     * — иначе сохраняем PendingNotification с userChatId для отложенной отправки.
     */
    fun notifyOrDefer(
        chatId: Long,
        username: String?,
        surveyAns: Map<SurveyQuestion, String>,
        caseAns: Map<Int, String>,
        catalog: CaseCatalog,
        completedAt: LocalDateTime
    ) {
        // 1) Собираем PDF
        val pdfFile: File = pdfService.build(
            chatId    = chatId,
            username  = username,
            surveyAns = surveyAns,
            caseAns   = caseAns,
            cat       = catalog,
            completedAt = completedAt
        )

        // 2) Проверяем текущее время
        val now = LocalTime.now(zone)
        log.debug("GroupNotifier: now={}, window={}–{}", now, startTime, endTime)

        if (!now.isBefore(startTime) && now.isBefore(endTime)) {
            // 10:00 ≤ now < 22:00 — отправляем сразу
            doSend(pdfFile, username, chatId)
        } else {
            // иначе — откладываем
            pendingRepo.save(
                PendingNotification(
                    telegramId = chatId,                         // сохраняем chatId пользователя
                    username   = username ?: chatId.toString()
                )
            )
            log.info("Deferred notification for ${username ?: chatId}: now $now outside $startTime–$endTime")
        }
    }

    /**
     * Фактическая отправка PDF в группу (без проверки времени).
     */
    private fun doSend(pdf: File, username: String?, chatId: Long) {
        val who     = username?.let { "@$it" } ?: chatId.toString()
        val caption = "📥 PDF от $who"

        groupId?.let { gid ->
            sender.document(gid, pdf, caption)
            log.debug("Sent PDF to group $gid for $who")
        } ?: log.warn("GroupNotifier: groupId is null, cannot send PDF")
    }
}
