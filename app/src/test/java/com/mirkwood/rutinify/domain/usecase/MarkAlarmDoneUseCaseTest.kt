package com.mirkwood.rutinify.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.scheduler.AlarmNotification
import com.mirkwood.rutinify.testutil.FakeAlarmRepository
import com.mirkwood.rutinify.testutil.FakeAlarmScheduler
import com.mirkwood.rutinify.testutil.FakeNotificationPublisher
import com.mirkwood.rutinify.testutil.testAlarm
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

/** T-17: "Hecho" from the notification or from the order detail (R-7.4, R-9.5). */
class MarkAlarmDoneUseCaseTest {

    private val now = Instant.parse("2026-11-05T11:50:00Z")
    private val alarms = FakeAlarmRepository(Clock.fixed(now, ZoneOffset.UTC))
    private val scheduler = FakeAlarmScheduler()
    private val publisher = FakeNotificationPublisher()
    private val markDone = MarkAlarmDoneUseCase(alarms, scheduler, publisher, AlarmMutationLock())

    private fun showNotification(alarmId: Long) {
        publisher.publish(AlarmNotification(alarmId, 1L, "Cachorro", "Siesta", null, LocalTime.NOON, "siesta", now))
    }

    @Test
    fun `una alarma disparada pasa a hecha y se quita su notificacion`() = runTest { // R-7.4
        alarms.add(testAlarm(id = 10L, status = AlarmStatus.FIRED))
        showNotification(10L)

        val done = markDone(10L)

        assertThat(done).isTrue()
        assertThat(alarms.alarms.getValue(10L).status).isEqualTo(AlarmStatus.DONE)
        assertThat(alarms.alarms.getValue(10L).doneAt).isEqualTo(now)
        assertThat(publisher.shown).isEmpty()
    }

    @Test
    fun `una alarma disparada no toca el AlarmManager`() = runTest { // R-7.4: nothing left to cancel
        alarms.add(testAlarm(id = 10L, status = AlarmStatus.FIRED))

        markDone(10L)

        assertThat(scheduler.cancelled).isEmpty()
    }

    @Test
    fun `una alarma pendiente marcada a mano se cancela en el sistema`() = runTest { // R-9.5
        alarms.add(testAlarm(id = 10L, status = AlarmStatus.PENDING))
        scheduler.schedule(10L, now.plusSeconds(600))

        val done = markDone(10L)

        assertThat(done).isTrue()
        assertThat(alarms.alarms.getValue(10L).status).isEqualTo(AlarmStatus.DONE)
        assertThat(scheduler.scheduled).isEmpty()
        assertThat(scheduler.cancelled).containsExactly(10L)
    }

    @Test
    fun `una alarma hecha omitida o cancelada no cambia pero se quita la notificacion`() = runTest { // R-9.5
        val cases = listOf(11L to AlarmStatus.DONE, 12L to AlarmStatus.SKIPPED, 13L to AlarmStatus.CANCELLED)
        for ((id, status) in cases) {
            alarms.add(testAlarm(id = id, status = status))
            showNotification(id)

            assertThat(markDone(id)).isFalse()
            assertThat(alarms.alarms.getValue(id).status).isEqualTo(status)
        }
        assertThat(publisher.shown).isEmpty()
        assertThat(scheduler.cancelled).isEmpty()
    }

    @Test
    fun `si la alarma ya no existe solo se quita la notificacion`() = runTest { // R-9.3, stale notification
        showNotification(99L)

        assertThat(markDone(99L)).isFalse()
        assertThat(publisher.shown).isEmpty()
    }

    @Test
    fun `pulsar Hecho dos veces deja la alarma hecha una sola vez`() = runTest { // idempotent
        alarms.add(testAlarm(id = 10L, status = AlarmStatus.FIRED))

        assertThat(markDone(10L)).isTrue()
        val firstDoneAt = alarms.alarms.getValue(10L).doneAt
        assertThat(markDone(10L)).isFalse()
        assertThat(alarms.alarms.getValue(10L).doneAt).isEqualTo(firstDoneAt)
    }

    @Test
    fun `Hecho quita el corte de una alarma critica`() = runTest { // R-15.8
        alarms.add(testAlarm(id = 10L, status = AlarmStatus.FIRED))
        scheduler.scheduleSilence(10L, now.plusSeconds(600))

        markDone(10L)

        assertThat(scheduler.silences).isEmpty()
    }
}
