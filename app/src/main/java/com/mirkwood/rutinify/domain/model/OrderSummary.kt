package com.mirkwood.rutinify.domain.model

/** Order with alarm counts for the Home list (R-8.2). */
data class OrderSummary(
    val order: RoutineOrder,
    val pending: Int,
    val total: Int,
    /** Largest `dayOffset` of its alarms, 0 without alarms (D-55). */
    val maxDayOffset: Int = 0,
) {
    /** Days the order covers: Home shows a range when it is multi-day (R-8.2). */
    val span: RoutineSpan get() = RoutineSpan.of(order.date, maxDayOffset)

    /** R-8.3: an active order with no pending alarms is shown as finished ("Terminada"). */
    val displayStatus: OrderDisplayStatus
        get() = when (order.status) {
            // R-23.11: a plan that repeats with no end is never "Terminada"
            OrderStatus.ACTIVE -> if (pending == 0 && !(order.recurrence != null && order.recurrence.until == null)) {
                OrderDisplayStatus.FINISHED
            } else {
                OrderDisplayStatus.ACTIVE
            }
            OrderStatus.PAUSED -> OrderDisplayStatus.PAUSED
            OrderStatus.CANCELLED -> OrderDisplayStatus.CANCELLED
        }
}

/** Order with all its alarms (detail screen). */
data class OrderWithAlarms(
    val order: RoutineOrder,
    val alarms: List<AlarmItem>,
)
