package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.importer.DraftItemRules
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
import javax.inject.Inject

/** Outcome of editing an alarm from the plan detail (R-18.9, R-18.10). */
sealed interface UpdateAlarmResult {

    /** Saved; [titleTruncated] when the title was cut to 80 characters (R-3.10). */
    data class Updated(val titleTruncated: Boolean) : UpdateAlarmResult

    /** Fields that break the Review rules (R-4.2); nothing saved. */
    data class Invalid(
        val timeInvalid: Boolean = false,
        val titleBlank: Boolean = false,
        val categoryTooLong: Boolean = false,
        val tooManyCategories: Boolean = false,
    ) : UpdateAlarmResult

    /** R-18.10: the new time is not ahead of now. */
    data object PastTime : UpdateAlarmResult

    /** Not a PENDING alarm of an ACTIVE or PAUSED plan any more. */
    data object NotEditable : UpdateAlarmResult
}

/**
 * Edits time, title and category of a PENDING alarm with the Review rules (R-18.9, D-74): same day, a new category gets
 * its channel, and the alarm is scheduled again while the plan is ACTIVE.
 */
class UpdateAlarmUseCase @Inject constructor(
    private val alarms: AlarmRepository,
    private val orders: OrderRepository,
    private val scheduler: AlarmScheduler,
    private val publisher: NotificationPublisher,
    private val categories: CategorySettingsRepository,
    private val calculator: TriggerTimeCalculator,
    private val zoneProvider: ZoneProvider,
    private val clock: Clock,
    private val lock: AlarmMutationLock,
) {

    suspend operator fun invoke(alarmId: Long, timeText: String, titleText: String, categoryText: String): UpdateAlarmResult =
        lock.withLock {
            val alarm = alarms.getById(alarmId)
            val order = alarm?.let { orders.getOrder(it.orderId) }
            if (alarm == null || order == null || alarm.status != AlarmStatus.PENDING ||
                (order.status != OrderStatus.ACTIVE && order.status != OrderStatus.PAUSED)
            ) {
                return@withLock UpdateAlarmResult.NotEditable
            }
            val time = DraftItemRules.parseTime(timeText)
            val title = DraftItemRules.checkTitle(titleText)
            val category = DraftItemRules.normalizeCategory(categoryText)
            val others = alarms.getByOrder(order.id).filter { it.id != alarmId }.map { it.category }.toSet()
            val tooMany = category != null && (others + category).size > DraftItemRules.MAX_CATEGORIES
            if (time == null || title !is DraftItemRules.TitleCheck.Valid || category == null || tooMany) {
                return@withLock UpdateAlarmResult.Invalid(
                    timeInvalid = time == null,
                    titleBlank = title is DraftItemRules.TitleCheck.Blank,
                    categoryTooLong = category == null,
                    tooManyCategories = tooMany,
                )
            }
            val edited = alarm.copy(time = time, title = title.value, category = category, snoozedUntil = null)
            val zone = zoneProvider.zone()
            val triggerAt = calculator.effectiveTrigger(edited, order.date, zone)
            if (!triggerAt.isAfter(clock.instant())) return@withLock UpdateAlarmResult.PastTime

            // Room first, then the channel and AlarmManager (CLAUDE.md §10)
            alarms.updateDetails(alarmId, time, title.value, category)
            publisher.ensureChannels(listOf(category))
            if (order.status == OrderStatus.ACTIVE) {
                scheduler.schedule(alarmId, triggerAt, edited.criticalIn(categories.criticalNames()))
            }
            UpdateAlarmResult.Updated(title.truncated)
        }
}
