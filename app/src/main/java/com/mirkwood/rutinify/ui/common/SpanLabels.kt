package com.mirkwood.rutinify.ui.common

import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.domain.model.RoutineSpan
import java.time.LocalDate

/**
 * Labels for the days an order covers: conflict dialog (R-6.7), Review (R-4.13, R-4.14), Home and detail (R-8.2).
 * Dates stay [UiText.Date] so each language picks its own pattern and word order from resources (D-71); the
 * comments show the Spanish result.
 */
internal object SpanLabels {

    /**
     * "14 de noviembre" for a single-day routine (its early hours do not count); otherwise "del 14 al 20 de
     * noviembre", naming the month or the year on both ends only when they differ.
     */
    fun short(span: RoutineSpan): UiText {
        val (start, end) = span
        return when {
            !span.isMultiDay -> date(start, R.string.date_pattern_short)
            start.year != end.year -> range(date(start, R.string.date_pattern_short_year), date(end, R.string.date_pattern_short_year))
            start.month != end.month -> range(date(start, R.string.date_pattern_short), date(end, R.string.date_pattern_short))
            else -> range(date(start, R.string.date_pattern_start_same_month), date(end, R.string.date_pattern_end_same_month))
        }
    }

    /**
     * "del sábado 14 al viernes 20 de noviembre" (R-4.14), with the same month and year rules as [short];
     * "el sábado 14 de noviembre" when [first] and [last] are the same day (D-59).
     */
    fun withWeekdays(first: LocalDate, last: LocalDate): UiText = when {
        first == last -> UiText.Res(R.string.date_single, listOf(date(first, R.string.date_pattern_weekday)))
        first.year != last.year ->
            range(date(first, R.string.date_pattern_weekday_year), date(last, R.string.date_pattern_weekday_year))
        first.month != last.month -> range(date(first, R.string.date_pattern_weekday), date(last, R.string.date_pattern_weekday))
        else -> range(
            date(first, R.string.date_pattern_weekday_start_same_month),
            date(last, R.string.date_pattern_weekday_end_same_month),
        )
    }

    /**
     * Home and order detail (R-8.2): "sábado 14 de noviembre de 2026" for a single-day routine, otherwise
     * "del sábado 14 al viernes 20 de noviembre de 2026", the year at the end and on both ends only when it changes.
     */
    fun long(span: RoutineSpan): UiText {
        val (start, end) = span
        return when {
            !span.isMultiDay -> date(start, R.string.date_pattern_weekday_year)
            start.year != end.year ->
                range(date(start, R.string.date_pattern_weekday_year), date(end, R.string.date_pattern_weekday_year))
            start.month != end.month ->
                range(date(start, R.string.date_pattern_weekday), date(end, R.string.date_pattern_weekday_year))
            else -> range(
                date(start, R.string.date_pattern_weekday_start_same_month),
                date(end, R.string.date_pattern_weekday_end_same_month_year),
            )
        }
    }

    /** "sábado 14 de noviembre de 2026": the date of a single-day plan in Review (R-4.2). */
    fun fullDate(date: LocalDate): UiText = date(date, R.string.date_pattern_weekday_year)

    /** "lunes 16 de noviembre", for the day headers of Review and order detail (R-4.13, R-9.6). */
    fun weekdayDate(date: LocalDate): UiText = date(date, R.string.date_pattern_weekday)

    /** "Día 3 · lunes 16 de noviembre" (R-4.13, R-9.6). */
    fun dayHeader(dayOffset: Int, date: LocalDate): UiText =
        UiText.Res(R.string.review_day_header, listOf(dayOffset + 1, weekdayDate(date)))

    private fun date(date: LocalDate, patternId: Int) = UiText.Date(date, patternId)

    private fun range(from: UiText, to: UiText) = UiText.Res(R.string.date_range, listOf(from, to))
}
