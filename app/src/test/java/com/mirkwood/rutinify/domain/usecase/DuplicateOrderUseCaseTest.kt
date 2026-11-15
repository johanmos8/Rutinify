package com.mirkwood.rutinify.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.data.draft.InMemoryDraftStore
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.DraftItem
import com.mirkwood.rutinify.domain.model.DraftOrigin
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.model.OrderWithAlarms
import com.mirkwood.rutinify.testutil.FakeOrderRepository
import com.mirkwood.rutinify.testutil.testAlarm
import com.mirkwood.rutinify.testutil.testOrder
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** T-28, T-36: "Duplicar para otra fecha" (R-10.7). */
class DuplicateOrderUseCaseTest {

    private val orders = FakeOrderRepository()
    private val drafts = InMemoryDraftStore()
    private val duplicate = DuplicateOrderUseCase(orders, drafts)

    private val newDate = LocalDate.of(2026, 11, 20)

    @Test
    fun `crea un borrador con las mismas actividades para la fecha elegida`() = runTest { // R-10.7
        val order = testOrder(id = 1, name = "Cachorro").copy(notes = "Llevar agua")
        orders.detail(1).value = OrderWithAlarms(
            order,
            listOf(
                testAlarm(id = 10, time = LocalTime.of(6, 45), title = "Desayuno", description = "Medio vaso", category = "comida")
                    .copy(durationMin = 15, assignee = "Ana"),
                testAlarm(id = 11, time = LocalTime.of(2, 0), dayOffset = 1, title = "Salida", description = null, category = "paseo"),
            ),
        )

        val draftId = duplicate(1, newDate)

        val entry = drafts.get(requireNotNull(draftId))
        assertThat(entry).isNotNull()
        val draft = requireNotNull(entry).draft
        assertThat(draft.id).isEqualTo(draftId)
        assertThat(draft.name).isEqualTo("Cachorro")
        assertThat(draft.date).isEqualTo(newDate)
        assertThat(draft.notes).isEqualTo("Llevar agua")
        assertThat(draft.origin).isEqualTo(DraftOrigin.DUPLICATE)
        assertThat(draft.rawJson).isNull()
        assertThat(draft.items).containsExactly(
            DraftItem(0, LocalTime.of(6, 45), 0, "Desayuno", "Medio vaso", 15, "comida", "Ana"),
            DraftItem(1, LocalTime.of(2, 0), 1, "Salida", null, null, "paseo", null),
        ).inOrder()
        assertThat(entry.warnings).isEmpty()
    }

    @Test
    fun `las alarmas entran sin estados ni posposiciones`() = runTest { // R-10.7: "sin estados"
        orders.detail(1).value = OrderWithAlarms(
            testOrder(id = 1, status = OrderStatus.CANCELLED),
            listOf(
                testAlarm(id = 10, time = LocalTime.of(6, 0), status = AlarmStatus.DONE),
                testAlarm(id = 11, time = LocalTime.of(7, 0), status = AlarmStatus.SKIPPED),
                testAlarm(id = 12, time = LocalTime.of(8, 0), status = AlarmStatus.CANCELLED),
                testAlarm(id = 13, time = LocalTime.of(9, 0), snoozedUntil = Instant.parse("2026-11-05T15:30:00Z")),
            ),
        )

        val draft = requireNotNull(drafts.get(requireNotNull(duplicate(1, newDate)))).draft

        // Every activity, at its original time (a snoozed one keeps 09:00)
        assertThat(draft.items.map { it.time })
            .containsExactly(LocalTime.of(6, 0), LocalTime.of(7, 0), LocalTime.of(8, 0), LocalTime.of(9, 0)).inOrder()
    }

    @Test
    fun `respeta el orden de la linea de tiempo`() = runTest { // R-10.7
        orders.detail(1).value = OrderWithAlarms(
            testOrder(id = 1),
            listOf(
                testAlarm(id = 10, time = LocalTime.of(1, 0), dayOffset = 1, title = "C"),
                testAlarm(id = 11, time = LocalTime.of(9, 0), title = "B"),
                testAlarm(id = 12, time = LocalTime.of(6, 0), title = "A"),
            ),
        )

        val draft = requireNotNull(drafts.get(requireNotNull(duplicate(1, newDate)))).draft

        assertThat(draft.items.map { it.title }).containsExactly("A", "B", "C").inOrder()
        assertThat(draft.items.map { it.key }).containsExactly(0, 1, 2).inOrder()
    }

    @Test
    fun `cada duplicado es un borrador nuevo`() = runTest {
        orders.detail(1).value = OrderWithAlarms(testOrder(id = 1), listOf(testAlarm(id = 10)))

        assertThat(duplicate(1, newDate)).isNotEqualTo(duplicate(1, newDate))
    }

    @Test
    fun `una orden inexistente o sin alarmas no crea borrador`() = runTest { // D-50
        orders.detail(2).value = OrderWithAlarms(testOrder(id = 2), emptyList())

        assertThat(duplicate(1, newDate)).isNull()
        assertThat(duplicate(2, newDate)).isNull()
    }

    @Test
    fun `una orden de varios dias conserva sus dayOffset y la fecha elegida es el dia 1`() = runTest { // R-10.7
        orders.detail(1).value = OrderWithAlarms(
            testOrder(id = 1, name = "Antibiótico"),
            (0..6).map { day -> testAlarm(id = 10L + day, time = LocalTime.of(8, 0), dayOffset = day, title = "Dosis") },
        )

        val draft = requireNotNull(drafts.get(requireNotNull(duplicate(1, newDate)))).draft

        assertThat(draft.date).isEqualTo(newDate)
        assertThat(draft.items.map { it.dayOffset }).containsExactly(0, 1, 2, 3, 4, 5, 6).inOrder()
    }
}
