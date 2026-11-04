package com.mirkwood.rutinify.domain.model

import java.time.LocalDate
import java.time.LocalTime

/** Data to create an order (no id or timestamps; the repository sets them). */
data class NewOrder(
    val name: String,
    val date: LocalDate,
    val notes: String?,
    /** Original extracted JSON; stored for debugging / v2. */
    val rawJson: String?,
    val schemaVersion: Int = 1,
)

/** Data to create an alarm of a [NewOrder]. Past/excluded items already arrive as SKIPPED (R-5.5). */
data class NewAlarm(
    val position: Int,
    val time: LocalTime,
    val dayOffset: Int,
    val title: String,
    val description: String?,
    val durationMin: Int?,
    val category: String,
    val assignee: String?,
    val status: AlarmStatus,
)

/** Result of creating an order: what must be scheduled and what must be cancelled in the system. */
data class CreatedOrder(
    val order: RoutineOrder,
    /** Created alarms, with their ids (= requestCode, design §6.1). */
    val alarms: List<AlarmItem>,
    /** Previously pending alarms of the replaced orders, now CANCELLED (R-6.2). */
    val cancelledAlarmIds: List<Long>,
)

/** Pending alarm with its order date, to compute the trigger time (design §7.2). */
data class PendingAlarm(
    val alarm: AlarmItem,
    val orderDate: LocalDate,
)
