package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.repository.AlarmRepository
import com.mirkwood.rutinify.domain.repository.OrderRepository
import com.mirkwood.rutinify.domain.scheduler.AlarmScheduler
import com.mirkwood.rutinify.domain.scheduler.NotificationPublisher
import javax.inject.Inject

/** "Eliminar plan": removes the order and all its alarms (R-10.4). */
class DeleteOrderUseCase @Inject constructor(
    private val orders: OrderRepository,
    private val alarms: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val publisher: NotificationPublisher,
    private val lock: AlarmMutationLock,
) {

    /** @return false if the order no longer existed. */
    suspend operator fun invoke(orderId: Long): Boolean = lock.withLock {
        orders.getOrder(orderId) ?: return@withLock false
        // Read before deleting: the cascade takes the alarms (and their requestCodes) with it
        val pendingIds = alarms.getPendingByOrder(orderId).map { it.id }
        orders.delete(orderId)
        pendingIds.forEach(scheduler::cancel)
        publisher.cancelForOrder(orderId)
        true
    }
}
