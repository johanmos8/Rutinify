package com.mirkwood.rutinify.domain.model

import java.time.Instant

/** One alarm on the order timeline with its effective trigger instant (R-9.1). */
data class TimelineEntry(val alarm: AlarmItem, val triggerAt: Instant)

/** Order detail: alarms sorted by trigger time and the next one to ring (R-9.1, R-9.2). */
data class OrderDetail(
    val order: RoutineOrder,
    val timeline: List<TimelineEntry>,
    /** First pending alarm of an ACTIVE order; null when nothing is going to ring (D-46). */
    val nextAlarmId: Long?,
) {
    val pending: Int get() = timeline.count { it.alarm.status == AlarmStatus.PENDING }
    val total: Int get() = timeline.size
    val displayStatus: OrderDisplayStatus get() = OrderSummary(order, pending, total).displayStatus
}
