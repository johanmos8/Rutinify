package com.mirkwood.rutinify.data.alarm

import com.mirkwood.rutinify.data.receiver.RescheduleReceiver
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.mirkwood.rutinify.MainActivity
import com.mirkwood.rutinify.data.receiver.AlarmReceiver
import com.mirkwood.rutinify.data.receiver.NotificationActionReceiver
import com.mirkwood.rutinify.ui.alarm.AlarmActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Single place that builds the alarm `PendingIntent`s (design §6.1). Identity is
 * component + action + data URI + requestCode, with `requestCode = alarmId`, so the same intent can
 * always be recreated to replace or cancel an alarm, and the intents of one alarm never collide.
 */
class PendingIntentFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /** Creates the fire intent, or updates the existing one for [alarmId]. */
    fun fire(alarmId: Long): PendingIntent =
        createBroadcast(alarmId, fireIntent(alarmId))

    /** The fire intent for [alarmId] if it exists; never creates one. */
    fun existingFire(alarmId: Long): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            requestCode(alarmId),
            fireIntent(alarmId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )

    /** "Hecho" notification action (R-7.4). */
    fun done(alarmId: Long): PendingIntent =
        createBroadcast(alarmId, actionIntent(alarmId, ACTION_DONE, "done"))

    /** "+5 min" notification action (R-7.5). */
    fun snooze(alarmId: Long): PendingIntent =
        createBroadcast(alarmId, actionIntent(alarmId, ACTION_SNOOZE, "snooze"))

    /**
     * "Silenciar" on a ringing critical alarm (R-15.9) and its 10-minute cutoff alarm (R-15.10): one intent, so the
     * cutoff can be cancelled from either path (D-67).
     */
    fun silence(alarmId: Long): PendingIntent =
        createBroadcast(alarmId, actionIntent(alarmId, ACTION_SILENCE, SUFFIX_SILENCE))

    /** The silence intent for [alarmId] if it exists; never creates one. */
    fun existingSilence(alarmId: Long): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            requestCode(alarmId),
            actionIntent(alarmId, ACTION_SILENCE, SUFFIX_SILENCE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )

    /**
     * Daily refresh of recurring plans (R-23.7, D-94). Not tied to an alarm, so it uses requestCode 0 with its own
     * action and data, which no alarm intent shares.
     */
    fun dailyRefresh(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, RescheduleReceiver::class.java).setAction(ACTION_MATERIALIZE).setData("$SCHEME://materialize".toUri()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Full-screen alarm screen of a critical alarm (R-15.7). */
    fun alarmScreen(alarmId: Long, orderId: Long): PendingIntent {
        val intent = Intent(context, AlarmActivity::class.java)
            .setData("$SCHEME://$HOST_ALARM/$alarmId/screen".toUri())
            .putExtra(AlarmActivity.EXTRA_ALARM_ID, alarmId)
            .putExtra(AlarmActivity.EXTRA_ORDER_ID, orderId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        return PendingIntent.getActivity(
            context,
            requestCode(alarmId),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Notification body: order detail with [alarmId] highlighted (R-7.6); matches `ORDER_DEEP_LINK` in the NavHost. */
    fun openOrder(orderId: Long, alarmId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData("$SCHEME://$HOST_ORDER/$orderId?alarm=$alarmId".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            requestCode(alarmId),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun createBroadcast(alarmId: Long, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(alarmId),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun fireIntent(alarmId: Long): Intent =
        Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setData("$SCHEME://$HOST_ALARM/$alarmId".toUri())

    private fun actionIntent(alarmId: Long, action: String, suffix: String): Intent =
        Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .setData("$SCHEME://$HOST_ALARM/$alarmId/$suffix".toUri())

    companion object {
        const val ACTION_FIRE = "com.mirkwood.rutinify.action.FIRE"
        const val ACTION_DONE = "com.mirkwood.rutinify.action.DONE"
        const val ACTION_SNOOZE = "com.mirkwood.rutinify.action.SNOOZE"
        const val ACTION_SILENCE = "com.mirkwood.rutinify.action.SILENCE"
        const val ACTION_MATERIALIZE = "com.mirkwood.rutinify.action.MATERIALIZE"
        private const val SUFFIX_SILENCE = "silence"
        private const val SCHEME = "rutinify"
        private const val HOST_ALARM = "alarm"
        private const val HOST_ORDER = "order"

        /** `rutinify://alarm/{id}[/done|/snooze|/silence]` → id; null for anything else. */
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
    }
}
