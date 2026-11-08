package com.mirkwood.rutinify.data.receiver

import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** T-19: system events that trigger a reschedule (design §7.1, R-11.6, R-11.7, R-12.1–R-12.3). */
@RunWith(AndroidJUnit4::class)
class RescheduleReceiverTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val systemActions = listOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
    )

    @Test
    fun `acepta los eventos del sistema que obligan a reprogramar`() { // R-11.6, R-11.7, R-12.1–R-12.3
        systemActions.forEach { assertThat(RescheduleReceiver.isRescheduleAction(it)).isTrue() }
    }

    @Test
    fun `ignora cualquier otra accion`() {
        assertThat(RescheduleReceiver.isRescheduleAction(null)).isFalse()
        assertThat(RescheduleReceiver.isRescheduleAction(Intent.ACTION_SCREEN_ON)).isFalse()
        assertThat(RescheduleReceiver.isRescheduleAction("com.mirkwood.rutinify.action.FIRE")).isFalse()
    }

    @Test
    fun `el manifiesto registra el receiver para cada evento`() { // design §7.1
        val component = ComponentName(context, RescheduleReceiver::class.java)
        systemActions.forEach { action ->
            val receivers = context.packageManager
                .queryBroadcastReceivers(Intent(action).setPackage(context.packageName), 0)
                .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }

            assertThat(receivers).contains(component)
        }
    }

    @Test
    fun `el receiver esta exportado para recibir broadcasts del sistema`() { // design §7.1
        val info = context.packageManager.getReceiverInfo(
            ComponentName(context, RescheduleReceiver::class.java),
            PackageManager.GET_META_DATA,
        )

        assertThat(info.exported).isTrue()
    }
}
