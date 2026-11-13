package com.mirkwood.rutinify.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.scheduler.AlarmNotification
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

/** T-26: "Eliminar orden" (R-10.4). */
class DeleteOrderUseCaseTest {

    private val now = Instant.parse("2026-11-05T11:50:00Z")
    private val alarms = FakeAlarmRepository(Clock.fixed(now, ZoneOffset.UTC))
    private val orders = FakeOrderRepository(alarms)
    private val scheduler = FakeAlarmScheduler()
    private val publisher = FakeNotificationPublisher()
    private val deleteOrder = DeleteOrderUseCase(orders, alarms, scheduler, publisher, AlarmMutationLock())

    @Test
    fun `borra la orden y sus alarmas, cancela las pendientes y quita sus notificaciones`() = runTest { // R-10.4
        orders.add(testOrder(id = 1))
        alarms.add(
            testAlarm(id = 10, status = AlarmStatus.PENDING),
            testAlarm(id = 12, status = AlarmStatus.FIRED),
            testAlarm(id = 13, status = AlarmStatus.DONE),
        )
        scheduler.schedule(10, now.plusSeconds(60))
        publisher.publish(AlarmNotification(12, 1, "Cachorro", "Siesta", null, LocalTime.NOON, "siesta", now))

        val deleted = deleteOrder(1)

        assertThat(deleted).isTrue()
        assertThat(orders.orders).isEmpty()
        assertThat(alarms.alarms).isEmpty()
        assertThat(scheduler.cancelled).containsExactly(10L)
        assertThat(publisher.shown).isEmpty()
    }

    @Test
    fun `tambien borra una orden cancelada`() = runTest { // R-10.8: "Eliminar orden" stays available
        orders.add(testOrder(id = 1, status = OrderStatus.CANCELLED))
        alarms.add(testAlarm(id = 10, status = AlarmStatus.CANCELLED))

        assertThat(deleteOrder(1)).isTrue()
        assertThat(orders.orders).isEmpty()
        assertThat(scheduler.cancelled).isEmpty()
    }

    @Test
    fun `una orden que no existe devuelve false`() = runTest {
        assertThat(deleteOrder(1)).isFalse()
    }
}
