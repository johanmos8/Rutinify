package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.domain.repository.AlarmRepository
import com.mirkwood.rutinify.domain.repository.OrderRepository
import com.mirkwood.rutinify.domain.scheduler.AlarmScheduler
import javax.inject.Inject

/** "Pausar": takes the order's alarms out of the system but keeps them PENDING in Room (R-10.5). */
class PauseOrderUseCase @Inject constructor(
    private val orders: OrderRepository,
    private val alarms: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val lock: AlarmMutationLock,
) {

    /** @return true if the order was ACTIVE and is now PAUSED; any other order is left as it is. */
    suspend operator fun invoke(orderId: Long): Boolean = lock.withLock {
        val order = orders.getOrder(orderId) ?: return@withLock false
        if (order.status != OrderStatus.ACTIVE) return@withLock false
        // Room first: from here on a stray alarm that still fires is ignored (R-7.7), then the system
        orders.updateStatus(orderId, OrderStatus.PAUSED)
        alarms.getPendingByOrder(orderId).forEach { scheduler.cancel(it.id) }
        true
    }
}
