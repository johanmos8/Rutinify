package com.mirkwood.rutinify.domain.model

import java.time.Instant
import java.time.LocalDate

/** Rutina importada y confirmada; agrupa sus [AlarmItem]. */
data class RoutineOrder(
    val id: Long,
    val name: String,
    val date: LocalDate,
    val notes: String?,
    val status: OrderStatus,
    val createdAt: Instant,
)

/** Estado persistido de una orden. */
enum class OrderStatus { ACTIVE, PAUSED, CANCELLED }

/** Estado de presentación (R-8.2, R-8.3). [FINISHED] es derivado y no se guarda (D-14). */
enum class OrderDisplayStatus { ACTIVE, PAUSED, CANCELLED, FINISHED }
