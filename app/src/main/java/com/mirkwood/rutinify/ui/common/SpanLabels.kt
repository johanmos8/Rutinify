package com.mirkwood.rutinify.ui.common

import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.domain.model.RoutineSpan
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Labels for the days an order covers: conflict dialog (R-6.7), Review (R-4.13, R-4.14), Home and detail (R-8.2). */
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

    /**
     * Home and order detail (R-8.2): "sábado 14 de noviembre de 2026" for a single-day routine, otherwise
     * "del sábado 14 al viernes 20 de noviembre de 2026", the year at the end and on both ends only when it changes.
     */
    fun long(span: RoutineSpan): UiText {
        val (start, end) = span
        return when {
            !span.isMultiDay -> UiText.Plain(start.format(WEEKDAY_DAY_MONTH_YEAR))
            start.year != end.year -> range(start.format(WEEKDAY_DAY_MONTH_YEAR), end.format(WEEKDAY_DAY_MONTH_YEAR))
            start.month != end.month -> range(start.format(WEEKDAY_DAY_MONTH), end.format(WEEKDAY_DAY_MONTH_YEAR))
            else -> range(start.format(WEEKDAY_DAY), end.format(WEEKDAY_DAY_MONTH_YEAR))
        }
    }

    /** "lunes 16 de noviembre", for the day headers of Review and order detail (R-4.13, R-9.6). */
    fun weekdayDate(date: LocalDate): String = date.format(WEEKDAY_DAY_MONTH)

    /** "Día 3 · lunes 16 de noviembre" (R-4.13, R-9.6). */
    fun dayHeader(dayOffset: Int, date: LocalDate): UiText =
        UiText.Res(R.string.review_day_header, listOf(dayOffset + 1, weekdayDate(date)))

    private fun range(from: String, to: String) = UiText.Res(R.string.date_range, listOf(from, to))
}
