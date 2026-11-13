package com.mirkwood.rutinify.ui.home

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.OrderDisplayStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.model.OrderSummary
import com.mirkwood.rutinify.domain.usecase.ObserveOrderSummariesUseCase
import com.mirkwood.rutinify.testutil.FakeOrderRepository
import com.mirkwood.rutinify.testutil.MainDispatcherRule
import com.mirkwood.rutinify.testutil.testOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** T-23: Home screen logic (R-8.1–R-8.3, R-8.5, R-8.6). */
@OptIn(ExperimentalCoroutinesApi::class) // UnconfinedTestDispatcher
class HomeViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val orders = FakeOrderRepository()

    private fun viewModel() = HomeViewModel(ObserveOrderSummariesUseCase(orders))

    /** Keeps `uiState` subscribed (WhileSubscribed) for the whole test. */
    private fun TestScope.subscribed(vm: HomeViewModel) = vm.also {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.uiState.collect {} }
    }

    private fun summary(
        id: Long,
        pending: Int = 2,
        total: Int = 4,
        status: OrderStatus = OrderStatus.ACTIVE,
        name: String = "Rutina $id",
        date: LocalDate = LocalDate.of(2026, 11, 5),
    ) = OrderSummary(testOrder(id = id, name = name, date = date, status = status), pending, total)

    @Test
    fun `mientras carga no muestra el estado vacio`() { // R-8.5: no empty-state flash before Room answers
        val state = viewModel().uiState.value

        assertThat(state.isLoading).isTrue()
        assertThat(state.showEmptyState).isFalse()
    }

    @Test
    fun `sin ordenes muestra el estado vacio`() = runTest { // R-8.5
        val vm = subscribed(viewModel())

        assertThat(vm.uiState.value.isLoading).isFalse()
        assertThat(vm.uiState.value.orders).isEmpty()
        assertThat(vm.uiState.value.showEmptyState).isTrue()
    }

    @Test
    fun `cada tarjeta muestra nombre, fecha, pendientes de total y estado`() = runTest { // R-8.2
        orders.summaries.value = listOf(summary(7, pending = 3, total = 5, name = "Cachorro"))

        val card = subscribed(viewModel()).uiState.value.orders.single()

        assertThat(card).isEqualTo(
            OrderCardUi(
                id = 7,
                name = "Cachorro",
                dateLabel = "jueves 5 de noviembre de 2026",
                pending = 3,
                total = 5,
                status = OrderDisplayStatus.ACTIVE,
            ),
        )
    }

    @Test
    fun `una orden activa sin pendientes aparece como terminada`() = runTest { // R-8.3
        orders.summaries.value = listOf(summary(1, pending = 0, total = 4))

        val card = subscribed(viewModel()).uiState.value.orders.single()

        assertThat(card.status).isEqualTo(OrderDisplayStatus.FINISHED)
    }

    @Test
    fun `las ordenes pausadas y canceladas conservan su estado aunque no tengan pendientes`() = runTest { // R-8.2
        orders.summaries.value = listOf(
            summary(1, pending = 0, status = OrderStatus.PAUSED),
            summary(2, pending = 0, status = OrderStatus.CANCELLED),
        )

        val statuses = subscribed(viewModel()).uiState.value.orders.map { it.status }

        assertThat(statuses).containsExactly(OrderDisplayStatus.PAUSED, OrderDisplayStatus.CANCELLED).inOrder()
    }

    @Test
    fun `respeta el orden que da el repositorio`() = runTest { // R-8.1: sorting lives in the DAO query
        orders.summaries.value = listOf(
            summary(3, date = LocalDate.of(2026, 11, 7)),
            summary(1, date = LocalDate.of(2026, 11, 6)),
            summary(2, date = LocalDate.of(2026, 11, 6)),
        )

        val ids = subscribed(viewModel()).uiState.value.orders.map { it.id }

        assertThat(ids).containsExactly(3L, 1L, 2L).inOrder()
    }

    @Test
    fun `se actualiza sola cuando cambian las alarmas o las ordenes`() = runTest { // R-8.6
        orders.summaries.value = listOf(summary(1, pending = 2, total = 2))
        val vm = subscribed(viewModel())

        orders.summaries.value = listOf(summary(1, pending = 1, total = 2), summary(2, pending = 3, total = 3))

        assertThat(vm.uiState.value.orders.map { it.id to it.pending }).containsExactly(1L to 1, 2L to 3).inOrder()

        orders.summaries.value = emptyList()

        assertThat(vm.uiState.value.showEmptyState).isTrue()
    }
}
