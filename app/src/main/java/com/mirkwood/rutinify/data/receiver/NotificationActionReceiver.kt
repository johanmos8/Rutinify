package com.mirkwood.rutinify.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Target of the "Hecho" and "+5 min" notification actions (design §6.1, §7). Declared now so the
 * actions have a stable component; their handling is added in T-18 (D-38).
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Intentionally empty until T-18
    }
}
