package com.mirkwood.rutinify.domain.model

import java.time.Instant
import java.time.LocalTime

/**
 * An order activity with its reminder. The trigger time is not stored: it is computed from
 * order date + [dayOffset] + [time] in the current zone, unless [snoozedUntil] is set.
 */
data class AlarmItem(
    val id: Long,
    val orderId: Long,
    /** Original index in the JSON (0-based). */
    val position: Int,
    /** Original wall-clock time. */
    val time: LocalTime,
    /** 0 = same day, 1 = next day (D-05). */
    val dayOffset: Int,
    val title: String,
    val description: String?,
    val durationMin: Int?,
    /** Normalized; `general` by default. */
    val category: String,
    val assignee: String?,
    val status: AlarmStatus,
    /** When not null, overrides the computed time (D-12). */
    val snoozedUntil: Instant?,
    val statusChangedAt: Instant,
    val firedAt: Instant? = null,
    val doneAt: Instant? = null,
)

/** Transitions in design §3. Deleting an alarm removes it; it is not a status. */
enum class AlarmStatus { PENDING, FIRED, DONE, SKIPPED, CANCELLED }
