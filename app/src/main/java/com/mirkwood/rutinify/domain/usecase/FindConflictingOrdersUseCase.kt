package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.model.RoutineOrder
import com.mirkwood.rutinify.domain.repository.OrderRepository
import java.time.LocalDate
import javax.inject.Inject

/** ACTIVE or PAUSED orders on [date]: if any, the Review screen asks before scheduling (R-6.1, R-6.5). */
class FindConflictingOrdersUseCase @Inject constructor(
    private val orders: OrderRepository,
) {

    suspend operator fun invoke(date: LocalDate): List<RoutineOrder> = orders.findOpenByDate(date)
}
