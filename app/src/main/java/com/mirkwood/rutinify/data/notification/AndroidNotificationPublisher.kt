package com.mirkwood.rutinify.data.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.data.LOG_TAG
import com.mirkwood.rutinify.data.alarm.PendingIntentFactory
import com.mirkwood.rutinify.domain.scheduler.AlarmNotification
import com.mirkwood.rutinify.domain.scheduler.NotificationPublisher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.format.DateTimeFormatter
import java.util.Locale
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
        ensureChannels(listOf(notification.category))
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

    private fun build(n: AlarmNotification): Notification {
        val done = context.getString(R.string.notification_action_done)
        val snooze = context.getString(R.string.notification_action_snooze)
        return NotificationCompat.Builder(context, channelId(n.category))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(n.title)
            .setContentText(n.description ?: n.time.format(TIME_FORMAT))
            .setSubText(n.orderName)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(n.triggerAt.toEpochMilli())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntents.openOrder(n.orderId, n.alarmId))
            .addAction(NotificationCompat.Action.Builder(null, done, pendingIntents.done(n.alarmId)).build())
            .addAction(NotificationCompat.Action.Builder(null, snooze, pendingIntents.snooze(n.alarmId)).build())
            .addExtras(Bundle().apply { putLong(EXTRA_ORDER_ID, n.orderId) })
            .build()
    }

    private companion object {
        /** Notification id = alarm id, with this tag (design §6.1). */
        const val TAG = "alarm"
        const val GROUP_ID = "routines"
        const val EXTRA_ORDER_ID = "com.mirkwood.rutinify.extra.ORDER_ID"
        const val NO_ORDER = -1L
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        fun channelId(category: String) = "cat_$category"

        /** "paseo-largo" → "Paseo largo" (design §8). */
        fun channelName(category: String) =
            category.replace('-', ' ').replaceFirstChar { it.titlecase(Locale.ROOT) }
    }
}

/** Runtime permission on Android 13+ plus the app-level switch in system settings (R-7.8, R-11.4). */
internal fun notificationsAllowed(context: Context, manager: NotificationManager): Boolean {
    val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    return permitted && manager.areNotificationsEnabled()
}
