package ru.greemlab.tutor_telegram_bot.dto

import java.io.Serializable

data class CaseData(
    val id: Int,
    val image: String,
    val description: String,
    var tasks: List<String> = emptyList()
) : Serializable
