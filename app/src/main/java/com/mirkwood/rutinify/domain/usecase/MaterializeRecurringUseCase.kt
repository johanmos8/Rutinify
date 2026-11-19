package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.NewAlarm
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.repository.AlarmRepository
import com.mirkwood.rutinify.domain.repository.CategorySettingsRepository
import com.mirkwood.rutinify.domain.repository.OrderRepository
import com.mirkwood.rutinify.domain.scheduler.AlarmScheduler
import com.mirkwood.rutinify.domain.scheduler.NotificationPublisher
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import com.mirkwood.rutinify.domain.time.ZoneProvider
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Creates the alarms of recurring plans that enter the window; the caller must hold [AlarmMutationLock]. */
fun interface RecurringMaterializer {
    /** For one plan ([orderId]) or for every ACTIVE recurring plan (null). @return how many alarms were created. */
    suspend fun materialize(orderId: Long?): Int
}

/**
 * The programming window of recurring plans (R-23.7, R-23.12, D-81): for the next [WINDOW_DAYS] days from today, each
 * day of the week the plan repeats gets one alarm per day-type activity, scheduled at once. Idempotent: an activity and
 * day already created is never created again, so a reboot or a zone change does not duplicate anything. Paused plans
 * and days already past are not created, so the days of a pause never ring.
 */
class MaterializeRecurringUseCase @Inject constructor(
    private val orders: OrderRepository,
    private val alarms: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val publisher: NotificationPublisher,
    private val categories: CategorySettingsRepository,
    private val calculator: TriggerTimeCalculator,
    private val zoneProvider: ZoneProvider,
    private val clock: Clock,
) : RecurringMaterializer {

    override suspend fun materialize(orderId: Long?): Int {
        val plans = orders.getActiveRecurring().filter { orderId == null || it.id == orderId }
        if (plans.isEmpty()) return 0
        val zone = zoneProvider.zone()
        val now = clock.instant()
        val today = LocalDate.now(clock.withZone(zone))
        val critical = categories.criticalNames()
        var created = 0
        for (order in plans) {
            val recurrence = order.recurrence ?: continue
            if (order.status != OrderStatus.ACTIVE) continue
            val activities = orders.getActivities(order.id)
            val candidates = (0 until WINDOW_DAYS).map { today.plusDays(it.toLong()) }
                .filter { recurrence.includes(it, order.date) }
                .flatMap { day ->
                    val offset = ChronoUnit.DAYS.between(order.date, day).toInt()
                    activities.map { activity ->
                        NewAlarm(
                            position = activity.position,
                            time = activity.time,
                            dayOffset = offset + activity.dayOffset,
                            title = activity.title,
                            description = activity.description,
                            durationMin = activity.durationMin,
                            category = activity.category,
                            assignee = activity.assignee,
                            status = AlarmStatus.PENDING,
                            activityId = activity.id,
                        )
                    }
                }
                // Only what is still ahead: a past occurrence is simply never created
                .filter { calculator.triggerAt(order.date, it.time, it.dayOffset, zone).isAfter(now) }
            val inserted = alarms.insertOccurrences(order.id, candidates)
            if (inserted.isEmpty()) continue
            // Room first, then the channels and AlarmManager (CLAUDE.md §10)
            publisher.ensureChannels(inserted.map { it.category })
            inserted.forEach { alarm ->
                scheduler.schedule(alarm.id, calculator.effectiveTrigger(alarm, order.date, zone), alarm.criticalIn(critical))
            }
            created += inserted.size
        }
        return created
    }

    companion object {
        /** R-23.7: the programming window. */
        const val WINDOW_DAYS = 7
    }
}
