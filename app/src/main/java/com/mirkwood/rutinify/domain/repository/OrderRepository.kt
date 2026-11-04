package com.mirkwood.rutinify.domain.repository

import com.mirkwood.rutinify.domain.model.CreatedOrder
import com.mirkwood.rutinify.domain.model.NewAlarm
import com.mirkwood.rutinify.domain.model.NewOrder
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.model.OrderSummary
import com.mirkwood.rutinify.domain.model.OrderWithAlarms
import com.mirkwood.rutinify.domain.model.RoutineOrder
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface OrderRepository {

    /** Órdenes por fecha desc y creación desc, con recuentos; se re-emite ante cualquier cambio (R-8.1, R-8.6). */
    fun observeSummaries(): Flow<List<OrderSummary>>

    /** Orden con sus alarmas ordenadas por `(dayOffset, time, position)`; null si no existe (R-9.1). */
    fun observeOrderWithAlarms(orderId: Long): Flow<OrderWithAlarms?>

    suspend fun getOrder(orderId: Long): RoutineOrder?

    /** Órdenes Activas o Pausadas en esa fecha (R-6.1). */
    suspend fun findOpenByDate(date: LocalDate): List<RoutineOrder>

    /**
     * Crea la orden ACTIVE y sus alarmas en una sola transacción. Si [replaceOrderIds] no está vacío,
     * en la misma transacción esas órdenes pasan a CANCELLED y sus alarmas pendientes también (R-6.2).
     * Si algo falla no queda nada escrito y se devuelve un fallo (R-5.7).
     */
    suspend fun createOrder(
        order: NewOrder,
        alarms: List<NewAlarm>,
        replaceOrderIds: List<Long> = emptyList(),
    ): Result<CreatedOrder>

    suspend fun updateStatus(orderId: Long, status: OrderStatus)

    /** Borra la orden y, en cascada, sus alarmas (R-10.4). */
    suspend fun delete(orderId: Long)
}
