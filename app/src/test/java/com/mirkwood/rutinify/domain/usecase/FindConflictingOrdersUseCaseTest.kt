package com.mirkwood.rutinify.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.DraftItem
import com.mirkwood.rutinify.domain.model.DraftOrigin
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.model.Recurrence
import com.mirkwood.rutinify.domain.model.RoutineDraft
import com.mirkwood.rutinify.domain.model.RoutineSpan
import com.mirkwood.rutinify.testutil.FakeAlarmRepository
import com.mirkwood.rutinify.testutil.FakeOrderRepository
import com.mirkwood.rutinify.testutil.testAlarm
import com.mirkwood.rutinify.testutil.testOrder
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** T-20, T-34: orders that trigger the "Reemplazar / Mantener ambas" dialog (R-6.1, R-6.5, R-6.6, D-58). */
class FindConflictingOrdersUseCaseTest {

    private val date = LocalDate.of(2026, 11, 5)
    private val orders = FakeOrderRepository()
    private val alarms = FakeAlarmRepository(Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC)) { null }
        .also { orders.alarmRepository = it }
    private val findConflicts = FindConflictingOrdersUseCase(orders)

    private fun draftWithOffsets(vararg offsets: Int) = RoutineDraft(
        id = "d",
        name = "Nueva",
        date = null,
        notes = null,
        items = offsets.mapIndexed { i, offset -> DraftItem(i, LocalTime.NOON, offset, "T$i", null, null, "general", null) },
        origin = DraftOrigin.IMPORT,
    )

    private val oneDay = draftWithOffsets(0, 1)

    @Test
    fun `devuelve las ordenes activas y pausadas de la misma fecha`() = runTest { // R-6.1, R-6.5
        orders.add(
            testOrder(id = 1L, name = "Cachorro", date = date),
            testOrder(id = 2L, name = "Gato", date = date, status = OrderStatus.PAUSED),
        )

        assertThat(findConflicts(oneDay, date).map { it.order.name }).containsExactly("Cachorro", "Gato")
    }

    @Test
    fun `ignora las canceladas y las de otras fechas`() = runTest { // R-6.1
        orders.add(
            testOrder(id = 1L, date = date, status = OrderStatus.CANCELLED),
            testOrder(id = 2L, date = date.plusDays(1)),
        )

        assertThat(findConflicts(oneDay, date)).isEmpty()
    }

    @Test
    fun `un tratamiento que empezo antes y sigue en esa fecha esta en conflicto con su rango`() = runTest { // R-6.6
        orders.add(testOrder(id = 1L, name = "Antibiótico", date = date.minusDays(2)))
        alarms.add(testAlarm(id = 10L, orderId = 1L), testAlarm(id = 11L, orderId = 1L, dayOffset = 6))

        val conflict = findConflicts(oneDay, date).single()

        assertThat(conflict.order.name).isEqualTo("Antibiótico")
        assertThat(conflict.span).isEqualTo(RoutineSpan.of(date.minusDays(2), 6))
    }

    @Test
    fun `un tratamiento nuevo choca con la rutina de un dia que cae dentro`() = runTest { // R-6.6
        orders.add(testOrder(id = 1L, name = "Cachorro", date = date.plusDays(4)))

        assertThat(findConflicts(draftWithOffsets(0, 6), date).map { it.order.name }).containsExactly("Cachorro")
    }

    @Test
    fun `la madrugada de la rutina nueva no choca con la del dia siguiente`() = runTest { // D-58
        orders.add(testOrder(id = 1L, date = date.plusDays(1)))

        assertThat(findConflicts(oneDay, date)).isEmpty()
    }

    // T-69 · R-23.9: date is Thursday 5 November 2026

    private fun recurringDraft(vararg days: DayOfWeek, until: LocalDate? = null) =
        draftWithOffsets(0).copy(recurrence = Recurrence(days.toSet(), until))

    @Test
    fun `un plan recurrente nuevo choca con un plan de un dia en uno de sus dias de la semana`() = runTest { // R-23.9
        orders.add(
            testOrder(id = 1L, name = "Lunes lejano", date = date.plusDays(18)), // Monday 23 November
            testOrder(id = 2L, name = "Martes", date = date.plusDays(5)), // Tuesday
            testOrder(id = 3L, name = "Lunes pasado", date = date.minusDays(3)), // before the plan starts
        )

        assertThat(findConflicts(recurringDraft(DayOfWeek.MONDAY), date).map { it.order.name }).containsExactly("Lunes lejano")
        assertThat(findConflicts(recurringDraft(DayOfWeek.MONDAY, until = date.plusDays(11)), date)).isEmpty()
    }

    @Test
    fun `un plan recurrente nuevo choca con un plan de varios dias que pisa uno de sus dias`() = runTest { // R-23.9
        orders.add(testOrder(id = 1L, name = "Viaje", date = date.plusDays(1))) // Friday to Sunday
        alarms.add(testAlarm(id = 10L, orderId = 1L), testAlarm(id = 11L, orderId = 1L, dayOffset = 2))

        assertThat(findConflicts(recurringDraft(DayOfWeek.SUNDAY), date).map { it.order.name }).containsExactly("Viaje")
        assertThat(findConflicts(recurringDraft(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), date)).isEmpty()
    }

    @Test
    fun `un plan de un dia nuevo choca con un plan recurrente guardado ese dia de la semana`() = runTest { // R-23.9
        orders.add(testOrder(id = 1L, name = "Gimnasio", date = date.minusDays(10), recurrence = Recurrence(setOf(DayOfWeek.THURSDAY), null)))
        // Materialized occurrences must not stretch the stored plan into a range
        alarms.add(testAlarm(id = 10L, orderId = 1L, dayOffset = 10), testAlarm(id = 11L, orderId = 1L, dayOffset = 17))

        val conflict = findConflicts(oneDay, date).single()
        assertThat(conflict.order.name).isEqualTo("Gimnasio")
        assertThat(conflict.span).isEqualTo(RoutineSpan(date.minusDays(10), date.minusDays(10)))
        assertThat(findConflicts(oneDay, date.plusDays(1))).isEmpty() // Friday
    }

    @Test
    fun `dos planes recurrentes chocan solo si comparten un dia de la semana`() = runTest { // R-23.9
        orders.add(testOrder(id = 1L, name = "Gimnasio", date = date, recurrence = Recurrence(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), null)))

        assertThat(findConflicts(recurringDraft(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), date)).isEmpty()
        assertThat(findConflicts(recurringDraft(DayOfWeek.WEDNESDAY), date.plusDays(60)).map { it.order.name }).containsExactly("Gimnasio")
    }

    @Test
    fun `un plan recurrente que ya termino no choca`() = runTest { // R-23.9
        orders.add(testOrder(id = 1L, date = date.minusDays(30), recurrence = Recurrence(setOf(DayOfWeek.THURSDAY), date.minusDays(1))))

        assertThat(findConflicts(oneDay, date)).isEmpty()
    }
}
