package com.mirkwood.rutinify.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.mirkwood.rutinify.data.LOG_TAG
import com.mirkwood.rutinify.data.local.AlarmItemDao
import com.mirkwood.rutinify.data.local.OrderDao
import com.mirkwood.rutinify.data.local.RutinifyDatabase
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
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class OrderRepositoryImpl @Inject constructor(
    private val db: RutinifyDatabase,
    private val orderDao: OrderDao,
    private val alarmDao: AlarmItemDao,
    private val clock: Clock,
) : OrderRepository {

    override fun observeSummaries(): Flow<List<OrderSummary>> =
        orderDao.observeSummaries().map { rows -> rows.map { it.toDomain() } }

    override fun observeOrderWithAlarms(orderId: Long): Flow<OrderWithAlarms?> =
        orderDao.observeWithAlarms(orderId).map { it?.toDomain() }

    override suspend fun getOrder(orderId: Long): RoutineOrder? = orderDao.getById(orderId)?.toDomain()

    override suspend fun findOpenByDate(date: LocalDate): List<RoutineOrder> =
        orderDao.findOpenByDate(date).map { it.toDomain() }

    override suspend fun createOrder(
        order: NewOrder,
        alarms: List<NewAlarm>,
        replaceOrderIds: List<Long>,
    ): Result<CreatedOrder> {
        val now = clock.instant()
        return try {
            val created = db.withTransaction {
                val cancelledIds = replaceOrderIds.flatMap { replacedId ->
                    val pendingIds = alarmDao.getPendingByOrder(replacedId).map { it.id }
                    if (pendingIds.isNotEmpty()) alarmDao.updateStatus(pendingIds, AlarmStatus.CANCELLED, now)
                    orderDao.updateStatus(replacedId, OrderStatus.CANCELLED, now)
                    pendingIds
                }
                val orderEntity = order.toEntity(now)
                val orderId = orderDao.insert(orderEntity)
                val alarmEntities = alarms.map { it.toEntity(orderId, now) }
                val alarmIds = alarmDao.insertAll(alarmEntities)
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

    override suspend fun updateStatus(orderId: Long, status: OrderStatus) =
        orderDao.updateStatus(orderId, status, clock.instant())

    override suspend fun delete(orderId: Long) = orderDao.delete(orderId)
}
