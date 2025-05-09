package ru.greemlab.tutor_telegram_bot

import org.springframework.boot.Banner
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import ru.greemlab.tutor_telegram_bot.service.ReportService
import java.time.LocalDateTime

/**
 * Запуск из IDE: просто поставьте курсор в эту функцию и нажмите ▶
 */
fun main() {
    // 1) Telegram ID, для которого хотим пересобрать PDF
    val telegramId = 255046562L

    // 2) Поднимаем Spring-контекст без веб-слоя
    val ctx = SpringApplicationBuilder(TutorTelegramBotApplication::class.java)
        .bannerMode(Banner.Mode.OFF)
        .web(WebApplicationType.NONE)
        .run()

    try {
        // 3) Берём из контекста сервис, который умеет строить PDF
        val reportService = ctx.getBean(ReportService::class.java)

        // 4) Пересобираем
        val pdf = reportService.rebuildPdfFor(telegramId)

        // 5) Выводим путь в консоль
        println("✅ PDF for $telegramId: ${pdf.absolutePath}")
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        // 6) Корректно завершаем приложение
        ctx.close()
    }
}
