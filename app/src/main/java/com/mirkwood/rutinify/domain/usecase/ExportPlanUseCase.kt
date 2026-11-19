package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.AlarmItem
import com.mirkwood.rutinify.domain.importer.PlanExporter
import com.mirkwood.rutinify.domain.repository.AlarmRepository
import com.mirkwood.rutinify.domain.repository.OrderRepository
import javax.inject.Inject

/** "Compartir plan" (R-19.1): the plan as contract JSON; null if it no longer exists. */
class ExportPlanUseCase @Inject constructor(
    private val orders: OrderRepository,
    private val alarms: AlarmRepository,
    private val exporter: PlanExporter,
) {
    suspend operator fun invoke(orderId: Long, pretty: Boolean = true): String? {
        val order = orders.getOrder(orderId) ?: return null
        // R-19.1: a recurring plan is shared as v2 with its day type
        val items = if (order.recurrence != null) {
            orders.getActivities(orderId).map {
                AlarmItem(
                    id = it.id, orderId = orderId, position = it.position, time = it.time, dayOffset = it.dayOffset, title = it.title,
                    description = it.description, durationMin = it.durationMin, category = it.category, assignee = it.assignee,
                    status = AlarmStatus.PENDING, snoozedUntil = null, statusChangedAt = order.createdAt,
                )
            }
        } else {
            alarms.getByOrder(orderId)
        }
        return exporter.export(order, items, pretty)
    }
}
