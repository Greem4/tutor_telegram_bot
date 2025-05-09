package ru.greemlab.tutor_telegram_bot.notifier

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import ru.greemlab.tutor_telegram_bot.catalog.CaseCatalog
import ru.greemlab.tutor_telegram_bot.enums.SurveyQuestion
import ru.greemlab.tutor_telegram_bot.repository.CaseAnswerRepository
import ru.greemlab.tutor_telegram_bot.repository.PendingNotificationRepository
import ru.greemlab.tutor_telegram_bot.repository.SurveyAnswerRepository
import ru.greemlab.tutor_telegram_bot.repository.TelegramUserRepository
import java.time.LocalTime
import java.time.ZoneId

@Component
class PendingNotificationScheduler(
    private val pendingRepo: PendingNotificationRepository,
    private val notifier:    GroupNotifierService,
    private val catalog:     CaseCatalog,
    private val surveyAnswerRepo: SurveyAnswerRepository,
    private val caseAnswerRepo: CaseAnswerRepository,
    private val userRepo: TelegramUserRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // часовой пояс для проверки “рабочего окна”
    private val zone      = ZoneId.of("Europe/Moscow")
    // начало окна (включительно): с этого времени можно отправлять
    private val startTime = LocalTime.of(10, 0)
    // конец окна (исключительно): до этого времени можно отправлять
    private val endTime   = LocalTime.of(22, 0)

    /**
     * Запускается каждые 60 000 мс (1 минута).
     * 1) Логируем текущее время и сколько отложенных есть в базе.
     * 2) Если ещё не начало окна или уже после конца — выходим.
     * 3) Иначе проходим по всем `sent = false`, собираем их ответы из БД
     *    и передаём в `notifyOrDefer` (которая так как окно уже открыто, сразу шлёт).
     * 4) Отмечаем каждую запись как `sent = true`.
     */
    @Scheduled(fixedRate = 60_000)
    fun process() {
        val now = LocalTime.now(zone)
        log.debug(
            "Scheduler tick at {}; pending count = {}",
            now,
            pendingRepo.findBySentFalse().size
        )

        // Если текущее время < startTime или ≥ endTime — выходим
        if (now.isBefore(startTime) || !now.isBefore(endTime)) {
            log.debug(" Scheduler skipping: outside window {}–{}", startTime, endTime)
            return
        }

        // Иначе — обрабатываем все отложенные
        pendingRepo.findBySentFalse().forEach { pn ->
            log.debug(" Processing pending id=${pn.id}, user=${pn.username}")
            val user = userRepo.findByTelegramId(pn.telegramId).orElse(null)
            if (user == null) {
                log.warn("  → User ${pn.telegramId} not found, marking sent")
                pn.sent = true
                pendingRepo.save(pn)
                return@forEach
            }

            // Собираем ответы анкеты
            val surveyAns: Map<SurveyQuestion,String> = surveyAnswerRepo
                .findByUser(user)
                .associate { ans -> ans.question to ans.answer }
            // Собираем ответы кейсов
            val caseAns: Map<Int,String> = caseAnswerRepo
                .findByUser(user)
                .associate { ca -> ca.caseIndex to ca.answer }

            try {
                notifier.notifyOrDefer(
                    chatId    = pn.telegramId,
                    username  = pn.username,
                    surveyAns = surveyAns,
                    caseAns   = caseAns,
                    catalog   = catalog,
                    completedAt = pn.createdAt
                )
                pn.sent = true
                pendingRepo.save(pn)
                log.debug("  → Sent and marked id=${pn.id}")
            } catch (e: Exception) {
                log.error("  → Error sending pending id=${pn.id}", e)
            }
        }
    }
}
