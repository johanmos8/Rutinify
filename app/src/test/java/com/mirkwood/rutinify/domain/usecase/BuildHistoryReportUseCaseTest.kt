package com.mirkwood.rutinify.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderWithAlarms
import com.mirkwood.rutinify.testutil.FakeAlarmRepository
import com.mirkwood.rutinify.testutil.FakeOrderRepository
import com.mirkwood.rutinify.testutil.testAlarm
import com.mirkwood.rutinify.testutil.testOrder
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** T-62: history summary and report (R-20.2, R-20.4, D-77). */
class BuildHistoryReportUseCaseTest {

    private val bogota = ZoneId.of("America/Bogota")
    private val date = LocalDate.of(2026, 11, 14)
    private val clock = Clock.fixed(Instant.parse("2026-11-20T17:00:00Z"), bogota)
    private val orders = FakeOrderRepository()
    private val alarms = FakeAlarmRepository(clock)
    private val report = BuildHistoryReportUseCase(orders, alarms) { bogota }

    /** 21 alarms over 7 days at 06:00, 14:00 and 22:00. */
    private fun treatment() = (0..6).flatMap { day ->
        listOf(6, 14, 22).mapIndexed { i, hour -> testAlarm(id = day * 10L + i, time = LocalTime.of(hour, 0), dayOffset = day, title = "Amoxicilina") }
    }

    @Test
    fun `19 de 21 hechas con omitidas canceladas y pospuestas`() = runTest { // R-20.2
        orders.add(testOrder(id = 1, date = date))
        val all = treatment()
        alarms.add(*all.toTypedArray())
        // 19 done (two of them snoozed first), 2 skipped
        all.take(19).forEach { if (it.id == 0L || it.id == 1L) alarms.snooze(it.id, clock.instant()) }
        alarms.updateStatus(all.take(19).map { it.id }, AlarmStatus.DONE)
        alarms.updateStatus(all.drop(19).map { it.id }, AlarmStatus.SKIPPED)
        // A cancelled extra alarm never counts
        alarms.add(testAlarm(id = 99, time = LocalTime.of(23, 0), dayOffset = 6, status = AlarmStatus.CANCELLED))

        val summary = requireNotNull(report(1)).summary

        assertThat(summary).isEqualTo(HistorySummary(done = 19, total = 21, skipped = 2, snoozes = 2))
    }

    @Test
    fun `el informe va por dia con hora titulo estado hora de hecha y posposiciones`() = runTest { // R-20.4
        orders.add(testOrder(id = 1, name = "Antibiótico", date = date))
        alarms.add(
            testAlarm(id = 10, time = LocalTime.of(14, 0), title = "Toma"),
            testAlarm(id = 11, time = LocalTime.of(6, 0), title = "Toma de la mañana"),
            testAlarm(id = 12, time = LocalTime.of(6, 0), dayOffset = 2, title = "Toma"),
        )
        alarms.snooze(11, clock.instant())
        alarms.updateStatus(listOf(11), AlarmStatus.DONE)

        val result = requireNotNull(report(1))

        assertThat(result.order.name).isEqualTo("Antibiótico")
        assertThat(result.span.isMultiDay).isTrue()
        assertThat(result.days.map { it.date }).containsExactly(date, date.plusDays(2)).inOrder()
        assertThat(result.days.first().entries).containsExactly(
            HistoryEntry(LocalTime.of(6, 0), "Toma de la mañana", AlarmStatus.DONE, doneAt = LocalTime.of(12, 0), snoozes = 1),
            HistoryEntry(LocalTime.of(14, 0), "Toma", AlarmStatus.PENDING, doneAt = null, snoozes = 0),
        ).inOrder()
    }

    @Test
    fun `sin plan no hay informe`() = runTest {
        assertThat(report(99)).isNull()
    }

    @Test
    fun `el resumen sigue los cambios del plan`() = runTest { // R-20.2
        val order = testOrder(id = 1, date = date)
        val alarm = testAlarm(id = 10, time = LocalTime.of(14, 0))
        orders.add(order)
        alarms.add(alarm)
        orders.detail(1).value = OrderWithAlarms(order, listOf(alarm))

        report.observeSummary(1).test {
            assertThat(awaitItem()).isEqualTo(HistorySummary(done = 0, total = 1, skipped = 0, snoozes = 0))

            alarms.updateStatus(listOf(10), AlarmStatus.DONE)
            orders.detail(1).value = OrderWithAlarms(order, listOf(requireNotNull(alarms.getById(10))))

            assertThat(awaitItem()).isEqualTo(HistorySummary(done = 1, total = 1, skipped = 0, snoozes = 0))
        }
    }

    @Test
    fun `un plan de un dia con madrugada queda bajo su fecha`() = runTest { // D-58
        orders.add(testOrder(id = 1, date = date))
        alarms.add(testAlarm(id = 10, time = LocalTime.of(22, 0)), testAlarm(id = 11, time = LocalTime.of(2, 0), dayOffset = 1))

        val result = requireNotNull(report(1))

        assertThat(result.span.isMultiDay).isFalse()
        assertThat(result.days.single().entries.map { it.time }).containsExactly(LocalTime.of(22, 0), LocalTime.of(2, 0)).inOrder()
    }
}
