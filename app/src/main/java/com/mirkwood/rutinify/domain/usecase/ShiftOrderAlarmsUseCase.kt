package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.model.AlarmItem
import com.mirkwood.rutinify.domain.model.AlarmScheduleUpdate
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.model.RoutineOrder
import com.mirkwood.rutinify.domain.repository.AlarmRepository
import com.mirkwood.rutinify.domain.repository.CategorySettingsRepository
import com.mirkwood.rutinify.domain.repository.OrderRepository
import com.mirkwood.rutinify.domain.scheduler.AlarmScheduler
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import com.mirkwood.rutinify.domain.time.ZoneProvider
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Outcome of moving a plan's pending alarms, for the preview and for the change itself (R-18.2–R-18.6). */
sealed interface ShiftResult {

    /** [count] alarms move; the next one goes from [nextFrom] to [nextTo] (null when nothing is pending). */
    data class Ok(val count: Int, val nextFrom: LocalTime?, val nextTo: LocalTime?) : ShiftResult

    /** R-18.5: moving earlier would leave [count] alarms at or before now. */
    data class InPast(val count: Int) : ShiftResult

    /** R-18.6: some alarm would fall before Day 1 or after Day 31. */
    data object OutOfRange : ShiftResult

    /** The plan does not exist or is not ACTIVE or PAUSED (R-18.1). */
    data object NotAllowed : ShiftResult
}

/**
 * "Voy tarde" (R-18, D-73): moves every PENDING alarm of a plan the same number of minutes, as wall-clock time (time
 * and day), plus its snooze if any. All or nothing; scheduled again only while the plan is ACTIVE (R-18.7). Conflicts
 * are not checked again (R-18.8).
 */
class ShiftOrderAlarmsUseCase @Inject constructor(
    private val orders: OrderRepository,
    private val alarms: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val categories: CategorySettingsRepository,
    private val calculator: TriggerTimeCalculator,
    private val zoneProvider: ZoneProvider,
    private val clock: Clock,
    private val lock: AlarmMutationLock,
) {

    /** What [invoke] would do with [minutes] (negative = earlier), without changing anything (R-18.2). */
    suspend fun preview(orderId: Long, minutes: Int): ShiftResult = lock.withLock { plan(orderId, minutes).result }

    suspend operator fun invoke(orderId: Long, minutes: Int): ShiftResult = lock.withLock {
        val plan = plan(orderId, minutes)
        if (plan.result !is ShiftResult.Ok || plan.updates.isEmpty()) return@withLock plan.result
        // Room first, then AlarmManager (CLAUDE.md §10)
        alarms.updateSchedules(plan.updates)
        if (plan.order?.status == OrderStatus.ACTIVE) {
            val critical = categories.criticalNames()
            val zone = zoneProvider.zone()
            plan.moved.forEach { alarm ->
                scheduler.schedule(alarm.id, calculator.effectiveTrigger(alarm, plan.order.date, zone), alarm.criticalIn(critical))
            }
        }
        plan.result
    }

    private class Plan(
        val result: ShiftResult,
        val order: RoutineOrder? = null,
        val updates: List<AlarmScheduleUpdate> = emptyList(),
        val moved: List<AlarmItem> = emptyList(),
    )

    private suspend fun plan(orderId: Long, minutes: Int): Plan {
        val order = orders.getOrder(orderId)
        if (order == null || (order.status != OrderStatus.ACTIVE && order.status != OrderStatus.PAUSED)) {
            return Plan(ShiftResult.NotAllowed)
        }
        val zone = zoneProvider.zone()
        val pending = alarms.getPendingByOrder(orderId)
        val moved = pending.map { alarm ->
            val wall = order.date.plusDays(alarm.dayOffset.toLong()).atTime(alarm.time).plusMinutes(minutes.toLong())
            alarm.copy(
                time = wall.toLocalTime(),
                dayOffset = ChronoUnit.DAYS.between(order.date, wall.toLocalDate()).toInt(),
                snoozedUntil = alarm.snoozedUntil?.plus(Duration.ofMinutes(minutes.toLong())),
            )
        }
        if (moved.any { it.dayOffset !in DAY_OFFSETS }) return Plan(ShiftResult.OutOfRange)
        val now: Instant = clock.instant()
        if (minutes < 0) {
            val past = moved.count { !calculator.effectiveTrigger(it, order.date, zone).isAfter(now) }
            if (past > 0) return Plan(ShiftResult.InPast(past))
        }
        val next = pending.zip(moved).minByOrNull { (before, _) -> calculator.effectiveTrigger(before, order.date, zone) }
        val result = ShiftResult.Ok(moved.size, next?.first?.shownTime(zone), next?.second?.shownTime(zone))
        return Plan(result, order, moved.map { AlarmScheduleUpdate(it.id, it.time, it.dayOffset, it.snoozedUntil) }, moved)
    }

    /** The time the user sees for [this] alarm: its snooze if it has one, else its wall-clock time. */
    private fun AlarmItem.shownTime(zone: ZoneId): LocalTime =
        snoozedUntil?.atZone(zone)?.toLocalTime() ?: time

    private companion object {
        /** Day 1 to Day 31 of the plan (R-18.6). */
        val DAY_OFFSETS = 0..30
    }
}
