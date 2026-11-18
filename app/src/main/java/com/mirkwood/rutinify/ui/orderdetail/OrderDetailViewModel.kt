package com.mirkwood.rutinify.ui.orderdetail

import kotlinx.coroutines.Job
import com.mirkwood.rutinify.domain.usecase.ShiftResult
import com.mirkwood.rutinify.domain.usecase.ShiftOrderAlarmsUseCase
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.domain.model.AlarmItem
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderDetail
import com.mirkwood.rutinify.domain.model.OrderDisplayStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.model.TimelineEntry
import com.mirkwood.rutinify.domain.time.ZoneProvider
import com.mirkwood.rutinify.domain.usecase.CanScheduleExactAlarmsUseCase
import com.mirkwood.rutinify.domain.usecase.CancelOrderAlarmsUseCase
import com.mirkwood.rutinify.domain.usecase.DeleteAlarmUseCase
import com.mirkwood.rutinify.domain.usecase.DeleteOrderUseCase
import com.mirkwood.rutinify.domain.usecase.DuplicateOrderUseCase
import com.mirkwood.rutinify.domain.usecase.MarkAlarmDoneUseCase
import com.mirkwood.rutinify.domain.usecase.ObserveOrderDetailUseCase
import com.mirkwood.rutinify.domain.usecase.PauseOrderUseCase
import com.mirkwood.rutinify.domain.usecase.RestoreAlarmUseCase
import com.mirkwood.rutinify.domain.usecase.ResumeOrderUseCase
import com.mirkwood.rutinify.ui.common.DateFormats
import com.mirkwood.rutinify.ui.common.SpanLabels
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
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** One alarm on the timeline (R-9.1). */
data class TimelineItemUi(
    val id: Long,
    /** Effective local time, "06:45" (a snoozed alarm shows its new time). */
    val time: String,
    /** Rings the day after the order date: shown as "(+1 día)"; never in a multi-day order (R-9.6). */
    val nextDay: Boolean,
    val title: String,
    val description: String?,
    val category: String,
    val status: AlarmStatus,
    /** Next pending alarm (R-9.2). */
    val isNext: Boolean,
    /** Opened from this alarm's notification (R-7.6). */
    val isHighlighted: Boolean,
    /** "Marcar como hecha" is offered for PENDING and FIRED alarms (R-9.5). */
    val canMarkDone: Boolean = status == AlarmStatus.PENDING || status == AlarmStatus.FIRED,
    /** "Día 3 · lunes 16 de noviembre" above the first alarm of each day of a multi-day order (R-9.6). */
    val dayTitle: UiText? = null,
)

data class OrderDetailUiState(
    val isLoading: Boolean = true,
    /** The order was deleted (or a stale notification pointed to it). */
    val notFound: Boolean = false,
    val name: String = "",
    /** Date, or the range of a multi-day order (R-8.2). */
    val dateLabel: UiText = UiText.Plain(""),
    val notes: String? = null,
    val status: OrderDisplayStatus = OrderDisplayStatus.ACTIVE,
    val pending: Int = 0,
    val total: Int = 0,
    val timeline: List<TimelineItemUi> = emptyList(),
    /** Timeline index to bring into view: the highlighted alarm, else the next one. */
    val scrollToIndex: Int? = null,
    /** "Las alarmas pueden retrasarse unos minutos…" (R-5.4). */
    val windowedWarning: Boolean = false,
    /** "Eliminar todas las alarmas" is hidden once the order is cancelled (R-10.8). */
    val canCancelAll: Boolean = false,
    /** Which of "Pausar" / "Reanudar" the header offers; none for cancelled or finished orders (R-10.8, D-49). */
    val pauseAction: PauseAction? = null,
    /** "Duplicar para otra fecha" needs at least one activity to copy (R-10.7, D-50); kept for cancelled orders (R-10.8). */
    val canDuplicate: Boolean = false,
    /** Non-null while the date picker of [OrderDetailAction.DuplicateClicked] is open: its preselected date as UTC-midnight millis. */
    val duplicateDateMillis: Long? = null,
    val dialog: OrderDetailDialog? = null,
    /** "Voy tarde" in the header: an ACTIVE or PAUSED plan with pending alarms (R-18.1). */
    val canShift: Boolean = false,
    /** Non-null while the "Voy tarde" dialog is open (R-18.2). */
    val shiftDialog: ShiftDialogUi? = null,
)

enum class PauseAction { PAUSE, RESUME }

/** "Voy tarde" dialog: direction, minutes as typed, and the preview or the reason it cannot move (R-18.2, R-18.5, R-18.6). */
data class ShiftDialogUi(
    val delay: Boolean = true,
    val minutes: String = "",
    val message: UiText? = null,
    val isError: Boolean = false,
    val canConfirm: Boolean = false,
)

/** Confirmations before destructive order actions. */
sealed interface OrderDetailDialog {
    /** "Eliminar todas las alarmas", saying how many pending alarms will be cancelled (R-10.1). */
    data class CancelAll(val message: UiText) : OrderDetailDialog

    /** "Eliminar plan" (R-10.4). */
    data class DeleteOrder(val message: UiText) : OrderDetailDialog
}

sealed interface OrderDetailAction {
    /** Screen back in the foreground: the exact-alarm permission may have changed in Settings. */
    data object Resumed : OrderDetailAction

    /** R-9.5 */
    data class MarkDone(val alarmId: Long) : OrderDetailAction

    /** R-9.3 */
    data class Delete(val alarmId: Long) : OrderDetailAction

    /** "Deshacer" on the snackbar of [Delete] (R-9.4). */
    data class UndoDelete(val alarmId: Long) : OrderDetailAction

    /** Opens the R-10.1 confirmation. */
    data object CancelAllClicked : OrderDetailAction

    /** Opens the R-10.4 confirmation. */
    data object DeleteOrderClicked : OrderDetailAction

    /** R-10.5; reversible, so no confirmation. */
    data object PauseClicked : OrderDetailAction

    /** R-10.6 */
    data object ResumeClicked : OrderDetailAction

    /** Opens the date picker of "Duplicar para otra fecha" (R-10.7). */
    data object DuplicateClicked : OrderDetailAction

    /** Date picked, as UTC-midnight millis: create the draft and open Review. */
    data class DuplicateDateSelected(val utcMillis: Long) : OrderDetailAction

    data object DismissDuplicate : OrderDetailAction

    data object ConfirmDialog : OrderDetailAction
    data object DismissDialog : OrderDetailAction

    /** "Voy tarde" (R-18.1). */
    data object ShiftClicked : OrderDetailAction

    /** "Retrasar" (true) or "Adelantar" (false). */
    data class ShiftDirectionChanged(val delay: Boolean) : OrderDetailAction

    data class ShiftMinutesChanged(val text: String) : OrderDetailAction

    data object ShiftConfirmed : OrderDetailAction

    data object ShiftDismissed : OrderDetailAction
}

sealed interface OrderDetailEffect {
    data class ShowMessage(val message: UiText) : OrderDetailEffect

    /** "Alarma eliminada" with "Deshacer", which sends [OrderDetailAction.UndoDelete] for [alarmId]. */
    data class ShowUndo(val message: UiText, val alarmId: Long) : OrderDetailEffect

    /** The order was deleted: leave the screen. */
    data object NavigateBack : OrderDetailEffect

    /** Review of a duplicated draft (R-10.7). */
    data class NavigateToReview(val draftId: String) : OrderDetailEffect
}

@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    observeOrderDetail: ObserveOrderDetailUseCase,
    private val canScheduleExactAlarms: CanScheduleExactAlarmsUseCase,
    private val markAlarmDone: MarkAlarmDoneUseCase,
    private val deleteAlarm: DeleteAlarmUseCase,
    private val restoreAlarm: RestoreAlarmUseCase,
    private val cancelOrderAlarms: CancelOrderAlarmsUseCase,
    private val deleteOrder: DeleteOrderUseCase,
    private val pauseOrder: PauseOrderUseCase,
    private val resumeOrder: ResumeOrderUseCase,
    private val duplicateOrder: DuplicateOrderUseCase,
    private val shiftOrderAlarms: ShiftOrderAlarmsUseCase,
    private val zoneProvider: ZoneProvider,
    private val clock: Clock,
) : ViewModel() {

    // Argument names of OrderDetailDestination (type-safe navigation stores them under those keys)
    private val orderId: Long = savedStateHandle.get<Long>(KEY_ORDER_ID) ?: 0L
    private val highlightAlarmId: Long? = savedStateHandle.get<Long>(KEY_HIGHLIGHT_ALARM_ID)

    private val exactAllowed = MutableStateFlow(canScheduleExactAlarms())
    private val dialog = MutableStateFlow<OrderDetailDialog?>(null)
    private val duplicateDate = MutableStateFlow<Long?>(null)
    private val shiftDialog = MutableStateFlow<ShiftDialogUi?>(null)
    private var shiftPreviewJob: Job? = null

    /** Set while the order is being deleted, so the screen does not flash "Esta rutina ya no existe". */
    private var leaving = false
    private var lastShown: OrderDetailUiState? = null

    /** Deleted alarms that can still be restored, by id; only lives while the screen does. */
    private val deleted = mutableMapOf<Long, AlarmItem>()

    private val effectChannel = Channel<OrderDetailEffect>(Channel.BUFFERED)
    val effects: Flow<OrderDetailEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<OrderDetailUiState> =
        combine(observeOrderDetail(orderId), exactAllowed, dialog, duplicateDate, shiftDialog) { detail, exact, dialog, duplicateDate, shift ->
            val state = when {
                detail != null -> toUiState(detail, exact).copy(dialog = dialog, duplicateDateMillis = duplicateDate, shiftDialog = shift)
                leaving -> lastShown?.copy(dialog = null) ?: OrderDetailUiState()
                else -> OrderDetailUiState(isLoading = false, notFound = true)
            }
            state.also { lastShown = it }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrderDetailUiState())

    init {
        announceScheduled()
    }

    fun onAction(action: OrderDetailAction) {
        when (action) {
            OrderDetailAction.Resumed -> exactAllowed.value = canScheduleExactAlarms()
            is OrderDetailAction.MarkDone -> viewModelScope.launch { markAlarmDone(action.alarmId) }
            is OrderDetailAction.Delete -> viewModelScope.launch { delete(action.alarmId) }
            is OrderDetailAction.UndoDelete -> viewModelScope.launch { undoDelete(action.alarmId) }
            OrderDetailAction.CancelAllClicked -> dialog.value = cancelAllDialog(uiState.value.pending)
            OrderDetailAction.DeleteOrderClicked ->
                dialog.value = OrderDetailDialog.DeleteOrder(UiText.Res(R.string.delete_order_body, listOf(uiState.value.name)))
            OrderDetailAction.PauseClicked -> viewModelScope.launch { pauseOrder(orderId) }
            OrderDetailAction.ResumeClicked -> viewModelScope.launch { resume() }
            OrderDetailAction.DuplicateClicked -> duplicateDate.value = tomorrowUtcMillis()
            is OrderDetailAction.DuplicateDateSelected -> duplicate(action.utcMillis)
            OrderDetailAction.DismissDuplicate -> duplicateDate.value = null
            OrderDetailAction.ConfirmDialog -> confirm()
            OrderDetailAction.DismissDialog -> dialog.value = null
            OrderDetailAction.ShiftClicked -> updateShift(ShiftDialogUi())
            is OrderDetailAction.ShiftDirectionChanged -> shiftDialog.value?.let { updateShift(it.copy(delay = action.delay)) }
            is OrderDetailAction.ShiftMinutesChanged ->
                shiftDialog.value?.let { updateShift(it.copy(minutes = action.text.filter(Char::isDigit).take(MINUTES_DIGITS))) }
            OrderDetailAction.ShiftConfirmed -> confirmShift()
            OrderDetailAction.ShiftDismissed -> {
                shiftPreviewJob?.cancel()
                shiftDialog.value = null
            }
        }
    }

    private fun cancelAllDialog(pending: Int) = OrderDetailDialog.CancelAll(
        if (pending == 0) {
            UiText.Res(R.string.cancel_all_body_none)
        } else {
            UiText.Plural(R.plurals.cancel_all_body, pending, listOf(pending))
        },
    )

    private fun confirm() {
        val confirmed = dialog.value ?: return
        dialog.value = null
        viewModelScope.launch {
            when (confirmed) {
                is OrderDetailDialog.CancelAll -> cancelOrderAlarms(orderId)
                is OrderDetailDialog.DeleteOrder -> {
                    leaving = true
                    deleteOrder(orderId)
                    effectChannel.send(OrderDetailEffect.NavigateBack)
                }
            }
        }
    }

    /** The picker opens on tomorrow, the usual reason to repeat a routine (D-50). */
    private fun tomorrowUtcMillis(): Long =
        clock.instant().atZone(zoneProvider.zone()).toLocalDate().plusDays(1)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun duplicate(utcMillis: Long) {
        duplicateDate.value = null
        val date = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        viewModelScope.launch {
            val draftId = duplicateOrder(orderId, date) ?: return@launch
            effectChannel.send(OrderDetailEffect.NavigateToReview(draftId))
        }
    }

    /** Says how many alarms were skipped because their time passed while paused (D-49). */
    private suspend fun resume() {
        val skipped = resumeOrder(orderId)?.skipped ?: return
        if (skipped > 0) {
            effectChannel.send(
                OrderDetailEffect.ShowMessage(UiText.Plural(R.plurals.order_detail_resumed_skipped, skipped, listOf(skipped))),
            )
        }
    }

    private suspend fun delete(alarmId: Long) {
        val alarm = deleteAlarm(alarmId) ?: return
        deleted[alarmId] = alarm
        effectChannel.send(OrderDetailEffect.ShowUndo(UiText.Res(R.string.order_detail_alarm_deleted), alarmId))
    }

    private suspend fun undoDelete(alarmId: Long) {
        val alarm = deleted.remove(alarmId) ?: return
        restoreAlarm(alarm)
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
        val multiDay = detail.span.isMultiDay
        var lastDay: LocalDate? = null
        val timeline = detail.timeline.map { entry ->
            val day = entry.triggerAt.atZone(zoneProvider.zone()).toLocalDate()
            val title = if (multiDay && day != lastDay) {
                SpanLabels.dayHeader(ChronoUnit.DAYS.between(detail.order.date, day).toInt(), day)
            } else {
                null
            }
            lastDay = day
            toItem(entry, detail, highlighted, multiDay, title)
        }
        val scrollTarget = highlighted ?: detail.nextAlarmId
        return OrderDetailUiState(
            isLoading = false,
            name = detail.order.name,
            dateLabel = SpanLabels.long(detail.span),
            notes = detail.order.notes,
            status = detail.displayStatus,
            pending = detail.pending,
            total = detail.total,
            timeline = timeline,
            scrollToIndex = timeline.indexOfFirst { it.id == scrollTarget }.takeIf { it >= 0 },
            // Only while something is still going to ring (D-46)
            windowedWarning = !exactAllowed && detail.nextAlarmId != null,
            canCancelAll = detail.order.status != OrderStatus.CANCELLED,
            canDuplicate = detail.total > 0,
            canShift = detail.pending > 0 &&
                (detail.order.status == OrderStatus.ACTIVE || detail.order.status == OrderStatus.PAUSED),
            pauseAction = when (detail.displayStatus) {
                OrderDisplayStatus.ACTIVE -> PauseAction.PAUSE
                OrderDisplayStatus.PAUSED -> PauseAction.RESUME
                OrderDisplayStatus.CANCELLED, OrderDisplayStatus.FINISHED -> null
            },
        )
    }

    private fun toItem(
        entry: TimelineEntry,
        detail: OrderDetail,
        highlighted: Long?,
        multiDay: Boolean,
        dayTitle: UiText?,
    ): TimelineItemUi {
        val local = entry.triggerAt.atZone(zoneProvider.zone())
        val alarm = entry.alarm
        return TimelineItemUi(
            id = alarm.id,
            time = local.format(DateFormats.TIME),
            nextDay = !multiDay && local.toLocalDate().isAfter(detail.order.date),
            title = alarm.title,
            description = alarm.description,
            category = alarm.category,
            status = alarm.status,
            isNext = alarm.id == detail.nextAlarmId,
            isHighlighted = alarm.id == highlighted,
            dayTitle = dayTitle,
        )
    }

    /** Shows [dialog] at once, then fills in the preview for its minutes (R-18.2). */
    private fun updateShift(dialog: ShiftDialogUi) {
        shiftPreviewJob?.cancel()
        val minutes = signedMinutes(dialog)
        if (minutes == null) {
            val invalid = dialog.minutes.isNotEmpty()
            shiftDialog.value = dialog.copy(
                message = if (invalid) UiText.Res(R.string.shift_minutes_invalid) else null,
                isError = invalid,
                canConfirm = false,
            )
            return
        }
        shiftDialog.value = dialog.copy(canConfirm = false)
        shiftPreviewJob = viewModelScope.launch {
            val preview = shiftOrderAlarms.preview(orderId, minutes)
            shiftDialog.value = shiftDialog.value?.takeIf { it.minutes == dialog.minutes && it.delay == dialog.delay }
                ?.withResult(preview) ?: return@launch
        }
    }

    private fun confirmShift() {
        val dialog = shiftDialog.value ?: return
        val minutes = signedMinutes(dialog) ?: return
        if (!dialog.canConfirm) return
        shiftPreviewJob?.cancel()
        viewModelScope.launch {
            when (val result = shiftOrderAlarms(orderId, minutes)) {
                is ShiftResult.Ok -> {
                    shiftDialog.value = null
                    effectChannel.send(
                        OrderDetailEffect.ShowMessage(UiText.Plural(R.plurals.shift_done, result.count, listOf(result.count))),
                    )
                }
                // The plan changed since the preview: show why instead of closing (R-18.5, R-18.6)
                else -> shiftDialog.value = shiftDialog.value?.withResult(result)
            }
        }
    }

    /** Minutes with their sign (negative = earlier), or null outside 1..720 (R-18.2). */
    private fun signedMinutes(dialog: ShiftDialogUi): Int? {
        val value = dialog.minutes.toIntOrNull()?.takeIf { it in SHIFT_MINUTES } ?: return null
        return if (dialog.delay) value else -value
    }

    private fun ShiftDialogUi.withResult(result: ShiftResult): ShiftDialogUi = when (result) {
        is ShiftResult.Ok -> if (result.count == 0 || result.nextFrom == null || result.nextTo == null) {
            copy(message = UiText.Res(R.string.shift_nothing), isError = true, canConfirm = false)
        } else {
            copy(
                message = UiText.Plural(
                    R.plurals.shift_preview,
                    result.count,
                    listOf(result.count, result.nextFrom.format(DateFormats.TIME), result.nextTo.format(DateFormats.TIME)),
                ),
                isError = false,
                canConfirm = true,
            )
        }
        is ShiftResult.InPast -> copy(message = UiText.Plural(R.plurals.shift_in_past, result.count, listOf(result.count)), isError = true, canConfirm = false)
        ShiftResult.OutOfRange -> copy(message = UiText.Res(R.string.shift_out_of_range), isError = true, canConfirm = false)
        ShiftResult.NotAllowed -> copy(message = UiText.Res(R.string.shift_nothing), isError = true, canConfirm = false)
    }

    private companion object {
        /** R-18.2: from 1 minute to 12 hours. */
        val SHIFT_MINUTES = 1..720
        const val MINUTES_DIGITS = 3

        const val KEY_ORDER_ID = "orderId"
        const val KEY_HIGHLIGHT_ALARM_ID = "highlightAlarmId"
        const val KEY_SCHEDULED_COUNT = "scheduledCount"
        const val KEY_SCHEDULED_SHOWN = "scheduledCountShown"
    }
}
