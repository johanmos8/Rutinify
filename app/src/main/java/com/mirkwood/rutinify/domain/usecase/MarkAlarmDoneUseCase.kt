package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.repository.AlarmRepository
import com.mirkwood.rutinify.domain.scheduler.AlarmScheduler
import com.mirkwood.rutinify.domain.scheduler.NotificationPublisher
import javax.inject.Inject

/** "Hecho" from the notification (R-7.4) or from the order detail (R-9.5). */
class MarkAlarmDoneUseCase @Inject constructor(
    private val alarms: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val publisher: NotificationPublisher,
    private val lock: AlarmMutationLock,
) {

    /**
     * @return true if the alarm was PENDING or FIRED and is now DONE. Otherwise nothing changes,
     * but a leftover notification is still removed.
     */
    suspend operator fun invoke(alarmId: Long): Boolean = lock.withLock {
        scheduler.cancelSilence(alarmId) // R-15.8: no cutoff left for an answered alarm
        val alarm = alarms.getById(alarmId)
        val status = alarm?.status
        if (status != AlarmStatus.PENDING && status != AlarmStatus.FIRED) {
            publisher.cancel(alarmId)
            return@withLock false
        }
        alarms.updateStatus(listOf(alarmId), AlarmStatus.DONE)
        publisher.cancel(alarmId)
        // A FIRED alarm has nothing left in AlarmManager
        if (status == AlarmStatus.PENDING) scheduler.cancel(alarmId)
        true
    }
}
