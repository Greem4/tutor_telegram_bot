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

/**
 * Формирует PDF-файл с ответами соискателя.
 * Таблица «Опрос» сверстана так же, как в образце **Соискатель.pdf**:
 * ширина колонок 30 / 340 / 180 pt и компактные отступы 2 × 1 pt.
 */
@Service
class PdfService {

    companion object {
        /** Пути к шрифтам */
        private const val REGULAR_FONT = "/fonts/Arial.ttf"
        private const val BOLD_FONT = "/fonts/Arial-Bold.ttf"

        /** Размеры шрифтов */
        private const val TITLE_FONT_SIZE = 12f
        private const val BASE_FONT_SIZE = 11f

        /** Отступы секций/заголовков */
        private const val TITLE_MARGIN_BOTTOM = 8f
        private const val HEADER_MARGIN_BOTTOM = 12f
        private const val SECTION_MARGIN_BOTTOM = 8f
        private const val TABLE_MARGIN_BOTTOM = 16f
        private const val CASES_MARGIN_TOP = 12f
        private const val CASES_MARGIN_BOTTOM = 6f

        /** Ширины колонок [№, Вопрос, Ответ] в pt — взяты из макета */
        private const val COL1_WIDTH = 30f   // №
        private const val COL2_WIDTH = 340f  // Текст вопроса
        private const val COL3_WIDTH = 180f  // Ответ соискателя

        /** Внутренние отступы ячеек (pt): горизонталь × вертикаль */
        private const val PAD_H = 2f
        private const val PAD_V = 1f

        /** Формат даты/времени в шапке */
        private const val DATE_PATTERN = "yyyy-MM-dd HH:mm"
    }

    /**
     * Генерирует PDF и возвращает временный файл.
     */
    fun build(
        chatId: Long,
        username: String?,
        surveyAns: Map<SurveyQuestion, String>,
        caseAns: Map<Int, String>,
        cat: CaseCatalog,
        completedAt: LocalDateTime
    ): File {

        val file = Files
            .createTempFile("Ответы кандидаты_@${username ?: chatId}_", ".pdf")
            .toFile()

        PdfWriter(file).use { writer ->
            PdfDocument(writer).use { pdf ->
                Document(pdf).use { doc ->

                    /* ─────────── Шрифты ─────────── */
                    val reg: PdfFont = PdfFontFactory.createFont(
                        REGULAR_FONT,
                        PdfEncodings.IDENTITY_H,
                        PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED
                    )
                    val bold: PdfFont = PdfFontFactory.createFont(
                        BOLD_FONT,
                        PdfEncodings.IDENTITY_H,
                        PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED
                    )

                    doc.setFont(reg).setFontSize(BASE_FONT_SIZE)

                    /* ─────────── Заголовок ─────────── */
                    doc.add(
                        Paragraph("ОТВЕТЫ ОПРОСНИКА НА ДОЛЖНОСТЬ ТЬЮТОРА")
                            .setFont(bold)
                            .setFontSize(TITLE_FONT_SIZE)
                            .setTextAlignment(TextAlignment.CENTER)
                            .setUnderline(1f, -2f)
                            .setMarginBottom(TITLE_MARGIN_BOTTOM)
                    )

                    /* ─────────── Дата + ник ─────────── */
                    val dateStr =
                        completedAt.format(DateTimeFormatter.ofPattern(DATE_PATTERN))
                    val header = Paragraph()
                        .setFont(reg)
                        .setFontSize(BASE_FONT_SIZE)
                        .setTextAlignment(TextAlignment.RIGHT)
                        .setMarginBottom(HEADER_MARGIN_BOTTOM)

                    if (!username.isNullOrBlank()) {
                        val link = Link(
                            "@$username",
                            PdfAction.createURI("https://t.me/$username")
                        )
                            .setFont(bold)
                            .setFontSize(BASE_FONT_SIZE)
                            .setFontColor(ColorConstants.BLUE)
                            .setUnderline()
                        header.add("Кандидат ").add(link).add(" от: $dateStr")
                    } else {
                        header.add("От: $dateStr")
                    }
                    doc.add(header)

                    /* ─────────── Секция «Опрос» ─────────── */
                    doc.add(
                        Paragraph("Опрос")
                            .setFont(bold)
                            .setFontSize(BASE_FONT_SIZE)
                            .setTextAlignment(TextAlignment.CENTER)
                            .setMarginBottom(SECTION_MARGIN_BOTTOM)
                    )

                    val table = Table(
                        floatArrayOf(COL1_WIDTH, COL2_WIDTH, COL3_WIDTH)
                    )
                        .setWidth(
                            UnitValue.createPointValue(
                                COL1_WIDTH + COL2_WIDTH + COL3_WIDTH
                            )
                        )
                        .setFixedLayout()
                        .setMarginBottom(TABLE_MARGIN_BOTTOM)

                    SurveyQuestion.entries.forEachIndexed { idx, q ->

                        // №
                        table.addCell(
                            Cell().add(
                                Paragraph("${idx + 1}")
                                    .setFont(reg)
                                    .setFontSize(BASE_FONT_SIZE)
                            )
                                .applyPadding()
                                ?.setTextAlignment(TextAlignment.CENTER)
                                ?.setVerticalAlignment(VerticalAlignment.MIDDLE)
                        )

                        // Вопрос
                        table.addCell(
                            Cell().add(
                                Paragraph(q.label())
                                    .setFont(reg)
                                    .setFontSize(BASE_FONT_SIZE)
                            )
                                .applyPadding()
                                ?.setTextAlignment(TextAlignment.LEFT)
                                ?.setVerticalAlignment(VerticalAlignment.MIDDLE)
                        )

                        // Ответ
                        table.addCell(
                            Cell().add(
                                Paragraph(surveyAns[q] ?: "—")
                                    .setFont(bold)
                                    .setFontSize(BASE_FONT_SIZE)
                            )
                                .applyPadding()
                                ?.setTextAlignment(TextAlignment.LEFT)
                                ?.setVerticalAlignment(VerticalAlignment.MIDDLE)
                        )
                    }

                    doc.add(table)

                    /* ─────────── Секция «Кейсы» ─────────── */
                    doc.add(
                        Paragraph("Кейсы")
                            .setFont(bold)
                            .setFontSize(BASE_FONT_SIZE)
                            .setTextAlignment(TextAlignment.CENTER)
                            .setMarginTop(CASES_MARGIN_TOP)
                            .setMarginBottom(CASES_MARGIN_BOTTOM)
                    )

                    caseAns.forEach { (idx, ans) ->
                        val c = cat.byIndex(idx)

                        // Заголовок кейса
                        doc.add(
                            Paragraph("КЕЙС №${c.id}")
                                .setFont(bold)
                                .setFontSize(BASE_FONT_SIZE)
                                .setMarginBottom(2f)
                        )
                        // Описание
                        doc.add(
                            Paragraph(c.description)
                                .setFont(reg)
                                .setFontSize(BASE_FONT_SIZE)
                                .setMarginBottom(4f)
                        )
                        // Подзадачи
                        if (c.tasks.isNotEmpty()) {
                            val list = List()
                                .setFont(reg)
                                .setFontSize(BASE_FONT_SIZE)
                                .setSymbolIndent(12f)
                                .setListSymbol("")
                                .setMarginLeft(20f)
                                .setMarginBottom(4f)
                            c.tasks.forEach { t -> list.add(ListItem(t)) }
                            doc.add(list)
                        }
                        // Ответ соискателя
                        doc.add(
                            Paragraph("ОТВЕТ СОИСКАТЕЛЯ:")
                                .setFont(bold)
                                .setFontSize(BASE_FONT_SIZE)
                                .setMarginBottom(2f)
                        )
                        doc.add(
                            Paragraph(ans)
                                .setFont(reg)
                                .setFontSize(BASE_FONT_SIZE)
                                .setMarginBottom(8f)
                        )
                    }
                }
            }
        }

        return file
    }

    /* ─────────── Вспомогательные расширения ─────────── */

    /** Короткий хелпер: задаёт «правильные» внутренние отступы для ячейки */
    private fun <T : Cell> T.applyPadding(): Cell? = this
        .setPaddingLeft(PAD_H)
        .setPaddingRight(PAD_H)
        .setPaddingTop(PAD_V)
        .setPaddingBottom(PAD_V)

    /** Читаемый лейбл каждого вопроса опросника */
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
