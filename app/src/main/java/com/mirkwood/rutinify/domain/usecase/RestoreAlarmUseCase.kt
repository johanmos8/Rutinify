package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.model.AlarmItem
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.repository.AlarmRepository
import com.mirkwood.rutinify.domain.repository.CategorySettingsRepository
import com.mirkwood.rutinify.domain.repository.OrderRepository
import com.mirkwood.rutinify.domain.scheduler.AlarmScheduler
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import com.mirkwood.rutinify.domain.time.ZoneProvider
import java.time.Clock
import javax.inject.Inject

/** "Deshacer" after deleting one alarm (R-9.4). */
class RestoreAlarmUseCase @Inject constructor(
    private val alarms: AlarmRepository,
    private val orders: OrderRepository,
    private val scheduler: AlarmScheduler,
    private val categories: CategorySettingsRepository,
    private val calculator: TriggerTimeCalculator,
    private val zoneProvider: ZoneProvider,
    private val clock: Clock,
    private val lock: AlarmMutationLock,
) {

    /**
     * Re-inserts [alarm] with the same id (= requestCode) and reschedules it when it is a pending alarm
     * of an ACTIVE order whose time is still ahead. A pending alarm whose time passed while it was
     * deleted becomes SKIPPED (D-47). Nothing happens if the order was deleted meanwhile.
     * @return true if the alarm was handed to the system again.
     */
    suspend operator fun invoke(alarm: AlarmItem): Boolean = lock.withLock {
        val order = orders.getOrder(alarm.orderId) ?: return@withLock false
        alarms.restore(alarm)
        if (alarm.status != AlarmStatus.PENDING || order.status != OrderStatus.ACTIVE) return@withLock false

        val triggerAt = calculator.effectiveTrigger(alarm, order.date, zoneProvider.zone())
        if (!triggerAt.isAfter(clock.instant())) {
            alarms.updateStatus(listOf(alarm.id), AlarmStatus.SKIPPED)
            return@withLock false
        }
        scheduler.schedule(alarm.id, triggerAt, alarm.criticalIn(categories.criticalNames()))
        true
    }
}
