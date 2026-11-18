package com.mirkwood.rutinify.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.NextAlarm
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import com.mirkwood.rutinify.testutil.FakeAlarmRepository
import com.mirkwood.rutinify.testutil.testAlarm
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** T-63: what the "Lo siguiente" widget shows (R-21.1, R-21.3, R-21.4). */
class ObserveNextAlarmUseCaseTest {

    private val bogota = ZoneId.of("America/Bogota")
    private val date = LocalDate.of(2026, 11, 5)
    private val clock = Clock.fixed(Instant.parse("2026-11-05T17:00:00Z"), bogota)
    private val calculator = TriggerTimeCalculator()

    /** Plans 1 and 2 are active; plan 3 is paused. */
    private val alarms = FakeAlarmRepository(clock) { id -> if (id == 3L) null else date }
    private val next = ObserveNextAlarmUseCase(alarms, calculator) { bogota }

    private fun at(time: String, day: Int = 0) = calculator.triggerAt(date, LocalTime.parse(time), day, bogota)

    @Test
    fun `elige la pendiente mas proxima de todos los planes activos`() = runTest { // R-21.1
        alarms.add(
            testAlarm(id = 10, orderId = 1, time = LocalTime.of(18, 0), title = "Cena"),
            testAlarm(id = 20, orderId = 2, time = LocalTime.of(14, 0), title = "Amoxicilina"),
            testAlarm(id = 30, orderId = 3, time = LocalTime.of(13, 0), title = "Pausada"),
            testAlarm(id = 21, orderId = 2, time = LocalTime.of(13, 30), title = "Hecha", status = AlarmStatus.DONE),
        )

        assertThat(next.current()).isEqualTo(NextAlarm(20, 2, "Amoxicilina", "Plan 2", at("14:00")))
    }

    @Test
    fun `una pospuesta cuenta por su hora pospuesta`() = runTest { // R-21.1, D-12
        alarms.add(
            testAlarm(id = 10, orderId = 1, time = LocalTime.of(14, 0)),
            testAlarm(id = 11, orderId = 1, time = LocalTime.of(11, 50), snoozedUntil = Instant.parse("2026-11-05T17:04:00Z")),
        )

        assertThat(next.current()?.alarmId).isEqualTo(11L)
        assertThat(next.current()?.triggerAt).isEqualTo(Instant.parse("2026-11-05T17:04:00Z"))
    }

    @Test
    fun `sin pendientes no hay nada`() = runTest { // R-21.3
        alarms.add(testAlarm(id = 10, orderId = 1, status = AlarmStatus.DONE))

        assertThat(next.current()).isNull()
    }

    @Test
    fun `cambia cuando la proxima se marca como hecha`() = runTest { // R-21.4
        alarms.add(testAlarm(id = 10, orderId = 1, time = LocalTime.of(14, 0)), testAlarm(id = 11, orderId = 1, time = LocalTime.of(2, 0), dayOffset = 1))

        next().test {
            assertThat(awaitItem()?.alarmId).isEqualTo(10L)

            alarms.updateStatus(listOf(10), AlarmStatus.DONE)
            alarms.notifyChanged()

            assertThat(awaitItem()?.triggerAt).isEqualTo(at("02:00", day = 1))
        }
    }
}
