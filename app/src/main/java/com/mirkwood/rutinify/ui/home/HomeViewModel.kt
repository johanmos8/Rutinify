package com.mirkwood.rutinify.ui.home

import com.mirkwood.rutinify.ui.common.RecurrenceLabels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirkwood.rutinify.domain.model.OrderDisplayStatus
import com.mirkwood.rutinify.domain.model.NextAlarm
import com.mirkwood.rutinify.domain.model.OrderSummary
import com.mirkwood.rutinify.domain.time.ZoneProvider
import com.mirkwood.rutinify.domain.usecase.MarkAlarmDoneUseCase
import com.mirkwood.rutinify.domain.usecase.ObserveNextAlarmUseCase
import com.mirkwood.rutinify.ui.widget.NextAlarmText
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import com.mirkwood.rutinify.domain.usecase.ExactAlarmsCardUseCase
import com.mirkwood.rutinify.domain.usecase.GetPermissionStatusUseCase
import com.mirkwood.rutinify.domain.usecase.ObserveCategorySettingsUseCase
import com.mirkwood.rutinify.domain.usecase.ObserveOrderSummariesUseCase
import com.mirkwood.rutinify.ui.common.SpanLabels
import com.mirkwood.rutinify.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** One order card on Home (R-8.2). */
data class OrderCardUi(
    val id: Long,
    val name: String,
    /** "jueves 5 de noviembre de 2026" or "del sábado 14 al viernes 20 de noviembre de 2026" (R-8.2). */
    val dateLabel: UiText,
    val pending: Int,
    val total: Int,
    val status: OrderDisplayStatus,
)

/** R-8.7: the next pending alarm of all active plans, at the top of Home. */
data class NextAlarmUi(
    val alarmId: Long,
    val orderId: Long,
    val title: String,
    val orderName: String,
    /** "14:00", "02:00 · mañana" or "06:00 · lunes 16 de noviembre" (same text as the widget). */
    val time: UiText,
)

data class HomeUiState(
    /** True until the first list arrives, so the empty state does not flash on launch. */
    val isLoading: Boolean = true,
    val orders: List<OrderCardUi> = emptyList(),
    val nextAlarm: NextAlarmUi? = null,
    /** Persistent banner to the Permissions screen while any permission is missing (R-11.3). */
    val missingPermissions: Boolean = false,
    /** R-11.2, D-89: asks for exact alarms after the first plan; takes the banner's place while it is the only gap. */
    val showExactAlarmsCard: Boolean = false,
) {
    /** R-8.5: the 3-step explanation, only once we know there are no orders. */
    val showEmptyState: Boolean get() = !isLoading && orders.isEmpty()

    /** R-8.1: "En curso" (active and paused), in the repository's order. */
    val ongoingOrders: List<OrderCardUi> get() = orders.filter { it.status.isOngoing }

    /** R-8.1: "Anteriores" (finished and cancelled), in the repository's order. */
    val pastOrders: List<OrderCardUi> get() = orders.filterNot { it.status.isOngoing }
}

private val OrderDisplayStatus.isOngoing: Boolean
    get() = this == OrderDisplayStatus.ACTIVE || this == OrderDisplayStatus.PAUSED

sealed interface HomeAction {
    /** Back in the foreground: permissions may have changed in system settings (R-11.5). */
    data object Resumed : HomeAction

    /** "Activar" on the exact-alarm card (D-89). */
    data object ExactAlarmsCardEnable : HomeAction

    /** "Ahora no" on the exact-alarm card: it does not come back; the banner stays (D-89). */
    data object ExactAlarmsCardDismiss : HomeAction

    /** "Hecho" on the next-alarm card (R-8.7, R-7.4). */
    data class MarkNextDone(val alarmId: Long) : HomeAction
}

sealed interface HomeEffect {
    data object OpenExactAlarmSettings : HomeEffect
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeOrderSummaries: ObserveOrderSummariesUseCase,
    observeCategorySettings: ObserveCategorySettingsUseCase,
    private val getPermissionStatus: GetPermissionStatusUseCase,
    private val exactAlarmsCard: ExactAlarmsCardUseCase,
    observeNextAlarm: ObserveNextAlarmUseCase,
    private val markAlarmDone: MarkAlarmDoneUseCase,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
) : ViewModel() {

    private val permissions = MutableStateFlow(getPermissionStatus())

    /** Bumped when the card may change (resume, dismiss), so its stored state is read again. */
    private val cardRefresh = MutableStateFlow(0)

    private val effectChannel = Channel<HomeEffect>(Channel.BUFFERED)
    val effects: Flow<HomeEffect> = effectChannel.receiveAsFlow()

    /** Follows Room, so any order or alarm change refreshes the list (R-8.6). */
    val uiState: StateFlow<HomeUiState> =
        combine(
            observeOrderSummaries(),
            observeCategorySettings(),
            permissions,
            cardRefresh,
            observeNextAlarm(),
        ) { summaries, categories, status, _, next ->
            // D-88: full screen only matters once some category is critical (R-15.11)
            val missingFullScreen = !status.fullScreenIntent && categories.any { it.critical }
            val card = exactAlarmsCard.shouldShow(hasPlans = summaries.isNotEmpty())
            // D-89: the card already says what is missing; the banner comes back for anything else
            val onlyExactMissing = status.notifications && !missingFullScreen
            HomeUiState(
                isLoading = false,
                orders = summaries.map(::toCard),
                nextAlarm = next?.let(::toNextAlarm),
                missingPermissions = (!status.allGranted || missingFullScreen) && !(card && onlyExactMissing),
                showExactAlarmsCard = card,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.Resumed -> {
                permissions.value = getPermissionStatus()
                cardRefresh.value++
            }
            HomeAction.ExactAlarmsCardEnable -> effectChannel.trySend(HomeEffect.OpenExactAlarmSettings)
            HomeAction.ExactAlarmsCardDismiss -> {
                exactAlarmsCard.dismiss()
                cardRefresh.value++
            }
            // Room changes and the card follows ObserveNextAlarmUseCase to the alarm after it
            is HomeAction.MarkNextDone -> viewModelScope.launch { markAlarmDone(action.alarmId) }
        }
    }

    private fun toNextAlarm(next: NextAlarm): NextAlarmUi {
        val zone = zoneProvider.zone()
        return NextAlarmUi(
            alarmId = next.alarmId,
            orderId = next.orderId,
            title = next.title,
            orderName = next.orderName,
            time = NextAlarmText.time(next, LocalDate.now(clock.withZone(zone)), zone),
        )
    }

    private fun toCard(summary: OrderSummary) = OrderCardUi(
        id = summary.order.id,
        name = summary.order.name,
        dateLabel = summary.order.recurrence?.let { RecurrenceLabels.label(it, summary.order.date) } ?: SpanLabels.long(summary.span),
        pending = summary.pending,
        total = summary.total,
        status = summary.displayStatus,
    )
}
