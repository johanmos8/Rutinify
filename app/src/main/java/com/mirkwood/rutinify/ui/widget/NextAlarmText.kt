package com.mirkwood.rutinify.ui.widget

import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.domain.model.NextAlarm
import com.mirkwood.rutinify.ui.common.DateFormats
import com.mirkwood.rutinify.ui.common.UiText
import java.time.LocalDate
import java.time.ZoneId

/** The widget's time line (R-21.1): "14:00" today, "02:00 · mañana", or "06:00 · sábado 14 de noviembre". */
internal object NextAlarmText {

    fun time(next: NextAlarm, today: LocalDate, zone: ZoneId): UiText {
        val local = next.triggerAt.atZone(zone)
        val time = local.format(DateFormats.TIME)
        return when (local.toLocalDate()) {
            today -> UiText.Plain(time)
            today.plusDays(1) -> UiText.Res(R.string.widget_time_day, listOf(time, UiText.Res(R.string.widget_tomorrow)))
            else -> UiText.Res(R.string.widget_time_day, listOf(time, UiText.Date(local.toLocalDate(), R.string.date_pattern_weekday)))
        }
    }
}
