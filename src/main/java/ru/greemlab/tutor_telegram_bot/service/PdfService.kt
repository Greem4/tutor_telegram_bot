package ru.greemlab.tutor_telegram_bot.service

// Импортируем кодировки и шрифты для PDF
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.action.PdfAction
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.*
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue

// Импортируем аннотацию Spring и наши классы-каталоги и энумы
import org.springframework.stereotype.Service
import ru.greemlab.tutor_telegram_bot.catalog.CaseCatalog
import ru.greemlab.tutor_telegram_bot.enums.SurveyQuestion

// Импортируем классы для работы с датой/временем и файлами
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service  // Объявляем этот класс как Spring-сервис
class PdfService {

    // Основной метод для сборки PDF документа
    fun build(
        chat: Long,                              // ID чата, используется в имени временного файла
        nike: String?,                           // Ник пользователя в Telegram (может быть null)
        surveyAns: Map<SurveyQuestion, String>,  // Ответы на вопросы опроса
        caseAns: Map<Int, String>,               // Ответы на кейсы по индексу
        cat: CaseCatalog,                        // Каталог доступных кейсов
    ): File {
        // Создаём временный файл с префиксом cases_<chat>_ и суффиксом .pdf
        val file = Files.createTempFile("cases_${chat}_", ".pdf").toFile()

        // Инициализируем PdfWriter и PdfDocument для получения low-level PDF API
        PdfDocument(PdfWriter(file)).use { pdf ->
            // Оборачиваем PdfDocument в high-level Document для удобного добавления элементов
            Document(pdf).use { doc ->

                // Загружаем жирный шрифт из ресурсов
                val bold: PdfFont = PdfFontFactory.createFont(
                    "/fonts/OpenSans-Bold.ttf",            // Путь к TTF-файлу жирного шрифта
                    PdfEncodings.IDENTITY_H,                // Кодировка для Unicode
                    PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED // Встраивание шрифта в PDF
                )

                // Загружаем обычный (роман) шрифт из ресурсов
                val norm: PdfFont = PdfFontFactory.createFont(
                    "/fonts/OpenSans-Regular.ttf",         // Путь к TTF-файлу обычного шрифта
                    PdfEncodings.IDENTITY_H,                // Кодировка для Unicode
                    PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED // Встраивание шрифта
                )

                // Устанавливаем шрифт по умолчанию и размер 12
                doc.setFont(norm).setFontSize(12f)

                // Добавляем заголовок документа
                doc.add(
                    Paragraph("ОТВЕТЫ ОПРОСНИКА НА ДОЛЖНОСТЬ ТЬЮТОРА") // Текст заголовка
                        .setFont(bold)                            // Применяем жирный шрифт
                        .setFontSize(14f)                         // Устанавливаем размер 14
                        .setTextAlignment(TextAlignment.CENTER)   // Центруем по ширине страницы
                        .setUnderline(2f, -3f)                   // Подчёркивание толщиной 2, смещено вниз на 3 единицы
                )

                // Формируем строку с датой/временем
                val formattedDate = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) // Форматируем дату

                // Если есть ник, создаём кликабельную ссылку на профиль Telegram
                if (!nike.isNullOrBlank()) {
                    // Создаём ссылку @ник -> https://t.me/ник
                    val tgLink = Link(
                        "@${nike}",
                        PdfAction.createURI("https://t.me/${nike}")
                    )
                        .setFont(bold)                        // Жирный шрифт для ссылки
                        .setFontSize(14f)                     // Размер 14
                        .setFontColor(com.itextpdf.kernel.colors.ColorConstants.BLUE) // Синий цвет
                        .setUnderline()                       // Подчёркивание

                    // Собираем параграф: "Кандидат @ник от: дата"
                    doc.add(
                        Paragraph()
                            .add("Кандидат ")
                            .add(tgLink)
                            .add(" от: $formattedDate")
                            .setFont(norm)                      // Обычный шрифт для остального текста
                            .setFontSize(14f)                   // Размер 14
                            .setTextAlignment(TextAlignment.RIGHT) // Выравнивание по правому краю
                            .setMarginTop(2f)                   // Отступ сверху
                    )
                } else {
                    // Если ника нет, выводим только дату
                    doc.add(
                        Paragraph("От: $formattedDate")
                            .setFont(norm)
                            .setFontSize(14f)
                            .setTextAlignment(TextAlignment.RIGHT)
                            .setMarginTop(2f)
                    )
                }

                // Добавляем заголовок секции "Опрос"
                doc.add(
                    Paragraph("Опрос")                          // Текст заголовка секции
                        .setFont(bold)                            // Жирный шрифт
                        .setFontSize(12f)                         // Размер 12
                        .setTextAlignment(TextAlignment.CENTER)   // Центровка
                        .setMarginTop(12f)                        // Отступ сверху
                        .setMarginBottom(4f)                      // Отступ снизу
                )

                // Создаём таблицу для отображения опроса
                val surveyTable = Table(UnitValue.createPercentArray(floatArrayOf(10f, 50f, 40f)))
                    .useAllAvailableWidth()                  // Тянем таблицу на всю ширину документа
                    .setFixedLayout()                        // Фиксируем ширину столбцов

                // Заполняем таблицу: номер | вопрос | ответ
                SurveyQuestion.entries.forEachIndexed { i, q ->
                    surveyTable.addCell(td("${i + 1}", norm))           // Номер вопроса
                    surveyTable.addCell(td(q.label(), norm))              // Текст вопроса
                    surveyTable.addCell(
                        Cell().add(
                            Paragraph(surveyAns[q] ?: "—")           // Ответ или дефис
                                .setFont(bold)                          // Жирный шрифт для ответа
                                .setFontSize(11f)                       // Размер 11
                        ).setPadding(4f)                               // Отступ внутри ячейки
                    )
                }
                doc.add(surveyTable)  // Прикрепляем таблицу к документу

                // Заголовок секции "Кейсы"
                doc.add(
                    Paragraph("Кейсы")                          // Текст заголовка
                        .setFont(bold)                            // Жирный шрифт
                        .setFontSize(12f)                         // Размер 12
                        .setTextAlignment(TextAlignment.CENTER)   // Центровка
                        .setMarginTop(14f)                        // Отступ сверху
                        .setMarginBottom(6f)                      // Отступ снизу
                )

                // Проходим по каждому кейсу
                caseAns.forEach { (idx, answer) ->
                    val case = cat.byIndex(idx)                    // Получаем кейс по индексу

                    // Заголовок кейса
                    doc.add(
                        Paragraph("КЕЙС №${case.id}")           // "КЕЙС №<id>"
                            .setFont(bold)                          // Жирный шрифт
                            .setFontSize(12f)                       // Размер 12
                    )

                    // Описание кейса
                    doc.add(
                        Paragraph(case.description)              // Текст описания
                            .setFont(norm)                        // Обычный шрифт
                            .setFontSize(12f)                     // Размер 12
                    )

                    // Если есть задания — список
                    if (case.tasks.isNotEmpty()) {
                        val tasksList = List()
                            .setFont(norm)
                            .setFontSize(12f)
                            .setSymbolIndent(12f)
                            .setMarginLeft(20f)
                            .setListSymbol("")
                        case.tasks.forEach { t -> tasksList.add(ListItem(t)) }
                        doc.add(tasksList)
                    }

                    // Заголовок ответа соискателя
                    doc.add(
                        Paragraph("ОТВЕТ СОИСКАТЕЛЯ:")           // Текст заголовка
                            .setFont(bold)                        // Жирный шрифт
                            .setFontSize(12f)                     // Размер 12
                            .setMarginTop(8f)                     // Отступ сверху
                    )

                    // Текст ответа
                    doc.add(
                        Paragraph(answer)                       // Ответ соискателя
                            .setFont(norm)                        // Обычный шрифт
                            .setFontSize(12f)                     // Размер 12
                            .setMarginBottom(10f)                 // Отступ снизу
                    )
                }
            }
        }

        return file // Возвращаем PDF-файл
    }

    // Утилита для создания ячейки таблицы
    private fun td(text: String, font: PdfFont) = Cell()
        .add(Paragraph(text).setFont(font).setFontSize(12f)) // Текст с размером 12
        .setPadding(4f)                                      // Внутренний отступ

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