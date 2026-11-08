package com.mirkwood.rutinify.data.alarm

import android.app.AlarmManager
import android.os.Build
import android.util.Log
import com.mirkwood.rutinify.data.LOG_TAG
import com.mirkwood.rutinify.domain.scheduler.AlarmScheduler
import com.mirkwood.rutinify.domain.scheduler.ScheduleMode
import java.time.Instant
import javax.inject.Inject

/** [AlarmScheduler] on top of `AlarmManager` (design §6.2). Never uses WorkManager to fire. */
class AndroidAlarmScheduler @Inject constructor(
    private val alarmManager: AlarmManager,
    private val pendingIntents: PendingIntentFactory,
) : AlarmScheduler {

    /** Always true on API 28–30, where exact alarms need no permission. */
    override fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    override fun schedule(alarmId: Long, triggerAt: Instant): ScheduleMode {
        // Same PendingIntent identity → AlarmManager replaces the previous alarm (R-12.6)
        val operation = pendingIntents.fire(alarmId)
        val millis = triggerAt.toEpochMilli()
        if (canScheduleExact()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
                return ScheduleMode.EXACT
            } catch (e: SecurityException) {
                // Permission revoked between the check and the call: degrade instead of losing the alarm
                Log.w(LOG_TAG, "Exact alarm denied for $alarmId, falling back to a window", e)
            }
        }
        alarmManager.setWindow(AlarmManager.RTC_WAKEUP, millis, WINDOW_MILLIS, operation)
        return ScheduleMode.WINDOWED
    }

    override fun cancel(alarmId: Long) {
        val operation = pendingIntents.existingFire(alarmId) ?: return
        alarmManager.cancel(operation)
        operation.cancel()
    }

    private companion object {
        /** R-5.4: up to 10 minutes late without the exact alarm permission. */
        const val WINDOW_MILLIS = 10 * 60 * 1000L
    }
}
