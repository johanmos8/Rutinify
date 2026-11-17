package com.mirkwood.rutinify.ui.alarm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirkwood.rutinify.di.ApplicationScope
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderDetail
import com.mirkwood.rutinify.domain.scheduler.SilencedAlarms
import com.mirkwood.rutinify.domain.usecase.MarkAlarmDoneUseCase
import com.mirkwood.rutinify.domain.usecase.ObserveOrderDetailUseCase
import com.mirkwood.rutinify.domain.usecase.SilenceAlarmUseCase
import com.mirkwood.rutinify.domain.usecase.SnoozeAlarmUseCase
import com.mirkwood.rutinify.ui.common.DateFormats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlarmUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val description: String? = null,
    /** "13:20" (R-15.7). */
    val timeLabel: String = "",
    val planName: String = "",
    /** The screen must go away: answered, silenced, deleted or no longer ringing (R-15.8–R-15.10). */
    val closed: Boolean = false,
)

sealed interface AlarmAction {
    data object Done : AlarmAction
    data object Snooze : AlarmAction
    data object Silence : AlarmAction
}

/** Alarm screen of a ringing critical alarm (R-15.7–R-15.10, D-67). */
@HiltViewModel
class AlarmViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeOrderDetail: ObserveOrderDetailUseCase,
    silencedAlarms: SilencedAlarms,
    private val markDone: MarkAlarmDoneUseCase,
    private val snooze: SnoozeAlarmUseCase,
    private val silence: SilenceAlarmUseCase,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val alarmId: Long = savedStateHandle[AlarmActivity.EXTRA_ALARM_ID] ?: NO_ID
    private val orderId: Long = savedStateHandle[AlarmActivity.EXTRA_ORDER_ID] ?: NO_ID

    /** Set as soon as a button is pressed, so a double tap does nothing more. */
    private val answered = MutableStateFlow(false)

    val uiState: StateFlow<AlarmUiState> =
        combine(observeOrderDetail(orderId), silencedAlarms.observe(), answered) { detail, silenced, done ->
            toUi(detail, closed = done || alarmId in silenced)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlarmUiState())

    fun onAction(action: AlarmAction) {
        if (answered.value) return
        answered.value = true
        // The screen closes as soon as Room changes: the application scope lets the use case finish (cancelling the
        // notification, the cutoff) after this ViewModel is cleared
        appScope.launch {
            when (action) {
                AlarmAction.Done -> markDone(alarmId) // R-7.4
                AlarmAction.Snooze -> snooze(alarmId) // R-7.5
                AlarmAction.Silence -> silence(alarmId) // R-15.9
            }
        }
    }

    private fun toUi(detail: OrderDetail?, closed: Boolean): AlarmUiState {
        val alarm = detail?.timeline?.firstOrNull { it.alarm.id == alarmId }?.alarm
        if (detail == null || alarm == null || alarm.status != AlarmStatus.FIRED) {
            return AlarmUiState(isLoading = false, closed = true)
        }
        return AlarmUiState(
            isLoading = false,
            title = alarm.title,
            description = alarm.description,
            timeLabel = alarm.time.format(DateFormats.TIME),
            planName = detail.order.name,
            closed = closed,
        )
    }

    private companion object {
        const val NO_ID = -1L
    }
}
