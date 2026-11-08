package com.mirkwood.rutinify.testutil

import com.mirkwood.rutinify.domain.model.AlarmItem
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.model.RoutineOrder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Test builders with sensible defaults. */
fun testOrder(
    id: Long = 1L,
    name: String = "Cachorro",
    date: LocalDate = LocalDate.of(2026, 11, 5),
    status: OrderStatus = OrderStatus.ACTIVE,
) = RoutineOrder(id, name, date, notes = null, status = status, createdAt = Instant.parse("2026-11-04T12:00:00Z"))

fun testAlarm(
    id: Long = 10L,
    orderId: Long = 1L,
    time: LocalTime = LocalTime.of(6, 45),
    dayOffset: Int = 0,
    title: String = "Siesta",
    description: String? = "En su cama",
    category: String = "siesta",
    status: AlarmStatus = AlarmStatus.PENDING,
    snoozedUntil: Instant? = null,
) = AlarmItem(
    id = id,
    orderId = orderId,
    position = 0,
    time = time,
    dayOffset = dayOffset,
    title = title,
    description = description,
    durationMin = null,
    category = category,
    assignee = null,
    status = status,
    snoozedUntil = snoozedUntil,
    statusChangedAt = Instant.parse("2026-11-04T12:00:00Z"),
)
