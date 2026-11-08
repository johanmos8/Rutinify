package com.mirkwood.rutinify.testutil

import com.mirkwood.rutinify.domain.scheduler.AlarmScheduler
import com.mirkwood.rutinify.domain.scheduler.ScheduleMode
import java.time.Instant

/** Records what would be handed to `AlarmManager`; idempotent like the real one. */
class FakeAlarmScheduler(var exact: Boolean = true) : AlarmScheduler {

    val scheduled = linkedMapOf<Long, Instant>()
    val cancelled = mutableListOf<Long>()

    override fun canScheduleExact(): Boolean = exact

    override fun schedule(alarmId: Long, triggerAt: Instant): ScheduleMode {
        scheduled[alarmId] = triggerAt
        return if (exact) ScheduleMode.EXACT else ScheduleMode.WINDOWED
    }

    override fun cancel(alarmId: Long) {
        scheduled.remove(alarmId)
        cancelled += alarmId
    }
}
