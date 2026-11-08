package com.mirkwood.rutinify.data.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.scheduler.ScheduleMode
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Instant

/** T-15: AlarmManager scheduling (R-5.3, R-5.4, R-12.6, design §6). */
@RunWith(AndroidJUnit4::class)
class AndroidAlarmSchedulerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val factory = PendingIntentFactory(context)
    private val scheduler = AndroidAlarmScheduler(alarmManager, factory)
    private val shadow = shadowOf(alarmManager)

    private val triggerAt = Instant.parse("2026-11-05T11:45:00Z")

    /** `ScheduledAlarm` exposes the operation only as a deprecated public field; there is no getter. */
    @Suppress("DEPRECATION")
    private fun operationOf(alarm: ShadowAlarmManager.ScheduledAlarm): PendingIntent? = alarm.operation

    @After
    fun restoreExactPermission() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun `con permiso programa una alarma exacta permitida en reposo`() { // R-5.3
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        val mode = scheduler.schedule(1L, triggerAt)

        val alarm = shadow.scheduledAlarms.single()
        assertThat(mode).isEqualTo(ScheduleMode.EXACT)
        assertThat(alarm.getType()).isEqualTo(AlarmManager.RTC_WAKEUP)
        assertThat(alarm.triggerAtMs).isEqualTo(triggerAt.toEpochMilli())
        assertThat(alarm.isAllowWhileIdle).isTrue()
    }

    @Test
    fun `sin permiso programa con ventana de 10 minutos`() { // R-5.4
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        val mode = scheduler.schedule(1L, triggerAt)

        val alarm = shadow.scheduledAlarms.single()
        assertThat(mode).isEqualTo(ScheduleMode.WINDOWED)
        assertThat(alarm.getType()).isEqualTo(AlarmManager.RTC_WAKEUP)
        assertThat(alarm.triggerAtMs).isEqualTo(triggerAt.toEpochMilli())
        assertThat(alarm.windowLengthMs).isEqualTo(10 * 60 * 1000L)
    }

    @Test
    fun `canScheduleExact refleja el permiso del sistema`() { // R-5.3, R-5.4
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertThat(scheduler.canScheduleExact()).isFalse()

        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertThat(scheduler.canScheduleExact()).isTrue()
    }

    @Test
    fun `la alarma usa el PendingIntent con requestCode igual al id`() { // design §6.1
        scheduler.schedule(42L, triggerAt)

        assertThat(shadowOf(operationOf(shadow.scheduledAlarms.single())).requestCode).isEqualTo(42)
    }

    @Test
    fun `programar dos veces el mismo id deja una sola alarma`() { // R-12.6
        scheduler.schedule(1L, triggerAt)
        scheduler.schedule(1L, triggerAt)

        assertThat(shadow.scheduledAlarms).hasSize(1)
    }

    @Test
    fun `reprogramar el mismo id reemplaza la hora`() { // R-12.6, snooze (R-7.5)
        val later = triggerAt.plusSeconds(300)

        scheduler.schedule(1L, triggerAt)
        scheduler.schedule(1L, later)

        assertThat(shadow.scheduledAlarms.single().triggerAtMs).isEqualTo(later.toEpochMilli())
    }

    @Test
    fun `ids distintos son alarmas distintas`() { // design §6.1
        scheduler.schedule(1L, triggerAt)
        scheduler.schedule(2L, triggerAt)

        assertThat(shadow.scheduledAlarms).hasSize(2)
    }

    @Test
    fun `cancel encuentra el mismo PendingIntent y quita la alarma`() { // design §6.1
        scheduler.schedule(1L, triggerAt)
        scheduler.schedule(2L, triggerAt)

        scheduler.cancel(1L)

        val remaining = shadow.scheduledAlarms.single()
        assertThat(shadowOf(operationOf(remaining)).requestCode).isEqualTo(2)
        assertThat(factory.existingFire(1L)).isNull()
    }

    @Test
    fun `cancelar una alarma que no existe no falla`() { // idempotent (design §6.1)
        scheduler.cancel(77L)
        scheduler.cancel(77L)

        assertThat(shadow.scheduledAlarms).isEmpty()
    }

    @Test
    fun `se puede volver a programar tras cancelar`() { // R-12.6
        scheduler.schedule(1L, triggerAt)
        scheduler.cancel(1L)

        scheduler.schedule(1L, triggerAt)

        assertThat(shadow.scheduledAlarms).hasSize(1)
    }
}
