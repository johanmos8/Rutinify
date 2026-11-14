package com.mirkwood.rutinify.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirkwood.rutinify.domain.model.OrderDisplayStatus
import com.mirkwood.rutinify.domain.model.OrderSummary
import com.mirkwood.rutinify.domain.usecase.GetPermissionStatusUseCase
import com.mirkwood.rutinify.domain.usecase.ObserveOrderSummariesUseCase
import com.mirkwood.rutinify.ui.common.DateFormats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** One order card on Home (R-8.2). */
data class OrderCardUi(
    val id: Long,
    val name: String,
    val dateLabel: String,
    val pending: Int,
    val total: Int,
    val status: OrderDisplayStatus,
)

data class HomeUiState(
    /** True until the first list arrives, so the empty state does not flash on launch. */
    val isLoading: Boolean = true,
    val orders: List<OrderCardUi> = emptyList(),
    /** Persistent banner to the Permissions screen while any permission is missing (R-11.3). */
    val missingPermissions: Boolean = false,
) {
    /** R-8.5: the 3-step explanation, only once we know there are no orders. */
    val showEmptyState: Boolean get() = !isLoading && orders.isEmpty()
}

sealed interface HomeAction {
    /** Back in the foreground: permissions may have changed in system settings (R-11.5). */
    data object Resumed : HomeAction
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeOrderSummaries: ObserveOrderSummariesUseCase,
    private val getPermissionStatus: GetPermissionStatusUseCase,
) : ViewModel() {

    private val missingPermissions = MutableStateFlow(!getPermissionStatus().allGranted)

    /** Follows Room, so any order or alarm change refreshes the list (R-8.6). */
    val uiState: StateFlow<HomeUiState> = combine(observeOrderSummaries(), missingPermissions) { summaries, missing ->
        HomeUiState(isLoading = false, orders = summaries.map(::toCard), missingPermissions = missing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.Resumed -> missingPermissions.value = !getPermissionStatus().allGranted
        }
    }

    private fun toCard(summary: OrderSummary) = OrderCardUi(
        id = summary.order.id,
        name = summary.order.name,
        dateLabel = summary.order.date.format(DateFormats.LONG_DATE),
        pending = summary.pending,
        total = summary.total,
        status = summary.displayStatus,
    )
}
