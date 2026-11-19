package com.mirkwood.rutinify.domain.scheduler

import java.time.Instant

/** How an alarm was handed to the system (R-5.3, R-5.4). */
enum class ScheduleMode {
    /** Exact and allowed while idle, or an alarm clock when critical (R-15.3). */
    EXACT,

    /** Up to 10 minutes late: exact alarms are not permitted (D-08, R-15.4). */
    WINDOWED,
}

/**
 * Asks the system to ring an alarm as an alarm clock (R-15.3, D-67). [orderId] is the plan the system clock UI
 * opens when the user taps the upcoming alarm.
 */
data class CriticalAlarm(val orderId: Long)

/** System alarm clock (design §6). Room is written first; this only mirrors it. */
interface AlarmScheduler {

    fun canScheduleExact(): Boolean

    /**
     * Idempotent: scheduling an [alarmId] that is already scheduled replaces it, also when it changes between normal
     * and [critical] (R-12.6, R-15.2). [critical] is null for alarms of non-critical categories (R-15.14).
     */
    fun schedule(alarmId: Long, triggerAt: Instant, critical: CriticalAlarm?): ScheduleMode

    /** No-op when nothing is scheduled for [alarmId]. */
    fun cancel(alarmId: Long)

    /** Silences a ringing critical alarm at [at] if nobody acts before (R-15.10, D-67). Idempotent. */
    fun scheduleSilence(alarmId: Long, at: Instant)

    /** No-op when no silence is scheduled for [alarmId]. */
    fun cancelSilence(alarmId: Long)

    /** The daily, inexact wake-up that creates the alarms entering the 7-day window (R-23.7, D-81). Replaces the previous one. */
    fun scheduleDailyRefresh(at: Instant)
}
