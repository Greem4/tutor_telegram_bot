package ru.greemlab.tutor_telegram_bot.service

// Импортируем кодировки и шрифты для PDF
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
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
                        .setUnderline(
                            2f,
                            -3f
                        )                   // Подчёркивание толщиной 2, смещено вниз на 3 единицы
                )

                // Формируем строку с ником и текущей датой/временем
                val formattedDate = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) // Форматируем дату

                // Добавляем параграф с ником (если есть) и датой справа
                doc.add(
                    Paragraph(
                        if (!nike.isNullOrBlank()) // Если ник не null и не пустой
                            "Кандидат @${nike} от: $formattedDate" // Пишем ник и дату
                        else
                            "От: $formattedDate"                   // Иначе только дату
                    )
                        .setFont(norm)                            // Применяем обычный шрифт
                        .setFontSize(14f)                         // Размер 14
                        .setTextAlignment(TextAlignment.RIGHT)    // Выравнивание по правому краю
                        .setMarginTop(2f)                         // Отступ сверху в 2 пункта
                )

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
                val surveyTable =
                    Table(UnitValue.createPercentArray(floatArrayOf(10f, 50f, 40f)))
                        .useAllAvailableWidth()                  // Тянем таблицу на всю ширину документа
                        .setFixedLayout()                        // Фиксируем ширину столбцов

                // Заполняем таблицу: номер | вопрос | ответ
                SurveyQuestion.entries.forEachIndexed { i, q ->
                    surveyTable.addCell(
                        td(
                            "${i + 1}",
                            norm
                        )
                    )           // Добавляем ячейку с номером вопроса
                    surveyTable.addCell(
                        td(
                            q.label(),
                            norm
                        )
                    )              // Добавляем ячейку с текстом вопроса
                    surveyTable.addCell(                                  // Добавляем ячейку с ответом
                        Cell().add(
                            Paragraph(
                                surveyAns[q] ?: "—"
                            )            // Текст ответа или дефис
                                .setFont(bold)                          // Жирный шрифт для ответа
                                .setFontSize(10f)                       // Специальный размер 10
                        )
                            .setPadding(4f)                              // Внутренний отступ ячейки
                    )
                }
                // Прикрепляем таблицу к документу
                doc.add(surveyTable)

                // Заголовок секции "Кейсы"
                doc.add(
                    Paragraph("Кейсы")                          // Текст заголовка
                        .setFont(bold)                            // Жирный шрифт
                        .setFontSize(12f)                         // Размер 12
                        .setTextAlignment(TextAlignment.CENTER)   // Центровка
                        .setMarginTop(14f)                        // Отступ сверху
                        .setMarginBottom(6f)                      // Отступ снизу
                )

                // Проходим по каждому кейсу и выводим его
                caseAns.forEach { (idx, answer) ->
                    val case =
                        cat.byIndex(idx)                     // Получаем объект кейса по индексу

                    // Заголовок отдельного кейса
                    doc.add(
                        Paragraph("КЕЙС №${case.id}")          // "КЕЙС №<id>"
                            .setFont(bold)                        // Жирный шрифт
                            .setFontSize(12f)                     // Размер 12
                    )

                    // Описание кейса
                    doc.add(
                        Paragraph(case.description)             // Текст описания из каталога
                            .setFont(norm)                       // Обычный шрифт
                            .setFontSize(12f)                    // Размер 12
                    )

                    // Если у кейса есть список заданий — создаём маркированный список
                    if (case.tasks.isNotEmpty()) {
                        val tasksList = List()                // iText List
                            .setFont(norm)                   // Обычный шрифт
                            .setFontSize(12f)                // Размер 12
                            .setSymbolIndent(12f)            // Отступ символа маркера
                            .setMarginLeft(20f)              // Отступ слева для всего списка
                            .setListSymbol("")             // Пустой символ - нумерация автоматически

                        case.tasks.forEach { t ->            // Для каждого задания
                            tasksList.add(ListItem(t))        // Добавляем пункт списка
                        }
                        doc.add(tasksList)                   // Добавляем список в документ
                    }

                    // Заголовок раздела с ответом соискателя
                    doc.add(
                        Paragraph("ОТВЕТ СОИСКАТЕЛЯ:")       // Текст заголовка
                            .setFont(bold)                    // Жирный шрифт
                            .setFontSize(12f)                 // Размер 12
                            .setMarginTop(8f)                 // Отступ сверху
                    )

                    // Сам ответ соискателя
                    doc.add(
                        Paragraph(answer)                   // Текст ответа из входных данных
                            .setFont(norm)                   // Обычный шрифт
                            .setFontSize(12f)                // Размер 12
                            .setMarginBottom(10f)            // Отступ снизу
                    )
                }
            }
        }

        return file // Возвращаем сгенерированный PDF-файл
    }

    // Утилита для создания ячейки таблицы с текстом и шрифтом
    private fun td(text: String, font: PdfFont) = Cell()
        .add(
            Paragraph(text).setFont(font).setFontSize(12f)
        ) // Добавляем Paragraph с текстом и размером
        .setPadding(4f)                                      // Устанавливаем отступ внутри ячейки

    // Распределяем значения enum SurveyQuestion в читаемые названия
    private fun SurveyQuestion.label(): String = when (this) {
        SurveyQuestion.FULL_NAME -> "ФИО"
        SurveyQuestion.LAST_POSITION -> "Должность на предыдущем месте"
        SurveyQuestion.YEARS_OF_WORK -> "Педагогический стаж в школе"
        SurveyQuestion.COURSES -> "Курсы (актуальные)"
        SurveyQuestion.SCHOOL_KNOWLEDGE -> "Знакомы ли с деятельностью школы"
        SurveyQuestion.READY_TO_COMBINE -> "Тьюторство или совмещение?"
        SurveyQuestion.TUTOR_QUALITIES -> "Качества тьютора по мнению соискателя"
        SurveyQuestion.AGE_GROUP -> "Предпочт. возраст детей"
        SurveyQuestion.HOBBIES -> "Увлечения и хобби"
        SurveyQuestion.LEARNING_READY -> "Готовность совершенствоваться"
    }
}
