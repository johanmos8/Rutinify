package com.mirkwood.rutinify.domain.model

import java.time.DayOfWeek
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

/**
 * Days a plan claims when looking for conflicts (R-6.6, R-23.9): the dates from [start] to [end] (no end when null)
 * that fall on one of [weekdays]. A plain span claims every weekday; a recurring plan only its own and, without
 * `until`, has no end. Kept apart from [RoutineSpan], whose end is always known because labels and day counts need it.
 */
data class ConflictDays(val start: LocalDate, val end: LocalDate?, val weekdays: Set<DayOfWeek> = ALL_WEEKDAYS) {

    /** Whether some date belongs to both: one week of the common dates is enough to meet every weekday. */
    fun overlaps(other: ConflictDays): Boolean {
        val from = maxOf(start, other.start)
        val to = listOfNotNull(end, other.end).minOrNull()
        val shared = weekdays intersect other.weekdays
        return (0 until DayOfWeek.entries.size).asSequence()
            .map { from.plusDays(it.toLong()) }
            .takeWhile { to == null || !it.isAfter(to) }
            .any { it.dayOfWeek in shared }
    }

    private companion object {
        val ALL_WEEKDAYS: Set<DayOfWeek> = DayOfWeek.entries.toSet()
    }
}

/** Conflict days of a plain order or routine: its [RoutineSpan.forConflicts] dates, any weekday (D-58). */
fun RoutineSpan.conflictDays(): ConflictDays = forConflicts().let { ConflictDays(it.start, it.end) }

/** Conflict days of a recurring plan starting on [start] (R-23.9). */
fun Recurrence.conflictDays(start: LocalDate): ConflictDays = ConflictDays(start, until, weekdays)

/** Conflict days the draft would claim if scheduled on [date]. */
fun RoutineDraft.conflictDaysOn(date: LocalDate): ConflictDays =
    recurrence?.conflictDays(date) ?: spanOn(date).conflictDays()

/** Conflict days of an existing order: its weekdays when it repeats, otherwise its span. */
val ConflictingOrder.conflictDays: ConflictDays
    get() = order.recurrence?.conflictDays(order.date) ?: span.conflictDays()
