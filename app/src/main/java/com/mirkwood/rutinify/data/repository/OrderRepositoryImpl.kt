package com.mirkwood.rutinify.data.repository

import com.mirkwood.rutinify.data.local.RoutineActivityDao
import com.mirkwood.rutinify.domain.model.RoutineActivity
import com.mirkwood.rutinify.domain.model.NewActivity
import android.util.Log
import androidx.room.withTransaction
import com.mirkwood.rutinify.data.LOG_TAG
import com.mirkwood.rutinify.data.local.AlarmItemDao
import com.mirkwood.rutinify.data.local.OrderDao
import com.mirkwood.rutinify.data.local.RutinifyDatabase
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.ConflictDays
import com.mirkwood.rutinify.domain.model.ConflictingOrder
import com.mirkwood.rutinify.domain.model.conflictDays
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
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class OrderRepositoryImpl @Inject constructor(
    private val db: RutinifyDatabase,
    private val orderDao: OrderDao,
    private val alarmDao: AlarmItemDao,
    private val activityDao: RoutineActivityDao,
    private val clock: Clock,
) : OrderRepository {

    override fun observeSummaries(): Flow<List<OrderSummary>> =
        orderDao.observeSummaries().map { rows -> rows.map { it.toDomain() } }

    override fun observeOrderWithAlarms(orderId: Long): Flow<OrderWithAlarms?> =
        orderDao.observeWithAlarms(orderId).map { it?.toDomain() }

    override suspend fun getOrder(orderId: Long): RoutineOrder? = orderDao.getById(orderId)?.toDomain()

    /**
     * SQL narrows plain orders by date; the weekdays of a recurring draft and every open recurring plan are checked
     * here with [ConflictDays.overlaps] (R-23.9). A recurring plan's span is its start day: its stored offsets only
     * reflect the occurrences created so far.
     */
    override suspend fun findOpenOverlapping(days: ConflictDays): List<ConflictingOrder> {
        val plain = orderDao.findOpenOverlapping(days.start, days.end ?: OPEN_END).map { row ->
            ConflictingOrder(row.order.toDomain(), RoutineSpan.of(row.order.date, row.maxDayOffset))
        }
        val recurring = orderDao.getOpenRecurring().map { ConflictingOrder(it.toDomain(), RoutineSpan(it.date, it.date)) }
        return (plain + recurring)
            .filter { days.overlaps(it.conflictDays) }
            .sortedWith(compareBy({ it.order.createdAt }, { it.order.id }))
    }

    override suspend fun createOrder(
        order: NewOrder,
        alarms: List<NewAlarm>,
        replaceOrderIds: List<Long>,
        activities: List<NewActivity>,
    ): Result<CreatedOrder> {
        val now = clock.instant()
        return try {
            val created = db.withTransaction {
                val cancelledIds = replaceOrderIds.flatMap { cancelPendingAndOrder(it, now) }
                val orderEntity = order.toEntity(now)
                val orderId = orderDao.insert(orderEntity)
                val alarmEntities = alarms.map { it.toEntity(orderId, now) }
                val alarmIds = alarmDao.insertAll(alarmEntities)
                if (activities.isNotEmpty()) activityDao.insertAll(activities.map { it.toEntity(orderId) })
                CreatedOrder(
                    order = orderEntity.copy(id = orderId).toDomain(),
                    alarms = alarmEntities.zip(alarmIds) { entity, id -> entity.copy(id = id).toDomain() }
                        .sortedWith(ALARM_TIMELINE_ORDER),
                    cancelledAlarmIds = cancelledIds,
                )
            }
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // R-5.7: the transaction is rolled back; the use case will not schedule anything
            Log.e(LOG_TAG, "No se pudo guardar la orden", e)
            Result.failure(e)
        }
    }

    override suspend fun getActivities(orderId: Long): List<RoutineActivity> = activityDao.getByOrder(orderId).map { it.toDomain() }

    override suspend fun getActiveRecurring(): List<RoutineOrder> = orderDao.getActiveRecurring().map { it.toDomain() }

    override suspend fun updateStatus(orderId: Long, status: OrderStatus) =
        orderDao.updateStatus(orderId, status, clock.instant())

    override suspend fun cancelOrder(orderId: Long): List<Long> =
        db.withTransaction { cancelPendingAndOrder(orderId, clock.instant()) }

    /** Must run inside a transaction. */
    private suspend fun cancelPendingAndOrder(orderId: Long, now: Instant): List<Long> {
        val pendingIds = alarmDao.getPendingByOrder(orderId).map { it.id }
        if (pendingIds.isNotEmpty()) alarmDao.updateStatus(pendingIds, AlarmStatus.CANCELLED, now)
        orderDao.updateStatus(orderId, OrderStatus.CANCELLED, now)
        return pendingIds
    }

    override suspend fun delete(orderId: Long) = orderDao.delete(orderId)

    private companion object {
        /** Upper bound for the SQL date filter when the claimed days have no end; ISO text still compares in order. */
        val OPEN_END: LocalDate = LocalDate.of(9999, 12, 31)
    }
}
