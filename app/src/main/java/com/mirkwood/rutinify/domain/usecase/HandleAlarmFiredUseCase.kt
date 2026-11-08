package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.repository.AlarmRepository
import com.mirkwood.rutinify.domain.repository.OrderRepository
import com.mirkwood.rutinify.domain.scheduler.AlarmNotification
import com.mirkwood.rutinify.domain.scheduler.NotificationPublisher
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import com.mirkwood.rutinify.domain.time.ZoneProvider
import javax.inject.Inject

enum class FireOutcome {
    /** Marked FIRED and notified (R-7.1). */
    NOTIFIED,

    /** Marked FIRED, but notifications are disabled or not permitted (R-7.8). */
    FIRED_WITHOUT_NOTIFICATION,

    /** Missing alarm, not PENDING or order not ACTIVE: nothing changes (R-7.7). */
    IGNORED,
}

/** Called by `AlarmReceiver` when an alarm goes off (design §6.4). */
class HandleAlarmFiredUseCase @Inject constructor(
    private val alarms: AlarmRepository,
    private val orders: OrderRepository,
    private val publisher: NotificationPublisher,
    private val calculator: TriggerTimeCalculator,
    private val zoneProvider: ZoneProvider,
    private val lock: AlarmMutationLock,
) {

    suspend operator fun invoke(alarmId: Long): FireOutcome = lock.withLock { fireLocked(alarmId) }

    /** Same as [invoke] for a use case that already holds [AlarmMutationLock], which is not reentrant. */
    internal suspend fun fireLocked(alarmId: Long): FireOutcome {
        val alarm = alarms.getById(alarmId)
        val order = alarm?.let { orders.getOrder(it.orderId) }
        if (alarm == null || order == null || alarm.status != AlarmStatus.PENDING || order.status != OrderStatus.ACTIVE) {
            return FireOutcome.IGNORED
        }
        // Room first: the alarm is FIRED even if the notification cannot be shown (R-7.8)
        alarms.updateStatus(listOf(alarmId), AlarmStatus.FIRED)
        val notification = AlarmNotification(
            alarmId = alarm.id,
            orderId = order.id,
            orderName = order.name,
            title = alarm.title,
            description = alarm.description,
            time = alarm.time,
            category = alarm.category,
            triggerAt = calculator.effectiveTrigger(alarm, order.date, zoneProvider.zone()),
        )
        return if (publisher.publish(notification)) FireOutcome.NOTIFIED else FireOutcome.FIRED_WITHOUT_NOTIFICATION
    }
}
