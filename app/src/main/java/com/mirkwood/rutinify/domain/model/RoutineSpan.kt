package com.mirkwood.rutinify.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Days an order covers: from its date to its date plus the largest `dayOffset` of its activities
 * (requirements glossary, D-55). Never stored: derived from the date and the activities.
 */
data class RoutineSpan(val start: LocalDate, val end: LocalDate) {

    init {
        require(!end.isBefore(start)) { "end $end is before start $start" }
    }

    /** Days from [start] to [end], both included. */
    val dayCount: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1

    /** Largest `dayOffset` of 2 or more; a single day with its early hours (offset 1) is not (D-55). */
    val isMultiDay: Boolean get() = dayCount > MULTI_DAY_MIN_OFFSET

    /**
     * Days compared for conflicts (R-6.6, D-58): the whole span of a multi-day routine, but only the
     * start date of a single-day one, so the early hours of today's routine never clash with tomorrow's.
     */
    fun forConflicts(): RoutineSpan = if (isMultiDay) this else RoutineSpan(start, start)

    /** Whether both spans share at least one day. */
    fun overlaps(other: RoutineSpan): Boolean = !start.isAfter(other.end) && !other.start.isAfter(end)

    companion object {
        /** Smallest largest-`dayOffset` that makes a routine multi-day (requirements glossary). */
        const val MULTI_DAY_MIN_OFFSET = 2

        fun of(start: LocalDate, maxDayOffset: Int): RoutineSpan =
            RoutineSpan(start, start.plusDays(maxDayOffset.coerceAtLeast(0).toLong()))
    }
}

/** Span the draft would have if scheduled on [date]: all its activities count, included or not. */
fun RoutineDraft.spanOn(date: LocalDate): RoutineSpan = RoutineSpan.of(date, items.maxOfOrNull { it.dayOffset } ?: 0)

/** An ACTIVE or PAUSED order whose days clash with a new routine, with its own span (R-6.6, R-6.7). */
data class ConflictingOrder(val order: RoutineOrder, val span: RoutineSpan)
