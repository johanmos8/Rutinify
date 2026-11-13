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

/** T-26: "Eliminar todas las alarmas" (R-10.2, R-10.3). */
class CancelOrderAlarmsUseCaseTest {

    private val now = Instant.parse("2026-11-05T11:50:00Z")
    private val alarms = FakeAlarmRepository(Clock.fixed(now, ZoneOffset.UTC))
    private val orders = FakeOrderRepository(alarms)
    private val scheduler = FakeAlarmScheduler()
    private val publisher = FakeNotificationPublisher()
    private val cancelAll = CancelOrderAlarmsUseCase(orders, scheduler, publisher, AlarmMutationLock())

    private fun show(alarmId: Long, orderId: Long) {
        publisher.publish(AlarmNotification(alarmId, orderId, "Cachorro", "Siesta", null, LocalTime.NOON, "siesta", now))
    }

    @Test
    fun `cancela solo las pendientes en el sistema y conserva el historial`() = runTest { // R-10.2, R-10.3
        orders.add(testOrder(id = 1))
        alarms.add(
            testAlarm(id = 10, status = AlarmStatus.PENDING),
            testAlarm(id = 11, status = AlarmStatus.PENDING),
            testAlarm(id = 12, status = AlarmStatus.FIRED),
            testAlarm(id = 13, status = AlarmStatus.DONE),
            testAlarm(id = 14, status = AlarmStatus.SKIPPED),
        )
        scheduler.schedule(10, now.plusSeconds(60))
        scheduler.schedule(11, now.plusSeconds(120))

        val cancelled = cancelAll(1)

        assertThat(cancelled).isEqualTo(2)
        assertThat(scheduler.cancelled).containsExactly(10L, 11L)
        assertThat(scheduler.scheduled).isEmpty()
        assertThat(alarms.alarms.values.map { it.status }).containsExactly(
            AlarmStatus.CANCELLED, AlarmStatus.CANCELLED, AlarmStatus.FIRED, AlarmStatus.DONE, AlarmStatus.SKIPPED,
        ).inOrder()
        assertThat(orders.orders.getValue(1).status).isEqualTo(OrderStatus.CANCELLED)
    }

    @Test
    fun `quita de la bandeja las notificaciones de la orden y no las de otras`() = runTest { // R-10.3
        orders.add(testOrder(id = 1), testOrder(id = 2))
        alarms.add(testAlarm(id = 12, orderId = 1, status = AlarmStatus.FIRED), testAlarm(id = 20, orderId = 2, status = AlarmStatus.FIRED))
        show(12, orderId = 1)
        show(20, orderId = 2)

        cancelAll(1)

        assertThat(publisher.shown.keys).containsExactly(20L)
    }

    @Test
    fun `una orden que no existe no hace nada`() = runTest {
        assertThat(cancelAll(1)).isNull()
        assertThat(orders.orders).isEmpty()
    }
}
