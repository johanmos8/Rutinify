package com.mirkwood.rutinify.domain.model

import java.time.Instant

/** What happened to an alarm and when, for the plan history (R-20.1, D-77). */
enum class AlarmEventType { DONE, SKIPPED, SNOOZED }

data class AlarmEvent(val alarmId: Long, val orderId: Long, val type: AlarmEventType, val at: Instant)
