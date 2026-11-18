package com.mirkwood.rutinify.data.repository

import java.time.LocalTime
import com.mirkwood.rutinify.domain.model.AlarmScheduleUpdate
import com.mirkwood.rutinify.data.local.RutinifyDatabase
import androidx.room.withTransaction
import com.mirkwood.rutinify.data.local.AlarmItemDao
import com.mirkwood.rutinify.domain.model.AlarmItem
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.PendingAlarm
import com.mirkwood.rutinify.domain.repository.AlarmRepository
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

class AlarmRepositoryImpl @Inject constructor(
    private val db: RutinifyDatabase,
    private val alarmDao: AlarmItemDao,
    private val clock: Clock,
) : AlarmRepository {

    override suspend fun getById(alarmId: Long): AlarmItem? = alarmDao.getById(alarmId)?.toDomain()

    override suspend fun getPendingForActiveOrders(): List<PendingAlarm> =
        alarmDao.getPendingForActiveOrders().map { it.toDomain() }

    override suspend fun getPendingByOrder(orderId: Long): List<AlarmItem> =
        alarmDao.getPendingByOrder(orderId).map { it.toDomain() }

    override suspend fun updateStatus(alarmIds: List<Long>, status: AlarmStatus) {
        if (alarmIds.isEmpty()) return
        alarmDao.updateStatus(alarmIds, status, clock.instant())
    }

    override suspend fun snooze(alarmId: Long, until: Instant) =
        alarmDao.snooze(alarmId, until, clock.instant())

    override suspend fun updateSchedules(updates: List<AlarmScheduleUpdate>) {
        if (updates.isEmpty()) return
        db.withTransaction {
            updates.forEach { alarmDao.updateSchedule(it.alarmId, it.time, it.dayOffset, it.snoozedUntil) }
        }
    }

    override suspend fun getByOrder(orderId: Long): List<AlarmItem> = alarmDao.getByOrder(orderId).map { it.toDomain() }

    override suspend fun updateDetails(alarmId: Long, time: LocalTime, title: String, category: String) =
        alarmDao.updateDetails(alarmId, time, title, category)

    override suspend fun delete(alarmId: Long) = alarmDao.delete(alarmId)

    override suspend fun restore(alarm: AlarmItem) {
        alarmDao.insert(alarm.toEntity())
    }
}
