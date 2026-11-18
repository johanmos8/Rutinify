package com.mirkwood.rutinify.ui.orderdetail

import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.usecase.HistoryReport
import com.mirkwood.rutinify.domain.usecase.HistorySummary
import com.mirkwood.rutinify.ui.common.DateFormats
import com.mirkwood.rutinify.ui.common.SpanLabels
import com.mirkwood.rutinify.ui.common.UiText

/** The history as lines of text, shared by "Texto" and "PDF" (R-20.4, R-20.5), and the detail summary (R-20.2). */
internal object HistoryText {

    /** "19 de 21 hechas". */
    fun done(summary: HistorySummary): UiText =
        UiText.Plural(R.plurals.history_done, summary.total, listOf(summary.done, summary.total))

    /** "2 omitidas · 3 pospuestas", or null when there are none (R-20.2). */
    fun others(summary: HistorySummary): UiText? {
        if (summary.skipped == 0 && summary.snoozes == 0) return null
        return UiText.Res(
            R.string.history_others,
            listOf(
                UiText.Plural(R.plurals.history_skipped, summary.skipped, listOf(summary.skipped)),
                UiText.Plural(R.plurals.history_snoozes, summary.snoozes, listOf(summary.snoozes)),
            ),
        )
    }

    fun lines(report: HistoryReport): List<UiText> = buildList {
        add(SpanLabels.long(report.span))
        add(done(report.summary))
        others(report.summary)?.let(::add)
        report.days.forEach { day ->
            add(UiText.Plain(""))
            add(if (report.span.isMultiDay) SpanLabels.dayHeader(day.dayOffset, day.date) else SpanLabels.fullDate(day.date))
            day.entries.forEach { entry ->
                add(
                    UiText.Res(
                        R.string.history_line,
                        listOf(
                            entry.time.format(DateFormats.TIME),
                            entry.title,
                            UiText.Res(statusRes(entry.status)),
                            entry.doneAt?.let { UiText.Res(R.string.history_done_at, listOf(it.format(DateFormats.TIME))) } ?: UiText.Plain(""),
                            if (entry.snoozes > 0) UiText.Plural(R.plurals.history_line_snoozes, entry.snoozes, listOf(entry.snoozes)) else UiText.Plain(""),
                        ),
                    ),
                )
            }
        }
    }

    private fun statusRes(status: AlarmStatus): Int = when (status) {
        AlarmStatus.PENDING -> R.string.alarm_status_pending
        AlarmStatus.FIRED -> R.string.alarm_status_fired
        AlarmStatus.DONE -> R.string.alarm_status_done
        AlarmStatus.SKIPPED -> R.string.alarm_status_skipped
        AlarmStatus.CANCELLED -> R.string.alarm_status_cancelled
    }
}
