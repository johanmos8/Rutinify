package com.mirkwood.rutinify.domain.scheduler

import java.time.Instant
import java.time.LocalTime

/** What a fired alarm shows (R-7.1). Built by the use case from the alarm and its order. */
data class AlarmNotification(
    val alarmId: Long,
    val orderId: Long,
    val orderName: String,
    val title: String,
    val description: String?,
    /** Shown as text when there is no [description]. */
    val time: LocalTime,
    /** Normalized category: picks the `cat_<category>` channel (R-7.2). */
    val category: String,
    val triggerAt: Instant,
    /** Rings as an alarm on the "Alarmas críticas" channel instead of [category]'s (R-15.6, D-67). */
    val critical: Boolean = false,
)

/** System notifications (design §8). Never throws for a missing permission. */
interface NotificationPublisher {

    /** Creates the channel of each category that does not exist yet; existing ones are left untouched (R-5.9, D-15). */
    fun ensureChannels(categories: Collection<String>)

    /** @return false when notifications are disabled or not permitted; nothing is shown (R-7.8). */
    fun publish(notification: AlarmNotification): Boolean

    /** Removes the notification of [alarmId], if shown. */
    fun cancel(alarmId: Long)

    /** Removes every notification shown for [orderId] (R-10.2). */
    fun cancelForOrder(orderId: Long)
}
