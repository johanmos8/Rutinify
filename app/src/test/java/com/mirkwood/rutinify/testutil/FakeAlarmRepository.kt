package com.mirkwood.rutinify.testutil

import com.mirkwood.rutinify.domain.model.AlarmItem
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.PendingAlarm
import com.mirkwood.rutinify.domain.repository.AlarmRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** In-memory [AlarmRepository]; mirrors the DAO's `fired_at`/`done_at` handling. */
class FakeAlarmRepository(
    private val clock: Clock,
    private val orderDates: (orderId: Long) -> LocalDate? = { null },
) : AlarmRepository {

    val alarms = linkedMapOf<Long, AlarmItem>()

    fun add(vararg items: AlarmItem) = items.forEach { alarms[it.id] = it }

    override suspend fun getById(alarmId: Long): AlarmItem? = alarms[alarmId]

    override suspend fun getPendingForActiveOrders(): List<PendingAlarm> =
        alarms.values.filter { it.status == AlarmStatus.PENDING }
            .mapNotNull { alarm -> orderDates(alarm.orderId)?.let { PendingAlarm(alarm, it) } }

    override suspend fun getPendingByOrder(orderId: Long): List<AlarmItem> =
        alarms.values.filter { it.orderId == orderId && it.status == AlarmStatus.PENDING }

    override suspend fun updateStatus(alarmIds: List<Long>, status: AlarmStatus) {
        val now = clock.instant()
        alarmIds.forEach { id ->
            alarms[id]?.let { alarm ->
                alarms[id] = alarm.copy(
                    status = status,
                    statusChangedAt = now,
                    firedAt = if (status == AlarmStatus.FIRED) now else alarm.firedAt,
                    doneAt = if (status == AlarmStatus.DONE) now else alarm.doneAt,
                )
            }
        }
    }

    override suspend fun snooze(alarmId: Long, until: Instant) {
        alarms[alarmId]?.let {
            alarms[alarmId] = it.copy(status = AlarmStatus.PENDING, snoozedUntil = until, statusChangedAt = clock.instant())
        }
    }

    override suspend fun delete(alarmId: Long) {
        alarms.remove(alarmId)
    }

    override suspend fun restore(alarm: AlarmItem) {
        alarms[alarm.id] = alarm
    }
}
