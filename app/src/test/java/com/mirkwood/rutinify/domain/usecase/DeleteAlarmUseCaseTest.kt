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

/** T-25: deleting one alarm from the order detail (R-9.3). */
class DeleteAlarmUseCaseTest {

    private val now = Instant.parse("2026-11-05T11:50:00Z")
    private val alarms = FakeAlarmRepository(Clock.fixed(now, ZoneOffset.UTC))
    private val scheduler = FakeAlarmScheduler()
    private val publisher = FakeNotificationPublisher()
    private val delete = DeleteAlarmUseCase(alarms, scheduler, publisher, AlarmMutationLock())

    @Test
    fun `una alarma pendiente se cancela en el sistema y se borra`() = runTest { // R-9.3
        val alarm = testAlarm(id = 10L, status = AlarmStatus.PENDING)
        alarms.add(alarm)
        scheduler.schedule(10L, now.plusSeconds(600))

        val deleted = delete(10L)

        assertThat(deleted).isEqualTo(alarm)
        assertThat(alarms.alarms).doesNotContainKey(10L)
        assertThat(scheduler.scheduled).doesNotContainKey(10L)
        assertThat(scheduler.cancelled).containsExactly(10L)
    }

    @Test
    fun `una alarma disparada quita su notificacion visible`() = runTest { // R-9.3
        alarms.add(testAlarm(id = 10L, status = AlarmStatus.FIRED))
        publisher.publish(AlarmNotification(10L, 1L, "Cachorro", "Siesta", null, LocalTime.NOON, "siesta", now))

        delete(10L)

        assertThat(publisher.shown).isEmpty()
        assertThat(alarms.alarms).isEmpty()
    }

    @Test
    fun `una alarma que no estaba pendiente no toca el AlarmManager`() = runTest { // R-9.3: "si estaba pendiente"
        alarms.add(testAlarm(id = 10L, status = AlarmStatus.DONE))

        delete(10L)

        assertThat(scheduler.cancelled).isEmpty()
    }

    @Test
    fun `una alarma que ya no existe devuelve null`() = runTest {
        assertThat(delete(10L)).isNull()
    }
}
