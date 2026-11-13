package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.model.DraftItem
import com.mirkwood.rutinify.domain.model.DraftOrigin
import com.mirkwood.rutinify.domain.model.RoutineDraft
import com.mirkwood.rutinify.domain.repository.DraftEntry
import com.mirkwood.rutinify.domain.repository.DraftStore
import com.mirkwood.rutinify.domain.repository.OrderRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/** "Duplicar para otra fecha": a new draft with the same activities, opened in Review (R-10.7). */
class DuplicateOrderUseCase @Inject constructor(
    private val orders: OrderRepository,
    private val draftStore: DraftStore,
) {

    /**
     * Copies every alarm of the order, whatever its status, at its original time (a snooze is not
     * carried over), in timeline order. No raw JSON: the copy was not imported (D-50).
     * @return the new draft id; null if the order does not exist or has no alarms.
     */
    suspend operator fun invoke(orderId: Long, date: LocalDate): String? {
        val source = orders.observeOrderWithAlarms(orderId).first() ?: return null
        if (source.alarms.isEmpty()) return null
        val items = source.alarms
            .sortedWith(compareBy({ it.dayOffset }, { it.time }, { it.position }))
            .mapIndexed { index, alarm ->
                DraftItem(
                    key = index,
                    time = alarm.time,
                    dayOffset = alarm.dayOffset,
                    title = alarm.title,
                    description = alarm.description,
                    durationMin = alarm.durationMin,
                    category = alarm.category,
                    assignee = alarm.assignee,
                )
            }
        val draft = RoutineDraft(
            id = UUID.randomUUID().toString(),
            name = source.order.name,
            date = date,
            notes = source.order.notes,
            items = items,
            origin = DraftOrigin.DUPLICATE,
        )
        draftStore.put(DraftEntry(draft, warnings = emptyList()))
        return draft.id
    }
}
