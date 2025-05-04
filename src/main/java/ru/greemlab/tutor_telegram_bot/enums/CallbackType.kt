package ru.greemlab.tutor_telegram_bot.enums

enum class CallbackType(val id: String) {
    START_SURVEY("START_SURVEY"),
    START_CASES ("START_CASES" );
    companion object { fun from(raw: String) = entries.firstOrNull { it.id == raw } }
}