package com.mirkwood.rutinify.data.repository

import com.mirkwood.rutinify.data.local.AlarmItemEntity
import com.mirkwood.rutinify.data.local.OrderSummaryRow
import com.mirkwood.rutinify.data.local.OrderWithAlarmsRow
import com.mirkwood.rutinify.data.local.PendingAlarmRow
import com.mirkwood.rutinify.data.local.RoutineOrderEntity
import com.mirkwood.rutinify.domain.model.AlarmItem
import com.mirkwood.rutinify.domain.model.NewAlarm
import com.mirkwood.rutinify.domain.model.NewOrder
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.model.OrderSummary
import com.mirkwood.rutinify.domain.model.OrderWithAlarms
import com.mirkwood.rutinify.domain.model.PendingAlarm
import com.mirkwood.rutinify.domain.model.RoutineOrder
import java.time.Instant

/** Timeline order of an order's alarms (R-4.1, R-9.1, D-27). */
internal val ALARM_TIMELINE_ORDER: Comparator<AlarmItem> =
    compareBy<AlarmItem>({ it.dayOffset }, { it.time }, { it.position })

internal fun RoutineOrderEntity.toDomain() = RoutineOrder(
    id = id,
    name = name,
    date = date,
    notes = notes,
    status = status,
    createdAt = createdAt,
)

internal fun AlarmItemEntity.toDomain() = AlarmItem(
    id = id,
    orderId = orderId,
    position = position,
    time = time,
    dayOffset = dayOffset,
    title = title,
    description = description,
    durationMin = durationMin,
    category = category,
    assignee = assignee,
    status = status,
    snoozedUntil = snoozedUntil,
    statusChangedAt = statusChangedAt,
    firedAt = firedAt,
    doneAt = doneAt,
)

internal fun AlarmItem.toEntity() = AlarmItemEntity(
    id = id,
    orderId = orderId,
    position = position,
    time = time,
    dayOffset = dayOffset,
    title = title,
    description = description,
    durationMin = durationMin,
    category = category,
    assignee = assignee,
    status = status,
    snoozedUntil = snoozedUntil,
    firedAt = firedAt,
    doneAt = doneAt,
    statusChangedAt = statusChangedAt,
)

internal fun OrderSummaryRow.toDomain() = OrderSummary(order = order.toDomain(), pending = pending, total = total, maxDayOffset = maxDayOffset)

internal fun OrderWithAlarmsRow.toDomain() = OrderWithAlarms(
    order = order.toDomain(),
    alarms = alarms.map { it.toDomain() }.sortedWith(ALARM_TIMELINE_ORDER),
)

internal fun PendingAlarmRow.toDomain() = PendingAlarm(alarm = alarm.toDomain(), orderDate = orderDate)

internal fun NewOrder.toEntity(now: Instant) = RoutineOrderEntity(
    name = name,
    date = date,
    notes = notes,
    status = OrderStatus.ACTIVE,
    schemaVersion = schemaVersion,
    rawJson = rawJson,
    createdAt = now,
    updatedAt = now,
)

internal fun NewAlarm.toEntity(orderId: Long, now: Instant) = AlarmItemEntity(
    orderId = orderId,
    position = position,
    time = time,
    dayOffset = dayOffset,
    title = title,
    description = description,
    durationMin = durationMin,
    category = category,
    assignee = assignee,
    status = status,
    snoozedUntil = null,
    firedAt = null,
    doneAt = null,
    statusChangedAt = now,
)
