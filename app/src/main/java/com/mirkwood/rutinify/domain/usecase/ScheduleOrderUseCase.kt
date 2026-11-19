package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.model.NewActivity
import com.mirkwood.rutinify.domain.diagnostics.DiagnosticsRecorder
import com.mirkwood.rutinify.domain.diagnostics.DiagnosticEvent
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.NewAlarm
import com.mirkwood.rutinify.domain.model.NewOrder
import com.mirkwood.rutinify.domain.model.RoutineDraft
import com.mirkwood.rutinify.domain.model.Recurrence
import com.mirkwood.rutinify.domain.model.conflictDaysOn
import com.mirkwood.rutinify.domain.repository.AlarmRepository
import com.mirkwood.rutinify.domain.repository.CategorySettingsRepository
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
    private val categories: CategorySettingsRepository,
    private val publisher: NotificationPublisher,
    private val buildPreview: BuildSchedulePreviewUseCase,
    private val calculator: TriggerTimeCalculator,
    private val zoneProvider: ZoneProvider,
    private val clock: Clock,
    private val lock: AlarmMutationLock,
    private val diagnostics: DiagnosticsRecorder,
    private val materializer: RecurringMaterializer,
) {

    suspend operator fun invoke(
        draft: RoutineDraft,
        date: LocalDate,
        excludedKeys: Set<Int>,
        policy: ConflictPolicy,
    ): ScheduleOrderResult = lock.withLock {
        val zone = zoneProvider.zone()
        draft.recurrence?.let { return@withLock scheduleRecurring(draft, it, date, excludedKeys, policy) }
        // Recomputed now, not taken from the screen: time may have passed since the preview (R-4.7)
        val preview = buildPreview(draft, date, excludedKeys, clock.instant(), zone)
        val requested = preview.items.count { it.isIncluded }
        if (requested == 0) return@withLock ScheduleOrderResult.NothingToSchedule

        val replaceIds = if (policy == ConflictPolicy.REPLACE) {
            orders.findOpenOverlapping(draft.conflictDaysOn(date)).map { it.order.id }
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
        val critical = categories.criticalNames()
        val modes = created.alarms
            .filter { it.status == AlarmStatus.PENDING }
            .map { scheduler.schedule(it.id, calculator.triggerAt(date, it.time, it.dayOffset, zone), it.criticalIn(critical)) }

        // R-17.2, R-17.3: no-op unless the user turned diagnostics on
        diagnostics.record(DiagnosticEvent.Step.SCHEDULED)
        diagnostics.record(DiagnosticEvent.PlanCreated)
        ScheduleOrderResult.Scheduled(
            orderId = created.order.id,
            scheduledCount = modes.size,
            windowed = ScheduleMode.WINDOWED in modes,
        )
    }

    /**
     * R-23.7: a recurring plan stores its day type (minus the excluded activities) and creates only the alarms of the
     * window; the daily refresh and every reschedule create the rest. Caller holds the lock.
     */
    private suspend fun scheduleRecurring(
        draft: RoutineDraft,
        recurrence: Recurrence,
        date: LocalDate,
        excludedKeys: Set<Int>,
        policy: ConflictPolicy,
    ): ScheduleOrderResult {
        val activities = draft.items.filter { it.key !in excludedKeys }.map {
            NewActivity(it.key, it.time, it.dayOffset, it.title, it.description, it.durationMin, it.category, it.assignee)
        }
        if (activities.isEmpty()) return ScheduleOrderResult.NothingToSchedule
        val replaceIds = if (policy == ConflictPolicy.REPLACE) {
            orders.findOpenOverlapping(draft.conflictDaysOn(date)).map { it.order.id }
        } else {
            emptyList()
        }
        // R-23.8: only the alarms of the window count, the same ones the materializer is about to create
        val zone = zoneProvider.zone()
        val now = clock.instant()
        val requested = recurrence.windowOffsets(date, LocalDate.now(clock.withZone(zone))).sumOf { offset ->
            activities.count { calculator.triggerAt(date, it.time, offset + it.dayOffset, zone).isAfter(now) }
        }
        val alreadyScheduled = alarms.getPendingForActiveOrders().count { it.alarm.orderId !in replaceIds }
        if (alreadyScheduled + requested > MAX_PENDING_ALARMS) {
            return ScheduleOrderResult.TooManyAlarms(requested, alreadyScheduled)
        }
        val created = orders.createOrder(
            NewOrder(name = draft.name, date = date, notes = draft.notes, rawJson = draft.rawJson, schemaVersion = 2, recurrence = recurrence),
            alarms = emptyList(),
            replaceOrderIds = replaceIds,
            activities = activities,
        ).getOrElse { return ScheduleOrderResult.StorageFailure }
        created.cancelledAlarmIds.forEach(scheduler::cancel)
        replaceIds.forEach(publisher::cancelForOrder)
        publisher.ensureChannels(activities.map { it.category })
        val count = materializer.materialize(created.order.id)
        diagnostics.record(DiagnosticEvent.Step.SCHEDULED)
        diagnostics.record(DiagnosticEvent.PlanCreated)
        return ScheduleOrderResult.Scheduled(orderId = created.order.id, scheduledCount = count, windowed = !scheduler.canScheduleExact())
    }

    private companion object {
        /** Android's limit of pending alarms per app (R-5.10). */
        const val MAX_PENDING_ALARMS = 500
    }
}
