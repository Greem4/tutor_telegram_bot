package ru.greemlab.tutor_telegram_bot.service

// Импорт необходимых компонентов
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ru.greemlab.tutor_telegram_bot.catalog.CaseCatalog
import ru.greemlab.tutor_telegram_bot.entity.CaseAnswer
import ru.greemlab.tutor_telegram_bot.entity.TelegramUser
import ru.greemlab.tutor_telegram_bot.enums.SurveyQuestion
import ru.greemlab.tutor_telegram_bot.repository.CaseAnswerRepository
import ru.greemlab.tutor_telegram_bot.repository.SurveyAnswerRepository
import ru.greemlab.tutor_telegram_bot.session.CaseSession
import java.util.concurrent.ConcurrentHashMap

@Service // Аннотация Spring, обозначающая сервисный слой
class CaseService(
    @Value("\${app.bot.admin_id}") private val adminId: Long?, // ID администратора из конфигурации
    private val catalog: CaseCatalog, // Каталог кейсов
    private val sender: SenderService, // Сервис отправки сообщений и медиа
    private val kb: KeyboardService, // Сервис клавиатур
    private val pdf: PdfService, // Сервис генерации PDF
    private val surveyService: SurveyService, // Сервис, управляющий анкетой
    private val surveyAnswerRepo: SurveyAnswerRepository, // Репозиторий ответов анкеты
    private val caseAnswerRepo: CaseAnswerRepository, // Репозиторий ответов на кейсы
) {
    private val sessions = ConcurrentHashMap<Long, CaseSession>() // Хранилище активных сессий кейсов по chatId

    /**
     * Метод запуска этапа кейсов.
     * Получает пользователя, проверяет, проходил ли он кейсы ранее,
     * и запускает новую сессию с кейсами.
     */
    suspend fun start(chatId: Long) {
        // Получаем пользователя из опросной части
        val user: TelegramUser = surveyService.takeProfile(chatId)
            ?: run {
                // Если не найден, просим сначала пройти опросник
                sender.send(chatId, "Сначала пройдите опросник.", kb.start())
                return
            }

        // Проверка: если пользователь уже прошёл кейсы, не запускаем заново
        if (user.casesCompleted) {
            sender.send(chatId, "Вы уже завершили кейсы ранее.", kb.remove())
            return
        }

        // Создаём новую сессию с кейсами и сохраняем её
        sessions[chatId] = CaseSession(user, catalog)
        askNext(chatId) // Показываем первый кейс
    }

    // Проверка, активна ли сессия кейсов у пользователя
    fun active(chatId: Long): Boolean =
        sessions.containsKey(chatId)

    // Отмена прохождения кейсов — удаление сессии
    fun cancel(chatId: Long) {
        sessions.remove(chatId)
    }

    /**
     * Метод обработки ответа пользователя на кейс.
     * Переходит к следующему кейсу или завершает прохождение.
     */
    suspend fun answer(chatId: Long, text: String) {
        val session = sessions[chatId] ?: return // Если сессии нет — игнор
        session.answer(text) // Сохраняем ответ

        if (session.next()) { // Если есть ещё кейсы — следующий
            askNext(chatId)
        } else {
            finish(chatId, session) // Если кейсы закончились — финал
        }
    }

    // Отправка пользователю следующего кейса
    private suspend fun askNext(chatId: Long) {
        val kase = sessions[chatId]?.current ?: return // Получаем текущий кейс

        sender.photo(chatId, kase.image, kb.cancel())

    }

    /**
     * Завершение этапа кейсов:
     * - Сохраняем ответы
     * - Обновляем пользователя
     * - Генерируем PDF
     * - Отправляем пользователю и администратору
     */
    private suspend fun finish(chatId: Long, session: CaseSession) {
        // 1) Сохраняем ответы на кейсы в базу
        session.dump().forEach { (idx, answer) ->
            caseAnswerRepo.save(
                CaseAnswer(
                    user = session.user,
                    caseIndex = idx,
                    answer = answer
                )
            )
        }

        // 2) Обновляем пользователя — ставим флаг о завершении кейсов
        session.user.apply { casesCompleted = true }
            .also { surveyService.updateUser(it) }

        // 3) Удаляем сессию из памяти
        sessions.remove(chatId)

        // 4) Получаем ответы из анкеты
        val surveyAnswers: Map<SurveyQuestion, String> =
            surveyAnswerRepo.findByUser(session.user)
                .associate { it.question to it.answer }

        // 5) Получаем ответы на кейсы
        val caseAnswers: Map<Int, String> = session.dump()

        // 6) Генерируем PDF файл с результатами
        val nick = session.user.username
        val pdfFile = pdf.build(
            chatId    = chatId,
            nike      = nick,
            surveyAns = surveyAnswers,
            caseAns   = caseAnswers,
            cat       = catalog
        )

        // 7) Отправляем пользователю сообщение о завершении
        sender.send(
            chatId, """
            Вы ответили на все вопросы! 🏁
            Мы свяжемся с Вами в ближайшее время. 
            Спасибо.
        """.trimIndent()
        )

        // 8) Информируем пользователя о тьюторстве
        sender.send(
            chatId, """
            Можете ознакомиться с тем, кто такой тьютор в ОАНО 
            Школа "НИКА" 👇
        """.trimIndent(), kb.abortTutor()
        )

        // 9) Отправляем администратору PDF с результатами
        adminId?.let { admin ->
            sender.document(admin, pdfFile, "📥 Ответы кандидата @${nick ?: chatId}")
        }
    }
}
