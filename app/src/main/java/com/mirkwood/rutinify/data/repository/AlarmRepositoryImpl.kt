package com.mirkwood.rutinify.data.repository

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import com.mirkwood.rutinify.domain.model.UpcomingAlarm
import com.mirkwood.rutinify.domain.model.AlarmEventType
import com.mirkwood.rutinify.domain.model.AlarmEvent
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
        val now = clock.instant()
        val event = when (status) {
            AlarmStatus.DONE -> AlarmEventType.DONE
            AlarmStatus.SKIPPED -> AlarmEventType.SKIPPED
            else -> null
        }
        db.withTransaction {
            alarmDao.updateStatus(alarmIds, status, now)
            if (event != null) alarmDao.insertEvents(alarmIds, event, now)
        }
    }

    override suspend fun snooze(alarmId: Long, until: Instant) {
        val now = clock.instant()
        db.withTransaction {
            alarmDao.snooze(alarmId, until, now)
            alarmDao.insertEvents(listOf(alarmId), AlarmEventType.SNOOZED, now)
        }
    }

    override suspend fun getEventsByOrder(orderId: Long): List<AlarmEvent> =
        alarmDao.getEventsByOrder(orderId).map { AlarmEvent(it.alarmId, it.orderId, it.type, it.at) }

    override suspend fun updateSchedules(updates: List<AlarmScheduleUpdate>) {
        if (updates.isEmpty()) return
        db.withTransaction {
            updates.forEach { alarmDao.updateSchedule(it.alarmId, it.time, it.dayOffset, it.snoozedUntil) }
        }
    }

    override fun observeUpcoming(): Flow<List<UpcomingAlarm>> =
        alarmDao.observeUpcoming().map { rows -> rows.map { UpcomingAlarm(it.alarm.toDomain(), it.orderDate, it.orderName) } }

    override suspend fun getByOrder(orderId: Long): List<AlarmItem> = alarmDao.getByOrder(orderId).map { it.toDomain() }

    override suspend fun updateDetails(alarmId: Long, time: LocalTime, title: String, category: String) =
        alarmDao.updateDetails(alarmId, time, title, category)

    override suspend fun delete(alarmId: Long) = alarmDao.delete(alarmId)

    override suspend fun restore(alarm: AlarmItem) {
        alarmDao.insert(alarm.toEntity())
    }
}
