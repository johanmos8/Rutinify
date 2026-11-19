package com.mirkwood.rutinify.data.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.data.LOG_TAG
import com.mirkwood.rutinify.data.alarm.PendingIntentFactory
import com.mirkwood.rutinify.domain.model.categoryDisplayName
import com.mirkwood.rutinify.domain.scheduler.AlarmNotification
import com.mirkwood.rutinify.domain.scheduler.NotificationPublisher
import com.mirkwood.rutinify.domain.scheduler.PermissionChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** [NotificationPublisher] on top of `NotificationManager` (design §8). */
class AndroidNotificationPublisher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val manager: NotificationManager,
    private val pendingIntents: PendingIntentFactory,
    private val permissions: PermissionChecker,
) : NotificationPublisher {

    override fun ensureChannels(categories: Collection<String>) {
        val missing = categories.distinct().filter { manager.getNotificationChannel(channelId(it)) == null }
        if (missing.isEmpty()) return
        manager.createNotificationChannelGroup(
            NotificationChannelGroup(GROUP_ID, context.getString(R.string.notification_group_routines)),
        )
        // Only missing channels are created: the user's settings on existing ones are kept (D-15)
        missing.forEach { category ->
            val channel = NotificationChannel(channelId(category), channelName(category), NotificationManager.IMPORTANCE_HIGH)
            channel.group = GROUP_ID
            manager.createNotificationChannel(channel)
        }
    }

    override fun publish(notification: AlarmNotification): Boolean {
        if (!canNotify()) return false
        if (notification.critical) ensureCriticalChannels() else ensureChannels(listOf(notification.category))
        return try {
            val built = build(notification)
            manager.notify(TAG, notification.alarmId.toInt(), built)
            // After the alarm, so it is already in the group when the summary appears (K-13)
            postSummary(built.channelId)
            true
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call (R-7.8)
            Log.w(LOG_TAG, "Notification denied for alarm ${notification.alarmId}", e)
            false
        }
    }

    override fun cancel(alarmId: Long) {
        manager.cancel(TAG, alarmId.toInt())
        removeSummaryIfEmpty(cancelled = setOf(alarmId.toInt()))
    }

    override fun cancelForOrder(orderId: Long) {
        val ids = manager.activeNotifications
            .filter { it.tag == TAG && it.notification.extras.getLong(EXTRA_ORDER_ID, NO_ORDER) == orderId }
            .map { it.id }
        ids.forEach { manager.cancel(TAG, it) }
        removeSummaryIfEmpty(cancelled = ids.toSet())
    }

    /**
     * K-13, D-87: Android 16 groups two or more ungrouped notifications of an app by itself and marks them silent,
     * so a second alarm would not ring. Our own group with a summary keeps the system from regrouping them; the
     * summary never alerts (`GROUP_ALERT_CHILDREN`) and each alarm rings on its own channel.
     */
    private fun postSummary(channelId: String) {
        val summary = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setGroup(ALARM_GROUP)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        manager.notify(TAG_SUMMARY, SUMMARY_ID, summary)
    }

    /** [cancelled] are left out: `cancel` is asynchronous, so they may still be listed as active. */
    private fun removeSummaryIfEmpty(cancelled: Set<Int>) {
        if (manager.activeNotifications.none { it.tag == TAG && it.id !in cancelled }) manager.cancel(TAG_SUMMARY, SUMMARY_ID)
    }

    private fun canNotify(): Boolean = notificationsAllowed(context, manager)

    /**
     * The two channels of critical alarms (D-67). A channel's sound cannot change after it is created, so critical
     * alarms cannot use `cat_<category>`: one channel rings with the system alarm sound, the other is silent (T-46).
     */
    private fun ensureCriticalChannels() {
        if (manager.getNotificationChannel(CHANNEL_CRITICAL) == null) {
            val channel = NotificationChannel(
                CHANNEL_CRITICAL,
                context.getString(R.string.notification_channel_critical),
                NotificationManager.IMPORTANCE_HIGH,
            )
            // USAGE_ALARM: rings at alarm volume and passes "No molestar" when it lets alarms through (R-15.12)
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            channel.setSound(alarmSound(), attributes)
            channel.enableVibration(true)
            manager.createNotificationChannel(channel)
        }
        if (manager.getNotificationChannel(CHANNEL_CRITICAL_SILENT) == null) {
            val channel = NotificationChannel(
                CHANNEL_CRITICAL_SILENT,
                context.getString(R.string.notification_channel_critical_silent),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            channel.setSound(null, null)
            channel.enableVibration(false)
            manager.createNotificationChannel(channel)
        }
    }

    /** The user's alarm tone; the default notification tone only if the phone has none. */
    private fun alarmSound(): Uri =
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI

    /**
     * R-24, D-82: never `setLocalOnly`, so a paired Wear OS watch shows the alarm with the same actions. They are
     * broadcasts to `NotificationActionReceiver`, which the watch runs on the phone; their icons are the watch's
     * buttons (the phone shows only the text). The full screen and the insistent sound stay on the phone.
     */
    private fun build(n: AlarmNotification): Notification {
        val done = context.getString(R.string.notification_action_done)
        val snooze = context.getString(R.string.notification_action_snooze)
        val channel = when {
            n.critical && n.silenced -> CHANNEL_CRITICAL_SILENT
            n.critical -> CHANNEL_CRITICAL
            else -> channelId(n.category)
        }
        val ringing = n.critical && !n.silenced
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(n.title)
            .setContentText(n.description ?: n.time.format(TIME_FORMAT))
            .setSubText(n.orderName)
            .setCategory(if (n.critical) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(n.triggerAt.toEpochMilli())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntents.openOrder(n.orderId, n.alarmId))
            .addAction(NotificationCompat.Action.Builder(R.drawable.ic_action_done, done, pendingIntents.done(n.alarmId)).build())
            .addAction(NotificationCompat.Action.Builder(R.drawable.ic_action_snooze, snooze, pendingIntents.snooze(n.alarmId)).build())
            .addExtras(Bundle().apply { putLong(EXTRA_ORDER_ID, n.orderId) })
            .setGroup(ALARM_GROUP)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        if (ringing) {
            val silence = context.getString(R.string.notification_action_silence)
            builder.addAction(NotificationCompat.Action.Builder(R.drawable.ic_action_silence, silence, pendingIntents.silence(n.alarmId)).build())
            // R-15.7 over the lock screen; checked on every alarm, because the user can revoke it at any time. Without
            // it the same notification shows as heads-up, with the same sound and buttons (R-15.11, D-68)
            if (permissions.fullScreenIntentGranted()) {
                builder.setFullScreenIntent(pendingIntents.alarmScreen(n.alarmId, n.orderId), true)
            }
        }
        return builder.build().apply {
            // R-15.6: the sound repeats until "Hecho", "+5 min", "Silenciar" or dismissing replaces or removes it
            if (ringing) flags = flags or Notification.FLAG_INSISTENT
        }
    }

    private companion object {
        /** Notification id = alarm id, with this tag (design §6.1). */
        const val TAG = "alarm"
        const val GROUP_ID = "routines"

        /** Notification group of every alarm, with one summary (K-13, D-87); not the channel group [GROUP_ID]. */
        const val ALARM_GROUP = "com.mirkwood.rutinify.ALARMS"
        const val TAG_SUMMARY = "alarm_summary"
        const val SUMMARY_ID = 0
        const val CHANNEL_CRITICAL = "alarm_critical"
        const val CHANNEL_CRITICAL_SILENT = "alarm_critical_silent"
        const val EXTRA_ORDER_ID = "com.mirkwood.rutinify.extra.ORDER_ID"
        const val NO_ORDER = -1L
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        fun channelId(category: String) = "cat_$category"

        /** "paseo-largo" → "Paseo largo" (design §8). */
        fun channelName(category: String) = categoryDisplayName(category)
    }
}

/** Runtime permission on Android 13+ plus the app-level switch in system settings (R-7.8, R-11.4). */
internal fun notificationsAllowed(context: Context, manager: NotificationManager): Boolean {
    val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    return permitted && manager.areNotificationsEnabled()
}
