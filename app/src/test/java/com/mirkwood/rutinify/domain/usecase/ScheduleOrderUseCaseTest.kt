package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.model.Recurrence
import com.mirkwood.rutinify.domain.diagnostics.DiagnosticEvent
import com.mirkwood.rutinify.testutil.FakeDiagnosticsRecorder
import com.mirkwood.rutinify.domain.scheduler.CriticalAlarm
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.DraftItem
import com.mirkwood.rutinify.domain.model.DraftOrigin
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.model.RoutineDraft
import com.mirkwood.rutinify.domain.scheduler.AlarmNotification
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** T-20: saving and scheduling an order (R-5.1, R-5.5, R-5.7, R-5.9, R-5.10, R-6.2, R-6.3). */
class ScheduleOrderUseCaseTest {

    private val bogota = ZoneId.of("America/Bogota")
    private val date = LocalDate.of(2026, 11, 5)
    private val now = Instant.parse("2026-11-05T17:00:00Z") // 12:00 in Bogotá
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val calculator = TriggerTimeCalculator()

    private val orders = FakeOrderRepository(now = now)
    private val alarms = FakeAlarmRepository(clock) { orders.activeDate(it) }.also { orders.alarmRepository = it }
    private val scheduler = FakeAlarmScheduler()
    private val categories = FakeCategorySettingsRepository()
    private val diagnostics = FakeDiagnosticsRecorder()
    private val publisher = FakeNotificationPublisher()
    private val scheduleOrder = ScheduleOrderUseCase(
        orders = orders,
        alarms = alarms,
        scheduler = scheduler,
        categories = categories,
        publisher = publisher,
        buildPreview = BuildSchedulePreviewUseCase(calculator),
        calculator = calculator,
        zoneProvider = { bogota },
        clock = clock,
        lock = AlarmMutationLock(),
        diagnostics = diagnostics,
        materializer = MaterializeRecurringUseCase(orders, alarms, scheduler, publisher, categories, calculator, { bogota }, clock),
    )

    /** 06:45 (already past at 12:00), 13:00 paseo, 18:00, 22:30 and 02:00 (+1 day). Keys 0..4. */
    private val draft = RoutineDraft(
        id = "d",
        name = "Cachorro",
        date = date,
        notes = "Notas",
        items = listOf("06:45" to 0, "13:00" to 0, "18:00" to 0, "22:30" to 0, "02:00" to 1).mapIndexed { i, (t, offset) ->
            DraftItem(i, LocalTime.parse(t), offset, "A$i", null, null, if (i == 1) "paseo" else "general", null)
        },
        origin = DraftOrigin.IMPORT,
        rawJson = "{}",
    )

    private fun draftOf(count: Int) = draft.copy(
        items = (0 until count).map { i ->
            DraftItem(i, LocalTime.of(13, 0).plusSeconds(i.toLong()), 0, "A$i", null, null, "general", null)
        },
    )

    private suspend fun schedule(
        excluded: Set<Int> = emptySet(),
        policy: ConflictPolicy = ConflictPolicy.KEEP_BOTH,
        routine: RoutineDraft = draft,
    ) = scheduleOrder(routine, date, excluded, policy)

    private fun newOrderAlarms(orderId: Long) = alarms.alarms.values.filter { it.orderId == orderId }

    // --- Happy path -----------------------------------------------------------------------------

    @Test
    fun `guarda la orden activa y programa cada alarma incluida en su hora`() = runTest { // R-5.1, R-5.2
        val result = schedule() as ScheduleOrderResult.Scheduled

        val order = orders.orders.getValue(result.orderId)
        assertThat(order.name).isEqualTo("Cachorro")
        assertThat(order.date).isEqualTo(date)
        assertThat(order.status).isEqualTo(OrderStatus.ACTIVE)
        val pending = newOrderAlarms(result.orderId).filter { it.status == AlarmStatus.PENDING }
        assertThat(scheduler.scheduled).containsExactlyEntriesIn(
            pending.associate { it.id to calculator.triggerAt(date, it.time, it.dayOffset, bogota) },
        )
        assertThat(result.scheduledCount).isEqualTo(4)
        assertThat(result.windowed).isFalse()
    }

    @Test
    fun `las actividades pasadas y excluidas se guardan como omitidas y no se programan`() = runTest { // R-5.5
        val result = schedule(excluded = setOf(2)) as ScheduleOrderResult.Scheduled

        val byPosition = newOrderAlarms(result.orderId).associate { it.position to it.status }
        assertThat(byPosition).containsExactly(
            0, AlarmStatus.SKIPPED,
            1, AlarmStatus.PENDING,
            2, AlarmStatus.SKIPPED,
            3, AlarmStatus.PENDING,
            4, AlarmStatus.PENDING,
        )
        assertThat(scheduler.scheduled).hasSize(3)
        assertThat(result.scheduledCount).isEqualTo(3)
    }

    @Test
    fun `crea los canales de las categorias antes de programar`() = runTest { // R-5.9
        schedule()

        assertThat(publisher.channels).containsAtLeast("general", "paseo")
    }

    @Test
    fun `sin permiso de alarmas exactas indica que van con ventana`() = runTest { // R-5.4
        scheduler.exact = false

        val result = schedule() as ScheduleOrderResult.Scheduled

        assertThat(result.windowed).isTrue()
    }

    @Test
    fun `sin actividades incluidas no guarda ni programa nada`() = runTest { // R-4.10
        val result = schedule(excluded = setOf(1, 2, 3, 4))

        assertThat(result).isEqualTo(ScheduleOrderResult.NothingToSchedule)
        assertThat(orders.createCalls).isEqualTo(0)
        assertThat(scheduler.scheduled).isEmpty()
    }

    // --- Storage failure ------------------------------------------------------------------------

    @Test
    fun `si falla Room no se llama al scheduler`() = runTest { // R-5.7
        orders.failCreate = true

        val result = schedule()

        assertThat(result).isEqualTo(ScheduleOrderResult.StorageFailure)
        assertThat(scheduler.scheduled).isEmpty()
        assertThat(scheduler.cancelled).isEmpty()
    }

    // --- Conflicts ------------------------------------------------------------------------------

    @Test
    fun `con REPLACE las ordenes previas pasan a canceladas y sus alarmas se cancelan`() = runTest { // R-6.2, R-6.5
        orders.add(testOrder(id = 1L, date = date), testOrder(id = 2L, date = date, status = OrderStatus.PAUSED))
        alarms.add(
            testAlarm(id = 10L, orderId = 1L, time = LocalTime.of(15, 0)),
            testAlarm(id = 11L, orderId = 1L, status = AlarmStatus.DONE),
            testAlarm(id = 20L, orderId = 2L, time = LocalTime.of(16, 0)),
        )
        scheduler.schedule(10L, now.plusSeconds(3_600))

        schedule(policy = ConflictPolicy.REPLACE)

        assertThat(orders.orders.getValue(1L).status).isEqualTo(OrderStatus.CANCELLED)
        assertThat(orders.orders.getValue(2L).status).isEqualTo(OrderStatus.CANCELLED)
        assertThat(alarms.alarms.getValue(10L).status).isEqualTo(AlarmStatus.CANCELLED)
        assertThat(alarms.alarms.getValue(11L).status).isEqualTo(AlarmStatus.DONE)
        assertThat(alarms.alarms.getValue(20L).status).isEqualTo(AlarmStatus.CANCELLED)
        assertThat(scheduler.cancelled).containsExactly(10L, 20L)
        assertThat(scheduler.scheduled.keys).doesNotContain(10L)
    }

    @Test
    fun `con REPLACE se quitan las notificaciones de las ordenes reemplazadas`() = runTest { // R-6.2
        orders.add(testOrder(id = 1L, date = date))
        alarms.add(testAlarm(id = 10L, orderId = 1L, status = AlarmStatus.FIRED))
        publisher.publish(AlarmNotification(10L, 1L, "Cachorro", "Siesta", null, LocalTime.NOON, "siesta", now))

        schedule(policy = ConflictPolicy.REPLACE)

        assertThat(publisher.shown).isEmpty()
    }

    @Test
    fun `REPLACE no toca ordenes de otras fechas`() = runTest { // R-6.1
        orders.add(testOrder(id = 1L, date = date.plusDays(1)))
        alarms.add(testAlarm(id = 10L, orderId = 1L))

        schedule(policy = ConflictPolicy.REPLACE)

        assertThat(orders.orders.getValue(1L).status).isEqualTo(OrderStatus.ACTIVE)
        assertThat(alarms.alarms.getValue(10L).status).isEqualTo(AlarmStatus.PENDING)
    }

    @Test
    fun `con REPLACE se cancela un tratamiento que empezo antes y sigue en esa fecha`() = runTest { // R-6.6, D-56
        orders.add(testOrder(id = 1L, date = date.minusDays(2)))
        alarms.add(testAlarm(id = 10L, orderId = 1L, dayOffset = 5, time = LocalTime.of(15, 0)))

        schedule(policy = ConflictPolicy.REPLACE)

        assertThat(orders.orders.getValue(1L).status).isEqualTo(OrderStatus.CANCELLED)
        assertThat(alarms.alarms.getValue(10L).status).isEqualTo(AlarmStatus.CANCELLED)
    }

    @Test
    fun `KEEP_BOTH no toca las ordenes existentes`() = runTest { // R-6.3
        orders.add(testOrder(id = 1L, date = date))
        alarms.add(testAlarm(id = 10L, orderId = 1L, time = LocalTime.of(15, 0)))

        val result = schedule(policy = ConflictPolicy.KEEP_BOTH)

        assertThat(result).isInstanceOf(ScheduleOrderResult.Scheduled::class.java)
        assertThat(orders.orders.getValue(1L).status).isEqualTo(OrderStatus.ACTIVE)
        assertThat(alarms.alarms.getValue(10L).status).isEqualTo(AlarmStatus.PENDING)
        assertThat(scheduler.cancelled).isEmpty()
    }

    // --- 500 limit ------------------------------------------------------------------------------

    @Test
    fun `500 alarmas exactas se programan`() = runTest { // R-5.10
        val result = schedule(routine = draftOf(500)) as ScheduleOrderResult.Scheduled

        assertThat(result.scheduledCount).isEqualTo(500)
        assertThat(scheduler.scheduled).hasSize(500)
    }

    @Test
    fun `501 alarmas no escriben en Room ni llaman al scheduler`() = runTest { // R-5.10
        val result = schedule(routine = draftOf(501))

        assertThat(result).isEqualTo(ScheduleOrderResult.TooManyAlarms(requested = 501, alreadyScheduled = 0))
        assertThat(orders.createCalls).isEqualTo(0)
        assertThat(scheduler.scheduled).isEmpty()
    }

    @Test
    fun `el limite cuenta las pendientes de otras ordenes activas`() = runTest { // R-5.10
        orders.add(testOrder(id = 1L, date = date.plusDays(1)))
        alarms.add(*(1L..498L).map { testAlarm(id = it, orderId = 1L) }.toTypedArray())

        val result = schedule(routine = draftOf(3))

        assertThat(result).isEqualTo(ScheduleOrderResult.TooManyAlarms(requested = 3, alreadyScheduled = 498))
        assertThat(orders.createCalls).isEqualTo(0)
    }

    @Test
    fun `las pendientes de ordenes pausadas no cuentan para el limite`() = runTest { // R-5.10: not in AlarmManager
        orders.add(testOrder(id = 1L, date = date.plusDays(1), status = OrderStatus.PAUSED))
        alarms.add(*(1L..498L).map { testAlarm(id = it, orderId = 1L) }.toTypedArray())

        val result = schedule(routine = draftOf(3))

        assertThat(result).isInstanceOf(ScheduleOrderResult.Scheduled::class.java)
    }

    @Test
    fun `con REPLACE se descuentan las pendientes que se cancelarian`() = runTest { // R-5.10, R-6.2
        orders.add(testOrder(id = 1L, date = date))
        alarms.add(*(1L..498L).map { testAlarm(id = it, orderId = 1L) }.toTypedArray())

        val keepBoth = schedule(routine = draftOf(3), policy = ConflictPolicy.KEEP_BOTH)
        val replace = schedule(routine = draftOf(3), policy = ConflictPolicy.REPLACE)

        assertThat(keepBoth).isEqualTo(ScheduleOrderResult.TooManyAlarms(requested = 3, alreadyScheduled = 498))
        assertThat(replace).isInstanceOf(ScheduleOrderResult.Scheduled::class.java)
    }

    @Test
    fun `las actividades de categorias criticas se programan como despertador`() = runTest { // R-15.3, R-15.14
        categories.setCritical("paseo", true)

        val result = schedule() as ScheduleOrderResult.Scheduled

        val paseo = newOrderAlarms(result.orderId).single { it.category == "paseo" }
        assertThat(scheduler.critical).containsExactly(paseo.id, CriticalAlarm(result.orderId))
        assertThat(result.scheduledCount).isEqualTo(4)
    }

    @Test
    fun `al programar cuenta el paso y el plan creado`() = runTest { // R-17.2, R-17.3
        schedule()

        assertThat(diagnostics.events).containsExactly(DiagnosticEvent.Step.SCHEDULED, DiagnosticEvent.PlanCreated).inOrder()
    }

    @Test
    fun `si no se programa nada no cuenta`() = runTest { // R-17.3
        schedule(excluded = setOf(0, 1, 2, 3, 4))

        assertThat(diagnostics.events).isEmpty()
    }

    @Test
    fun `un plan recurrente guarda su dia tipo y crea solo la ventana`() = runTest { // R-23.7, D-81
        val weekly = draft.copy(recurrence = Recurrence(setOf(java.time.DayOfWeek.THURSDAY), until = null))

        val result = schedule(routine = weekly, excluded = setOf(4)) as ScheduleOrderResult.Scheduled

        val order = orders.orders.getValue(result.orderId)
        assertThat(order.recurrence).isEqualTo(weekly.recurrence)
        assertThat(orders.activities.getValue(result.orderId)).hasSize(4)
        // Thursday 2026-11-05 at 12:00: 13:00, 18:00 and 22:30 today; 06:45 is past; next Thursday is outside the window
        assertThat(result.scheduledCount).isEqualTo(3)
        assertThat(newOrderAlarms(result.orderId).map { it.activityId }.all { it != null }).isTrue()
    }
}
