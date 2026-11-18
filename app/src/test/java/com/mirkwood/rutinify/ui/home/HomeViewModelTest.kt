package com.mirkwood.rutinify.ui.home

import com.mirkwood.rutinify.testutil.es
import app.cash.turbine.test
import com.mirkwood.rutinify.testutil.FakePermissionPromptStore
import com.mirkwood.rutinify.domain.usecase.ExactAlarmsCardUseCase
import com.mirkwood.rutinify.testutil.FakeCategorySettingsRepository
import com.mirkwood.rutinify.domain.usecase.ObserveCategorySettingsUseCase
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.OrderDisplayStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.model.OrderSummary
import com.mirkwood.rutinify.domain.usecase.GetPermissionStatusUseCase
import com.mirkwood.rutinify.domain.usecase.ObserveOrderSummariesUseCase
import com.mirkwood.rutinify.testutil.FakeOrderRepository
import com.mirkwood.rutinify.testutil.FakePermissionChecker
import com.mirkwood.rutinify.testutil.MainDispatcherRule
import com.mirkwood.rutinify.testutil.testOrder
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.ui.common.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** T-23, T-36: Home screen logic (R-8.1–R-8.3, R-8.5, R-8.6). */
@OptIn(ExperimentalCoroutinesApi::class) // UnconfinedTestDispatcher
class HomeViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val orders = FakeOrderRepository()

    private val checker = FakePermissionChecker()

    private val categories = FakeCategorySettingsRepository()
    private val prompts = FakePermissionPromptStore()

    private fun viewModel() = HomeViewModel(
        ObserveOrderSummariesUseCase(orders),
        ObserveCategorySettingsUseCase(categories),
        GetPermissionStatusUseCase(checker),
        ExactAlarmsCardUseCase(checker, prompts),
    )

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
        maxDayOffset: Int = 0,
    ) = OrderSummary(testOrder(id = id, name = name, date = date, status = status), pending, total, maxDayOffset)

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
                dateLabel = UiText.Date(LocalDate.of(2026, 11, 5), R.string.date_pattern_weekday_year),
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

    @Test
    fun `con todos los permisos no muestra el banner`() = runTest { // R-11.3
        assertThat(subscribed(viewModel()).uiState.value.missingPermissions).isFalse()
    }

    @Test
    fun `si falta algun permiso muestra el banner`() = runTest { // R-11.3
        checker.exactAlarms = false

        assertThat(subscribed(viewModel()).uiState.value.missingPermissions).isTrue()
    }

    @Test
    fun `al volver a primer plano actualiza el banner`() = runTest { // R-11.5
        checker.notifications = false
        val vm = subscribed(viewModel())
        assertThat(vm.uiState.value.missingPermissions).isTrue()

        checker.notifications = true
        vm.onAction(HomeAction.Resumed)

        assertThat(vm.uiState.value.missingPermissions).isFalse()
    }

    @Test
    fun `una orden de un dia con madrugada muestra solo su fecha`() = runTest { // R-8.2
        orders.summaries.value = listOf(summary(1, maxDayOffset = 1))

        assertThat(subscribed(viewModel()).uiState.value.orders.single().dateLabel.es())
            .isEqualTo("jueves 5 de noviembre de 2026")
    }

    @Test
    fun `una orden de varios dias muestra su rango con el ano al final`() = runTest { // R-8.2
        orders.summaries.value = listOf(
            summary(1, date = LocalDate.of(2026, 11, 14), maxDayOffset = 6),
            summary(2, date = LocalDate.of(2026, 11, 28), maxDayOffset = 6),
            summary(3, date = LocalDate.of(2026, 12, 29), maxDayOffset = 5),
        )

        assertThat(subscribed(viewModel()).uiState.value.orders.map { it.dateLabel.es() }).containsExactly(
            "del sábado 14 al viernes 20 de noviembre de 2026",
            "del sábado 28 de noviembre al viernes 4 de diciembre de 2026",
            "del martes 29 de diciembre de 2026 al domingo 3 de enero de 2027",
        ).inOrder()
    }

    @Test
    fun `sin pantalla completa y sin categorias criticas no muestra el banner`() = runTest { // D-88
        checker.fullScreenIntent = false
        categories.used.value = listOf("medicación")

        assertThat(subscribed(viewModel()).uiState.value.missingPermissions).isFalse()
    }

    @Test
    fun `sin pantalla completa y con una categoria critica muestra el banner`() = runTest { // R-15.11, D-88
        checker.fullScreenIntent = false
        categories.used.value = listOf("medicación")
        val vm = subscribed(viewModel())

        categories.setCritical("medicación", true)

        assertThat(vm.uiState.value.missingPermissions).isTrue()
    }

    @Test
    fun `sin planes no muestra la tarjeta de alarmas exactas`() = runTest { // R-11.2, D-89
        checker.exactAlarms = false

        val state = subscribed(viewModel()).uiState.value

        assertThat(state.showExactAlarmsCard).isFalse()
        assertThat(state.missingPermissions).isTrue()
    }

    @Test
    fun `tras el primer plan sin alarmas exactas muestra la tarjeta en lugar del banner`() = runTest { // R-11.2, D-89
        checker.exactAlarms = false
        orders.summaries.value = listOf(summary(1))

        val state = subscribed(viewModel()).uiState.value

        assertThat(state.showExactAlarmsCard).isTrue()
        assertThat(state.missingPermissions).isFalse()
    }

    @Test
    fun `si tambien faltan las notificaciones se ven la tarjeta y el banner`() = runTest { // R-11.3, D-89
        checker.exactAlarms = false
        checker.notifications = false
        orders.summaries.value = listOf(summary(1))

        val state = subscribed(viewModel()).uiState.value

        assertThat(state.showExactAlarmsCard).isTrue()
        assertThat(state.missingPermissions).isTrue()
    }

    @Test
    fun `Activar abre el ajuste de alarmas exactas`() = runTest { // R-11.2
        checker.exactAlarms = false
        orders.summaries.value = listOf(summary(1))
        val vm = subscribed(viewModel())

        vm.effects.test {
            vm.onAction(HomeAction.ExactAlarmsCardEnable)
            assertThat(awaitItem()).isEqualTo(HomeEffect.OpenExactAlarmSettings)
        }
    }

    @Test
    fun `Ahora no quita la tarjeta para siempre y deja el banner`() = runTest { // D-89
        checker.exactAlarms = false
        orders.summaries.value = listOf(summary(1))
        val vm = subscribed(viewModel())

        vm.onAction(HomeAction.ExactAlarmsCardDismiss)

        assertThat(vm.uiState.value.showExactAlarmsCard).isFalse()
        assertThat(vm.uiState.value.missingPermissions).isTrue()
        assertThat(prompts.cardDismissed).isTrue()
    }

    @Test
    fun `al volver con el permiso concedido la tarjeta desaparece`() = runTest { // R-11.5
        checker.exactAlarms = false
        orders.summaries.value = listOf(summary(1))
        val vm = subscribed(viewModel())

        checker.exactAlarms = true
        vm.onAction(HomeAction.Resumed)

        assertThat(vm.uiState.value.showExactAlarmsCard).isFalse()
        assertThat(vm.uiState.value.missingPermissions).isFalse()
    }
}
