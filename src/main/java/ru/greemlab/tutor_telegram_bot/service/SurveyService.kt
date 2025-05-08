package ru.greemlab.tutor_telegram_bot.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import ru.greemlab.tutor_telegram_bot.entity.SurveyAnswer
import ru.greemlab.tutor_telegram_bot.entity.TelegramUser
import ru.greemlab.tutor_telegram_bot.repository.SurveyAnswerRepository
import ru.greemlab.tutor_telegram_bot.repository.TelegramUserRepository
import ru.greemlab.tutor_telegram_bot.session.SurveySession
import ru.greemlab.tutor_telegram_bot.text.BotMessages
import java.util.concurrent.ConcurrentHashMap

@Service
class SurveyService(
    private val sender: SenderService,
    private val kb: KeyboardService,
    private val userRepo: TelegramUserRepository,
    private val answerRepo: SurveyAnswerRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Сессии опроса
    private val sessions = ConcurrentHashMap<Long, SurveySession>()

    // Кэш профиля (TelegramUser) по chatId
    private val profileCache = ConcurrentHashMap<Long, TelegramUser>()

    /** Старт первого этапа — опроса */
    fun start(chatId: Long, userId: Long, nick: String?) {
        log.debug(
            "Starting survey for chatId={}, userId={}, nick={}",
            chatId,
            userId,
            nick
        )

        // 1) Если сессия уже активна — возобновляем её
        sessions[chatId]?.let {
            log.debug("Resuming existing survey session for chatId={}", chatId)
            askNext(chatId)  // отправляем текущий вопрос
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
        // 3) Если опрос уже пройден — отказываем в рестарте
        if (user.surveyCompleted) {
            log.warn(
                "User {} already completed survey; refusing to restart",
                user.telegramId
            )
            sender.send(chatId, "Вы уже проходили опрос. Повторно нельзя.\nПродолжайте ответы👇", kb.remove())
            return
        }
        // 4) Иначе создаём новую сессию и начинаем
        sessions[chatId] = SurveySession(user)
        profileCache[chatId] = user
        log.debug(
            "Created SurveySession for chatId={}, total sessions={}",
            chatId,
            sessions.size
        )
        askNext(chatId)
    }

    /** Обработка текста-ответа */
    fun answer(chatId: Long, text: String) {
        log.debug("Received survey answer for chatId={}: {}", chatId, text)
        val session = sessions[chatId] ?: run {
            log.warn("No active session for chatId={}, ignoring answer", chatId)
            return
        }
        session.answer(text)

        if (session.next()) {
            log.debug("Moving to next survey question for chatId={}", chatId)
            askNext(chatId)
        } else {
            log.debug("All survey questions answered for chatId={}", chatId)
            finish(chatId, session)
        }
    }

    /** Есть ли активная сессия опроса? */
    fun active(chatId: Long): Boolean =
        sessions.containsKey(chatId)

    /** Отменяем опрос */
    fun cancel(chatId: Long) {
        log.debug("Canceling survey for chatId={}", chatId)
        sessions.remove(chatId)
        profileCache.remove(chatId)
    }

    /** Возвращает профилированного пользователя для CaseService */
    fun takeProfile(chatId: Long): TelegramUser? =
        profileCache[chatId]?.also {
            log.debug("takeProfile for chatId={} -> telegramId={}", chatId, it.telegramId)
        }

    /** Сохраняет изменения пользователя (флаги) */
    fun updateUser(user: TelegramUser) {
        log.debug(
            "Updating TelegramUser id={} surveyCompleted={} casesCompleted={}",
            user.id, user.surveyCompleted, user.casesCompleted
        )
        userRepo.save(user)
    }

    /** Сбрасывает все данные опроса для данного чата */ //TODO удалить к проду 
    fun reset(chatId: Long) {
        profileCache.remove(chatId)
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

    /** Шлёт следующий вопрос + кнопку «🚫 отмена» */
    private fun askNext(chatId: Long) {
        val prompt = sessions[chatId]?.current?.prompt ?: return
        log.debug("Sending survey prompt to chatId={}: {}", chatId, prompt)
        sender.send(chatId, prompt, kb.cancel())
    }

    /** Завершает опрос: сохраняет ответы, ставит флаг и предлагает кейсы */
    private fun finish(chatId: Long, session: SurveySession) {
        // Сохраняем ответы
        session.dump().forEach { (question, answer) ->
            answerRepo.save(
                SurveyAnswer(user = session.user, question = question, answer = answer)
            )
            log.debug("Saved answer for question={} chatId={}", question, chatId)
        }

        // Помечаем флагом в БД
        session.user.apply { surveyCompleted = true }
            .also {
                userRepo.save(it)
                log.debug(
                    "surveyCompleted flag set true for telegramId={}",
                    it.telegramId
                )
            }

        sessions.remove(chatId)
        log.debug("Survey session removed for chatId={}", chatId)

        sender.send(
            chatId,
            BotMessages.CASES_WELCOME_MESSAGE,
            kb.beginCases()
        )
    }
}
