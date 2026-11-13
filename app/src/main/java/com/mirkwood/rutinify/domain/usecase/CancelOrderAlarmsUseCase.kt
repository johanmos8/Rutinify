package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.repository.OrderRepository
import com.mirkwood.rutinify.domain.scheduler.AlarmScheduler
import com.mirkwood.rutinify.domain.scheduler.NotificationPublisher
import javax.inject.Inject

/** "Eliminar todas las alarmas": cancels the order but keeps its history (R-10.2, R-10.3). */
class CancelOrderAlarmsUseCase @Inject constructor(
    private val orders: OrderRepository,
    private val scheduler: AlarmScheduler,
    private val publisher: NotificationPublisher,
    private val lock: AlarmMutationLock,
) {

    /** @return how many pending alarms were cancelled; null if the order no longer exists. */
    suspend operator fun invoke(orderId: Long): Int? = lock.withLock {
        orders.getOrder(orderId) ?: return@withLock null
        // Room first (one transaction), then the system
        val cancelledIds = orders.cancelOrder(orderId)
        cancelledIds.forEach(scheduler::cancel)
        publisher.cancelForOrder(orderId)
        cancelledIds.size
    }
}
