package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.repository.AlarmRepository
import com.mirkwood.rutinify.domain.repository.OrderRepository
import com.mirkwood.rutinify.domain.scheduler.AlarmScheduler
import com.mirkwood.rutinify.domain.scheduler.NotificationPublisher
import com.mirkwood.rutinify.domain.scheduler.SilencedAlarms
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import com.mirkwood.rutinify.domain.time.ZoneProvider
import javax.inject.Inject

/**
 * "Silenciar" on the alarm screen or the notification (R-15.9), and the cutoff 10 minutes after firing (R-15.10):
 * the sound stops, the alarm stays FIRED and a silent notification keeps "Hecho" and "+5 min" (D-67).
 */
class SilenceAlarmUseCase @Inject constructor(
    private val alarms: AlarmRepository,
    private val orders: OrderRepository,
    private val publisher: NotificationPublisher,
    private val scheduler: AlarmScheduler,
    private val silenced: SilencedAlarms,
    private val calculator: TriggerTimeCalculator,
    private val zoneProvider: ZoneProvider,
    private val lock: AlarmMutationLock,
) {

    /**
     * Only a FIRED alarm of an ACTIVE order is silenced; a late cutoff after "Hecho" or "+5 min" does nothing.
     * @return true if the notification was replaced by the silent one.
     */
    suspend operator fun invoke(alarmId: Long): Boolean = lock.withLock {
        scheduler.cancelSilence(alarmId)
        val alarm = alarms.getById(alarmId)
        val order = alarm?.let { orders.getOrder(it.orderId) }
        if (alarm == null || order == null || alarm.status != AlarmStatus.FIRED || order.status != OrderStatus.ACTIVE) {
            return@withLock false
        }
        silenced.add(alarmId)
        val triggerAt = calculator.effectiveTrigger(alarm, order.date, zoneProvider.zone())
        // Same notification id: replacing it on the silent channel stops the insistent sound
        publisher.publish(alarmNotification(alarm, order, triggerAt, critical = true, silenced = true))
    }
}
