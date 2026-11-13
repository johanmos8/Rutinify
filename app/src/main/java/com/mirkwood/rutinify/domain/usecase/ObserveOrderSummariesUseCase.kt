package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.model.OrderSummary
import com.mirkwood.rutinify.domain.repository.OrderRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Orders for the Home list, already sorted, re-emitted on any order or alarm change (R-8.1, R-8.6). */
class ObserveOrderSummariesUseCase @Inject constructor(
    private val orders: OrderRepository,
) {

    operator fun invoke(): Flow<List<OrderSummary>> = orders.observeSummaries()
}
