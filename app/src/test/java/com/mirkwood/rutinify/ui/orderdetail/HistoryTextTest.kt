package com.mirkwood.rutinify.ui.orderdetail

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.RoutineSpan
import com.mirkwood.rutinify.domain.usecase.HistoryDay
import com.mirkwood.rutinify.domain.usecase.HistoryEntry
import com.mirkwood.rutinify.domain.usecase.HistoryReport
import com.mirkwood.rutinify.domain.usecase.HistorySummary
import com.mirkwood.rutinify.testutil.en
import com.mirkwood.rutinify.testutil.es
import com.mirkwood.rutinify.testutil.testOrder
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** T-62: the exported history text (R-20.4). */
class HistoryTextTest {

    private val date = LocalDate.of(2026, 11, 14)
    private val report = HistoryReport(
        order = testOrder(id = 1, name = "Antibiótico", date = date),
        span = RoutineSpan.of(date, 2),
        summary = HistorySummary(done = 1, total = 2, skipped = 0, snoozes = 2),
        days = listOf(
            HistoryDay(0, date, listOf(HistoryEntry(LocalTime.of(6, 0), "Amoxicilina", AlarmStatus.DONE, LocalTime.of(6, 12), 2))),
            HistoryDay(2, date.plusDays(2), listOf(HistoryEntry(LocalTime.of(6, 0), "Amoxicilina", AlarmStatus.PENDING, null, 0))),
        ),
    )

    @Test
    fun `por dia con hora de hecha y posposiciones`() { // R-20.4
        assertThat(HistoryText.lines(report).map { it.es() }).containsExactly(
            "del sábado 14 al lunes 16 de noviembre de 2026",
            "1 de 2 hechas",
            "0 omitidas · 2 pospuestas",
            "",
            "Día 1 · sábado 14 de noviembre",
            "06:00 · Amoxicilina · Hecha · a las 06:12 · pospuesta 2 veces",
            "",
            "Día 3 · lunes 16 de noviembre",
            "06:00 · Amoxicilina · Pendiente",
        ).inOrder()
    }

    @Test
    fun `tambien en ingles`() { // R-13.3
        assertThat(HistoryText.lines(report).map { it.en() }[5]).isEqualTo("06:00 · Amoxicilina · Done · at 06:12 · snoozed 2 times")
    }
}
