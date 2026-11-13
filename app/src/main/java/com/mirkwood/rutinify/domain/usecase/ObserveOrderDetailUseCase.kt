package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderDetail
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.model.OrderWithAlarms
import com.mirkwood.rutinify.domain.model.TimelineEntry
import com.mirkwood.rutinify.domain.repository.OrderRepository
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import com.mirkwood.rutinify.domain.time.ZoneProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Observes an order as a timeline; emits null when the order does not exist (R-9.1, R-9.2). */
class ObserveOrderDetailUseCase @Inject constructor(
    private val orders: OrderRepository,
    private val calculator: TriggerTimeCalculator,
    private val zoneProvider: ZoneProvider,
) {
    operator fun invoke(orderId: Long): Flow<OrderDetail?> =
        orders.observeOrderWithAlarms(orderId).map { it?.let(::toDetail) }

    private fun toDetail(source: OrderWithAlarms): OrderDetail {
        val zone = zoneProvider.zone()
        // Effective time, so a snoozed alarm moves to its new place (D-12); position breaks ties
        val timeline = source.alarms
            .map { TimelineEntry(it, calculator.effectiveTrigger(it, source.order.date, zone)) }
            .sortedWith(compareBy({ it.triggerAt }, { it.alarm.position }))
        // A paused or cancelled order rings nothing, so nothing is "next" (D-46)
        val next = timeline.takeIf { source.order.status == OrderStatus.ACTIVE }
            ?.firstOrNull { it.alarm.status == AlarmStatus.PENDING }
        return OrderDetail(source.order, timeline, next?.alarm?.id)
    }
}
