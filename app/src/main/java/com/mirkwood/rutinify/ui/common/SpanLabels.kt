package com.mirkwood.rutinify.ui.common

import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.domain.model.RoutineSpan
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Short labels for the days an order covers (R-6.7); the long Home format comes with T-36. */
internal object SpanLabels {
    private val SPANISH: Locale = Locale.forLanguageTag("es-ES")
    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d", SPANISH)
    private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM", SPANISH)
    private val DAY_MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", SPANISH)

    /**
     * "14 de noviembre" for a single-day routine (its early hours do not count); otherwise "del 14 al 20 de
     * noviembre", naming the month or the year on both ends only when they differ.
     */
    fun short(span: RoutineSpan): UiText {
        val (start, end) = span
        return when {
            !span.isMultiDay -> UiText.Plain(start.format(DAY_MONTH))
            start.year != end.year -> range(start.format(DAY_MONTH_YEAR), end.format(DAY_MONTH_YEAR))
            start.month != end.month -> range(start.format(DAY_MONTH), end.format(DAY_MONTH))
            else -> range(start.format(DAY), end.format(DAY_MONTH))
        }
    }

    private fun range(from: String, to: String) = UiText.Res(R.string.date_range, listOf(from, to))
}
