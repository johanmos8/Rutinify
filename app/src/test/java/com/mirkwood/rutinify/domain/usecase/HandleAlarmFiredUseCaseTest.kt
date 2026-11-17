package com.mirkwood.rutinify.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.scheduler.AlarmNotification
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import com.mirkwood.rutinify.testutil.FakeCategorySettingsRepository
import com.mirkwood.rutinify.testutil.FakeAlarmRepository
import com.mirkwood.rutinify.testutil.FakeNotificationPublisher
import com.mirkwood.rutinify.testutil.FakeOrderRepository
import com.mirkwood.rutinify.testutil.testAlarm
import com.mirkwood.rutinify.testutil.testOrder
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** T-17: what happens when an alarm goes off (R-7.1, R-7.7, R-7.8). */
class HandleAlarmFiredUseCaseTest {

    private val bogota = ZoneId.of("America/Bogota")
    private val now = Instant.parse("2026-11-05T11:45:00Z") // 06:45 in Bogotá
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val alarms = FakeAlarmRepository(clock)
    private val orders = FakeOrderRepository()
    private val publisher = FakeNotificationPublisher()
    private val categories = FakeCategorySettingsRepository()
    private val handleFired = HandleAlarmFiredUseCase(
        alarms, orders, publisher, categories, TriggerTimeCalculator(), { bogota }, AlarmMutationLock(),
    )

    @Test
    fun `una alarma pendiente de una orden activa se notifica y pasa a disparada`() = runTest { // R-7.1
        orders.add(testOrder(id = 1L))
        alarms.add(testAlarm(id = 10L, orderId = 1L))

        val outcome = handleFired(10L)

        assertThat(outcome).isEqualTo(FireOutcome.NOTIFIED)
        assertThat(alarms.alarms.getValue(10L).status).isEqualTo(AlarmStatus.FIRED)
        assertThat(alarms.alarms.getValue(10L).firedAt).isEqualTo(now)
        assertThat(publisher.shown.keys).containsExactly(10L)
    }

    @Test
    fun `la notificacion lleva los datos de la alarma y de la orden`() = runTest { // R-7.1, R-7.2
        orders.add(testOrder(id = 1L, name = "Cachorro"))
        alarms.add(testAlarm(id = 10L, orderId = 1L, title = "Siesta", description = "En su cama", category = "siesta"))

        handleFired(10L)

        assertThat(publisher.shown.getValue(10L)).isEqualTo(
            AlarmNotification(
                alarmId = 10L,
                orderId = 1L,
                orderName = "Cachorro",
                title = "Siesta",
                description = "En su cama",
                time = LocalTime.of(6, 45),
                category = "siesta",
                triggerAt = now,
            ),
        )
    }

    @Test
    fun `una alarma pospuesta usa su hora pospuesta en la notificacion`() = runTest { // D-12
        val snoozed = now.minusSeconds(60)
        orders.add(testOrder(id = 1L))
        alarms.add(testAlarm(id = 10L, orderId = 1L, snoozedUntil = snoozed))

        handleFired(10L)

        assertThat(publisher.shown.getValue(10L).triggerAt).isEqualTo(snoozed)
    }

    @Test
    fun `si la alarma ya no existe no hace nada`() = runTest { // R-7.7
        val outcome = handleFired(99L)

        assertThat(outcome).isEqualTo(FireOutcome.IGNORED)
        assertThat(publisher.shown).isEmpty()
    }

    @Test
    fun `si la alarma no esta pendiente no notifica ni cambia su estado`() = runTest { // R-7.7
        orders.add(testOrder(id = 1L))
        val cases = listOf(
            11L to AlarmStatus.FIRED,
            12L to AlarmStatus.DONE,
            13L to AlarmStatus.SKIPPED,
            14L to AlarmStatus.CANCELLED,
        )
        for ((id, status) in cases) {
            alarms.add(testAlarm(id = id, orderId = 1L, status = status))

            assertThat(handleFired(id)).isEqualTo(FireOutcome.IGNORED)
            assertThat(alarms.alarms.getValue(id).status).isEqualTo(status)
        }
        assertThat(publisher.shown).isEmpty()
    }

    @Test
    fun `si la orden esta pausada o cancelada no notifica`() = runTest { // R-7.7
        orders.add(testOrder(id = 1L, status = OrderStatus.PAUSED), testOrder(id = 2L, status = OrderStatus.CANCELLED))
        alarms.add(testAlarm(id = 10L, orderId = 1L), testAlarm(id = 20L, orderId = 2L))

        assertThat(handleFired(10L)).isEqualTo(FireOutcome.IGNORED)
        assertThat(handleFired(20L)).isEqualTo(FireOutcome.IGNORED)
        assertThat(alarms.alarms.getValue(10L).status).isEqualTo(AlarmStatus.PENDING)
        assertThat(publisher.shown).isEmpty()
    }

    @Test
    fun `si la orden ya no existe no notifica`() = runTest { // R-7.7
        alarms.add(testAlarm(id = 10L, orderId = 1L))

        assertThat(handleFired(10L)).isEqualTo(FireOutcome.IGNORED)
        assertThat(publisher.shown).isEmpty()
    }

    @Test
    fun `sin permiso de notificaciones la alarma pasa a disparada igualmente`() = runTest { // R-7.8
        publisher.enabled = false
        orders.add(testOrder(id = 1L))
        alarms.add(testAlarm(id = 10L, orderId = 1L))

        val outcome = handleFired(10L)

        assertThat(outcome).isEqualTo(FireOutcome.FIRED_WITHOUT_NOTIFICATION)
        assertThat(alarms.alarms.getValue(10L).status).isEqualTo(AlarmStatus.FIRED)
    }

    @Test
    fun `un segundo disparo de la misma alarma se ignora`() = runTest { // R-7.7, R-12.6
        orders.add(testOrder(id = 1L))
        alarms.add(testAlarm(id = 10L, orderId = 1L))

        handleFired(10L)
        publisher.cancel(10L)
        val second = handleFired(10L)

        assertThat(second).isEqualTo(FireOutcome.IGNORED)
        assertThat(publisher.shown).isEmpty()
    }

    @Test
    fun `la alarma de una categoria critica se notifica como critica`() = runTest { // R-15.6
        orders.add(testOrder(id = 1L))
        alarms.add(testAlarm(id = 10L, orderId = 1L, category = "medicación"), testAlarm(id = 11L, orderId = 1L))
        categories.setCritical("medicación", true)

        handleFired(10L)
        handleFired(11L)

        assertThat(publisher.shown.getValue(10L).critical).isTrue()
        assertThat(publisher.shown.getValue(11L).critical).isFalse() // R-15.14
    }
}
