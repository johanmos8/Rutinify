package com.mirkwood.rutinify.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.testutil.FakeAlarmRepository
import com.mirkwood.rutinify.testutil.FakeAlarmScheduler
import com.mirkwood.rutinify.testutil.FakeOrderRepository
import com.mirkwood.rutinify.testutil.testAlarm
import com.mirkwood.rutinify.testutil.testOrder
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** T-27: "Pausar" (R-10.5). */
class PauseOrderUseCaseTest {

    private val now = Instant.parse("2026-11-05T11:50:00Z")
    private val alarms = FakeAlarmRepository(Clock.fixed(now, ZoneOffset.UTC))
    private val orders = FakeOrderRepository(alarms)
    private val scheduler = FakeAlarmScheduler()
    private val pause = PauseOrderUseCase(orders, alarms, scheduler, AlarmMutationLock())

    @Test
    fun `pausar cancela en el sistema las pendientes sin cambiar su estado`() = runTest { // R-10.5
        orders.add(testOrder(id = 1))
        val pending = testAlarm(id = 10, status = AlarmStatus.PENDING)
        val snoozed = testAlarm(id = 11, status = AlarmStatus.PENDING, snoozedUntil = now.plusSeconds(300))
        val fired = testAlarm(id = 12, status = AlarmStatus.FIRED)
        alarms.add(pending, snoozed, fired)
        scheduler.schedule(10, now.plusSeconds(600))
        scheduler.schedule(11, now.plusSeconds(300))

        val paused = pause(1)

        assertThat(paused).isTrue()
        assertThat(scheduler.scheduled).isEmpty()
        assertThat(scheduler.cancelled).containsExactly(10L, 11L)
        assertThat(alarms.alarms.values).containsExactly(pending, snoozed, fired)
        assertThat(orders.orders.getValue(1).status).isEqualTo(OrderStatus.PAUSED)
    }

    @Test
    fun `no toca alarmas de otras ordenes`() = runTest { // R-10.5
        orders.add(testOrder(id = 1), testOrder(id = 2))
        alarms.add(testAlarm(id = 20, orderId = 2))
        scheduler.schedule(20, now.plusSeconds(600))

        pause(1)

        assertThat(scheduler.scheduled).containsKey(20L)
        assertThat(orders.orders.getValue(2).status).isEqualTo(OrderStatus.ACTIVE)
    }

    @Test
    fun `una orden pausada o cancelada no se pausa`() = runTest { // R-10.5: solo una orden Activa
        orders.add(testOrder(id = 1, status = OrderStatus.PAUSED), testOrder(id = 2, status = OrderStatus.CANCELLED))

        assertThat(pause(1)).isFalse()
        assertThat(pause(2)).isFalse()
        assertThat(orders.orders.getValue(2).status).isEqualTo(OrderStatus.CANCELLED)
    }

    @Test
    fun `una orden que ya no existe no se pausa`() = runTest {
        assertThat(pause(1)).isFalse()
    }
}
