package com.mirkwood.rutinify.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.AlarmItem
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderDisplayStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.model.OrderWithAlarms
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import com.mirkwood.rutinify.testutil.FakeOrderRepository
import com.mirkwood.rutinify.testutil.testAlarm
import com.mirkwood.rutinify.testutil.testOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** T-24: order timeline (R-9.1, R-9.2). */
class ObserveOrderDetailUseCaseTest {

    private val zone = ZoneId.of("America/Bogota")
    private val orders = FakeOrderRepository()
    private val useCase = ObserveOrderDetailUseCase(orders, TriggerTimeCalculator()) { zone }

    private fun setOrder(status: OrderStatus = OrderStatus.ACTIVE, vararg alarms: AlarmItem) {
        orders.detail(1).value = OrderWithAlarms(testOrder(id = 1, status = status), alarms.toList())
    }

    @Test
    fun `una orden que no existe emite null`() = runTest {
        assertThat(useCase(1).first()).isNull()
    }

    @Test
    fun `ordena la linea de tiempo por hora de disparo, incluida la pospuesta`() = runTest { // R-9.1
        setOrder(
            OrderStatus.ACTIVE,
            testAlarm(id = 1, time = LocalTime.of(7, 0), status = AlarmStatus.DONE),
            // Snoozed from 07:30 to 09:05 Bogotá (14:05Z): its effective time wins (D-12)
            testAlarm(id = 2, time = LocalTime.of(7, 30), snoozedUntil = Instant.parse("2026-11-05T14:05:00Z")),
            testAlarm(id = 3, time = LocalTime.of(9, 0)),
            testAlarm(id = 4, time = LocalTime.of(1, 0), dayOffset = 1),
        )

        val detail = useCase(1).first()

        assertThat(detail?.timeline?.map { it.alarm.id }).containsExactly(1L, 3L, 2L, 4L).inOrder()
        assertThat(detail?.timeline?.get(2)?.triggerAt).isEqualTo(Instant.parse("2026-11-05T14:05:00Z"))
        assertThat(detail?.timeline?.get(3)?.triggerAt).isEqualTo(Instant.parse("2026-11-06T06:00:00Z"))
    }

    @Test
    fun `la proxima alarma es la primera pendiente de la linea de tiempo`() = runTest { // R-9.2
        setOrder(
            OrderStatus.ACTIVE,
            testAlarm(id = 1, time = LocalTime.of(7, 0), status = AlarmStatus.FIRED),
            testAlarm(id = 2, time = LocalTime.of(8, 0), status = AlarmStatus.SKIPPED),
            testAlarm(id = 3, time = LocalTime.of(10, 0)),
            testAlarm(id = 4, time = LocalTime.of(9, 0)),
        )

        assertThat(useCase(1).first()?.nextAlarmId).isEqualTo(4L)
    }

    @Test
    fun `una orden pausada no destaca ninguna alarma porque no van a sonar`() = runTest { // R-9.2, R-10.5
        setOrder(OrderStatus.PAUSED, testAlarm(id = 1))

        assertThat(useCase(1).first()?.nextAlarmId).isNull()
    }

    @Test
    fun `sin pendientes no hay proxima alarma y la orden figura como terminada`() = runTest { // R-8.3, R-9.2
        setOrder(OrderStatus.ACTIVE, testAlarm(id = 1, status = AlarmStatus.DONE), testAlarm(id = 2, status = AlarmStatus.SKIPPED))

        val detail = useCase(1).first()

        assertThat(detail?.nextAlarmId).isNull()
        assertThat(detail?.displayStatus).isEqualTo(OrderDisplayStatus.FINISHED)
        assertThat(detail?.pending).isEqualTo(0)
        assertThat(detail?.total).isEqualTo(2)
    }

    @Test
    fun `se actualiza cuando cambia una alarma`() = runTest { // R-9.1
        setOrder(OrderStatus.ACTIVE, testAlarm(id = 1))
        assertThat(useCase(1).first()?.pending).isEqualTo(1)

        setOrder(OrderStatus.ACTIVE, testAlarm(id = 1, status = AlarmStatus.DONE))

        assertThat(useCase(1).first()?.pending).isEqualTo(0)
    }
}
