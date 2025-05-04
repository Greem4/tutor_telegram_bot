package ru.greemlab.tutor_telegram_bot.dto

data class CaseData(
    val id: Int,
    val image: String,
    val description: String,
    var tasks: List<String> = emptyList()
)
