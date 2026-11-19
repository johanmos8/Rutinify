package com.mirkwood.rutinify.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Schema v2 `routine.recurrence` (R-23.2, D-81): the day-type activities repeat on [weekdays] from the plan date, up to
 * [until] or with no end when it is null.
 */
data class Recurrence(val weekdays: Set<DayOfWeek>, val until: LocalDate?) {

    /** True when [date] is one of the plan's days, given the plan starts on [start]. */
    fun includes(date: LocalDate, start: LocalDate): Boolean =
        !date.isBefore(start) && (until == null || !date.isAfter(until)) && date.dayOfWeek in weekdays

    companion object {
        /** Contract codes, Monday first (R-23.2). */
        val CODES: Map<String, DayOfWeek> = linkedMapOf(
            "MO" to DayOfWeek.MONDAY,
            "TU" to DayOfWeek.TUESDAY,
            "WE" to DayOfWeek.WEDNESDAY,
            "TH" to DayOfWeek.THURSDAY,
            "FR" to DayOfWeek.FRIDAY,
            "SA" to DayOfWeek.SATURDAY,
            "SU" to DayOfWeek.SUNDAY,
        )
    }
}
