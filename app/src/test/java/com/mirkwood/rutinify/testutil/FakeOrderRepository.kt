package com.mirkwood.rutinify.testutil

import com.mirkwood.rutinify.domain.model.RoutineActivity
import com.mirkwood.rutinify.domain.model.NewActivity
import com.mirkwood.rutinify.domain.model.AlarmItem
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.ConflictingOrder
import com.mirkwood.rutinify.domain.model.CreatedOrder
import com.mirkwood.rutinify.domain.model.NewAlarm
import com.mirkwood.rutinify.domain.model.NewOrder
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.model.OrderSummary
import com.mirkwood.rutinify.domain.model.OrderWithAlarms
import com.mirkwood.rutinify.domain.model.RoutineOrder
import com.mirkwood.rutinify.domain.model.RoutineSpan
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

    /** Same rule as the DAO: spans come from the linked alarms' largest `dayOffset` (D-58). */
    override suspend fun findOpenOverlapping(span: RoutineSpan): List<ConflictingOrder> =
        orders.values
            .filter { it.status != OrderStatus.CANCELLED }
            .map { order ->
                val maxOffset = alarmRepository?.alarms?.values?.filter { it.orderId == order.id }?.maxOfOrNull { it.dayOffset }
                ConflictingOrder(order, RoutineSpan.of(order.date, maxOffset ?: 0))
            }
            .filter { it.span.forConflicts().overlaps(span) }

    override suspend fun updateStatus(orderId: Long, status: OrderStatus) {
        orders[orderId]?.let { orders[orderId] = it.copy(status = status) }
    }

    override suspend fun delete(orderId: Long) {
        orders.remove(orderId)
        // ON DELETE CASCADE
        alarmRepository?.alarms?.values?.removeAll { it.orderId == orderId }
    }

    override suspend fun cancelOrder(orderId: Long): List<Long> {
        val alarmRepo = alarmRepository
        val pendingIds = alarmRepo?.alarms?.values
            ?.filter { it.orderId == orderId && it.status == AlarmStatus.PENDING }
            ?.map { it.id }
            .orEmpty()
        alarmRepo?.updateStatus(pendingIds, AlarmStatus.CANCELLED)
        updateStatus(orderId, OrderStatus.CANCELLED)
        return pendingIds
    }

    override suspend fun createOrder(
        order: NewOrder,
        alarms: List<NewAlarm>,
        replaceOrderIds: List<Long>,
        activities: List<NewActivity>,
    ): Result<CreatedOrder> {
        createCalls++
        if (failCreate) return Result.failure(IllegalStateException("disk full"))
        val alarmRepo = requireNotNull(alarmRepository) { "Link a FakeAlarmRepository first" }

        val cancelled = replaceOrderIds.flatMap { cancelOrder(it) }
        val created = RoutineOrder(nextOrderId++, order.name, order.date, order.notes, OrderStatus.ACTIVE, now, order.recurrence)
        orders[created.id] = created
        this.activities[created.id] = activities.mapIndexed { i, a ->
            RoutineActivity(nextActivityId++, created.id, a.position, a.time, a.dayOffset, a.title, a.description, a.durationMin, a.category, a.assignee)
        }
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
                activityId = it.activityId,
            )
        }
        alarmRepo.add(*items.toTypedArray())
        return Result.success(CreatedOrder(created, items, cancelled))
    }

    /** Day types of recurring plans, by order id (D-81). */
    val activities = mutableMapOf<Long, List<RoutineActivity>>()
    private var nextActivityId = 500L

    override suspend fun getActivities(orderId: Long): List<RoutineActivity> = activities[orderId].orEmpty()

    override suspend fun getActiveRecurring(): List<RoutineOrder> =
        orders.values.filter { it.status == OrderStatus.ACTIVE && it.recurrence != null }

    /** What [observeSummaries] emits; tests push new lists to simulate Room changes (R-8.6). */
    val summaries = MutableStateFlow<List<OrderSummary>>(emptyList())

    override fun observeSummaries(): Flow<List<OrderSummary>> = summaries

    /** What [observeOrderWithAlarms] emits per order; null until a test sets it (order not found). */
    private val details = mutableMapOf<Long, MutableStateFlow<OrderWithAlarms?>>()

    fun detail(orderId: Long): MutableStateFlow<OrderWithAlarms?> = details.getOrPut(orderId) { MutableStateFlow(null) }

    override fun observeOrderWithAlarms(orderId: Long): Flow<OrderWithAlarms?> = detail(orderId)
}
