package ru.greemlab.tutor_telegram_bot.service

import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.kernel.colors.DeviceGray
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
        phone: String?,
        surveyAns: Map<SurveyQuestion, String>,
        caseAns: Map<Int, String>,
        cat: CaseCatalog,
    ): File {
        val file = Files.createTempFile("cases_${chat}_", ".pdf").toFile()

        PdfDocument(PdfWriter(file)).use { pdf ->
            Document(pdf).use { doc ->
                val bold = PdfFontFactory.createFont(
                    "/fonts/OpenSans-Bold.ttf",
                    PdfEncodings.IDENTITY_H,
                    PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED
                )
                val norm = PdfFontFactory.createFont(
                    "/fonts/OpenSans-Regular.ttf",
                    PdfEncodings.IDENTITY_H,
                    PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED
                )
                doc.setFont(norm).setFontSize(12f)

                doc.add(
                    Paragraph("ОТВЕТЫ ОПРОСНИКА НА ДОЛЖНОСТЬ ТЬЮТОРА")
                        .setFont(bold).setFontSize(14f)
                        .setTextAlignment(TextAlignment.CENTER).setUnderline()
                )

                doc.add(
                    Paragraph(" @${nike ?: "—"} ${LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}")
                        .setFontSize(8f).setTextAlignment(TextAlignment.RIGHT)
                )


                /* --- анкета --- */
                doc.add(Paragraph("Опрос").setFont(bold).setFontSize(12f).setMarginTop(15f))
                val table = Table(UnitValue.createPercentArray(floatArrayOf(20f, 200f, 300f))).apply {
                    addHeaderCell(th("#")); addHeaderCell(th("Вопрос")); addHeaderCell(th("Ответ"))
                }

                SurveyQuestion.entries.forEachIndexed { i, q ->
                    table.addCell(td("${i + 1}"))
                    table.addCell(td(q.prompt))
                    table.addCell(td(surveyAns[q] ?: "—"))
                }
                doc.add(table)

                /* --- кейсы --- */
                doc.add(Paragraph("Кейсы").setFont(bold).setFontSize(12f).setMarginTop(15f))
                caseAns.forEach { (idx, answer) ->
                    val id = cat.byIndex(idx).id
                    doc.add(Paragraph("КЕЙС №$id").setFont(bold).setMarginTop(10f))
                    doc.add(Paragraph(answer).setMarginBottom(10f))
                }
            }
        }
        return file
    }

    private fun th(t: String) = Cell().add(Paragraph(t))
        .setBackgroundColor(DeviceGray(0.9f))
        .setTextAlignment(TextAlignment.CENTER)

    private fun td(t: String) = Cell().add(Paragraph(t))
}