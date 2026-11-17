package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.repository.AlarmRepository
import com.mirkwood.rutinify.domain.repository.CategorySettingsRepository
import com.mirkwood.rutinify.domain.repository.OrderRepository
import com.mirkwood.rutinify.domain.scheduler.AlarmScheduler
import com.mirkwood.rutinify.domain.scheduler.NotificationPublisher
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

/** "+5 min" from the notification (R-7.5). */
class SnoozeAlarmUseCase @Inject constructor(
    private val alarms: AlarmRepository,
    private val orders: OrderRepository,
    private val scheduler: AlarmScheduler,
    private val categories: CategorySettingsRepository,
    private val publisher: NotificationPublisher,
    private val clock: Clock,
    private val lock: AlarmMutationLock,
) {

    /**
     * Only a FIRED alarm of an ACTIVE order is snoozed (D-39); a repeated tap or a stale notification
     * just removes the notification. @return true if the alarm was rescheduled.
     */
    suspend operator fun invoke(alarmId: Long): Boolean = lock.withLock {
        publisher.cancel(alarmId)
        scheduler.cancelSilence(alarmId) // R-15.8
        val alarm = alarms.getById(alarmId)
        val order = alarm?.let { orders.getOrder(it.orderId) }
        if (alarm == null || order == null || alarm.status != AlarmStatus.FIRED || order.status != OrderStatus.ACTIVE) {
            return@withLock false
        }
        // Absolute instant from the moment of the tap (D-12); Room first, then AlarmManager
        val until = clock.instant().plus(SNOOZE)
        alarms.snooze(alarmId, until)
        // A snoozed critical alarm rings again as an alarm clock (R-15.8)
        scheduler.schedule(alarmId, until, alarm.criticalIn(categories.criticalNames()))
        true
    }

    private companion object {
        val SNOOZE: Duration = Duration.ofMinutes(5)
    }
}
