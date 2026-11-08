package com.mirkwood.rutinify.domain.scheduler

import java.time.Instant

/** How an alarm was handed to the system (R-5.3, R-5.4). */
enum class ScheduleMode {
    /** Exact and allowed while idle. */
    EXACT,

    /** Up to 10 minutes late: exact alarms are not permitted (D-08). */
    WINDOWED,
}

/** System alarm clock (design §6). Room is written first; this only mirrors it. */
interface AlarmScheduler {

    fun canScheduleExact(): Boolean

    /** Idempotent: scheduling an [alarmId] that is already scheduled replaces it (R-12.6). */
    fun schedule(alarmId: Long, triggerAt: Instant): ScheduleMode

    /** No-op when nothing is scheduled for [alarmId]. */
    fun cancel(alarmId: Long)
}
