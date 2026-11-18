package com.mirkwood.rutinify.testutil

import com.mirkwood.rutinify.domain.model.AlarmEventType
import com.mirkwood.rutinify.domain.model.AlarmEvent
import java.time.LocalTime
import com.mirkwood.rutinify.domain.model.AlarmItem
import com.mirkwood.rutinify.domain.model.AlarmScheduleUpdate
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

    /** History written by [updateStatus] and [snooze], like the real repository (D-77). */
    val events = mutableListOf<AlarmEvent>()

    fun add(vararg items: AlarmItem) = items.forEach { alarms[it.id] = it }

    override suspend fun getById(alarmId: Long): AlarmItem? = alarms[alarmId]

    override suspend fun getPendingForActiveOrders(): List<PendingAlarm> =
        alarms.values.filter { it.status == AlarmStatus.PENDING }
            .mapNotNull { alarm -> orderDates(alarm.orderId)?.let { PendingAlarm(alarm, it) } }

    override suspend fun getPendingByOrder(orderId: Long): List<AlarmItem> =
        alarms.values.filter { it.orderId == orderId && it.status == AlarmStatus.PENDING }

    override suspend fun updateStatus(alarmIds: List<Long>, status: AlarmStatus) {
        val now = clock.instant()
        val type = when (status) {
            AlarmStatus.DONE -> AlarmEventType.DONE
            AlarmStatus.SKIPPED -> AlarmEventType.SKIPPED
            else -> null
        }
        if (type != null) alarmIds.mapNotNull { alarms[it] }.forEach { events += AlarmEvent(it.id, it.orderId, type, now) }
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
        alarms[alarmId]?.let { events += AlarmEvent(it.id, it.orderId, AlarmEventType.SNOOZED, clock.instant()) }
        alarms[alarmId]?.let {
            alarms[alarmId] = it.copy(status = AlarmStatus.PENDING, snoozedUntil = until, statusChangedAt = clock.instant())
        }
    }

    override suspend fun updateSchedules(updates: List<AlarmScheduleUpdate>) {
        updates.forEach { update ->
            alarms[update.alarmId]?.takeIf { it.status == AlarmStatus.PENDING }?.let {
                alarms[update.alarmId] = it.copy(time = update.time, dayOffset = update.dayOffset, snoozedUntil = update.snoozedUntil)
            }
        }
    }

    override suspend fun getEventsByOrder(orderId: Long): List<AlarmEvent> = events.filter { it.orderId == orderId }

    override suspend fun getByOrder(orderId: Long): List<AlarmItem> = alarms.values.filter { it.orderId == orderId }

    override suspend fun updateDetails(alarmId: Long, time: LocalTime, title: String, category: String) {
        alarms[alarmId]?.takeIf { it.status == AlarmStatus.PENDING }?.let {
            alarms[alarmId] = it.copy(time = time, title = title, category = category, snoozedUntil = null)
        }
    }

    override suspend fun delete(alarmId: Long) {
        alarms.remove(alarmId)
    }

    override suspend fun restore(alarm: AlarmItem) {
        alarms[alarm.id] = alarm
    }
}
