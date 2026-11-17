package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.scheduler.CriticalAlarm
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import com.mirkwood.rutinify.testutil.FakeAlarmRepository
import com.mirkwood.rutinify.testutil.FakeAlarmScheduler
import com.mirkwood.rutinify.testutil.FakeCategorySettingsRepository
import com.mirkwood.rutinify.testutil.testAlarm
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** T-43: marking a category critical reschedules its pending alarms (R-15.1, R-15.2). */
class SetCategoryCriticalUseCaseTest {

    private val bogota = ZoneId.of("America/Bogota")
    private val date = LocalDate.of(2026, 11, 16)
    private val clock = Clock.fixed(Instant.parse("2026-11-16T17:00:00Z"), bogota) // 12:00 in Bogotá

    private val categories = FakeCategorySettingsRepository()
    private val scheduler = FakeAlarmScheduler()

    /** Order 1 is active on [date]; order 2 is not active (paused or cancelled). */
    private val alarms = FakeAlarmRepository(clock) { orderId -> if (orderId == 1L) date else null }

    private val setCritical = SetCategoryCriticalUseCase(
        categories, alarms, scheduler, TriggerTimeCalculator(), { bogota }, clock, AlarmMutationLock(),
    )

    @Test
    fun `guarda la marca`() = runTest { // R-15.1
        setCritical("medicación", critical = true)

        assertThat(categories.criticalNames()).containsExactly("medicación")

        setCritical("medicación", critical = false)

        assertThat(categories.criticalNames()).isEmpty()
    }

    @Test
    fun `reprograma solo las pendientes futuras de esa categoria en planes activos`() = runTest { // R-15.2
        alarms.add(
            testAlarm(id = 10, time = LocalTime.of(14, 0), category = "medicación"),
            testAlarm(id = 11, time = LocalTime.of(6, 0), dayOffset = 1, category = "medicación"),
            testAlarm(id = 12, time = LocalTime.of(8, 0), category = "medicación"), // already past
            testAlarm(id = 13, time = LocalTime.of(14, 0), category = "paseo"),
            testAlarm(id = 14, orderId = 2, time = LocalTime.of(14, 0), category = "medicación"),
        )

        val rescheduled = setCritical("medicación", critical = true)

        assertThat(rescheduled).isEqualTo(2)
        assertThat(scheduler.scheduled).containsExactly(
            10L, Instant.parse("2026-11-16T19:00:00Z"),
            11L, Instant.parse("2026-11-17T11:00:00Z"),
        )
        assertThat(scheduler.cancelled).isEmpty()
    }

    @Test
    fun `una alarma pospuesta se reprograma a su hora pospuesta`() = runTest { // R-15.2, R-12.7
        val snoozed = Instant.parse("2026-11-16T17:04:00Z")
        alarms.add(testAlarm(id = 10, time = LocalTime.of(11, 55), category = "medicación", snoozedUntil = snoozed))

        setCritical("medicación", critical = true)

        assertThat(scheduler.scheduled).containsExactly(10L, snoozed)
    }

    @Test
    fun `marcar programa como despertador y desmarcar vuelve a la alarma normal`() = runTest { // R-15.2, R-15.3
        alarms.add(testAlarm(id = 10, time = LocalTime.of(14, 0), category = "medicación"))

        setCritical("medicación", critical = true)
        assertThat(scheduler.critical).containsExactly(10L, CriticalAlarm(orderId = 1L))

        setCritical("medicación", critical = false)
        assertThat(scheduler.critical).isEmpty()
        assertThat(scheduler.scheduled.keys).containsExactly(10L)
    }
}
