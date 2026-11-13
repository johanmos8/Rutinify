package com.mirkwood.rutinify.domain.importer

import java.time.LocalTime

/**
 * Item field rules shared by the JSON parser and the Review editor, so an edit follows exactly the
 * import rules (R-3.8, R-3.9, R-3.10, R-3.14, R-3.21, R-3.22, R-4.2).
 */
object DraftItemRules {

    const val MAX_TITLE_LENGTH = 80
    const val MAX_CATEGORY_LENGTH = 30
    const val MAX_CATEGORIES = 10
    const val DEFAULT_CATEGORY = "general"

    private val TIME_PATTERN = Regex("""^([01]?\d|2[0-3]):([0-5]\d)$""")
    private val WHITESPACE = Regex("""\s+""")

    /** `H:mm` or `HH:mm`, 00:00–23:59 (R-3.8, D-13); null otherwise. */
    fun parseTime(text: String): LocalTime? {
        val match = TIME_PATTERN.matchEntire(text.trim()) ?: return null
        val (hour, minute) = match.destructured
        return LocalTime.of(hour.toInt(), minute.toInt())
    }

    sealed interface TitleCheck {
        /** Missing or only blanks (R-3.9). */
        data object Blank : TitleCheck

        /** Trimmed; [truncated] when it was cut to [MAX_TITLE_LENGTH] (R-3.10, a warning). */
        data class Valid(val value: String, val truncated: Boolean) : TitleCheck
    }

    fun checkTitle(text: String): TitleCheck {
        val title = text.trim()
        return when {
            title.isEmpty() -> TitleCheck.Blank
            title.length > MAX_TITLE_LENGTH -> TitleCheck.Valid(title.take(MAX_TITLE_LENGTH).trimEnd(), truncated = true)
            else -> TitleCheck.Valid(title, truncated = false)
        }
    }

    /**
     * Blank → "general"; otherwise lowercase, trimmed, inner whitespace → "-" (R-3.14). Null when the
     * normalized category is longer than [MAX_CATEGORY_LENGTH] (R-3.21).
     */
    fun normalizeCategory(text: String?): String? {
        val trimmed = text?.trim()
        if (trimmed.isNullOrEmpty()) return DEFAULT_CATEGORY
        return trimmed.lowercase().replace(WHITESPACE, "-").takeIf { it.length <= MAX_CATEGORY_LENGTH }
    }
}
