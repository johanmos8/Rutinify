package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.model.AlarmEvent
import com.mirkwood.rutinify.domain.model.AlarmEventType
import com.mirkwood.rutinify.domain.model.AlarmItem
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderWithAlarms
import com.mirkwood.rutinify.domain.model.RoutineOrder
import com.mirkwood.rutinify.domain.model.RoutineSpan
import com.mirkwood.rutinify.domain.repository.AlarmRepository
import com.mirkwood.rutinify.domain.repository.OrderRepository
import com.mirkwood.rutinify.domain.time.ZoneProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** "{done} de {total} hechas" and, when any, "{skipped} omitidas · {snoozes} pospuestas" (R-20.2). */
data class HistorySummary(val done: Int, val total: Int, val skipped: Int, val snoozes: Int)

/** One alarm in the history: its time, title, status, when it was marked done and how often it was snoozed (R-20.4). */
data class HistoryEntry(
    val time: LocalTime,
    val title: String,
    val status: AlarmStatus,
    val doneAt: LocalTime?,
    val snoozes: Int,
)

data class HistoryDay(val dayOffset: Int, val date: LocalDate, val entries: List<HistoryEntry>)

data class HistoryReport(val order: RoutineOrder, val span: RoutineSpan, val summary: HistorySummary, val days: List<HistoryDay>)

/** The plan history for the detail summary and the export (R-20.2–R-20.4, D-77). */
class BuildHistoryReportUseCase @Inject constructor(
    private val orders: OrderRepository,
    private val alarms: AlarmRepository,
    private val zoneProvider: ZoneProvider,
) {

    /** Follows the plan, so marking, snoozing or skipping updates the summary (R-20.2). */
    @OptIn(ExperimentalCoroutinesApi::class) // mapLatest
    fun observeSummary(orderId: Long): Flow<HistorySummary?> = orders.observeOrderWithAlarms(orderId).mapLatest { source ->
        source?.let { summaryOf(it.alarms, alarms.getEventsByOrder(orderId)) }
    }

    suspend operator fun invoke(orderId: Long): HistoryReport? {
        val order = orders.getOrder(orderId) ?: return null
        val items = alarms.getByOrder(orderId)
        val events = alarms.getEventsByOrder(orderId)
        return report(OrderWithAlarms(order, items), events)
    }

    private fun report(source: OrderWithAlarms, events: List<AlarmEvent>): HistoryReport {
        val zone = zoneProvider.zone()
        val order = source.order
        val snoozes = events.filter { it.type == AlarmEventType.SNOOZED }.groupingBy { it.alarmId }.eachCount()
        val span = RoutineSpan.of(order.date, source.alarms.maxOfOrNull { it.dayOffset } ?: 0)
        // A single-day plan keeps its early hours under its own date (D-58)
        val days = source.alarms
            .sortedWith(compareBy<AlarmItem>({ it.dayOffset }, { it.time }, { it.position }))
            .groupBy { if (span.isMultiDay) it.dayOffset else 0 }
            .map { (offset, dayAlarms) ->
                HistoryDay(
                    dayOffset = offset,
                    date = order.date.plusDays(offset.toLong()),
                    entries = dayAlarms.map { alarm ->
                        HistoryEntry(
                            time = alarm.time,
                            title = alarm.title,
                            status = alarm.status,
                            doneAt = alarm.doneAt?.takeIf { alarm.status == AlarmStatus.DONE }?.atZone(zone)?.toLocalTime(),
                            snoozes = snoozes[alarm.id] ?: 0,
                        )
                    },
                )
            }
        return HistoryReport(order, span, summaryOf(source.alarms, events), days)
    }

    /** Cancelled alarms do not count towards the total (R-20.2). */
    private fun summaryOf(items: List<AlarmItem>, events: List<AlarmEvent>): HistorySummary {
        val counted = items.filter { it.status != AlarmStatus.CANCELLED }
        val ids = items.map { it.id }.toSet()
        return HistorySummary(
            done = counted.count { it.status == AlarmStatus.DONE },
            total = counted.size,
            skipped = counted.count { it.status == AlarmStatus.SKIPPED },
            snoozes = events.count { it.type == AlarmEventType.SNOOZED && it.alarmId in ids },
        )
    }
}
