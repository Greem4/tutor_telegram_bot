package ru.greemlab.tutor_telegram_bot.catalog

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ru.greemlab.tutor_telegram_bot.dto.CaseData

@Component
class CaseCatalog(
    @Value("\${app.tutor-cases.IMAGE_CASE_1}") img1: String,
    @Value("\${app.tutor-cases.IMAGE_CASE_2}") img2: String,
    @Value("\${app.tutor-cases.IMAGE_CASE_3}") img3: String,
) {
    private val cases = listOf(
        CaseData(1, img1),
        CaseData(2, img2),
        CaseData(3, img3),
    )
    fun size() = cases.size
    fun byIndex(index: Int) = cases[index]
}