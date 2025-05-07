// src/main/kotlin/ru/greemlab/tutor_telegram_bot/service/SurveyService.kt
package ru.greemlab.tutor_telegram_bot.service

import org.springframework.stereotype.Service
import ru.greemlab.tutor_telegram_bot.entity.SurveyAnswer
import ru.greemlab.tutor_telegram_bot.entity.TelegramUser
import ru.greemlab.tutor_telegram_bot.enums.SurveyQuestion
import ru.greemlab.tutor_telegram_bot.repository.SurveyAnswerRepository
import ru.greemlab.tutor_telegram_bot.repository.TelegramUserRepository
import ru.greemlab.tutor_telegram_bot.session.SurveySession
import java.util.concurrent.ConcurrentHashMap

@Service
class SurveyService(
    private val sender: SenderService,
    private val kb: KeyboardService,
    private val userRepo: TelegramUserRepository,
    private val answerRepo: SurveyAnswerRepository,
) {
    // Сессии опроса
    private val sessions = ConcurrentHashMap<Long, SurveySession>()

    // Кэш профиля (TelegramUser) по chatId
    private val profileCache = ConcurrentHashMap<Long, TelegramUser>()

    /** Старт первого этапа — опроса */
    fun start(chatId: Long, userId: Long, nick: String?) {
        val user = userRepo.findByTelegramId(userId)
            .orElseGet {
                userRepo.save(TelegramUser(telegramId = userId, username = nick))
            }

        if (user.surveyCompleted) {
            sender.send(chatId, "Вы уже проходили опрос. Повторно нельзя.", kb.remove())
            return
        }

        // создаём сессию и кэшируем профиль
        sessions[chatId] = SurveySession(user)
        profileCache[chatId] = user
        askNext(chatId)
    }

    /** Обработка текста-ответа */
    fun answer(chatId: Long, text: String) {
        val session = sessions[chatId] ?: return
        session.answer(text)

        if (session.next()) {
            askNext(chatId)
        } else {
            finish(chatId, session)
        }
    }

    /** Есть ли активная сессия опроса? */
    fun active(chatId: Long): Boolean =
        sessions.containsKey(chatId)

    /** Отменяем опрос */
    fun cancel(chatId: Long) {
        sessions.remove(chatId)
        profileCache.remove(chatId)
    }

    /** Возвращает профилированного пользователя для CaseService */
    fun takeProfile(chatId: Long): TelegramUser? =
        profileCache[chatId]

    /** Сохраняет изменения пользователя (флаги) */
    fun updateUser(user: TelegramUser) {
        userRepo.save(user)
    }

    /** Шлёт следующий вопрос + кнопку «🚫 отмена» */
    private fun askNext(chatId: Long) {
        val prompt = sessions[chatId]?.current?.prompt ?: return
        sender.send(chatId, prompt, kb.cancel())
    }

    /** Завершает опрос: сохраняет ответы, ставит флаг и предлагает кейсы */
    private fun finish(chatId: Long, session: SurveySession) {
        session.dump().forEach { (question, answer) ->
            answerRepo.save(
                SurveyAnswer(
                    user = session.user,
                    question = question,
                    answer = answer
                )
            )
        }
        session.user.apply { surveyCompleted = true }
            .also(userRepo::save)

        sessions.remove(chatId)

        sender.send(
            chatId, """
            👏 Вы прошли 1 этап опросника на должность тьютора.
            ➡Впереди 2 этап - кейсы.
            Всего будет 3 кейса. 
            ⏱Примерное время ответа на кейсы - 30 мин. 
            Для продолжения нажмите ниже👇
        """.trimIndent(), kb.beginCases()
        )
    }
}
