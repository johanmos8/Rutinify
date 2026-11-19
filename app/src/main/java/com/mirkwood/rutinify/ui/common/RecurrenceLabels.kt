package com.mirkwood.rutinify.ui.common

import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.domain.model.Recurrence
import java.time.DayOfWeek
import java.time.LocalDate

/** "Se repite lunes, miércoles y viernes, desde el 16 de noviembre" (R-23.10), in the current language. */
internal object RecurrenceLabels {

    fun label(recurrence: Recurrence, start: LocalDate): UiText {
        val days = if (recurrence.weekdays.size == DayOfWeek.entries.size) {
            UiText.Res(R.string.recurrence_every_day)
        } else {
            join(recurrence.weekdays.sorted().map { UiText.Res(WEEKDAYS.getValue(it)) })
        }
        val from = UiText.Date(start, R.string.date_pattern_short)
        return when (val until = recurrence.until) {
            null -> UiText.Res(R.string.recurrence_from, listOf(days, from))
            else -> UiText.Res(R.string.recurrence_from_until, listOf(days, from, UiText.Date(until, R.string.date_pattern_short)))
        }
    }

    /** "lunes", "lunes y viernes", "lunes, miércoles y viernes". */
    private fun join(parts: List<UiText>): UiText = when (parts.size) {
        1 -> parts.single()
        2 -> UiText.Res(R.string.list_two, parts)
        else -> UiText.Res(R.string.list_many, listOf(parts.first(), join(parts.drop(1))))
    }

    private val WEEKDAYS = mapOf(
        DayOfWeek.MONDAY to R.string.weekday_monday,
        DayOfWeek.TUESDAY to R.string.weekday_tuesday,
        DayOfWeek.WEDNESDAY to R.string.weekday_wednesday,
        DayOfWeek.THURSDAY to R.string.weekday_thursday,
        DayOfWeek.FRIDAY to R.string.weekday_friday,
        DayOfWeek.SATURDAY to R.string.weekday_saturday,
        DayOfWeek.SUNDAY to R.string.weekday_sunday,
    )
}
