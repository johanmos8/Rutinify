package com.mirkwood.rutinify.domain.usecase

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

/** What happened to the order's pending alarms on "Reanudar". */
data class ResumeResult(val scheduled: Int, val skipped: Int)

/** "Reanudar": skips what passed while paused and schedules the rest (R-10.6). */
class ResumeOrderUseCase @Inject constructor(
    private val orders: OrderRepository,
    private val alarms: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val categories: CategorySettingsRepository,
    private val materializer: RecurringMaterializer,
    private val calculator: TriggerTimeCalculator,
    private val zoneProvider: ZoneProvider,
    private val clock: Clock,
    private val lock: AlarmMutationLock,
) {

    /**
     * A pending alarm whose effective time is not ahead becomes SKIPPED, with no 15-minute tolerance
     * (D-49): the user chose to silence them. Idempotent if interrupted halfway: the order stays PAUSED
     * until the alarms are settled. @return null if the order does not exist or is not PAUSED.
     */
    suspend operator fun invoke(orderId: Long): ResumeResult? = lock.withLock {
        val order = orders.getOrder(orderId) ?: return@withLock null
        if (order.status != OrderStatus.PAUSED) return@withLock null

        val now = clock.instant()
        val zone = zoneProvider.zone()
        val (future, past) = alarms.getPendingByOrder(orderId)
            .map { it to calculator.effectiveTrigger(it, order.date, zone) }
            .partition { (_, triggerAt) -> triggerAt.isAfter(now) }

        // Room first, then AlarmManager
        alarms.updateStatus(past.map { it.first.id }, AlarmStatus.SKIPPED)
        orders.updateStatus(orderId, OrderStatus.ACTIVE)
        val critical = if (future.isEmpty()) emptySet() else categories.criticalNames()
        future.sortedBy { it.second }
            .forEach { (alarm, triggerAt) -> scheduler.schedule(alarm.id, triggerAt, alarm.criticalIn(critical)) }
        // R-23.12: a recurring plan fills its window from now on, never the paused days
        val created = materializer.materialize(orderId)
        ResumeResult(scheduled = future.size + created, skipped = past.size)
    }
}
