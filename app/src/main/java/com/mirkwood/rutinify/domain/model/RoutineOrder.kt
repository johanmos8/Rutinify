package com.mirkwood.rutinify.domain.model

import java.time.Instant
import java.time.LocalDate

/** Imported and confirmed routine; groups its [AlarmItem]s. */
data class RoutineOrder(
    val id: Long,
    val name: String,
    val date: LocalDate,
    val notes: String?,
    val status: OrderStatus,
    val createdAt: Instant,
    /** Weekly repetition of a schema v2 plan; its day type lives in [RoutineActivity] rows (R-23, D-81). */
    val recurrence: Recurrence? = null,
)

/** Persisted status of an order. */
enum class OrderStatus { ACTIVE, PAUSED, CANCELLED }

/** Display status (R-8.2, R-8.3). [FINISHED] is derived and never stored (D-14). */
enum class OrderDisplayStatus { ACTIVE, PAUSED, CANCELLED, FINISHED }
