package com.mirkwood.rutinify.domain.model

/** Order with alarm counts for the Home list (R-8.2). */
data class OrderSummary(
    val order: RoutineOrder,
    val pending: Int,
    val total: Int,
) {
    /** R-8.3: an active order with no pending alarms is shown as finished ("Terminada"). */
    val displayStatus: OrderDisplayStatus
        get() = when (order.status) {
            OrderStatus.ACTIVE -> if (pending == 0) OrderDisplayStatus.FINISHED else OrderDisplayStatus.ACTIVE
            OrderStatus.PAUSED -> OrderDisplayStatus.PAUSED
            OrderStatus.CANCELLED -> OrderDisplayStatus.CANCELLED
        }
}

/** Order with all its alarms (detail screen). */
data class OrderWithAlarms(
    val order: RoutineOrder,
    val alarms: List<AlarmItem>,
)
