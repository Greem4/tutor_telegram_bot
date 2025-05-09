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
    private val zone      = ZoneId.of("Europe/Moscow")
    private val startTime = LocalTime.of(20, 42)
    private val endTime   = LocalTime.of(22, 0)

    /** Каждую минуту прогоняем отложенные и шлём, когда окно откроется */
    @Scheduled(fixedRate = 60_000)
    fun process() {
        val now = LocalTime.now(zone)
        log.debug("Scheduler запускается — Pending to send: ${pendingRepo.findBySentFalse().size}")
        if (now.isBefore(startTime) || !now.isBefore(endTime)) return

        pendingRepo.findBySentFalse().forEach { pn ->
            val user = userRepo.findByTelegramId(pn.telegramId)
                .orElse(null)
            if (user == null) {
                // не нашли — просто помечаем, чтобы не зациклить
                pn.sent = true
                pendingRepo.save(pn)
                return@forEach
            }

            // 1) достаём ответы анкеты из БД
            val surveyAns: Map<SurveyQuestion,String> = surveyAnswerRepo
                .findByUser(user)
                .associate { answer ->
                    answer.question to answer.answer
                }

            // 2) достаём ответы кейсов из БД
            val caseAns: Map<Int,String> = caseAnswerRepo
                .findByUser(user)
                .associate { ca ->
                    ca.caseIndex to ca.answer
                }

            try {
                // 3) отправляем (или откладываем вновь, но сейчас уже inWindow)
                notifier.notifyOrDefer(
                    chatId    = pn.telegramId,
                    username  = pn.username,
                    surveyAns = surveyAns,
                    caseAns   = caseAns,
                    catalog   = catalog
                )
                pn.sent = true
                pendingRepo.save(pn)
            } catch (e: Exception) {
                log.error("Ошибка при отправке отложенной нотификации для ${pn.username}", e)
            }
        }
    }
}
