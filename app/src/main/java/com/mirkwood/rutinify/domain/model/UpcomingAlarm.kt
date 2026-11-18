package com.mirkwood.rutinify.domain.model

import java.time.Instant

/** A pending alarm of an ACTIVE plan, with what the widget shows of its plan (R-21.1). */
data class UpcomingAlarm(val alarm: AlarmItem, val orderDate: java.time.LocalDate, val orderName: String)

/** The next alarm to ring across all active plans (R-21.1). */
data class NextAlarm(val alarmId: Long, val orderId: Long, val title: String, val orderName: String, val triggerAt: Instant)
