package ru.greemlab.tutor_telegram_bot.service

import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.*
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import org.springframework.stereotype.Service
import ru.greemlab.tutor_telegram_bot.catalog.CaseCatalog
import ru.greemlab.tutor_telegram_bot.enums.SurveyQuestion
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class PdfService {

    fun build(
        chat: Long,
        nike: String?,
        surveyAns: Map<SurveyQuestion, String>,
        caseAns: Map<Int, String>,
        cat: CaseCatalog,
    ): File {

        val file = Files.createTempFile("cases_${chat}_", ".pdf").toFile()

        PdfDocument(PdfWriter(file)).use { pdf ->
            Document(pdf).use { doc ->

                /* -------- шрифты -------- */
                val bold: PdfFont = PdfFontFactory.createFont(
                    "/fonts/OpenSans-Bold.ttf",
                    PdfEncodings.IDENTITY_H,
                    PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED
                )
                val norm: PdfFont = PdfFontFactory.createFont(
                    "/fonts/OpenSans-Regular.ttf",
                    PdfEncodings.IDENTITY_H,
                    PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED
                )
                doc.setFont(norm).setFontSize(12f)

                /* -------- заголовок -------- */
                doc.add(
                    Paragraph("ОТВЕТЫ ОПРОСНИКА НА ДОЛЖНОСТЬ ТЬЮТОРА")
                        .setFont(bold)
                        .setFontSize(14f)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setUnderline(1f, 1f)
                )

                /* ник + дата (справа) */
                doc.add(
                    Paragraph(
                        "@${nike ?: "—"}  ${
                            LocalDateTime.now().format(
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                            )
                        }"
                    )
                        .setFontSize(8f)
                        .setTextAlignment(TextAlignment.RIGHT)
                        .setMarginTop(2f)
                )

                /* -------- ОПРОС -------- */
                doc.add(
                    Paragraph("Опрос")
                        .setFont(bold)
                        .setFontSize(12f)
                        .setMarginTop(12f)
                        .setMarginBottom(4f)
                )

                val table = Table(UnitValue.createPercentArray(floatArrayOf(10f, 50f, 40f)))
                    .useAllAvailableWidth()
                    .setFixedLayout()

                SurveyQuestion.entries.forEachIndexed { i, q ->
                    table.addCell(td("${i + 1}", norm))           // #
                    table.addCell(td(q.label(), norm))            // короткое имя вопроса
                    table.addCell(td(surveyAns[q] ?: "—", norm))  // ответ кандидата
                }
                doc.add(table)

                /* -------- КЕЙСЫ -------- */
                doc.add(
                    Paragraph("Кейсы")
                        .setFont(bold)
                        .setFontSize(12f)
                        .setMarginTop(14f)
                        .setMarginBottom(6f)
                )

                caseAns.forEach { (idx, answer) ->

                    val case = cat.byIndex(idx)

                    /* заголовок + описание кейса */
                    doc.add(
                        Paragraph("КЕЙС №${case.id}")
                            .setFont(bold)
                            .setFontSize(12f)
                    )
                    doc.add(
                        Paragraph(case.description)
                            .setFont(norm)
                            .setFontSize(12f)
                    )

                    /* список заданий */
                    if (case.tasks.isNotEmpty()) {
                        val tasksList = com.itextpdf.layout.element.List()
                            .setFont(norm)
                            .setFontSize(12f)
                            .setSymbolIndent(12f)
                            .setMarginLeft(20f)
                            .setListSymbol("")   // ставим пустой символ ⇒ нумерация автоматически

                        case.tasks.forEach { t ->
                            tasksList.add(ListItem(t))
                        }
                        doc.add(tasksList)
                    }

                    /* ответ соискателя */
                    doc.add(
                        Paragraph("ОТВЕТ СОИСКАТЕЛЯ:")
                            .setFont(bold)
                            .setFontSize(12f)
                            .setMarginTop(8f)
                    )
                    doc.add(
                        Paragraph(answer)
                            .setFont(norm)
                            .setFontSize(12f)
                            .setMarginBottom(10f)
                    )
                }
            }
        }
        return file
    }

    /* -------- утилиты ячеек -------- */
    private fun td(text: String, font: PdfFont) = Cell()
        .add(Paragraph(text).setFont(font).setFontSize(12f))
        .setPadding(4f)

    /* ---------------------------------------------------------------------- */
    /*  Мапа «вопрос → короткое название»                                    */
    /* ---------------------------------------------------------------------- */
    private fun SurveyQuestion.label(): String = when (this) {
        SurveyQuestion.FULL_NAME       -> "ФИО"
        SurveyQuestion.LAST_POSITION   -> "Должность на предыдущем месте"
        SurveyQuestion.YEARS_OF_WORK   -> "Педагогический стаж в школе"
        SurveyQuestion.COURSES         -> "Курсы (актуальные)"
        SurveyQuestion.SCHOOL_KNOWLEDGE-> "Знакомы ли с деятельностью школы"
        SurveyQuestion.READY_TO_COMBINE-> "Тьюторство или совмещение?"
        SurveyQuestion.TUTOR_QUALITIES -> "Качества тьютора по мнению соискателя"
        SurveyQuestion.AGE_GROUP       -> "Предпочт. возраст детей"
        SurveyQuestion.HOBBIES         -> "Увлечения и хобби"
        SurveyQuestion.LEARNING_READY  -> "Готовность совершенствоваться"
    }
}
