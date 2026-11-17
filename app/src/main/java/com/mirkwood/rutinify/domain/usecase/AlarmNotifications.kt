package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.model.AlarmItem
import com.mirkwood.rutinify.domain.model.RoutineOrder
import com.mirkwood.rutinify.domain.scheduler.AlarmNotification
import java.time.Instant

/** What the notification of [alarm] shows (R-7.1); shared by firing and silencing. */
internal fun alarmNotification(
    alarm: AlarmItem,
    order: RoutineOrder,
    triggerAt: Instant,
    critical: Boolean,
    silenced: Boolean = false,
) = AlarmNotification(
    alarmId = alarm.id,
    orderId = order.id,
    orderName = order.name,
    title = alarm.title,
    description = alarm.description,
    time = alarm.time,
    category = alarm.category,
    triggerAt = triggerAt,
    critical = critical,
    silenced = silenced,
)
