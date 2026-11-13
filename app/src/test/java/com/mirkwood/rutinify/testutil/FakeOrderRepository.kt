package com.mirkwood.rutinify.testutil

import com.mirkwood.rutinify.domain.model.AlarmItem
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.CreatedOrder
import com.mirkwood.rutinify.domain.model.NewAlarm
import com.mirkwood.rutinify.domain.model.NewOrder
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.model.OrderSummary
import com.mirkwood.rutinify.domain.model.OrderWithAlarms
import com.mirkwood.rutinify.domain.model.RoutineOrder
import com.mirkwood.rutinify.domain.repository.OrderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.LocalDate

/**
 * In-memory [OrderRepository]. Link [alarmRepository] so [createOrder] writes alarms where the
 * alarm use cases read them. Only what the tests so far need; the rest is added with its task.
 */
class FakeOrderRepository(
    var alarmRepository: FakeAlarmRepository? = null,
    private val now: Instant = Instant.parse("2026-11-05T12:00:00Z"),
) : OrderRepository {

    val orders = linkedMapOf<Long, RoutineOrder>()

    /** Simulates a Room failure (R-5.7): [createOrder] writes nothing and returns a failure. */
    var failCreate = false
    var createCalls = 0
        private set

    private var nextOrderId = 100L
    private var nextAlarmId = 1_000L

    fun add(vararg items: RoutineOrder) = items.forEach { orders[it.id] = it }

    /** Date of [orderId] when it is ACTIVE; for [FakeAlarmRepository.getPendingForActiveOrders]. */
    fun activeDate(orderId: Long): LocalDate? = orders[orderId]?.takeIf { it.status == OrderStatus.ACTIVE }?.date

    override suspend fun getOrder(orderId: Long): RoutineOrder? = orders[orderId]

    override suspend fun findOpenByDate(date: LocalDate): List<RoutineOrder> =
        orders.values.filter { it.date == date && it.status != OrderStatus.CANCELLED }

    override suspend fun updateStatus(orderId: Long, status: OrderStatus) {
        orders[orderId]?.let { orders[orderId] = it.copy(status = status) }
    }

    override suspend fun delete(orderId: Long) {
        orders.remove(orderId)
    }

    override suspend fun createOrder(
        order: NewOrder,
        alarms: List<NewAlarm>,
        replaceOrderIds: List<Long>,
    ): Result<CreatedOrder> {
        createCalls++
        if (failCreate) return Result.failure(IllegalStateException("disk full"))
        val alarmRepo = requireNotNull(alarmRepository) { "Link a FakeAlarmRepository first" }

        val cancelled = replaceOrderIds.flatMap { replacedId ->
            val pendingIds = alarmRepo.alarms.values
                .filter { it.orderId == replacedId && it.status == AlarmStatus.PENDING }
                .map { it.id }
            alarmRepo.updateStatus(pendingIds, AlarmStatus.CANCELLED)
            updateStatus(replacedId, OrderStatus.CANCELLED)
            pendingIds
        }
        val created = RoutineOrder(nextOrderId++, order.name, order.date, order.notes, OrderStatus.ACTIVE, now)
        orders[created.id] = created
        val items = alarms.map {
            AlarmItem(
                id = nextAlarmId++,
                orderId = created.id,
                position = it.position,
                time = it.time,
                dayOffset = it.dayOffset,
                title = it.title,
                description = it.description,
                durationMin = it.durationMin,
                category = it.category,
                assignee = it.assignee,
                status = it.status,
                snoozedUntil = null,
                statusChangedAt = now,
            )
        }
        alarmRepo.add(*items.toTypedArray())
        return Result.success(CreatedOrder(created, items, cancelled))
    }

    /** What [observeSummaries] emits; tests push new lists to simulate Room changes (R-8.6). */
    val summaries = MutableStateFlow<List<OrderSummary>>(emptyList())

    override fun observeSummaries(): Flow<List<OrderSummary>> = summaries

    override fun observeOrderWithAlarms(orderId: Long): Flow<OrderWithAlarms?> = TODO("Not needed yet")
}
