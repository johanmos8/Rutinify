package com.mirkwood.rutinify.ui.common

import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.domain.model.RoutineSpan
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Labels for the days an order covers: conflict dialog (R-6.7) and Review summary (R-4.14). */
internal object SpanLabels {
    private val SPANISH: Locale = Locale.forLanguageTag("es-ES")
    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d", SPANISH)
    private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM", SPANISH)
    private val DAY_MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", SPANISH)
    private val WEEKDAY_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d", SPANISH)
    private val WEEKDAY_DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SPANISH)
    private val WEEKDAY_DAY_MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", SPANISH)

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

    /**
     * "del sábado 14 al viernes 20 de noviembre" (R-4.14), with the same month and year rules as [short];
     * "el sábado 14 de noviembre" when [first] and [last] are the same day (D-59).
     */
    fun withWeekdays(first: LocalDate, last: LocalDate): UiText = when {
        first == last -> UiText.Res(R.string.date_single, listOf(first.format(WEEKDAY_DAY_MONTH)))
        first.year != last.year -> range(first.format(WEEKDAY_DAY_MONTH_YEAR), last.format(WEEKDAY_DAY_MONTH_YEAR))
        first.month != last.month -> range(first.format(WEEKDAY_DAY_MONTH), last.format(WEEKDAY_DAY_MONTH))
        else -> range(first.format(WEEKDAY_DAY), last.format(WEEKDAY_DAY_MONTH))
    }

    /** "lunes 16 de noviembre", for the day headers of Review (R-4.13). */
    fun weekdayDate(date: LocalDate): String = date.format(WEEKDAY_DAY_MONTH)

    private fun range(from: String, to: String) = UiText.Res(R.string.date_range, listOf(from, to))
}
