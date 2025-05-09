package ru.greemlab.tutor_telegram_bot.catalog

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import ru.greemlab.tutor_telegram_bot.dto.CaseData

@Component
@ConfigurationProperties(prefix = "app.tutor-cases")
class CaseCatalog  {
    lateinit var cases: List<CaseData>

    fun size() = cases.size
    fun byIndex(index: Int) = cases[index]
}