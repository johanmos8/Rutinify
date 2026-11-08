package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.repository.AlarmRepository
import com.mirkwood.rutinify.domain.scheduler.AlarmScheduler
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import com.mirkwood.rutinify.domain.time.ZoneProvider
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

/** How many pending alarms went each way in one pass (design §7.2). */
data class RescheduleResult(val scheduled: Int, val firedNow: Int, val skipped: Int)

/**
 * Brings `AlarmManager` back in line with Room after boot, a time or zone change, an app update, an
 * exact-alarm permission change or an app start (design §7.2). Idempotent (R-12.6).
 */
class RescheduleAllUseCase @Inject constructor(
    private val alarms: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val handleFired: HandleAlarmFiredUseCase,
    private val calculator: TriggerTimeCalculator,
    private val zoneProvider: ZoneProvider,
    private val clock: Clock,
    private val lock: AlarmMutationLock,
) {

    suspend operator fun invoke(): RescheduleResult = lock.withLock {
        val now = clock.instant()
        val zone = zoneProvider.zone()
        // Recomputed in the current zone every time: trigger instants are never stored (R-12.2)
        val pending = alarms.getPendingForActiveOrders()
            .map { it.alarm.id to calculator.effectiveTrigger(it.alarm, it.orderDate, zone) }
        val (future, late) = pending.partition { (_, triggerAt) -> triggerAt.isAfter(now) }
        val (fireNow, skip) = late.partition { (_, triggerAt) -> Duration.between(triggerAt, now) <= LATE_TOLERANCE }

        // Room first, then AlarmManager. Skipped alarms are also cancelled: a clock moved forward
        // would otherwise fire their still-registered alarm at once (R-12.5)
        val skipIds = skip.map { it.first }
        alarms.updateStatus(skipIds, AlarmStatus.SKIPPED)
        skipIds.forEach(scheduler::cancel)
        // Same requestCode → replaces any existing alarm (R-12.6); WINDOWED without the permission (R-11.7)
        future.forEach { (id, triggerAt) -> scheduler.schedule(id, triggerAt) }
        fireNow.forEach { (id, _) -> handleFired.fireLocked(id) }

        RescheduleResult(scheduled = future.size, firedNow = fireNow.size, skipped = skip.size)
    }

    private companion object {
        /** R-12.4 / R-12.5: up to 15 minutes late still fires; later is skipped. */
        val LATE_TOLERANCE: Duration = Duration.ofMinutes(15)
    }
}
