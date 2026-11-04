package com.mirkwood.rutinify.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.mirkwood.rutinify.domain.model.AlarmStatus
import java.time.Instant

@Dao
interface AlarmItemDao {

    @Insert
    suspend fun insertAll(alarms: List<AlarmItemEntity>): List<Long>

    /** Reinserta una alarma con su id original ("Deshacer" tras eliminar, R-9.4). */
    @Insert
    suspend fun insert(alarm: AlarmItemEntity): Long

    @Query("SELECT * FROM alarm_item WHERE id = :id")
    suspend fun getById(id: Long): AlarmItemEntity?

    /** Pendientes de órdenes Activas con la fecha de su orden (R-12.1; pausadas fuera, R-10.5). */
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

    @Query("SELECT * FROM alarm_item WHERE order_id = :orderId AND status = 'PENDING' ORDER BY id")
    suspend fun getPendingByOrder(orderId: Long): List<AlarmItemEntity>

    /** Cambia el estado de varias alarmas; al pasar a FIRED/DONE guarda también `fired_at`/`done_at`. */
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

    /** "+5 min": vuelve a PENDING con un instante absoluto que manda sobre la hora calculada (R-7.5, D-12). */
    @Query(
        """
        UPDATE alarm_item
        SET status = 'PENDING', snoozed_until = :until, status_changed_at = :at
        WHERE id = :id
        """,
    )
    suspend fun snooze(id: Long, until: Instant, at: Instant)

    @Query("DELETE FROM alarm_item WHERE id = :id")
    suspend fun delete(id: Long)
}
