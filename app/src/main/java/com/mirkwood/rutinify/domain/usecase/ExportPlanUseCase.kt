package com.mirkwood.rutinify.domain.usecase

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
        return exporter.export(order, alarms.getByOrder(orderId), pretty)
    }
}
