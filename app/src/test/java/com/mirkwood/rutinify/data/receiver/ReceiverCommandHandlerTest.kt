package com.mirkwood.rutinify.data.receiver

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import com.mirkwood.rutinify.domain.usecase.AlarmMutationLock
import com.mirkwood.rutinify.domain.usecase.HandleAlarmFiredUseCase
import com.mirkwood.rutinify.domain.usecase.MarkAlarmDoneUseCase
import com.mirkwood.rutinify.domain.usecase.SnoozeAlarmUseCase
import com.mirkwood.rutinify.testutil.FakeAlarmRepository
import com.mirkwood.rutinify.testutil.FakeAlarmScheduler
import com.mirkwood.rutinify.testutil.FakeCategorySettingsRepository
import com.mirkwood.rutinify.testutil.FakeNotificationPublisher
import com.mirkwood.rutinify.testutil.FakeOrderRepository
import com.mirkwood.rutinify.testutil.testAlarm
import com.mirkwood.rutinify.testutil.testOrder
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** T-18: each receiver command reaches the right use case (R-7.1, R-7.4, R-7.5). */
class ReceiverCommandHandlerTest {

    private val now = Instant.parse("2026-11-05T11:45:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val alarms = FakeAlarmRepository(clock)
    private val orders = FakeOrderRepository()
    private val scheduler = FakeAlarmScheduler()
    private val categories = FakeCategorySettingsRepository()
    private val publisher = FakeNotificationPublisher()
    private val lock = AlarmMutationLock()
    private val handler = ReceiverCommandHandler(
        HandleAlarmFiredUseCase(alarms, orders, publisher, categories, TriggerTimeCalculator(), { ZoneId.of("America/Bogota") }, lock),
        MarkAlarmDoneUseCase(alarms, scheduler, publisher, lock),
        SnoozeAlarmUseCase(alarms, orders, scheduler, categories, publisher, clock, lock),
    )

    @Test
    fun `Fire dispara la alarma de ese id`() = runTest { // R-7.1
        orders.add(testOrder(id = 1L))
        alarms.add(testAlarm(id = 10L, orderId = 1L), testAlarm(id = 11L, orderId = 1L))

        handler.handle(ReceiverCommand.Fire(10L))

        assertThat(alarms.alarms.getValue(10L).status).isEqualTo(AlarmStatus.FIRED)
        assertThat(alarms.alarms.getValue(11L).status).isEqualTo(AlarmStatus.PENDING)
        assertThat(publisher.shown.keys).containsExactly(10L)
    }

    @Test
    fun `Done marca hecha la alarma de ese id`() = runTest { // R-7.4
        alarms.add(testAlarm(id = 10L, status = AlarmStatus.FIRED), testAlarm(id = 11L, status = AlarmStatus.FIRED))

        handler.handle(ReceiverCommand.Done(10L))

        assertThat(alarms.alarms.getValue(10L).status).isEqualTo(AlarmStatus.DONE)
        assertThat(alarms.alarms.getValue(11L).status).isEqualTo(AlarmStatus.FIRED)
    }

    @Test
    fun `Snooze pospone la alarma de ese id`() = runTest { // R-7.5
        orders.add(testOrder(id = 1L))
        alarms.add(testAlarm(id = 10L, orderId = 1L, status = AlarmStatus.FIRED))

        handler.handle(ReceiverCommand.Snooze(10L))

        assertThat(alarms.alarms.getValue(10L).status).isEqualTo(AlarmStatus.PENDING)
        assertThat(scheduler.scheduled).containsExactly(10L, now.plusSeconds(300))
    }
}
