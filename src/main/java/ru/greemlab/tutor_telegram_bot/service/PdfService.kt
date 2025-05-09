package ru.greemlab.tutor_telegram_bot.service

import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.action.PdfAction
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Link
import com.itextpdf.layout.element.List
import com.itextpdf.layout.element.ListItem
import com.itextpdf.layout.element.Paragraph
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

    // Параметры стилей для заголовка документа
    private val TITLE_FONT_SIZE = 12f           // размер шрифта заголовка
    private val TITLE_UNDERLINE_THICKNESS = 1f   // толщина подчеркивания заголовка
    private val TITLE_UNDERLINE_OFFSET = -2f     // смещение подчеркивания заголовка
    private val TITLE_MARGIN_BOTTOM = 8f         // отступ снизу заголовка

    // Базовые параметры стиля для параграфов
    private val BASE_FONT_SIZE = 11f             // базовый размер шрифта документа

    fun build(
        chatId: Long,
        username: String?,
        surveyAns: Map<SurveyQuestion, String>,
        caseAns: Map<Int, String>,
        cat: CaseCatalog
    ): File {
        // создаём временный файл
        val file = Files.createTempFile("cases_@${username ?: chatId}_", ".pdf").toFile()

        PdfWriter(file).use { writer ->
            PdfDocument(writer).use { pdf ->
                Document(pdf).use { doc ->

                    // Загружаем шрифты
                    val bold: PdfFont = PdfFontFactory.createFont(
                        "/fonts/OpenSans-Bold.ttf",            // путь к файлу полужирного шрифта
                        PdfEncodings.IDENTITY_H,               // кодировка
                        PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED // стратегия встраивания шрифта
                    )
                    val regular: PdfFont = PdfFontFactory.createFont(
                        "/fonts/OpenSans-Regular.ttf",        // путь к файлу обычного шрифта
                        PdfEncodings.IDENTITY_H,               // кодировка
                        PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED // стратегия встраивания
                    )

                    // Устанавливаем базовый шрифт и размер для всего документа
                    doc.setFont(regular)                    // задаём шрифт документа
                        .setFontSize(BASE_FONT_SIZE)        // задаём размер шрифта документа

                    // ——— Заголовок документа ———
                    doc.add(
                        Paragraph("ОТВЕТЫ ОПРОСНИКА НА ДОЛЖНОСТЬ ТЬЮТОРА") // текст заголовка
                            .setFont(bold)                              // используем полужирный шрифт
                            .setFontSize(TITLE_FONT_SIZE)               // размер шрифта заголовка
                            .setTextAlignment(TextAlignment.CENTER)      // выравнивание по центру
                            .setUnderline(
                                TITLE_UNDERLINE_THICKNESS,               // толщина подчеркивания
                                TITLE_UNDERLINE_OFFSET                   // смещение подчеркивания
                            )
                            .setMarginBottom(TITLE_MARGIN_BOTTOM)       // отступ снизу заголовка
                    )

                    // Текущая дата и время в формате yyyy-MM-dd HH:mm
                    val formattedDate = LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

                    // ——— Ник пользователя и дата ———
                    if (!username.isNullOrBlank()) {
                        // создаём ссылку на телеграм-аккаунт
                        val tgLink = Link("@$username", PdfAction.createURI("https://t.me/$username"))
                            .setFont(bold)                          // делаем ссылку полужирной
                            .setFontSize(BASE_FONT_SIZE)           // размер шрифта ссылки
                            .setFontColor(ColorConstants.BLUE)     // синий цвет текста
                            .setUnderline()                        // подчёркивание ссылки

                        doc.add(
                            Paragraph()
                                .add("Кандидат ")               // префикс текста
                                .add(tgLink)                     // вставляем ссылку
                                .add(" от: $formattedDate")    // дата и время
                                .setFont(regular)                // возвращаем обычный шрифт
                                .setFontSize(BASE_FONT_SIZE)     // размер шрифта — базовый
                                .setTextAlignment(TextAlignment.RIGHT) // выравнивание справа
                                .setMarginBottom(12f)            // отступ снизу блока
                        )
                    } else {
                        doc.add(
                            Paragraph("От: $formattedDate")   // если ника нет, выводим только дату
                                .setFont(regular)                // обычный шрифт
                                .setFontSize(BASE_FONT_SIZE)     // базовый размер шрифта
                                .setTextAlignment(TextAlignment.RIGHT) // выравнивание справа
                                .setMarginBottom(12f)            // отступ снизу блока
                        )
                    }

                    // ——— Секция «Опрос» ———
                    doc.add(
                        Paragraph("Опрос")                   // заголовок секции
                            .setFont(bold)                     // полужирный шрифт
                            .setFontSize(BASE_FONT_SIZE)       // размер — базовый
                            .setTextAlignment(TextAlignment.CENTER) // по центру
                            .setMarginBottom(4f)               // отступ снизу секции
                    )

                    // Таблица ответов опроса с колонками 8% / 50% / 42%
                    val surveyTable = com.itextpdf.layout.element.Table(
                        UnitValue.createPercentArray(floatArrayOf(8f, 50f, 42f))
                    ).useAllAvailableWidth()
                        .setFixedLayout()                       // фиксированная раскладка колонок

                    SurveyQuestion.entries.forEachIndexed { idx, question ->
                        surveyTable.addCell(td("${idx + 1}", regular))                // № вопроса
                        surveyTable.addCell(td(question.label(), regular))             // текст вопроса
                        surveyTable.addCell(tdAnswer(surveyAns[question] ?: "—", bold)) // ответ (полужирный)
                    }

                    doc.add(surveyTable) // добавляем таблицу в документ

                    // ——— Секция «Кейсы» ———
                    doc.add(
                        Paragraph("Кейсы")                    // заголовок секции
                            .setFont(bold)                      // полужирный шрифт
                            .setFontSize(BASE_FONT_SIZE)        // базовый размер шрифта
                            .setTextAlignment(TextAlignment.CENTER) // по центру
                            .setMarginTop(12f)                  // отступ сверху секции
                            .setMarginBottom(6f)                // отступ снизу секции
                    )

                    // Проходим по каждому кейсу и выводим описание и ответ
                    caseAns.forEach { (idx, answer) ->
                        val kase = cat.byIndex(idx)

                        // Заголовок кейса
                        doc.add(
                            Paragraph("КЕЙС №${kase.id}")     // номер кейса
                                .setFont(bold)                  // полужирный шрифт
                                .setFontSize(BASE_FONT_SIZE)    // базовый размер
                                .setMarginBottom(2f)            // отступ снизу
                        )

                        // Описание кейса
                        doc.add(
                            Paragraph(kase.description)       // текст описания
                                .setFont(regular)              // обычный шрифт
                                .setFontSize(BASE_FONT_SIZE)   // базовый размер
                                .setMarginBottom(4f)           // отступ снизу
                        )

                        // Список заданий внутри кейса (если есть)
                        if (kase.tasks.isNotEmpty()) {
                            val tasksList = List()
                                .setFont(regular)            // обычный шрифт для списка
                                .setFontSize(BASE_FONT_SIZE) // базовый размер
                                .setSymbolIndent(12f)        // отступ перед символом списка
                                .setListSymbol("")         // без символа списка
                                .setMarginLeft(20f)          // отступ слева
                                .setMarginBottom(4f)         // отступ снизу списка

                            kase.tasks.forEach { task ->
                                tasksList.add(ListItem(task)) // добавляем пункт списка
                            }
                            doc.add(tasksList)              // добавляем список в документ
                        }

                        // Заголовок раздела «Ответ соискателя»
                        doc.add(
                            Paragraph("ОТВЕТ СОИСКАТЕЛЯ:")   // текст заголовка раздела
                                .setFont(bold)                  // полужирный шрифт
                                .setFontSize(BASE_FONT_SIZE)    // базовый размер
                                .setMarginBottom(2f)            // отступ снизу
                        )

                        // Ответ соискателя
                        doc.add(
                            Paragraph(answer)               // текст ответа кандидата
                                .setFont(regular)            // обычный шрифт
                                .setFontSize(BASE_FONT_SIZE) // базовый размер
                                .setMarginBottom(8f)         // отступ снизу
                        )
                    }
                }
            }
        }

        return file // возвращаем сгенерированный PDF-файл
    }

    /** Утилита для обычной ячейки таблицы */
    private fun td(text: String, font: PdfFont): Cell =
        Cell()
            .add(Paragraph(text)
                .setFont(font)              // шрифт текста ячейки
                .setFontSize(BASE_FONT_SIZE) // размер текста
            )
            .setPadding(2f)                // отступ внутри ячейки
            .setVerticalAlignment(VerticalAlignment.MIDDLE) // выравнивание по вертикали

    /** Утилита для ячейки-ответа (жирный текст) */
    private fun tdAnswer(text: String, font: PdfFont): Cell =
        Cell()
            .add(Paragraph(text)
                .setFont(font)               // шрифт текста (жирный)
                .setFontSize(BASE_FONT_SIZE) // размер текста
            )
            .setPadding(2f)                // отступ внутри ячейки
            .setVerticalAlignment(VerticalAlignment.MIDDLE) // выравнивание по вертикали

    // Маппинг SurveyQuestion → читабельный лейбл
    private fun SurveyQuestion.label(): String = when (this) {
        SurveyQuestion.FULL_NAME        -> "ФИО"
        SurveyQuestion.LAST_POSITION    -> "Должность на предыдущем месте"
        SurveyQuestion.YEARS_OF_WORK    -> "Педагогический стаж в школе"
        SurveyQuestion.COURSES          -> "Курсы (актуальные)"
        SurveyQuestion.SCHOOL_KNOWLEDGE -> "Знакомы ли с деятельностью школы"
        SurveyQuestion.READY_TO_COMBINE -> "Тьюторство или совмещение?"
        SurveyQuestion.TUTOR_QUALITIES  -> "Качества тьютора по мнению соискателя"
        SurveyQuestion.AGE_GROUP        -> "Предпочт. возраст детей"
        SurveyQuestion.HOBBIES          -> "Увлечения и хобби"
        SurveyQuestion.LEARNING_READY   -> "Готовность совершенствоваться"
    }
}