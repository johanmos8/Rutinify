package com.mirkwood.rutinify.testutil

import com.mirkwood.rutinify.domain.model.CreatedOrder
import com.mirkwood.rutinify.domain.model.NewAlarm
import com.mirkwood.rutinify.domain.model.NewOrder
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.model.OrderSummary
import com.mirkwood.rutinify.domain.model.OrderWithAlarms
import com.mirkwood.rutinify.domain.model.RoutineOrder
import com.mirkwood.rutinify.domain.repository.OrderRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** In-memory [OrderRepository]. Only what the tests so far need; the rest is added with its task. */
class FakeOrderRepository : OrderRepository {

    val orders = linkedMapOf<Long, RoutineOrder>()

    fun add(vararg items: RoutineOrder) = items.forEach { orders[it.id] = it }

    override suspend fun getOrder(orderId: Long): RoutineOrder? = orders[orderId]

    override suspend fun findOpenByDate(date: LocalDate): List<RoutineOrder> =
        orders.values.filter { it.date == date && it.status != OrderStatus.CANCELLED }

    override suspend fun updateStatus(orderId: Long, status: OrderStatus) {
        orders[orderId]?.let { orders[orderId] = it.copy(status = status) }
    }

    override suspend fun delete(orderId: Long) {
        orders.remove(orderId)
    }

    override fun observeSummaries(): Flow<List<OrderSummary>> = TODO("Not needed yet")

    override fun observeOrderWithAlarms(orderId: Long): Flow<OrderWithAlarms?> = TODO("Not needed yet")

    override suspend fun createOrder(
        order: NewOrder,
        alarms: List<NewAlarm>,
        replaceOrderIds: List<Long>,
    ): Result<CreatedOrder> = TODO("Not needed yet")
}
