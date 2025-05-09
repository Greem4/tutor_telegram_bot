package ru.greemlab.tutor_telegram_bot.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import ru.greemlab.tutor_telegram_bot.catalog.CaseCatalog
import ru.greemlab.tutor_telegram_bot.entity.SurveyAnswer
import ru.greemlab.tutor_telegram_bot.entity.TelegramUser
import ru.greemlab.tutor_telegram_bot.notifier.GroupNotifierService
import ru.greemlab.tutor_telegram_bot.repository.SurveyAnswerRepository
import ru.greemlab.tutor_telegram_bot.repository.TelegramUserRepository
import ru.greemlab.tutor_telegram_bot.session.SurveySession
import ru.greemlab.tutor_telegram_bot.text.BotMessages
import java.time.LocalDateTime

@Service
class SurveyService(
    @Value("\${app.bot.admin_id}") private val adminId: Long?,
    private val sender: SenderService,
    private val kb: KeyboardService,
    private val userRepo: TelegramUserRepository,
    private val answerRepo: SurveyAnswerRepository,
    private val cacheManager: CacheManager,
    private val pdf: PdfService,
    private val catalog: CaseCatalog,
    private val groupNotifier: GroupNotifierService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // имя нашего кэша (можете задать любое, но оно должно совпадать с настройкой RedisConfig)
    private val cacheName = "surveySessions"

    // получить кэш-контейнер
    private val cache: Cache
        get() = cacheManager.getCache(cacheName)
            ?: throw IllegalStateException("Кэш '$cacheName' не найден")

    /** Старт первого этапа — опроса */
    fun start(chatId: Long, userId: Long, nick: String?) {
        log.debug(
            "Starting survey for chatId={}, userId={}, nick={}",
            chatId,
            userId,
            nick
        )

        // 1) если в Redis уже есть сессия — возобновляем её
        cache.get(chatId, SurveySession::class.java)?.let {
            log.debug("Resuming existing survey session for chatId={}", chatId)
            askNext(chatId)
            return
        }

        // 2) Иначе загружаем или создаём пользователя
        val user = userRepo.findByTelegramId(userId)
            .orElseGet {
                val newUser = TelegramUser(telegramId = userId, username = nick)
                userRepo.save(newUser).also {
                    log.debug(
                        "Created new TelegramUser id={} telegramId={}",
                        it.id,
                        it.telegramId
                    )
                }
            }

        // 3) Если опрос уже пройден — отказываем
        if (user.surveyCompleted) {
            log.warn(
                "User {} already completed survey; refusing to restart",
                user.telegramId
            )
            sender.send(
                chatId,
                "Вы уже проходили опрос. Повторно нельзя.\nПродолжайте ответы👇",
                kb.remove()
            )
            return
        }

        // 4) Создаём новую сессию и кладём её в Redis
        val session = SurveySession(user)
        cache.put(chatId, session)
        log.debug(
            "Created SurveySession for chatId={}, cached under '{}'",
            chatId,
            cacheName
        )

        askNext(chatId)
    }

    /** Обработка текста-ответа */
    fun answer(chatId: Long, text: String) {
        log.debug("Received survey answer for chatId={}: {}", chatId, text)

        // 1) Забираем сессию из Redis
        val entry = cache.get(chatId)?.get()
        if (entry == null) {
            log.warn("No active session for chatId={}, ignoring answer", chatId)
            return
        }
        val session = entry as SurveySession

        // 2) Обрабатываем ответ
        session.answer(text)

        if (session.next()) {
            // 3) Сохраняем обновлённую сессию обратно и шлём следующий вопрос
            cache.put(chatId, session)
            askNext(chatId)
        } else {
            // 4) Выгружаем в БД и чистим кэш
            finish(chatId, session)
            cache.evict(chatId)
        }
    }

    /** Есть ли активная сессия? */
    fun active(chatId: Long): Boolean =
        cache.get(chatId)?.get() != null

    /**
     * Досрочное завершение опроса: сохраняем, отправляем PDF и закрываем доступ.
     */
    suspend fun cancel(chatId: Long) {
        log.debug("Досрочное прерывание опроса для chatId={}", chatId)

        // 1) достаём сессию
        val entry = cache.get(chatId)?.get() as? SurveySession
        if (entry == null) {
            cache.evict(chatId)
            return
        }
        partialFinishSurvey(chatId, entry)
    }


    /** Сбрасывает все данные опроса для данного чата */ //TODO удалить к проду
    fun reset(chatId: Long) {
        cache.evict(chatId)
        log.debug("Resetting survey for chatId={}", chatId)
        // 2) Загружаем пользователя из БД
        userRepo.findByTelegramId(chatId)
            .ifPresent { user ->
                user.surveyCompleted = false
                userRepo.save(user)
                log.debug(
                    "surveyCompleted flag reset in DB for telegramId={}",
                    user.telegramId
                )
            }
    }

    fun takeProfile(chatId: Long): TelegramUser? {
        cache.get(chatId, SurveySession::class.java)?.let {
            return it.user
        }
        return userRepo.findByTelegramId(chatId)
            .filter { it.surveyCompleted }
            .orElse(null)
    }

    fun updateUser(user: TelegramUser) {
        log.debug(
            "Updating TelegramUser id={} surveyCompleted={} casesCompleted={}",
            user.id, user.surveyCompleted, user.casesCompleted
        )
        userRepo.save(user)
    }

    /** Шлёт следующий вопрос + кнопку «🚫 отмена» */
    private fun askNext(chatId: Long) {
        val session = cache.get(chatId)?.get() as? SurveySession ?: return
        val prompt = session.current.prompt
        log.debug("Sending survey prompt to chatId={}: {}", chatId, prompt)
        sender.send(chatId, prompt, kb.cancel())
    }

    /** Завершает опрос, сохраняет ответы в БД и предлагает кейсы */
    private fun finish(chatId: Long, session: SurveySession) {
        // Сохраняем ответы
        session.dump().forEach { (question, answer) ->
            answerRepo.save(
                SurveyAnswer(
                    user = session.user,
                    question = question,
                    answer = answer
                )
            )
            log.debug("Saved answer for question={} chatId={}", question, chatId)
        }

        // Помечаем флагом и сохраняем в БД
        session.user.apply { surveyCompleted = true }
            .also {
                userRepo.save(it)
                log.debug(
                    "surveyCompleted flag set true for telegramId={}",
                    it.telegramId
                )
            }

        log.debug("Survey session completed for chatId={}, removing from cache", chatId)
        sender.send(
            chatId,
            BotMessages.CASES_WELCOME_MESSAGE,
            kb.beginCases()
        )
    }

    /** Сохраняем частичные ответы и шлём PDF */
    private suspend fun partialFinishSurvey(chatId: Long, session: SurveySession) {
        // 1) сохраняем ответы, что есть
        val user = session.user.apply { surveyCompleted = true }
        userRepo.save(user)
        session.dump().forEach { (q, a) ->
            answerRepo.save(SurveyAnswer(user = user, question = q, answer = a))
        }

        // 2) чистим сессию
        cache.evict(chatId)

        // 3) строим PDF с текущими ответами и без кейсов
        val surveyAns = session.dump()
        val emptyCases = emptyMap<Int, String>()
        val pdfFile = pdf.build(
            chatId = chatId,
            username = user.username,
            surveyAns = surveyAns,
            caseAns = emptyCases,
            cat = catalog,
            completedAt = LocalDateTime.now()
        )
        sender.send(chatId, "Вы досрочно завершили опрос")

        // 4) отправляем PDF пользователю и админу
        adminId?.let {
            sender.document(
                it,
                pdfFile,
                "📥 Отменимая анкета @${user.username ?: chatId}"
            )
        }
        // 4. Уведомляем группу
        groupNotifier.notifyOrDefer(
            chatId = chatId,
            username = user.username,
            surveyAns = surveyAns,
            caseAns = emptyMap(),
            catalog = catalog,
            completedAt = LocalDateTime.now()
        )
        log.debug("Промежуточный PDF опроса отправлен для chatId={}", chatId)

        cache.evict(chatId)
    }
}
