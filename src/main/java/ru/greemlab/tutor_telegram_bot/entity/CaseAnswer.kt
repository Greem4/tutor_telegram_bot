package ru.greemlab.tutor_telegram_bot.entity

import jakarta.persistence.*

@Entity
@Table(name = "case_answer")
data class CaseAnswer(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: TelegramUser,

    @Column(name = "case_index", nullable = false)
    val caseIndex: Int,

    @Column(columnDefinition = "TEXT", nullable = false)
    val answer: String
)
