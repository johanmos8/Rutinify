package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.model.DraftItem
import com.mirkwood.rutinify.domain.model.RoutineDraft
import com.mirkwood.rutinify.domain.model.spanOn
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/** One Review row: the draft item for the chosen date (R-4.6–R-4.8). */
data class PreviewItem(
    val item: DraftItem,
    val triggerAt: Instant,
    /** Trigger at or before now: shown as "Ya pasó" and never scheduled (R-4.7). */
    val isPast: Boolean,
    /** Future and not excluded by the user (R-4.8). */
    val isIncluded: Boolean,
)

/** Activities of one day of a multi-day routine: "Día {dayOffset + 1} · {date}" (R-4.13). */
data class PreviewDay(val dayOffset: Int, val date: LocalDate, val items: List<PreviewItem>)

/**
 * "28 alarmas, de 06:45 a 02:00 (+1 día)" (R-4.9): first and last included activity by trigger time,
 * with their day offset relative to the chosen date so the UI can add "(+1 día)". A multi-day routine
 * shows "21 alarmas en 7 días, del … al …" instead (R-4.14).
 */
data class ScheduleSummary(
    val count: Int,
    val first: LocalTime,
    val firstDayOffset: Int,
    val last: LocalTime,
    val lastDayOffset: Int,
    /** Days with at least one included activity (R-4.14). */
    val dayCount: Int,
)

data class SchedulePreview(
    /** Same order as the draft: `dayOffset`, then `time` (R-4.1). */
    val items: List<PreviewItem>,
    /** [items] by `dayOffset`; only days with activities (R-4.13). */
    val days: List<PreviewDay>,
    /** Largest `dayOffset` of 2 or more: the UI groups by day (R-4.13, R-4.14). */
    val isMultiDay: Boolean,
    /** null when nothing is included. */
    val summary: ScheduleSummary?,
    /** false → "Programar alarmas" disabled with "No hay actividades futuras para programar" (R-4.10). */
    val canSchedule: Boolean,
    /** The chosen date is before today and every activity already passed (R-4.11). */
    val suggestTomorrow: Boolean,
)

/** Recomputed on every change of date, exclusions or time on the Review screen (R-4.6). */
class BuildSchedulePreviewUseCase @Inject constructor(
    private val calculator: TriggerTimeCalculator,
) {

    operator fun invoke(
        draft: RoutineDraft,
        date: LocalDate,
        excludedKeys: Set<Int>,
        now: Instant,
        zone: ZoneId,
    ): SchedulePreview {
        val items = draft.items.map { item ->
            val triggerAt = calculator.triggerAt(date, item.time, item.dayOffset, zone)
            val isPast = !triggerAt.isAfter(now)
            PreviewItem(item, triggerAt, isPast, isIncluded = !isPast && item.key !in excludedKeys)
        }
        val included = items.filter { it.isIncluded }
        val summary = summaryOf(included)
        val today = now.atZone(zone).toLocalDate()
        return SchedulePreview(
            items = items,
            days = items.groupBy { it.item.dayOffset }.toSortedMap().map { (offset, rows) ->
                PreviewDay(offset, date.plusDays(offset.toLong()), rows)
            },
            isMultiDay = draft.spanOn(date).isMultiDay,
            summary = summary,
            canSchedule = summary != null,
            suggestTomorrow = date.isBefore(today) && items.all { it.isPast },
        )
    }

    private fun summaryOf(included: List<PreviewItem>): ScheduleSummary? {
        val first = included.minByOrNull { it.triggerAt } ?: return null
        val last = included.maxBy { it.triggerAt }
        return ScheduleSummary(
            count = included.size,
            first = first.item.time,
            firstDayOffset = first.item.dayOffset,
            last = last.item.time,
            lastDayOffset = last.item.dayOffset,
            dayCount = included.map { it.item.dayOffset }.distinct().size,
        )
    }
}
