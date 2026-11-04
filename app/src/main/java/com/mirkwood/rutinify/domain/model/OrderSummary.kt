package com.mirkwood.rutinify.domain.model

/** Orden con el recuento de alarmas para la lista de Inicio (R-8.2). */
data class OrderSummary(
    val order: RoutineOrder,
    val pending: Int,
    val total: Int,
) {
    /** R-8.3: una orden activa sin alarmas pendientes se muestra como terminada. */
    val displayStatus: OrderDisplayStatus
        get() = when (order.status) {
            OrderStatus.ACTIVE -> if (pending == 0) OrderDisplayStatus.FINISHED else OrderDisplayStatus.ACTIVE
            OrderStatus.PAUSED -> OrderDisplayStatus.PAUSED
            OrderStatus.CANCELLED -> OrderDisplayStatus.CANCELLED
        }
}

/** Orden con todas sus alarmas (pantalla de detalle). */
data class OrderWithAlarms(
    val order: RoutineOrder,
    val alarms: List<AlarmItem>,
)
