package com.mirkwood.rutinify.data.local

import kotlinx.coroutines.flow.Flow
import com.mirkwood.rutinify.domain.model.AlarmEventType
import java.time.LocalTime
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.mirkwood.rutinify.domain.model.AlarmStatus
import java.time.Instant

@Dao
interface AlarmItemDao {

    @Insert
    suspend fun insertAll(alarms: List<AlarmItemEntity>): List<Long>

    /** Re-inserts an alarm with its original id (undo after delete, "Deshacer", R-9.4). */
    @Insert
    suspend fun insert(alarm: AlarmItemEntity): Long

    @Query("SELECT * FROM alarm_item WHERE id = :id")
    suspend fun getById(id: Long): AlarmItemEntity?

    /** Pending alarms of ACTIVE orders, with their order date (R-12.1; paused orders excluded, R-10.5). */
    @Query(
        """
        SELECT a.*, o.date AS order_date
        FROM alarm_item a
        INNER JOIN routine_order o ON o.id = a.order_id
        WHERE a.status = 'PENDING' AND o.status = 'ACTIVE'
        ORDER BY a.id
        """,
    )
    suspend fun getPendingForActiveOrders(): List<PendingAlarmRow>

    /** Same alarms with the plan name, observed for the widget (R-21.1, R-21.4). */
    @Query(
        """
        SELECT a.*, o.date AS order_date, o.name AS order_name
        FROM alarm_item a
        INNER JOIN routine_order o ON o.id = a.order_id
        WHERE a.status = 'PENDING' AND o.status = 'ACTIVE'
        ORDER BY a.id
        """,
    )
    fun observeUpcoming(): Flow<List<UpcomingAlarmRow>>

    @Query("SELECT * FROM alarm_item WHERE order_id = :orderId AND status = 'PENDING' ORDER BY id")
    suspend fun getPendingByOrder(orderId: Long): List<AlarmItemEntity>

    /** Changes the status of several alarms; moving to FIRED/DONE also stores `fired_at`/`done_at`. */
    @Query(
        """
        UPDATE alarm_item
        SET status = :status,
            status_changed_at = :at,
            fired_at = CASE WHEN :status = 'FIRED' THEN :at ELSE fired_at END,
            done_at = CASE WHEN :status = 'DONE' THEN :at ELSE done_at END
        WHERE id IN (:ids)
        """,
    )
    suspend fun updateStatus(ids: List<Long>, status: AlarmStatus, at: Instant)

    /** "+5 min": back to PENDING with an absolute instant that overrides the computed time (R-7.5, D-12). */
    @Query(
        """
        UPDATE alarm_item
        SET status = 'PENDING', snoozed_until = :until, status_changed_at = :at
        WHERE id = :id
        """,
    )
    suspend fun snooze(id: Long, until: Instant, at: Instant)

    /** "Voy tarde": only a PENDING alarm moves (R-18.3, D-73). */
    @Query(
        """
        UPDATE alarm_item
        SET time = :time, day_offset = :dayOffset, snoozed_until = :snoozedUntil
        WHERE id = :id AND status = 'PENDING'
        """,
    )
    suspend fun updateSchedule(id: Long, time: LocalTime, dayOffset: Int, snoozedUntil: Instant?)

    @Query("SELECT * FROM alarm_item WHERE order_id = :orderId ORDER BY id")
    suspend fun getByOrder(orderId: Long): List<AlarmItemEntity>

    /** Edit from the detail: only a PENDING alarm changes (R-18.9, D-74). */
    @Query(
        """
        UPDATE alarm_item
        SET time = :time, title = :title, category = :category, snoozed_until = NULL
        WHERE id = :id AND status = 'PENDING'
        """,
    )
    suspend fun updateDetails(id: Long, time: LocalTime, title: String, category: String)

    /** One event per alarm in [ids], with its plan (R-20.1, D-77). */
    @Query("INSERT INTO alarm_event (alarm_id, order_id, type, at) SELECT id, order_id, :type, :at FROM alarm_item WHERE id IN (:ids)")
    suspend fun insertEvents(ids: List<Long>, type: AlarmEventType, at: Instant)

    @Query("SELECT * FROM alarm_event WHERE order_id = :orderId ORDER BY at, id")
    suspend fun getEventsByOrder(orderId: Long): List<AlarmEventEntity>

    @Query("DELETE FROM alarm_item WHERE id = :id")
    suspend fun delete(id: Long)
}
