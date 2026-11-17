package com.mirkwood.rutinify.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import com.mirkwood.rutinify.testutil.FakeAlarmRepository
import com.mirkwood.rutinify.testutil.FakeAlarmScheduler
import com.mirkwood.rutinify.testutil.FakeNotificationPublisher
import com.mirkwood.rutinify.testutil.FakeOrderRepository
import com.mirkwood.rutinify.testutil.FakeSilencedAlarms
import com.mirkwood.rutinify.testutil.testAlarm
import com.mirkwood.rutinify.testutil.testOrder
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** T-46: "Silenciar" and the 10-minute cutoff (R-15.9, R-15.10, D-67). */
class SilenceAlarmUseCaseTest {

    private val now = Instant.parse("2026-11-05T11:50:00Z")
    private val alarms = FakeAlarmRepository(Clock.fixed(now, ZoneOffset.UTC))
    private val orders = FakeOrderRepository()
    private val publisher = FakeNotificationPublisher()
    private val scheduler = FakeAlarmScheduler()
    private val silenced = FakeSilencedAlarms()

    private val silence = SilenceAlarmUseCase(
        alarms, orders, publisher, scheduler, silenced, TriggerTimeCalculator(), { ZoneId.of("America/Bogota") }, AlarmMutationLock(),
    )

    @Test
    fun `deja una notificacion muda y la alarma sigue disparada`() = runTest { // R-15.9
        orders.add(testOrder(id = 1L, name = "Antibiótico"))
        alarms.add(testAlarm(id = 10L, status = AlarmStatus.FIRED, title = "Amoxicilina", category = "medicación"))

        val done = silence(10L)

        assertThat(done).isTrue()
        val shown = publisher.shown.getValue(10L)
        assertThat(shown.critical).isTrue()
        assertThat(shown.silenced).isTrue()
        assertThat(shown.title).isEqualTo("Amoxicilina")
        assertThat(shown.orderName).isEqualTo("Antibiótico")
        assertThat(alarms.alarms.getValue(10L).status).isEqualTo(AlarmStatus.FIRED)
    }

    @Test
    fun `quita el corte pendiente y avisa a la pantalla de alarma`() = runTest { // R-15.9, R-15.10
        orders.add(testOrder(id = 1L))
        alarms.add(testAlarm(id = 10L, status = AlarmStatus.FIRED))
        scheduler.scheduleSilence(10L, now.plusSeconds(600))

        silence(10L)

        assertThat(scheduler.silences).isEmpty()
        assertThat(silenced.ids.value).containsExactly(10L)
    }

    @Test
    fun `un corte que llega tras Hecho o mas 5 min no hace nada`() = runTest { // R-15.10
        orders.add(testOrder(id = 1L))
        alarms.add(testAlarm(id = 10L, status = AlarmStatus.DONE), testAlarm(id = 11L, status = AlarmStatus.PENDING))

        assertThat(silence(10L)).isFalse()
        assertThat(silence(11L)).isFalse()
        assertThat(publisher.shown).isEmpty()
        assertThat(silenced.ids.value).isEmpty()
    }

    @Test
    fun `no silencia la alarma de un plan pausado ni una que no existe`() = runTest { // R-15.10
        orders.add(testOrder(id = 1L, status = OrderStatus.PAUSED))
        alarms.add(testAlarm(id = 10L, status = AlarmStatus.FIRED))

        assertThat(silence(10L)).isFalse()
        assertThat(silence(99L)).isFalse()
        assertThat(publisher.shown).isEmpty()
    }
}
