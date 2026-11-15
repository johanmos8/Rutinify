package com.mirkwood.rutinify.domain.time

import com.mirkwood.rutinify.domain.model.RoutineDraft
import com.mirkwood.rutinify.domain.model.spanOn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Date preselected on the Review screen; [chosenByApp] drives "No venía fecha; elegí {fecha}" (R-4.5). */
data class DefaultDate(val date: LocalDate, val chosenByApp: Boolean)

/**
 * R-4.4, R-4.5: the JSON date wins; otherwise today if any activity is still ahead (for a multi-day routine, any with
 * `dayOffset` 0, D-61), else tomorrow.
 */
class DefaultDatePolicy @Inject constructor(
    private val calculator: TriggerTimeCalculator,
) {

    fun defaultDate(draft: RoutineDraft, now: Instant, zone: ZoneId): DefaultDate {
        draft.date?.let { return DefaultDate(it, chosenByApp = false) }
        // atZone instead of LocalDate.ofInstant, which needs API 31 (minSdk is 28)
        val today = now.atZone(zone).toLocalDate()
        // "Already passed" means <= now (R-4.7), so only strictly later triggers count as future
        // A multi-day routine only starts today if something of its Day 1 is still ahead (D-61). Offset 1 is Day 2
        // there, indistinguishable from Day 1's early hours once `repeatDays` is expanded, so it does not count
        val firstDay = if (draft.spanOn(today).isMultiDay) {
            draft.items.filter { it.dayOffset == 0 }
        } else {
            draft.items
        }
        val anyFutureToday = firstDay.any { calculator.triggerAt(today, it.time, it.dayOffset, zone).isAfter(now) }
        return DefaultDate(if (anyFutureToday) today else today.plusDays(1), chosenByApp = true)
    }
}
