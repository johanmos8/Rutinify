package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.model.AlarmItem
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.repository.AlarmRepository
import com.mirkwood.rutinify.domain.scheduler.AlarmScheduler
import com.mirkwood.rutinify.domain.scheduler.NotificationPublisher
import javax.inject.Inject

/** Deletes one alarm from the order detail (R-9.3). */
class DeleteAlarmUseCase @Inject constructor(
    private val alarms: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val publisher: NotificationPublisher,
    private val lock: AlarmMutationLock,
) {

    /** @return the deleted alarm, exactly as stored, for "Deshacer" (R-9.4); null if it no longer existed. */
    suspend operator fun invoke(alarmId: Long): AlarmItem? = lock.withLock {
        val alarm = alarms.getById(alarmId) ?: return@withLock null
        // Room first, then the system
        alarms.delete(alarmId)
        if (alarm.status == AlarmStatus.PENDING) scheduler.cancel(alarmId)
        publisher.cancel(alarmId)
        alarm
    }
}
