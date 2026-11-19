package com.mirkwood.rutinify.data.alarm

import android.app.AlarmManager
import android.os.Build
import android.util.Log
import com.mirkwood.rutinify.data.LOG_TAG
import com.mirkwood.rutinify.domain.scheduler.AlarmScheduler
import com.mirkwood.rutinify.domain.scheduler.CriticalAlarm
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

    override fun schedule(alarmId: Long, triggerAt: Instant, critical: CriticalAlarm?): ScheduleMode {
        // Same PendingIntent identity → AlarmManager replaces the previous alarm, in either mode (R-12.6, R-15.2)
        val operation = pendingIntents.fire(alarmId)
        val millis = triggerAt.toEpochMilli()
        if (canScheduleExact()) {
            try {
                if (critical != null) {
                    // R-15.3: a real alarm clock; tapping it in the system UI opens the plan (D-67)
                    val info = AlarmManager.AlarmClockInfo(millis, pendingIntents.openOrder(critical.orderId, alarmId))
                    alarmManager.setAlarmClock(info, operation)
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
                }
                return ScheduleMode.EXACT
            } catch (e: SecurityException) {
                // Permission revoked between the check and the call: degrade instead of losing the alarm
                Log.w(LOG_TAG, "Exact alarm denied for $alarmId, falling back to a window", e)
            }
        }
        // R-5.4, R-15.4: critical alarms also fall back to a window without the permission
        alarmManager.setWindow(AlarmManager.RTC_WAKEUP, millis, WINDOW_MILLIS, operation)
        return ScheduleMode.WINDOWED
    }

    override fun cancel(alarmId: Long) {
        val operation = pendingIntents.existingFire(alarmId) ?: return
        alarmManager.cancel(operation)
        operation.cancel()
    }

    override fun scheduleSilence(alarmId: Long, at: Instant) {
        val operation = pendingIntents.silence(alarmId)
        val millis = at.toEpochMilli()
        if (canScheduleExact()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
                return
            } catch (e: SecurityException) {
                Log.w(LOG_TAG, "Exact silence denied for $alarmId, falling back to a window", e)
            }
        }
        alarmManager.setWindow(AlarmManager.RTC_WAKEUP, millis, WINDOW_MILLIS, operation)
    }

    override fun cancelSilence(alarmId: Long) {
        // Only the alarm: the same PendingIntent may still back a shown "Silenciar" button
        val operation = pendingIntents.existingSilence(alarmId) ?: return
        alarmManager.cancel(operation)
    }

    override fun scheduleDailyRefresh(at: Instant) {
        // Inexact on purpose: a few minutes either way do not matter for a 7-day window (D-81)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pendingIntents.dailyRefresh())
    }

    private companion object {
        /** R-5.4: up to 10 minutes late without the exact alarm permission. */
        const val WINDOW_MILLIS = 10 * 60 * 1000L
    }
}
