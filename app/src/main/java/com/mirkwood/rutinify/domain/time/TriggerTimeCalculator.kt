package com.mirkwood.rutinify.domain.time

import com.mirkwood.rutinify.domain.model.AlarmItem
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * The only place that turns wall-clock data into an instant (design §6.3). Trigger times are never
 * stored, so a zone change only needs a recalculation with the new [ZoneId] (R-12.2).
 */
class TriggerTimeCalculator @Inject constructor() {

    /**
     * `date + dayOffset` at [time] in [zone] (R-5.2). `ZonedDateTime.of` moves a time inside a DST gap
     * forward by the gap length and picks the earlier offset in an overlap (R-5.8).
     */
    fun triggerAt(date: LocalDate, time: LocalTime, dayOffset: Int, zone: ZoneId): Instant =
        ZonedDateTime.of(date.plusDays(dayOffset.toLong()), time, zone).toInstant()

    /** A snoozed alarm keeps its absolute instant (D-12); otherwise the computed wall-clock time. */
    fun effectiveTrigger(alarm: AlarmItem, orderDate: LocalDate, zone: ZoneId): Instant =
        alarm.snoozedUntil ?: triggerAt(orderDate, alarm.time, alarm.dayOffset, zone)
}
