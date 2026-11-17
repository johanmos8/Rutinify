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
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** [NotificationPublisher] on top of `NotificationManager` (design §8). */
class AndroidNotificationPublisher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val manager: NotificationManager,
    private val pendingIntents: PendingIntentFactory,
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
            manager.notify(TAG, notification.alarmId.toInt(), build(notification))
            true
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call (R-7.8)
            Log.w(LOG_TAG, "Notification denied for alarm ${notification.alarmId}", e)
            false
        }
    }

    override fun cancel(alarmId: Long) {
        manager.cancel(TAG, alarmId.toInt())
    }

    override fun cancelForOrder(orderId: Long) {
        manager.activeNotifications
            .filter { it.tag == TAG && it.notification.extras.getLong(EXTRA_ORDER_ID, NO_ORDER) == orderId }
            .forEach { manager.cancel(TAG, it.id) }
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

    private fun build(n: AlarmNotification): Notification {
        val done = context.getString(R.string.notification_action_done)
        val snooze = context.getString(R.string.notification_action_snooze)
        val builder = NotificationCompat.Builder(context, if (n.critical) CHANNEL_CRITICAL else channelId(n.category))
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
            .addAction(NotificationCompat.Action.Builder(null, done, pendingIntents.done(n.alarmId)).build())
            .addAction(NotificationCompat.Action.Builder(null, snooze, pendingIntents.snooze(n.alarmId)).build())
            .addExtras(Bundle().apply { putLong(EXTRA_ORDER_ID, n.orderId) })
        return builder.build().apply {
            // R-15.6: the sound repeats until "Hecho", "+5 min" or dismissing removes the notification
            if (n.critical) flags = flags or Notification.FLAG_INSISTENT
        }
    }

    private companion object {
        /** Notification id = alarm id, with this tag (design §6.1). */
        const val TAG = "alarm"
        const val GROUP_ID = "routines"
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
