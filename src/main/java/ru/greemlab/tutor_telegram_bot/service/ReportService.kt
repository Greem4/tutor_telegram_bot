package ru.greemlab.tutor_telegram_bot.service

import org.springframework.stereotype.Service
import ru.greemlab.tutor_telegram_bot.catalog.CaseCatalog
import ru.greemlab.tutor_telegram_bot.repository.CaseAnswerRepository
import ru.greemlab.tutor_telegram_bot.repository.SurveyAnswerRepository
import ru.greemlab.tutor_telegram_bot.repository.TelegramUserRepository
import java.io.File
import java.time.LocalDateTime

@Service
class ReportService( //TODO для тех нужд
    private val userRepo: TelegramUserRepository,
    private val surveyAnswerRepo: SurveyAnswerRepository,
    private val caseAnswerRepo: CaseAnswerRepository,
    private val pdfService: PdfService,
    private val catalog: CaseCatalog,
) {
    /**
     * Собирает PDF по всем ответам из БД для telegramId.
     */
    fun rebuildPdfFor(telegramId: Long): File {
        val user = userRepo.findByTelegramId(telegramId)
            .orElseThrow { IllegalArgumentException("User $telegramId not found") }

        val surveyAns = surveyAnswerRepo.findByUser(user)
            .associate { it.question to it.answer }
        val caseAns   = caseAnswerRepo.findByUser(user)
            .associate { it.caseIndex to it.answer }

        val completedAt = LocalDateTime.now()
        return pdfService.build(
            chatId      = telegramId,
            username    = user.username,
            surveyAns   = surveyAns,
            caseAns     = caseAns,
            cat         = catalog,
            completedAt = completedAt
        )
    }
}