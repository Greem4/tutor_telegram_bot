package ru.greemlab.tutor_telegram_bot.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import ru.greemlab.tutor_telegram_bot.enums.SurveyQuestion

@Entity
@Table(name = "survey_answer")
data class SurveyAnswer(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: TelegramUser,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val question: SurveyQuestion,

    @Column(columnDefinition = "TEXT", nullable = false)
    val answer: String
)
