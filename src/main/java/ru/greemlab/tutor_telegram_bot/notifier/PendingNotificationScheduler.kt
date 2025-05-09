package ru.greemlab.tutor_telegram_bot.notifier

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import ru.greemlab.tutor_telegram_bot.repository.PendingNotificationRepository
import java.time.LocalTime
import java.time.ZoneId

@Component
class PendingNotificationScheduler(
    private val pendingRepo: PendingNotificationRepository,
    private val notifier:    GroupNotifierService,
    private val catalog:     ru.greemlab.tutor_telegram_bot.catalog.CaseCatalog
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val zone      = ZoneId.of("Europe/Moscow")
    private val startTime = LocalTime.of(10, 0)
    private val endTime   = LocalTime.of(22, 0)

    /** Каждую минуту прогоняем отложенные и шлём, когда окно откроется */
    @Scheduled(fixedRate = 60_000)
    fun process() {
        val now = LocalTime.now(zone)
        if (now.isBefore(startTime) || !now.isBefore(endTime)) return

        pendingRepo.findBySentFalse().forEach { pn ->
            try {
                // используем тот же notifyOrDefer, но поскольку в окне, он отправит сразу
                notifier.notifyOrDefer(
                    chatId    = pn.telegramId,
                    username  = pn.username,
                    surveyAns = emptyMap(),
                    caseAns   = emptyMap(),
                    catalog   = catalog
                )
                pn.sent = true
                pendingRepo.save(pn)
                log.info("Отложенная нотификация отправлена для ${pn.username}")
            } catch (e: Exception) {
                log.error("Ошибка при отправке отложенной нотификации для ${pn.username}", e)
            }
        }
    }
}
