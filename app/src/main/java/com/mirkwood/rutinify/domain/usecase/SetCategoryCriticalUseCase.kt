package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.repository.AlarmRepository
import com.mirkwood.rutinify.domain.repository.CategorySettingsRepository
import com.mirkwood.rutinify.domain.scheduler.AlarmScheduler
import com.mirkwood.rutinify.domain.scheduler.CriticalAlarm
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import com.mirkwood.rutinify.domain.time.ZoneProvider
import java.time.Clock
import javax.inject.Inject

/**
 * Marks or unmarks a category as critical and reschedules its future pending alarms of active plans, so they move to
 * the new mode (R-15.1, R-15.2). Room first, then `AlarmManager`; rescheduling is idempotent (R-12.6). Late alarms are
 * left to `RescheduleAllUseCase`.
 * @return how many alarms were rescheduled.
 */
class SetCategoryCriticalUseCase @Inject constructor(
    private val categories: CategorySettingsRepository,
    private val alarms: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val calculator: TriggerTimeCalculator,
    private val zoneProvider: ZoneProvider,
    private val clock: Clock,
    private val lock: AlarmMutationLock,
) {

    suspend operator fun invoke(category: String, critical: Boolean): Int = lock.withLock {
        categories.setCritical(category, critical)
        val now = clock.instant()
        val zone = zoneProvider.zone()
        alarms.getPendingForActiveOrders()
            .filter { it.alarm.category == category }
            .map { it.alarm to calculator.effectiveTrigger(it.alarm, it.orderDate, zone) }
            .filter { (_, triggerAt) -> triggerAt.isAfter(now) }
            .onEach { (alarm, triggerAt) -> scheduler.schedule(alarm.id, triggerAt, if (critical) CriticalAlarm(alarm.orderId) else null) }
            .size
    }
}
