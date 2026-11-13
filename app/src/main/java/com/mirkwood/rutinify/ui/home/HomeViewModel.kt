package com.mirkwood.rutinify.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirkwood.rutinify.domain.model.OrderDisplayStatus
import com.mirkwood.rutinify.domain.model.OrderSummary
import com.mirkwood.rutinify.domain.usecase.ObserveOrderSummariesUseCase
import com.mirkwood.rutinify.ui.common.DateFormats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
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
) {
    /** R-8.5: the 3-step explanation, only once we know there are no orders. */
    val showEmptyState: Boolean get() = !isLoading && orders.isEmpty()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeOrderSummaries: ObserveOrderSummariesUseCase,
) : ViewModel() {

    /** Follows Room, so any order or alarm change refreshes the list (R-8.6). */
    val uiState: StateFlow<HomeUiState> = observeOrderSummaries()
        .map { summaries -> HomeUiState(isLoading = false, orders = summaries.map(::toCard)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun toCard(summary: OrderSummary) = OrderCardUi(
        id = summary.order.id,
        name = summary.order.name,
        dateLabel = summary.order.date.format(DateFormats.LONG_DATE),
        pending = summary.pending,
        total = summary.total,
        status = summary.displayStatus,
    )
}
