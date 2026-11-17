package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.repository.AlarmRepository
import com.mirkwood.rutinify.domain.repository.CategorySettingsRepository
import com.mirkwood.rutinify.domain.repository.OrderRepository
import com.mirkwood.rutinify.domain.scheduler.AlarmScheduler
import com.mirkwood.rutinify.domain.scheduler.NotificationPublisher
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import com.mirkwood.rutinify.domain.time.ZoneProvider
import java.time.Clock
import java.time.Duration
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
    private val categories: CategorySettingsRepository,
    private val scheduler: AlarmScheduler,
    private val clock: Clock,
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
        val critical = alarm.category in categories.criticalNames() // R-15.6
        val triggerAt = calculator.effectiveTrigger(alarm, order.date, zoneProvider.zone())
        if (!publisher.publish(alarmNotification(alarm, order, triggerAt, critical))) return FireOutcome.FIRED_WITHOUT_NOTIFICATION
        // R-15.10: a critical alarm nobody answers goes quiet 10 minutes after it starts ringing
        if (critical) scheduler.scheduleSilence(alarmId, clock.instant().plus(CRITICAL_RING))
        return FireOutcome.NOTIFIED
    }

    private companion object {
        /** D-67: the usual cutoff of alarm clocks. */
        val CRITICAL_RING: Duration = Duration.ofMinutes(10)
    }
}
