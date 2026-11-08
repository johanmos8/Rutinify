package com.mirkwood.rutinify.testutil

import com.mirkwood.rutinify.domain.scheduler.AlarmNotification
import com.mirkwood.rutinify.domain.scheduler.NotificationPublisher

/** Keeps the notifications currently "shown"; [enabled] = false simulates a denied permission. */
class FakeNotificationPublisher(var enabled: Boolean = true) : NotificationPublisher {

    val channels = linkedSetOf<String>()
    val shown = linkedMapOf<Long, AlarmNotification>()
    val cancelled = mutableListOf<Long>()

    override fun ensureChannels(categories: Collection<String>) {
        channels += categories
    }

    override fun publish(notification: AlarmNotification): Boolean {
        if (!enabled) return false
        channels += notification.category
        shown[notification.alarmId] = notification
        return true
    }

    override fun cancel(alarmId: Long) {
        shown.remove(alarmId)
        cancelled += alarmId
    }

    override fun cancelForOrder(orderId: Long) {
        shown.values.filter { it.orderId == orderId }.forEach { cancel(it.alarmId) }
    }
}
