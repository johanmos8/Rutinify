package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.model.AlarmEventType
import com.mirkwood.rutinify.domain.scheduler.CriticalAlarm
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.scheduler.AlarmNotification
import com.mirkwood.rutinify.testutil.FakeCategorySettingsRepository
import com.mirkwood.rutinify.testutil.FakeAlarmRepository
import com.mirkwood.rutinify.testutil.FakeAlarmScheduler
import com.mirkwood.rutinify.testutil.FakeNotificationPublisher
import com.mirkwood.rutinify.testutil.FakeOrderRepository
import com.mirkwood.rutinify.testutil.testAlarm
import com.mirkwood.rutinify.testutil.testOrder
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

/** T-17: "+5 min" from the notification (R-7.5, D-12). */
class SnoozeAlarmUseCaseTest {

    private val now = Instant.parse("2026-11-05T11:47:30Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val alarms = FakeAlarmRepository(clock)
    private val orders = FakeOrderRepository()
    private val scheduler = FakeAlarmScheduler()
    private val categories = FakeCategorySettingsRepository()
    private val publisher = FakeNotificationPublisher()
    private val snooze = SnoozeAlarmUseCase(alarms, orders, scheduler, categories, publisher, clock, AlarmMutationLock())

    private val fiveMinutesLater = now.plusSeconds(300)

    private fun showNotification(alarmId: Long, orderId: Long = 1L) {
        publisher.publish(AlarmNotification(alarmId, orderId, "Cachorro", "Siesta", null, LocalTime.NOON, "siesta", now))
    }

    @Test
    fun `guarda snoozedUntil igual a ahora mas 5 minutos y vuelve a pendiente`() = runTest { // R-7.5, D-12
        orders.add(testOrder(id = 1L))
        alarms.add(testAlarm(id = 10L, orderId = 1L, status = AlarmStatus.FIRED))

        val snoozed = snooze(10L)

        assertThat(snoozed).isTrue()
        assertThat(alarms.alarms.getValue(10L).status).isEqualTo(AlarmStatus.PENDING)
        assertThat(alarms.alarms.getValue(10L).snoozedUntil).isEqualTo(fiveMinutesLater)
    }

    @Test
    fun `programa la alarma en la nueva hora y quita la notificacion`() = runTest { // R-7.5
        orders.add(testOrder(id = 1L))
        alarms.add(testAlarm(id = 10L, orderId = 1L, status = AlarmStatus.FIRED))
        showNotification(10L)

        snooze(10L)

        assertThat(scheduler.scheduled).containsExactly(10L, fiveMinutesLater)
        assertThat(publisher.shown).isEmpty()
    }

    @Test
    fun `posponer otra vez cuenta desde el momento en que se pulsa`() = runTest { // D-12
        orders.add(testOrder(id = 1L))
        alarms.add(testAlarm(id = 10L, orderId = 1L, status = AlarmStatus.FIRED, snoozedUntil = now.minusSeconds(600)))

        snooze(10L)

        assertThat(alarms.alarms.getValue(10L).snoozedUntil).isEqualTo(fiveMinutesLater)
    }

    @Test
    fun `una alarma que no esta disparada no se pospone pero se quita la notificacion`() = runTest { // R-7.5
        orders.add(testOrder(id = 1L))
        val cases = listOf(11L to AlarmStatus.PENDING, 12L to AlarmStatus.DONE, 13L to AlarmStatus.CANCELLED)
        for ((id, status) in cases) {
            alarms.add(testAlarm(id = id, orderId = 1L, status = status))
            showNotification(id)

            assertThat(snooze(id)).isFalse()
            assertThat(alarms.alarms.getValue(id).status).isEqualTo(status)
        }
        assertThat(scheduler.scheduled).isEmpty()
        assertThat(publisher.shown).isEmpty()
    }

    @Test
    fun `pulsar mas 5 min dos veces programa una sola vez`() = runTest { // R-12.6
        orders.add(testOrder(id = 1L))
        alarms.add(testAlarm(id = 10L, orderId = 1L, status = AlarmStatus.FIRED))

        assertThat(snooze(10L)).isTrue()
        assertThat(snooze(10L)).isFalse()
        assertThat(scheduler.scheduled).containsExactly(10L, fiveMinutesLater)
    }

    @Test
    fun `si la orden ya no esta activa no se pospone`() = runTest { // R-7.7, R-10.5
        orders.add(testOrder(id = 1L, status = OrderStatus.PAUSED))
        alarms.add(testAlarm(id = 10L, orderId = 1L, status = AlarmStatus.FIRED))
        showNotification(10L)

        assertThat(snooze(10L)).isFalse()
        assertThat(alarms.alarms.getValue(10L).status).isEqualTo(AlarmStatus.FIRED)
        assertThat(scheduler.scheduled).isEmpty()
        assertThat(publisher.shown).isEmpty()
    }

    @Test
    fun `si la alarma ya no existe solo se quita la notificacion`() = runTest { // R-9.3
        showNotification(99L)

        assertThat(snooze(99L)).isFalse()
        assertThat(publisher.shown).isEmpty()
        assertThat(scheduler.scheduled).isEmpty()
    }

    @Test
    fun `una alarma critica pospuesta vuelve a sonar como despertador`() = runTest { // R-15.8, R-15.3
        orders.add(testOrder(id = 1L))
        alarms.add(testAlarm(id = 10L, orderId = 1L, status = AlarmStatus.FIRED, category = "medicación"))
        categories.setCritical("medicación", true)

        snooze(10L)

        assertThat(scheduler.critical).containsExactly(10L, CriticalAlarm(orderId = 1L))
    }

    @Test
    fun `una alarma no critica pospuesta sigue siendo normal`() = runTest { // R-15.14
        orders.add(testOrder(id = 1L))
        alarms.add(testAlarm(id = 10L, orderId = 1L, status = AlarmStatus.FIRED))
        categories.setCritical("medicación", true)

        snooze(10L)

        assertThat(scheduler.critical).isEmpty()
    }

    @Test
    fun `posponer quita el corte de una alarma critica`() = runTest { // R-15.8
        orders.add(testOrder(id = 1L))
        alarms.add(testAlarm(id = 10L, orderId = 1L, status = AlarmStatus.FIRED, category = "medicación"))
        scheduler.scheduleSilence(10L, now.plusSeconds(600))

        snooze(10L)

        assertThat(scheduler.silences).isEmpty()
    }

    @Test
    fun `posponer deja su evento en el historial`() = runTest { // R-20.1
        orders.add(testOrder(id = 1L))
        alarms.add(testAlarm(id = 10L, orderId = 1L, status = AlarmStatus.FIRED))

        snooze(10L)

        assertThat(alarms.events.map { it.type }).containsExactly(AlarmEventType.SNOOZED)
    }
}
