package com.mirkwood.rutinify.data.repository

import java.time.DayOfWeek
import com.mirkwood.rutinify.domain.model.RoutineActivity
import com.mirkwood.rutinify.domain.model.Recurrence
import com.mirkwood.rutinify.domain.model.NewActivity
import com.mirkwood.rutinify.data.local.RoutineActivityEntity
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
    recurrence = recurrenceWeekdays?.let { Recurrence(weekdaysOf(it), recurrenceUntil) },
)

/** Monday = bit 0 … Sunday = bit 6 (D-81). */
internal fun maskOf(days: Set<DayOfWeek>): Int = days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

internal fun weekdaysOf(mask: Int): Set<DayOfWeek> = DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()

internal fun RoutineActivityEntity.toDomain() = RoutineActivity(
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
)

internal fun NewActivity.toEntity(orderId: Long) = RoutineActivityEntity(
    orderId = orderId,
    position = position,
    time = time,
    dayOffset = dayOffset,
    title = title,
    description = description,
    durationMin = durationMin,
    category = category,
    assignee = assignee,
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
    activityId = activityId,
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
    activityId = activityId,
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
    recurrenceWeekdays = recurrence?.let { maskOf(it.weekdays) },
    recurrenceUntil = recurrence?.until,
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
    activityId = activityId,
)
