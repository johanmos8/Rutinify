package com.mirkwood.rutinify.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Target of the fire `PendingIntent` (design §6.1, §7). Declared now so alarms have a stable component;
 * handling the fired alarm (`HandleAlarmFiredUseCase`) is added in T-18 (D-37).
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Intentionally empty until T-18
    }
}
