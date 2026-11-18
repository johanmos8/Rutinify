package com.mirkwood.rutinify.ui.settings

import com.mirkwood.rutinify.testutil.es
import com.mirkwood.rutinify.testutil.FakeDiagnosticsRepository
import com.mirkwood.rutinify.domain.diagnostics.DiagnosticEvent
import com.mirkwood.rutinify.domain.system.AppInfo
import com.mirkwood.rutinify.domain.usecase.DiagnosticsReport
import com.mirkwood.rutinify.domain.usecase.ExportDiagnosticsUseCase
import com.mirkwood.rutinify.domain.usecase.ManageDiagnosticsUseCase
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import com.mirkwood.rutinify.domain.usecase.AlarmMutationLock
import com.mirkwood.rutinify.domain.usecase.GetPermissionStatusUseCase
import com.mirkwood.rutinify.domain.usecase.HandleAlarmFiredUseCase
import com.mirkwood.rutinify.domain.usecase.ObserveCategorySettingsUseCase
import com.mirkwood.rutinify.domain.usecase.RescheduleAllUseCase
import com.mirkwood.rutinify.domain.usecase.SetCategoryCriticalUseCase
import com.mirkwood.rutinify.testutil.FakeAlarmRepository
import com.mirkwood.rutinify.testutil.FakeAlarmScheduler
import com.mirkwood.rutinify.testutil.FakeCategorySettingsRepository
import com.mirkwood.rutinify.testutil.FakeNotificationPublisher
import com.mirkwood.rutinify.testutil.FakeOrderRepository
import com.mirkwood.rutinify.testutil.FakePermissionChecker
import com.mirkwood.rutinify.testutil.MainDispatcherRule
import com.mirkwood.rutinify.testutil.testAlarm
import com.mirkwood.rutinify.testutil.testOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** T-44: Settings › "Alarmas críticas" (R-15.1, R-15.2, R-15.4, R-15.5). */
@OptIn(ExperimentalCoroutinesApi::class) // UnconfinedTestDispatcher
class SettingsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val zone = ZoneId.of("America/Bogota")
    private val date = LocalDate.of(2026, 11, 17)
    private val clock = Clock.fixed(Instant.parse("2026-11-17T17:00:00Z"), zone) // 12:00 in Bogotá
    private val orders = FakeOrderRepository()
    private val alarms = FakeAlarmRepository(clock) { id -> orders.activeDate(id) }
    private val scheduler = FakeAlarmScheduler()
    private val categories = FakeCategorySettingsRepository()
    private val checker = FakePermissionChecker()
    private val lock = AlarmMutationLock()
    private val calculator = TriggerTimeCalculator()
    private val diagnostics = FakeDiagnosticsRepository()
    private val appInfo = object : AppInfo {
        override val versionName = "1.3"
        override val versionCode = 13L
    }

    private fun viewModel() = SettingsViewModel(
        observeCategorySettings = ObserveCategorySettingsUseCase(categories),
        setCategoryCritical = SetCategoryCriticalUseCase(categories, alarms, scheduler, calculator, { zone }, clock, lock),
        getPermissionStatus = GetPermissionStatusUseCase(checker),
        rescheduleAll = RescheduleAllUseCase(
            alarms = alarms,
            scheduler = scheduler,
            categories = categories,
            handleFired = HandleAlarmFiredUseCase(alarms, orders, FakeNotificationPublisher(), categories, scheduler, clock, calculator, { zone }, lock),
            calculator = calculator,
            zoneProvider = { zone },
            clock = clock,
            lock = lock,
        ),
        manageDiagnostics = ManageDiagnosticsUseCase(diagnostics),
        exportDiagnostics = ExportDiagnosticsUseCase(diagnostics, appInfo),
    )

    /** Keeps `uiState` subscribed (WhileSubscribed) for the whole test. */
    private fun TestScope.subscribed(vm: SettingsViewModel) = vm.also {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.uiState.collect {} }
    }

    @Test
    fun `mientras carga no muestra el estado vacio`() {
        val state = viewModel().uiState.value

        assertThat(state.isLoading).isTrue()
        assertThat(state.showEmptyState).isFalse()
    }

    @Test
    fun `lista las categorias de los planes con su nombre legible y sin marcar`() = runTest { // R-15.1
        categories.used.value = listOf("paseo-largo", "medicación")

        val state = subscribed(viewModel()).uiState.value

        assertThat(state.categories).containsExactly(
            CategoryRowUi(name = "medicación", label = "Medicación", critical = false),
            CategoryRowUi(name = "paseo-largo", label = "Paseo largo", critical = false),
        ).inOrder()
        assertThat(state.showEmptyState).isFalse()
    }

    @Test
    fun `sin planes muestra el estado vacio`() = runTest { // R-15.1
        assertThat(subscribed(viewModel()).uiState.value.showEmptyState).isTrue()
    }

    @Test
    fun `marcar y desmarcar persiste y reprograma las pendientes de esa categoria`() = runTest { // R-15.1, R-15.2
        orders.add(testOrder(id = 1, date = date))
        alarms.add(
            testAlarm(id = 10, time = LocalTime.of(14, 0), category = "medicación"),
            testAlarm(id = 11, time = LocalTime.of(15, 0), category = "paseo"),
        )
        categories.used.value = listOf("medicación", "paseo")
        val vm = subscribed(viewModel())

        vm.onAction(SettingsAction.CriticalToggled("medicación", critical = true))

        assertThat(vm.uiState.value.categories.first { it.name == "medicación" }.critical).isTrue()
        assertThat(categories.criticalNames()).containsExactly("medicación")
        assertThat(scheduler.scheduled.keys).containsExactly(10L)

        scheduler.scheduled.clear()
        vm.onAction(SettingsAction.CriticalToggled("medicación", critical = false))

        assertThat(vm.uiState.value.categories.first { it.name == "medicación" }.critical).isFalse()
        assertThat(categories.criticalNames()).isEmpty()
        assertThat(scheduler.scheduled.keys).containsExactly(10L)
    }

    @Test
    fun `al marcar la primera critica sin alarmas exactas explica el permiso`() = runTest { // R-15.5
        checker.exactAlarms = false
        categories.used.value = listOf("medicación", "paseo")
        val vm = subscribed(viewModel())

        vm.onAction(SettingsAction.CriticalToggled("medicación", critical = true))

        assertThat(vm.uiState.value.showExactAlarmsDialog).isTrue()
        // The mark is kept either way: the alarms ring in a window until the permission arrives (R-15.4)
        assertThat(categories.criticalNames()).containsExactly("medicación")
    }

    @Test
    fun `con otra categoria ya critica no vuelve a explicar el permiso`() = runTest { // R-15.5
        checker.exactAlarms = false
        categories.used.value = listOf("medicación", "paseo")
        categories.setCritical("medicación", true)
        val vm = subscribed(viewModel())

        vm.onAction(SettingsAction.CriticalToggled("paseo", critical = true))

        assertThat(vm.uiState.value.showExactAlarmsDialog).isFalse()
    }

    @Test
    fun `con alarmas exactas no explica nada`() = runTest { // R-15.5
        categories.used.value = listOf("medicación")
        val vm = subscribed(viewModel())

        vm.onAction(SettingsAction.CriticalToggled("medicación", critical = true))

        assertThat(vm.uiState.value.showExactAlarmsDialog).isFalse()
        assertThat(vm.uiState.value.showExactAlarmsWarning).isFalse()
    }

    @Test
    fun `Abrir ajustes cierra el dialogo y abre el ajuste del sistema`() = runTest { // R-15.5, R-11.2
        checker.exactAlarms = false
        categories.used.value = listOf("medicación")
        val vm = subscribed(viewModel())
        vm.onAction(SettingsAction.CriticalToggled("medicación", critical = true))

        vm.effects.test {
            vm.onAction(SettingsAction.ExactAlarmsOpenSettings)

            assertThat(awaitItem()).isEqualTo(SettingsEffect.OpenExactAlarmSettings)
        }
        assertThat(vm.uiState.value.showExactAlarmsDialog).isFalse()
    }

    @Test
    fun `Ahora no cierra el dialogo y deja la categoria critica`() = runTest { // R-15.5
        checker.exactAlarms = false
        categories.used.value = listOf("medicación")
        val vm = subscribed(viewModel())
        vm.onAction(SettingsAction.CriticalToggled("medicación", critical = true))

        vm.onAction(SettingsAction.ExactAlarmsDialogDismissed)

        assertThat(vm.uiState.value.showExactAlarmsDialog).isFalse()
        assertThat(vm.uiState.value.categories.single().critical).isTrue()
    }

    @Test
    fun `avisa mientras haya una critica sin alarmas exactas`() = runTest { // R-15.4
        checker.exactAlarms = false
        categories.used.value = listOf("medicación")
        val vm = subscribed(viewModel())

        assertThat(vm.uiState.value.showExactAlarmsWarning).isFalse() // nothing critical yet

        vm.onAction(SettingsAction.CriticalToggled("medicación", critical = true))
        assertThat(vm.uiState.value.showExactAlarmsWarning).isTrue()

        checker.exactAlarms = true
        vm.onAction(SettingsAction.Resumed)
        assertThat(vm.uiState.value.showExactAlarmsWarning).isFalse()
    }

    @Test
    fun `al volver con las exactas concedidas reprograma todo`() = runTest { // R-11.6, R-15.4
        checker.exactAlarms = false
        orders.add(testOrder(id = 1, date = date))
        alarms.add(testAlarm(id = 10, time = LocalTime.of(14, 0), category = "paseo"))
        val vm = subscribed(viewModel())

        checker.exactAlarms = true
        vm.onAction(SettingsAction.Resumed)

        assertThat(scheduler.scheduled.keys).containsExactly(10L)
    }

    @Test
    fun `volver sin cambios en el permiso no reprograma`() = runTest { // R-11.6
        orders.add(testOrder(id = 1, date = date))
        alarms.add(testAlarm(id = 10, time = LocalTime.of(14, 0)))
        val vm = subscribed(viewModel())

        vm.onAction(SettingsAction.Resumed)

        assertThat(scheduler.scheduled).isEmpty()
    }

    @Test
    fun `el diagnostico empieza apagado y se puede activar`() = runTest { // R-17.1
        val vm = subscribed(viewModel())
        assertThat(vm.uiState.value.diagnosticsEnabled).isFalse()

        vm.onAction(SettingsAction.DiagnosticsToggled(true))

        assertThat(vm.uiState.value.diagnosticsEnabled).isTrue()
        assertThat(diagnostics.isEnabled()).isTrue()
    }

    @Test
    fun `desactivarlo borra los contadores`() = runTest { // R-17.7
        diagnostics.setEnabled(true)
        diagnostics.record(DiagnosticEvent.PlanCreated)
        val vm = subscribed(viewModel())

        vm.onAction(SettingsAction.DiagnosticsToggled(false))

        assertThat(diagnostics.counts).isEmpty()
    }

    @Test
    fun `exportar comparte los contadores con la version de la app`() = runTest { // R-17.5
        diagnostics.setEnabled(true)
        diagnostics.record(DiagnosticEvent.Step.VALIDATE)
        val vm = subscribed(viewModel())

        vm.effects.test {
            vm.onAction(SettingsAction.ExportDiagnostics)

            assertThat(awaitItem()).isEqualTo(SettingsEffect.ShareDiagnostics(DiagnosticsReport("1.3", 13L, mapOf("step.validate" to 1))))
        }
    }

    @Test
    fun `borrar pone a cero y avisa Diagnostico borrado`() = runTest { // R-17.6
        diagnostics.setEnabled(true)
        diagnostics.record(DiagnosticEvent.PlanCreated)
        val vm = subscribed(viewModel())

        vm.effects.test {
            vm.onAction(SettingsAction.ClearDiagnostics)

            val effect = awaitItem() as SettingsEffect.ShowMessage
            assertThat(effect.message.es()).isEqualTo("Diagnóstico borrado")
        }
        assertThat(diagnostics.counts).isEmpty()
        assertThat(diagnostics.isEnabled()).isTrue()
    }
}
