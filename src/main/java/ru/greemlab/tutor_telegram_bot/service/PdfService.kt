package ru.greemlab.tutor_telegram_bot.service

import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.action.PdfAction
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.*
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.itextpdf.layout.properties.VerticalAlignment
import org.springframework.stereotype.Service
import ru.greemlab.tutor_telegram_bot.catalog.CaseCatalog
import ru.greemlab.tutor_telegram_bot.enums.SurveyQuestion
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class PdfService {

    companion object {
        // Пути к шрифтам
        private const val REGULAR_FONT            = "/fonts/Arial.ttf"
        private const val BOLD_FONT               = "/fonts/Arial-Bold.ttf"

        // Размеры шрифтов
        private const val TITLE_FONT_SIZE         = 12f
        private const val BASE_FONT_SIZE          = 11f

        // Отступы секций/заголовков
        private const val TITLE_MARGIN_BOTTOM     = 8f
        private const val HEADER_MARGIN_BOTTOM    = 12f
        private const val SECTION_MARGIN_BOTTOM   = 8f
        private const val TABLE_MARGIN_BOTTOM     = 16f
        private const val CASES_MARGIN_TOP        = 12f
        private const val CASES_MARGIN_BOTTOM     = 6f

        // Пропорции колонок [№, Вопрос, Ответ]
        private val COLUMN_RATIOS = floatArrayOf(1f, 7f, 4f)

        // Внутренние отступы ячеек: горизонталь × вертикаль
        private const val PAD_H                   = 4f
        private const val PAD_V                   = 2f

        // Формат даты/времени в шапке
        private const val DATE_PATTERN            = "yyyy-MM-dd HH:mm"
    }

    fun build(
        chatId: Long,
        username: String?,
        surveyAns: Map<SurveyQuestion, String>,
        caseAns: Map<Int, String>,
        cat: CaseCatalog,
        completedAt: LocalDateTime
    ): File {
        // Создание временного файла PDF
        val file = Files
            .createTempFile("Ответы кандидаты_@${username ?: chatId}_", ".pdf")
            .toFile()

        PdfWriter(file).use { writer ->
            PdfDocument(writer).use { pdf ->
                Document(pdf).use { doc ->

                    // 1) Загрузка шрифтов и установка базового шрифта
                    val regular: PdfFont = PdfFontFactory.createFont(
                        REGULAR_FONT, PdfEncodings.IDENTITY_H,
                        PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED
                    )
                    val bold: PdfFont = PdfFontFactory.createFont(
                        BOLD_FONT, PdfEncodings.IDENTITY_H,
                        PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED
                    )
                    doc.setFont(regular).setFontSize(BASE_FONT_SIZE)

                    // 2) Заголовок документа
                    doc.add(
                        Paragraph("ОТВЕТЫ ОПРОСНИКА НА ДОЛЖНОСТЬ ТЬЮТОРА")
                            .setFont(bold)
                            .setFontSize(TITLE_FONT_SIZE)
                            .setTextAlignment(TextAlignment.CENTER)
                            .setUnderline(1f, -2f)
                            .setMarginBottom(TITLE_MARGIN_BOTTOM)
                    )

                    // 3) Блок кандидата и даты заполнения
                    val formattedDate = completedAt.format(DateTimeFormatter.ofPattern(DATE_PATTERN))
                    val header = Paragraph()
                        .setFont(regular)
                        .setFontSize(BASE_FONT_SIZE)
                        .setTextAlignment(TextAlignment.RIGHT)
                        .setMarginBottom(HEADER_MARGIN_BOTTOM)
                    if (!username.isNullOrBlank()) {
                        val link = Link("@$username", PdfAction.createURI("https://t.me/$username"))
                            .setFont(bold)
                            .setFontSize(BASE_FONT_SIZE)
                            .setFontColor(ColorConstants.BLUE)
                            .setUnderline()
                        header.add("Кандидат ").add(link).add(" от: $formattedDate")
                    } else {
                        header.add("От: $formattedDate")
                    }
                    doc.add(header)

                    // 4) Секция «Опрос»
                    doc.add(
                        Paragraph("Опрос")
                            .setFont(bold)
                            .setFontSize(BASE_FONT_SIZE)
                            .setTextAlignment(TextAlignment.CENTER)
                            .setMarginBottom(SECTION_MARGIN_BOTTOM)
                    )

                    // 5) Таблица «Опрос»
                    val table = Table(UnitValue.createPercentArray(COLUMN_RATIOS))
                        .useAllAvailableWidth()
                        .setMarginBottom(TABLE_MARGIN_BOTTOM)

                    // 5.1) Заголовки столбцов таблицы
                    listOf("№", "Вопрос", "Ответ").forEach { title ->
                        table.addHeaderCell(
                            Cell().add(
                                Paragraph(title)
                                    .setFont(bold)
                                    .setFontSize(BASE_FONT_SIZE)
                                    .setTextAlignment(TextAlignment.CENTER)
                            )
                                .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                                .setPaddingTop(PAD_V)
                                .setPaddingRight(PAD_H)
                                .setPaddingBottom(PAD_V)
                                .setPaddingLeft(PAD_H)
                        )
                    }

                    // 5.2) Заполнение строк ответами
                    SurveyQuestion.entries.forEachIndexed { idx, question ->
                        // Столбец №
                        table.addCell(
                            Cell().add(
                                Paragraph("${idx + 1}")
                                    .setFont(regular)
                                    .setFontSize(BASE_FONT_SIZE)
                                    .setTextAlignment(TextAlignment.CENTER)
                            )
                                .setPaddingTop(PAD_V)
                                .setPaddingRight(PAD_H)
                                .setPaddingBottom(PAD_V)
                                .setPaddingLeft(PAD_H)
                                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                        )

                        // Столбец Вопрос
                        table.addCell(
                            Cell().add(
                                Paragraph(question.label())
                                    .setFont(regular)
                                    .setFontSize(BASE_FONT_SIZE)
                                    .setTextAlignment(TextAlignment.LEFT)
                            )
                                .setPaddingTop(PAD_V)
                                .setPaddingRight(PAD_H)
                                .setPaddingBottom(PAD_V)
                                .setPaddingLeft(PAD_H * 2)
                                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                        )

                        // Столбец Ответ
                        table.addCell(
                            Cell().add(
                                Paragraph(surveyAns[question] ?: "—")
                                    .setFont(bold)
                                    .setFontSize(BASE_FONT_SIZE)
                                    .setTextAlignment(TextAlignment.LEFT)
                            )
                                .setPaddingTop(PAD_V)
                                .setPaddingRight(PAD_H)
                                .setPaddingBottom(PAD_V)
                                .setPaddingLeft(PAD_H)
                                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                        )
                    }
                    doc.add(table)

                    // 6) Секция «Кейсы»
                    doc.add(
                        Paragraph("Кейсы")
                            .setFont(bold)
                            .setFontSize(BASE_FONT_SIZE)
                            .setTextAlignment(TextAlignment.CENTER)
                            .setMarginTop(CASES_MARGIN_TOP)
                            .setMarginBottom(CASES_MARGIN_BOTTOM)
                    )

                    // 6.1) Вывод каждого кейса с описанием курсивом
                    caseAns.forEach { (idx, answer) ->
                        val kase = cat.byIndex(idx)

                        // Номер кейса
                        doc.add(
                            Paragraph("КЕЙС №${kase.id}")
                                .setFont(bold)
                                .setFontSize(BASE_FONT_SIZE)
                                .setMarginBottom(2f)
                        )

                        // Описание ситуации (курсив)
                        doc.add(
                            Paragraph().add(
                                Text(kase.description)
                                    .setFont(regular)
                                    .setFontSize(BASE_FONT_SIZE)
                                    .setItalic()
                            )
                                .setMarginBottom(4f)
                        )

                        // Задачи кейса (маркированный список)
                        if (kase.tasks.isNotEmpty()) {
                            val tasksList = List()
                                .setFont(regular)
                                .setFontSize(BASE_FONT_SIZE)
                                .setSymbolIndent(12f)
                                .setListSymbol("")
                                .setMarginLeft(20f)
                                .setMarginBottom(4f)
                            kase.tasks.forEach { t -> tasksList.add(ListItem(t)) }
                            doc.add(tasksList)
                        }

                        // Ответ соискателя
                        doc.add(
                            Paragraph("ОТВЕТ СОИСКАТЕЛЯ:")
                                .setFont(bold)
                                .setFontSize(BASE_FONT_SIZE)
                                .setMarginBottom(2f)
                        )
                        doc.add(
                            Paragraph(answer)
                                .setFont(regular)
                                .setFontSize(BASE_FONT_SIZE)
                                .setMarginBottom(8f)
                        )
                    }
                }
            }
        }

        return file
    }

    // Метод для получения текста вопроса по enum
    private fun SurveyQuestion.label(): String = when (this) {
        SurveyQuestion.FULL_NAME -> "ФИО"
        SurveyQuestion.LAST_POSITION -> "Должность на предыдущем месте"
        SurveyQuestion.YEARS_OF_WORK -> "Педагогический стаж в школе"
        SurveyQuestion.COURSES -> "Курсы (актуальные)"
        SurveyQuestion.SCHOOL_KNOWLEDGE -> "Знакома ли деятельность школы НИКА?"
        SurveyQuestion.READY_TO_COMBINE -> "Тьюторство или совмещение?"
        SurveyQuestion.TUTOR_QUALITIES -> "Качества тьютора по мнению соискателя"
        SurveyQuestion.AGE_GROUP -> "Приемлемый возраст детей для работы"
        SurveyQuestion.HOBBIES -> "Увлечения и хобби"
        SurveyQuestion.LEARNING_READY ->
            "Готовность совершенствоваться в тьюторском\nсопровождении"
    }
}