package com.mirkwood.rutinify.ui.review

import com.mirkwood.rutinify.domain.diagnostics.DiagnosticsRecorder
import com.mirkwood.rutinify.domain.diagnostics.DiagnosticEvent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.domain.importer.DraftItemRules
import com.mirkwood.rutinify.domain.importer.ValidationIssue
import com.mirkwood.rutinify.domain.model.DraftItem
import com.mirkwood.rutinify.domain.model.RoutineDraft
import com.mirkwood.rutinify.domain.model.spanOn
import com.mirkwood.rutinify.domain.repository.DraftStore
import com.mirkwood.rutinify.domain.time.DefaultDatePolicy
import com.mirkwood.rutinify.domain.time.ZoneProvider
import com.mirkwood.rutinify.domain.usecase.BuildSchedulePreviewUseCase
import com.mirkwood.rutinify.domain.usecase.ConflictPolicy
import com.mirkwood.rutinify.domain.usecase.FindConflictingOrdersUseCase
import com.mirkwood.rutinify.domain.usecase.PreviewItem
import com.mirkwood.rutinify.domain.usecase.RescheduleAllUseCase
import com.mirkwood.rutinify.domain.usecase.ScheduleOrderResult
import com.mirkwood.rutinify.domain.usecase.ScheduleOrderUseCase
import com.mirkwood.rutinify.domain.usecase.ScheduleSummary
import com.mirkwood.rutinify.domain.usecase.SchedulingPermissionsUseCase
import com.mirkwood.rutinify.ui.common.DateFormats
import com.mirkwood.rutinify.ui.common.ErrorMessages
import com.mirkwood.rutinify.ui.common.SpanLabels
import com.mirkwood.rutinify.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ReviewItemUi(
    val key: Int,
    /** "06:45" */
    val time: String,
    /** Shown as "(+1 día)"; never in a multi-day routine, whose rows sit under their day (R-4.13). */
    val nextDay: Boolean,
    val title: String,
    val category: String,
    val description: String?,
    /** "Ya pasó", dimmed and not selectable (R-4.7). */
    val isPast: Boolean,
    val isIncluded: Boolean,
)

/** "Día 3 · lunes 16 de noviembre" and its activities (R-4.13); [dayOffset] is the stable list key. */
data class ReviewDayUi(val dayOffset: Int, val title: UiText, val items: List<ReviewItemUi>)

/** Edit form of one activity (R-4.2); errors use the import rules. */
data class EditItemUi(
    val key: Int,
    val time: String,
    val title: String,
    val category: String,
    val timeError: UiText? = null,
    val titleError: UiText? = null,
    val categoryError: UiText? = null,
)

/** An order in the conflict dialog: [range] is "14 de noviembre" or "del 14 al 20 de noviembre" (R-6.7). */
data class ConflictOrderUi(val name: String, val range: UiText)

sealed interface ReviewDialog {
    /** Why notifications are needed, before Android's own permission dialog (R-11.1). */
    data object NotificationsRationale : ReviewDialog

    /** Why exact alarms matter: "Abrir ajustes" or "Continuar sin exactas" (R-11.2). */
    data object ExactAlarms : ReviewDialog

    /**
     * "Reemplazar la anterior / Mantener ambas / Cancelar" listing the orders by name (R-6.1, R-6.5). With a
     * multi-day routine on either side, each name shows its days and the dialog warns that replacing cancels
     * whole routines (R-6.7).
     */
    data class Conflict(val orders: List<ConflictOrderUi>, val showsRanges: Boolean = false) : ReviewDialog
}

data class ReviewUiState(
    /** The draft no longer exists (process death, D-10): "El borrador se perdió, vuelve a validar". */
    val draftLost: Boolean = false,
    val name: String = "",
    val notes: String? = null,
    val dateLabel: UiText = UiText.Plain(""),
    /** UTC midnight of the chosen date, as the date picker expects. */
    val dateMillisUtc: Long = 0,
    /** "No venía fecha; elegí {fecha}" while the app-chosen date is kept (R-4.5). */
    val dateNotice: UiText? = null,
    val items: List<ReviewItemUi> = emptyList(),
    /** Same rows grouped by day when the routine is multi-day; empty otherwise (R-4.13). */
    val days: List<ReviewDayUi> = emptyList(),
    /** null → "No hay actividades futuras para programar" (R-4.10). */
    val summary: UiText? = null,
    val warnings: List<UiText> = emptyList(),
    val canSchedule: Boolean = false,
    val suggestTomorrow: Boolean = false,
    val editing: EditItemUi? = null,
    val dialog: ReviewDialog? = null,
    val isScheduling: Boolean = false,
)

sealed interface ReviewAction {
    data class ToggleIncluded(val key: Int) : ReviewAction
    data class RemoveItem(val key: Int) : ReviewAction
    data object UndoRemove : ReviewAction
    data class DateSelected(val utcMillis: Long) : ReviewAction
    data object UseTomorrow : ReviewAction
    data class EditItem(val key: Int) : ReviewAction
    data class EditChanged(val time: String, val title: String, val category: String) : ReviewAction
    data object SaveEdit : ReviewAction
    data object DismissEdit : ReviewAction
    data object ScheduleClicked : ReviewAction

    /** "Continuar" on [ReviewDialog.NotificationsRationale]: the UI then shows Android's dialog. */
    data object NotificationsRationaleAccepted : ReviewAction

    /** "Ahora no" on [ReviewDialog.NotificationsRationale]: schedule anyway (R-7.8). */
    data object NotificationsRationaleSkipped : ReviewAction

    /** Android's notification dialog closed, granted or not. */
    data object NotificationPermissionResult : ReviewAction

    data object ExactAlarmsOpenSettings : ReviewAction
    data object ExactAlarmsContinueWithout : ReviewAction

    /** Back in the foreground, maybe from the exact-alarm settings page. */
    data object Resumed : ReviewAction
    data object ConflictReplace : ReviewAction
    data object ConflictKeepBoth : ReviewAction
    data object DismissDialog : ReviewAction
    data object RetrySchedule : ReviewAction
}

sealed interface ReviewEffect {
    data class ShowMessage(val message: UiText) : ReviewEffect

    /** Snackbar with "Deshacer" (R-4.3). */
    data class ShowUndo(val message: UiText) : ReviewEffect

    /** Snackbar with "Reintentar" (R-5.7). */
    data class ShowRetry(val message: UiText) : ReviewEffect

    /** Order detail, which shows "{n} alarmas programadas" (R-5.6). */
    data class NavigateToOrder(val orderId: Long, val scheduledCount: Int) : ReviewEffect

    /** Launch Android's `POST_NOTIFICATIONS` dialog (R-11.1). */
    data object RequestNotificationPermission : ReviewEffect

    /** Open "Alarmas y recordatorios" for this app (R-11.2). */
    data object OpenExactAlarmSettings : ReviewEffect
}

@HiltViewModel
class ReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val draftStore: DraftStore,
    defaultDatePolicy: DefaultDatePolicy,
    private val buildPreview: BuildSchedulePreviewUseCase,
    private val findConflicts: FindConflictingOrdersUseCase,
    private val scheduleOrder: ScheduleOrderUseCase,
    private val permissions: SchedulingPermissionsUseCase,
    private val rescheduleAll: RescheduleAllUseCase,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
    private val diagnostics: DiagnosticsRecorder,
) : ViewModel() {

    /** Everything the screen is built from; the draft only lives in memory (D-10). */
    private data class Model(
        val draftId: String,
        val draft: RoutineDraft?,
        val warnings: List<ValidationIssue>,
        val date: LocalDate,
        val dateChosenByApp: Boolean,
        val excluded: Set<Int> = emptySet(),
        val lastRemoved: DraftItem? = null,
        val editing: EditItemUi? = null,
        val dialog: ReviewDialog? = null,
        val isScheduling: Boolean = false,
        val lastPolicy: ConflictPolicy = ConflictPolicy.KEEP_BOTH,
    )

    private val model: MutableStateFlow<Model>
    private val effectChannel = Channel<ReviewEffect>(Channel.BUFFERED)
    val effects: Flow<ReviewEffect> = effectChannel.receiveAsFlow()

    init {
        val draftId = savedStateHandle.get<String>(KEY_DRAFT_ID).orEmpty()
        val entry = draftStore.get(draftId)
        val initial = if (entry == null) {
            Model(draftId, draft = null, warnings = emptyList(), date = today(), dateChosenByApp = false)
        } else {
            val default = defaultDatePolicy.defaultDate(entry.draft, clock.instant(), zoneProvider.zone())
            Model(draftId, entry.draft, entry.warnings, default.date, default.chosenByApp)
        }
        model = MutableStateFlow(initial)
        if (entry != null) viewModelScope.launch { diagnostics.record(DiagnosticEvent.Step.REVIEW) } // R-17.2
    }

    val uiState: StateFlow<ReviewUiState> = model
        .map(::toUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), toUiState(model.value))

    fun onAction(action: ReviewAction) {
        when (action) {
            is ReviewAction.ToggleIncluded -> toggle(action.key)
            is ReviewAction.RemoveItem -> remove(action.key)
            ReviewAction.UndoRemove -> undoRemove()
            is ReviewAction.DateSelected -> setDate(Instant.ofEpochMilli(action.utcMillis).atZone(ZoneOffset.UTC).toLocalDate())
            ReviewAction.UseTomorrow -> setDate(today().plusDays(1))
            is ReviewAction.EditItem -> startEdit(action.key)
            is ReviewAction.EditChanged -> model.update { m ->
                m.copy(editing = m.editing?.copy(time = action.time, title = action.title, category = action.category))
            }
            ReviewAction.SaveEdit -> saveEdit()
            ReviewAction.DismissEdit -> model.update { it.copy(editing = null) }
            ReviewAction.ScheduleClicked -> {
                viewModelScope.launch { diagnostics.record(DiagnosticEvent.Step.SCHEDULE_TAP) } // R-17.2
                startScheduling()
            }
            ReviewAction.NotificationsRationaleAccepted -> {
                permissions.markNotificationsExplained()
                model.update { it.copy(dialog = null) }
                effectChannel.trySend(ReviewEffect.RequestNotificationPermission)
            }
            ReviewAction.NotificationsRationaleSkipped -> {
                permissions.markNotificationsExplained()
                model.update { it.copy(dialog = null) }
                exactAlarmsStep()
            }
            ReviewAction.NotificationPermissionResult -> exactAlarmsStep()
            ReviewAction.ExactAlarmsOpenSettings -> {
                awaitingExactSettings = true
                model.update { it.copy(dialog = null) }
                effectChannel.trySend(ReviewEffect.OpenExactAlarmSettings)
            }
            ReviewAction.ExactAlarmsContinueWithout -> {
                model.update { it.copy(dialog = null) }
                checkConflictsAndSchedule()
            }
            ReviewAction.Resumed -> backFromExactSettings()
            ReviewAction.ConflictReplace -> schedule(ConflictPolicy.REPLACE)
            ReviewAction.ConflictKeepBoth -> schedule(ConflictPolicy.KEEP_BOTH)
            ReviewAction.DismissDialog -> model.update { it.copy(dialog = null) } // R-6.4
            ReviewAction.RetrySchedule -> schedule(model.value.lastPolicy)
        }
    }

    // ---- State mapping ----

    private fun toUiState(m: Model): ReviewUiState {
        val draft = m.draft ?: return ReviewUiState(draftLost = true)
        val preview = preview(m, draft)
        val dateLabel = SpanLabels.fullDate(m.date)
        return ReviewUiState(
            name = draft.name,
            notes = draft.notes,
            dateLabel = dateLabel,
            dateMillisUtc = m.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            dateNotice = if (m.dateChosenByApp) UiText.Res(R.string.review_date_defaulted, listOf(dateLabel)) else null,
            items = preview.items.map { itemUi(it, preview.isMultiDay) },
            days = if (preview.isMultiDay) {
                preview.days.map { day ->
                    ReviewDayUi(
                        dayOffset = day.dayOffset,
                        title = SpanLabels.dayHeader(day.dayOffset, day.date),
                        items = day.items.map { itemUi(it, multiDay = true) },
                    )
                }
            } else {
                emptyList()
            },
            summary = preview.summary?.let { if (preview.isMultiDay) multiDaySummaryText(it, m.date) else summaryText(it) },
            warnings = ErrorMessages.list(m.warnings),
            canSchedule = preview.canSchedule && !m.isScheduling,
            suggestTomorrow = preview.suggestTomorrow,
            editing = m.editing,
            dialog = m.dialog,
            isScheduling = m.isScheduling,
        )
    }

    private fun preview(m: Model, draft: RoutineDraft) =
        buildPreview(draft, m.date, m.excluded, clock.instant(), zoneProvider.zone())

    private fun itemUi(row: PreviewItem, multiDay: Boolean) = ReviewItemUi(
        key = row.item.key,
        time = row.item.time.format(TIME_FORMAT),
        nextDay = !multiDay && row.item.dayOffset > 0,
        title = row.item.title,
        category = row.item.category,
        description = row.item.description,
        isPast = row.isPast,
        isIncluded = row.isIncluded,
    )

    /** "21 alarmas en 7 días, del sábado 14 al viernes 20 de noviembre" (R-4.14, D-59). */
    private fun multiDaySummaryText(summary: ScheduleSummary, date: LocalDate): UiText = UiText.Res(
        R.string.review_summary_days,
        listOf(
            UiText.Plural(R.plurals.review_alarm_count, summary.count, listOf(summary.count)),
            UiText.Plural(R.plurals.review_day_count, summary.dayCount, listOf(summary.dayCount)),
            SpanLabels.withWeekdays(
                date.plusDays(summary.firstDayOffset.toLong()),
                date.plusDays(summary.lastDayOffset.toLong()),
            ),
        ),
    )

    /** "4 alarmas, de 13:00 a 02:00 (+1 día)" (R-4.9). */
    private fun summaryText(summary: ScheduleSummary): UiText = UiText.Plural(
        R.plurals.review_summary,
        summary.count,
        listOf(summary.count, timeText(summary.first, summary.firstDayOffset), timeText(summary.last, summary.lastDayOffset)),
    )

    private fun timeText(time: LocalTime, dayOffset: Int): Any {
        val text = time.format(TIME_FORMAT)
        return if (dayOffset > 0) UiText.Res(R.string.review_time_next_day, listOf(text)) else text
    }

    private fun today(): LocalDate = clock.instant().atZone(zoneProvider.zone()).toLocalDate()

    // ---- Draft changes ----

    private fun updateDraft(transform: (RoutineDraft) -> RoutineDraft) {
        model.update { m -> m.draft?.let { m.copy(draft = transform(it)) } ?: m }
    }

    private fun toggle(key: Int) {
        val m = model.value
        val draft = m.draft ?: return
        // A past activity can never be included (R-4.7)
        val row = preview(m, draft).items.firstOrNull { it.item.key == key } ?: return
        if (row.isPast) return
        model.update { it.copy(excluded = if (key in it.excluded) it.excluded - key else it.excluded + key) }
    }

    private fun remove(key: Int) {
        val item = model.value.draft?.items?.firstOrNull { it.key == key } ?: return
        updateDraft { d -> d.copy(items = d.items - item) }
        model.update { it.copy(lastRemoved = item) }
        effectChannel.trySend(ReviewEffect.ShowUndo(UiText.Res(R.string.review_item_removed)))
    }

    private fun undoRemove() {
        val item = model.value.lastRemoved ?: return
        updateDraft { d -> d.copy(items = (d.items + item).sortedWith(ITEM_ORDER)) }
        model.update { it.copy(lastRemoved = null) }
    }

    private fun setDate(date: LocalDate) {
        model.update { it.copy(date = date, dateChosenByApp = false) }
    }

    private fun startEdit(key: Int) {
        val item = model.value.draft?.items?.firstOrNull { it.key == key } ?: return
        model.update {
            it.copy(editing = EditItemUi(key, item.time.format(TIME_FORMAT), item.title, item.category))
        }
    }

    /** Same rules as the import (R-4.2): time R-3.8, title R-3.9/R-3.10, category R-3.14/R-3.21/R-3.22. */
    private fun saveEdit() {
        val m = model.value
        val draft = m.draft ?: return
        val form = m.editing ?: return
        val time = DraftItemRules.parseTime(form.time)
        val title = DraftItemRules.checkTitle(form.title)
        val category = DraftItemRules.normalizeCategory(form.category)
        val otherCategories = draft.items.filter { it.key != form.key }.map { it.category }.toSet()
        val tooManyCategories = category != null && (otherCategories + category).size > DraftItemRules.MAX_CATEGORIES

        val errors = form.copy(
            timeError = if (time == null) UiText.Res(R.string.review_error_time) else null,
            titleError = if (title is DraftItemRules.TitleCheck.Blank) UiText.Res(R.string.review_error_title_blank) else null,
            categoryError = when {
                category == null -> UiText.Res(R.string.review_error_category_long, listOf(DraftItemRules.MAX_CATEGORY_LENGTH))
                tooManyCategories -> UiText.Res(R.string.review_error_too_many_categories, listOf(DraftItemRules.MAX_CATEGORIES))
                else -> null
            },
        )
        if (time == null || title !is DraftItemRules.TitleCheck.Valid || category == null || tooManyCategories) {
            model.update { it.copy(editing = errors) }
            return
        }
        updateDraft { d ->
            d.copy(
                items = d.items
                    .map { if (it.key == form.key) it.copy(time = time, title = title.value, category = category) else it }
                    .sortedWith(ITEM_ORDER),
            )
        }
        model.update { it.copy(editing = null) }
        if (title.truncated) {
            effectChannel.trySend(
                ReviewEffect.ShowMessage(UiText.Res(R.string.review_title_truncated, listOf(DraftItemRules.MAX_TITLE_LENGTH))),
            )
        }
    }

    // ---- Scheduling: notifications → exact alarms → date conflict → schedule (design §9, D-52) ----

    /** Set while the user is in the exact-alarm settings page from [ReviewDialog.ExactAlarms]. */
    private var awaitingExactSettings = false

    private fun startScheduling() {
        val m = model.value
        if (m.draft == null || m.isScheduling) return
        if (permissions.shouldExplainNotifications()) {
            model.update { it.copy(dialog = ReviewDialog.NotificationsRationale) }
        } else {
            exactAlarmsStep()
        }
    }

    private fun exactAlarmsStep() {
        if (permissions.exactAlarmsGranted()) {
            checkConflictsAndSchedule()
        } else {
            model.update { it.copy(dialog = ReviewDialog.ExactAlarms) }
        }
    }

    /** Whatever the user chose in settings, scheduling goes on: windowed alarms still ring (R-11.7). */
    private fun backFromExactSettings() {
        if (!awaitingExactSettings) return
        awaitingExactSettings = false
        viewModelScope.launch {
            // R-11.6: other orders' windowed alarms become exact now (idempotent, R-12.6)
            if (permissions.exactAlarmsGranted()) rescheduleAll()
            checkConflictsAndSchedule()
        }
    }

    private fun checkConflictsAndSchedule() {
        val m = model.value
        if (m.draft == null || m.isScheduling) return
        viewModelScope.launch {
            model.update { it.copy(isScheduling = true) }
            val conflicts = findConflicts(m.draft, m.date)
            if (conflicts.isEmpty()) {
                runSchedule(ConflictPolicy.KEEP_BOTH)
            } else {
                val dialog = ReviewDialog.Conflict(
                    orders = conflicts.map { ConflictOrderUi(it.order.name, SpanLabels.short(it.span)) },
                    showsRanges = m.draft.spanOn(m.date).isMultiDay || conflicts.any { it.span.isMultiDay },
                )
                model.update { it.copy(isScheduling = false, dialog = dialog) }
            }
        }
    }

    private fun schedule(policy: ConflictPolicy) {
        if (model.value.draft == null || model.value.isScheduling) return
        viewModelScope.launch {
            model.update { it.copy(isScheduling = true, dialog = null) }
            runSchedule(policy)
        }
    }

    private suspend fun runSchedule(policy: ConflictPolicy) {
        val m = model.value
        val draft = m.draft ?: return
        val result = scheduleOrder(draft, m.date, m.excluded, policy)
        model.update { it.copy(isScheduling = false, lastPolicy = policy) }
        val effect = when (result) {
            is ScheduleOrderResult.Scheduled -> {
                draftStore.remove(m.draftId)
                ReviewEffect.NavigateToOrder(result.orderId, result.scheduledCount)
            }
            is ScheduleOrderResult.TooManyAlarms -> ReviewEffect.ShowMessage(
                UiText.Plural(R.plurals.review_too_many_alarms, result.requested, listOf(result.requested, result.alreadyScheduled)),
            )
            ScheduleOrderResult.NothingToSchedule -> ReviewEffect.ShowMessage(UiText.Res(R.string.review_nothing_to_schedule))
            ScheduleOrderResult.StorageFailure -> ReviewEffect.ShowRetry(UiText.Res(R.string.review_storage_failure))
        }
        effectChannel.send(effect)
    }

    private companion object {
        /** Property name of `ReviewDestination.draftId` (type-safe navigation stores it under that key). */
        const val KEY_DRAFT_ID = "draftId"
        val TIME_FORMAT: DateTimeFormatter = DateFormats.TIME
        val ITEM_ORDER = compareBy<DraftItem>({ it.dayOffset }, { it.time }, { it.key })
    }
}
