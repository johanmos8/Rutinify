package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.NewAlarm
import com.mirkwood.rutinify.domain.model.NewOrder
import com.mirkwood.rutinify.domain.model.RoutineDraft
import com.mirkwood.rutinify.domain.model.spanOn
import com.mirkwood.rutinify.domain.repository.AlarmRepository
import com.mirkwood.rutinify.domain.repository.OrderRepository
import com.mirkwood.rutinify.domain.scheduler.AlarmScheduler
import com.mirkwood.rutinify.domain.scheduler.NotificationPublisher
import com.mirkwood.rutinify.domain.scheduler.ScheduleMode
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import com.mirkwood.rutinify.domain.time.ZoneProvider
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Answer to the conflict dialog (R-6.1); "Cancelar" never reaches the use case (R-6.4). */
enum class ConflictPolicy {
    /** "Reemplazar la anterior": every order in conflict is cancelled (R-6.2, R-6.5). */
    REPLACE,

    /** "Mantener ambas" (R-6.3); also used when there is no conflict. */
    KEEP_BOTH,
}

sealed interface ScheduleOrderResult {
    /** "{n} alarmas programadas" (R-5.6); [windowed] → the R-5.4 notice. */
    data class Scheduled(val orderId: Long, val scheduledCount: Int, val windowed: Boolean) : ScheduleOrderResult

    /** R-5.10: [requested] new alarms on top of [alreadyScheduled] would exceed the limit; nothing written. */
    data class TooManyAlarms(val requested: Int, val alreadyScheduled: Int) : ScheduleOrderResult

    /** Every activity is past or excluded (R-4.10); nothing written. */
    data object NothingToSchedule : ScheduleOrderResult

    /** Room failed: nothing was scheduled (R-5.7). */
    data object StorageFailure : ScheduleOrderResult
}

/** "Programar alarmas" on the Review screen (design §6.4). */
class ScheduleOrderUseCase @Inject constructor(
    private val orders: OrderRepository,
    private val alarms: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val publisher: NotificationPublisher,
    private val buildPreview: BuildSchedulePreviewUseCase,
    private val calculator: TriggerTimeCalculator,
    private val zoneProvider: ZoneProvider,
    private val clock: Clock,
    private val lock: AlarmMutationLock,
) {

    suspend operator fun invoke(
        draft: RoutineDraft,
        date: LocalDate,
        excludedKeys: Set<Int>,
        policy: ConflictPolicy,
    ): ScheduleOrderResult = lock.withLock {
        val zone = zoneProvider.zone()
        // Recomputed now, not taken from the screen: time may have passed since the preview (R-4.7)
        val preview = buildPreview(draft, date, excludedKeys, clock.instant(), zone)
        val requested = preview.items.count { it.isIncluded }
        if (requested == 0) return@withLock ScheduleOrderResult.NothingToSchedule

        val replaceIds = if (policy == ConflictPolicy.REPLACE) {
            orders.findOpenOverlapping(draft.spanOn(date).forConflicts()).map { it.order.id }
        } else {
            emptyList()
        }
        // Only ACTIVE orders have alarms in AlarmManager; the ones being replaced will be cancelled (R-5.10)
        val alreadyScheduled = alarms.getPendingForActiveOrders().count { it.alarm.orderId !in replaceIds }
        if (alreadyScheduled + requested > MAX_PENDING_ALARMS) {
            return@withLock ScheduleOrderResult.TooManyAlarms(requested, alreadyScheduled)
        }

        val newAlarms = preview.items.map { row ->
            val item = row.item
            NewAlarm(
                position = item.key,
                time = item.time,
                dayOffset = item.dayOffset,
                title = item.title,
                description = item.description,
                durationMin = item.durationMin,
                category = item.category,
                assignee = item.assignee,
                status = if (row.isIncluded) AlarmStatus.PENDING else AlarmStatus.SKIPPED, // R-5.5
            )
        }
        val created = orders.createOrder(
            NewOrder(name = draft.name, date = date, notes = draft.notes, rawJson = draft.rawJson),
            newAlarms,
            replaceIds,
        ).getOrElse { return@withLock ScheduleOrderResult.StorageFailure } // R-5.7: nothing scheduled

        // Room is written; now mirror it in the system (CLAUDE.md §10)
        created.cancelledAlarmIds.forEach(scheduler::cancel)
        replaceIds.forEach(publisher::cancelForOrder)
        publisher.ensureChannels(created.alarms.map { it.category }) // R-5.9
        val modes = created.alarms
            .filter { it.status == AlarmStatus.PENDING }
            .map { scheduler.schedule(it.id, calculator.triggerAt(date, it.time, it.dayOffset, zone)) }

        ScheduleOrderResult.Scheduled(
            orderId = created.order.id,
            scheduledCount = modes.size,
            windowed = ScheduleMode.WINDOWED in modes,
        )
    }

    private companion object {
        /** Android's limit of pending alarms per app (R-5.10). */
        const val MAX_PENDING_ALARMS = 500
    }
}
