package com.mirkwood.rutinify.ui.orderdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderDetail
import com.mirkwood.rutinify.domain.model.OrderDisplayStatus
import com.mirkwood.rutinify.domain.model.TimelineEntry
import com.mirkwood.rutinify.domain.time.ZoneProvider
import com.mirkwood.rutinify.domain.usecase.CanScheduleExactAlarmsUseCase
import com.mirkwood.rutinify.domain.usecase.ObserveOrderDetailUseCase
import com.mirkwood.rutinify.ui.common.DateFormats
import com.mirkwood.rutinify.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** One alarm on the timeline (R-9.1). */
data class TimelineItemUi(
    val id: Long,
    /** Effective local time, "06:45" (a snoozed alarm shows its new time). */
    val time: String,
    /** Rings the day after the order date: shown as "(+1 día)". */
    val nextDay: Boolean,
    val title: String,
    val description: String?,
    val category: String,
    val status: AlarmStatus,
    /** Next pending alarm (R-9.2). */
    val isNext: Boolean,
    /** Opened from this alarm's notification (R-7.6). */
    val isHighlighted: Boolean,
)

data class OrderDetailUiState(
    val isLoading: Boolean = true,
    /** The order was deleted (or a stale notification pointed to it). */
    val notFound: Boolean = false,
    val name: String = "",
    val dateLabel: String = "",
    val notes: String? = null,
    val status: OrderDisplayStatus = OrderDisplayStatus.ACTIVE,
    val pending: Int = 0,
    val total: Int = 0,
    val timeline: List<TimelineItemUi> = emptyList(),
    /** Timeline index to bring into view: the highlighted alarm, else the next one. */
    val scrollToIndex: Int? = null,
    /** "Las alarmas pueden retrasarse unos minutos…" (R-5.4). */
    val windowedWarning: Boolean = false,
)

sealed interface OrderDetailAction {
    /** Screen back in the foreground: the exact-alarm permission may have changed in Settings. */
    data object Resumed : OrderDetailAction
}

sealed interface OrderDetailEffect {
    data class ShowMessage(val message: UiText) : OrderDetailEffect
}

@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    observeOrderDetail: ObserveOrderDetailUseCase,
    private val canScheduleExactAlarms: CanScheduleExactAlarmsUseCase,
    private val zoneProvider: ZoneProvider,
) : ViewModel() {

    // Argument names of OrderDetailDestination (type-safe navigation stores them under those keys)
    private val orderId: Long = savedStateHandle.get<Long>(KEY_ORDER_ID) ?: 0L
    private val highlightAlarmId: Long? = savedStateHandle.get<Long>(KEY_HIGHLIGHT_ALARM_ID)

    private val exactAllowed = MutableStateFlow(canScheduleExactAlarms())

    private val effectChannel = Channel<OrderDetailEffect>(Channel.BUFFERED)
    val effects: Flow<OrderDetailEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<OrderDetailUiState> =
        combine(observeOrderDetail(orderId), exactAllowed) { detail, exact ->
            detail?.let { toUiState(it, exact) } ?: OrderDetailUiState(isLoading = false, notFound = true)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrderDetailUiState())

    init {
        announceScheduled()
    }

    fun onAction(action: OrderDetailAction) {
        when (action) {
            OrderDetailAction.Resumed -> exactAllowed.value = canScheduleExactAlarms()
        }
    }

    /** "{n} alarmas programadas" once per arrival from Review, surviving recreation (R-5.6). */
    private fun announceScheduled() {
        val count = savedStateHandle.get<Int>(KEY_SCHEDULED_COUNT) ?: return
        if (savedStateHandle.get<Boolean>(KEY_SCHEDULED_SHOWN) == true) return
        savedStateHandle[KEY_SCHEDULED_SHOWN] = true
        effectChannel.trySend(
            OrderDetailEffect.ShowMessage(UiText.Plural(R.plurals.order_detail_scheduled, count, listOf(count))),
        )
    }

    private fun toUiState(detail: OrderDetail, exactAllowed: Boolean): OrderDetailUiState {
        val highlighted = highlightAlarmId?.takeIf { id -> detail.timeline.any { it.alarm.id == id } }
        val timeline = detail.timeline.map { toItem(it, detail, highlighted) }
        val scrollTarget = highlighted ?: detail.nextAlarmId
        return OrderDetailUiState(
            isLoading = false,
            name = detail.order.name,
            dateLabel = detail.order.date.format(DateFormats.LONG_DATE),
            notes = detail.order.notes,
            status = detail.displayStatus,
            pending = detail.pending,
            total = detail.total,
            timeline = timeline,
            scrollToIndex = timeline.indexOfFirst { it.id == scrollTarget }.takeIf { it >= 0 },
            // Only while something is still going to ring (D-46)
            windowedWarning = !exactAllowed && detail.nextAlarmId != null,
        )
    }

    private fun toItem(entry: TimelineEntry, detail: OrderDetail, highlighted: Long?): TimelineItemUi {
        val local = entry.triggerAt.atZone(zoneProvider.zone())
        val alarm = entry.alarm
        return TimelineItemUi(
            id = alarm.id,
            time = local.format(DateFormats.TIME),
            nextDay = local.toLocalDate().isAfter(detail.order.date),
            title = alarm.title,
            description = alarm.description,
            category = alarm.category,
            status = alarm.status,
            isNext = alarm.id == detail.nextAlarmId,
            isHighlighted = alarm.id == highlighted,
        )
    }

    private companion object {
        const val KEY_ORDER_ID = "orderId"
        const val KEY_HIGHLIGHT_ALARM_ID = "highlightAlarmId"
        const val KEY_SCHEDULED_COUNT = "scheduledCount"
        const val KEY_SCHEDULED_SHOWN = "scheduledCountShown"
    }
}
