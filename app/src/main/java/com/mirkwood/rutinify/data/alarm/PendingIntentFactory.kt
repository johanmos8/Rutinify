package com.mirkwood.rutinify.data.alarm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.mirkwood.rutinify.data.receiver.AlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Single place that builds the alarm `PendingIntent`s (design §6.1). Identity is
 * component + action + data URI + requestCode, with `requestCode = alarmId`, so the same intent can
 * always be recreated to replace or cancel an alarm.
 */
class PendingIntentFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /** Creates the fire intent, or updates the existing one for [alarmId]. */
    fun fire(alarmId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(alarmId),
            fireIntent(alarmId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** The fire intent for [alarmId] if it exists; never creates one. */
    fun existingFire(alarmId: Long): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            requestCode(alarmId),
            fireIntent(alarmId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )

    private fun fireIntent(alarmId: Long): Intent =
        Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setData(alarmUri(alarmId))

    companion object {
        const val ACTION_FIRE = "com.mirkwood.rutinify.action.FIRE"
        private const val SCHEME = "rutinify"
        private const val HOST_ALARM = "alarm"

        /** `rutinify://alarm/{id}` → id; null for anything else. */
        fun alarmIdOf(intent: Intent): Long? {
            val uri = intent.data ?: return null
            if (uri.scheme != SCHEME || uri.host != HOST_ALARM) return null
            val id = uri.pathSegments.firstOrNull()?.toLongOrNull() ?: return null
            return id.takeIf { it in VALID_IDS }
        }

        private val VALID_IDS = 1L..Int.MAX_VALUE.toLong()

        private fun requestCode(alarmId: Long): Int {
            require(alarmId in VALID_IDS) { "alarmId out of requestCode range: $alarmId" }
            return alarmId.toInt()
        }

        private fun alarmUri(alarmId: Long): Uri = "$SCHEME://$HOST_ALARM/$alarmId".toUri()
    }
}
