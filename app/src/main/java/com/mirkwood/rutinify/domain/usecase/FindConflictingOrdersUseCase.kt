package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.model.ConflictingOrder
import com.mirkwood.rutinify.domain.model.RoutineDraft
import com.mirkwood.rutinify.domain.model.conflictDaysOn
import com.mirkwood.rutinify.domain.repository.OrderRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * ACTIVE or PAUSED orders sharing a day with [draft] scheduled on [date]: if any, the Review screen asks
 * before scheduling (R-6.1, R-6.5, R-6.6). Single-day routines only compare their date (D-58);
 * recurring ones claim their weekdays from [date] on (R-23.9).
 */
class FindConflictingOrdersUseCase @Inject constructor(
    private val orders: OrderRepository,
) {

    suspend operator fun invoke(draft: RoutineDraft, date: LocalDate): List<ConflictingOrder> =
        orders.findOpenOverlapping(draft.conflictDaysOn(date))
}
