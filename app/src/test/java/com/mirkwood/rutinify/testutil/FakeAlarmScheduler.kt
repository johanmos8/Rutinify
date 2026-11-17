package com.mirkwood.rutinify.testutil

import com.mirkwood.rutinify.domain.scheduler.AlarmScheduler
import com.mirkwood.rutinify.domain.scheduler.CriticalAlarm
import com.mirkwood.rutinify.domain.scheduler.ScheduleMode
import java.time.Instant

/** Records what would be handed to `AlarmManager`; idempotent like the real one. */
class FakeAlarmScheduler(var exact: Boolean = true) : AlarmScheduler {

    val scheduled = linkedMapOf<Long, Instant>()
    val cancelled = mutableListOf<Long>()

    /** Alarm ids currently scheduled as alarm clocks, with their [CriticalAlarm] (R-15.3). */
    val critical = linkedMapOf<Long, CriticalAlarm>()

    override fun canScheduleExact(): Boolean = exact

    override fun schedule(alarmId: Long, triggerAt: Instant, critical: CriticalAlarm?): ScheduleMode {
        scheduled[alarmId] = triggerAt
        if (critical != null) this.critical[alarmId] = critical else this.critical.remove(alarmId)
        return if (exact) ScheduleMode.EXACT else ScheduleMode.WINDOWED
    }

    /** Test setup shortcut for a non-critical alarm already in the system. */
    fun schedule(alarmId: Long, triggerAt: Instant): ScheduleMode = schedule(alarmId, triggerAt, null)

    override fun cancel(alarmId: Long) {
        scheduled.remove(alarmId)
        critical.remove(alarmId)
        cancelled += alarmId
    }
}
