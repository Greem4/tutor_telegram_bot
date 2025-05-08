enum class UserCommand(val text: String) {
    START("/start"),
    BEGIN_SURVEY("начать_опрос"),    // payload для кнопки “НАЧАТЬ 🚀”
    BEGIN_CASES("начать_кейс"),      // payload для кнопки “Начать кейсы”
    CANCEL("🚫 Отмена");
    companion object {
        fun parse(s: String) = entries.firstOrNull { it.text.equals(s, true) }
    }
}