package com.mirkwood.rutinify.domain.model

import java.time.Instant
import java.time.LocalTime

/** New wall-clock time of a pending alarm after "Voy tarde" (R-18.3, R-18.4, D-73). */
data class AlarmScheduleUpdate(val alarmId: Long, val time: LocalTime, val dayOffset: Int, val snoozedUntil: Instant?)
