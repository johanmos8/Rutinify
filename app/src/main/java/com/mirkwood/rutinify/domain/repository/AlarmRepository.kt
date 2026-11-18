package com.mirkwood.rutinify.domain.repository

import com.mirkwood.rutinify.domain.model.AlarmScheduleUpdate
import com.mirkwood.rutinify.domain.model.AlarmItem
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.PendingAlarm
import java.time.Instant

interface AlarmRepository {

    suspend fun getById(alarmId: Long): AlarmItem?

    /** Pending alarms of ACTIVE orders, with their order date (R-12.1). */
    suspend fun getPendingForActiveOrders(): List<PendingAlarm>

    suspend fun getPendingByOrder(orderId: Long): List<AlarmItem>

    /** Changes the status of several alarms at once, using the current time as the change time. */
    suspend fun updateStatus(alarmIds: List<Long>, status: AlarmStatus)

    /** Back to PENDING with an absolute trigger instant (R-7.5, D-12). */
    suspend fun snooze(alarmId: Long, until: Instant)

    /** Rewrites time, day and snooze of pending alarms in one transaction; non-pending ones are left alone (D-73). */
    suspend fun updateSchedules(updates: List<AlarmScheduleUpdate>)

    /** Deletes the alarm (R-9.3). */
    suspend fun delete(alarmId: Long)

    /** Re-inserts a deleted alarm with the same id (undo, "Deshacer", R-9.4). */
    suspend fun restore(alarm: AlarmItem)
}
