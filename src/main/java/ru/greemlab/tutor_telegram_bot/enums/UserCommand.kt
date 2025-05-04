package ru.greemlab.tutor_telegram_bot.enums

enum class UserCommand(val text: String) {
    START("/start"),
    CANCEL("🚫 отмена");
    companion object { fun parse(raw: String) =
        entries.firstOrNull { it.text.equals(raw.trim(), true) } }
}