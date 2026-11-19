package com.mirkwood.rutinify.domain.repository

import com.mirkwood.rutinify.domain.model.RoutineActivity
import com.mirkwood.rutinify.domain.model.NewActivity
import com.mirkwood.rutinify.domain.model.ConflictingOrder
import com.mirkwood.rutinify.domain.model.CreatedOrder
import com.mirkwood.rutinify.domain.model.NewAlarm
import com.mirkwood.rutinify.domain.model.NewOrder
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.model.OrderSummary
import com.mirkwood.rutinify.domain.model.OrderWithAlarms
import com.mirkwood.rutinify.domain.model.RoutineOrder
import com.mirkwood.rutinify.domain.model.RoutineSpan
import kotlinx.coroutines.flow.Flow

interface OrderRepository {

    /** Orders by date desc then creation desc, with counts; re-emits on any change (R-8.1, R-8.6). */
    fun observeSummaries(): Flow<List<OrderSummary>>

    /** Order with its alarms sorted by `(dayOffset, time, position)`; null if it does not exist (R-9.1). */
    fun observeOrderWithAlarms(orderId: Long): Flow<OrderWithAlarms?>

    suspend fun getOrder(orderId: Long): RoutineOrder?

    /**
     * ACTIVE or PAUSED orders whose conflict days ([RoutineSpan.forConflicts]) share a day with [span],
     * each with its own span (R-6.1, R-6.6, D-58). [span] must already be the new routine's conflict days.
     */
    suspend fun findOpenOverlapping(span: RoutineSpan): List<ConflictingOrder>

    /**
     * Creates the ACTIVE order and its alarms in a single transaction. If [replaceOrderIds] is not
     * empty, in the same transaction those orders and their pending alarms become CANCELLED (R-6.2).
     * If anything fails nothing is written and a failure is returned (R-5.7).
     */
    suspend fun createOrder(
        order: NewOrder,
        alarms: List<NewAlarm>,
        replaceOrderIds: List<Long> = emptyList(),
        activities: List<NewActivity> = emptyList(),
    ): Result<CreatedOrder>

    /** Day type of a recurring plan, in timeline order (D-81). */
    suspend fun getActivities(orderId: Long): List<RoutineActivity>

    /** ACTIVE recurring plans (R-23.7). */
    suspend fun getActiveRecurring(): List<RoutineOrder>

    suspend fun updateStatus(orderId: Long, status: OrderStatus)

    /**
     * In one transaction: the order's PENDING alarms become CANCELLED and the order becomes CANCELLED;
     * FIRED, DONE and SKIPPED alarms are kept (R-10.2, R-10.3). @return the ids of the alarms cancelled.
     */
    suspend fun cancelOrder(orderId: Long): List<Long>

    /** Deletes the order and, by cascade, its alarms (R-10.4). */
    suspend fun delete(orderId: Long)
}
