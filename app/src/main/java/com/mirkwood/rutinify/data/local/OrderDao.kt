package com.mirkwood.rutinify.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.mirkwood.rutinify.domain.model.OrderStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface OrderDao {

    /**
     * Orders with pending/total counts, by date desc then creation desc (R-8.1, R-8.2).
     * Observes both tables: re-emits when any order or alarm changes (R-8.6).
     */
    @Query(
        """
        SELECT o.*,
               COALESCE(SUM(CASE WHEN a.status = 'PENDING' THEN 1 ELSE 0 END), 0) AS pending,
               COUNT(a.id) AS total,
               COALESCE(MAX(a.day_offset), 0) AS max_day_offset
        FROM routine_order o
        LEFT JOIN alarm_item a ON a.order_id = o.id
        GROUP BY o.id
        ORDER BY o.date DESC, o.created_at DESC, o.id DESC
        """,
    )
    fun observeSummaries(): Flow<List<OrderSummaryRow>>

    /** Order with all its alarms; emits null if it does not exist (R-9.1). */
    @Transaction
    @Query("SELECT * FROM routine_order WHERE id = :id")
    fun observeWithAlarms(id: Long): Flow<OrderWithAlarmsRow?>

    /**
     * ACTIVE or PAUSED orders whose conflict days touch [start]..[end] (R-6.1, R-6.5, R-6.6, D-56, D-58), with
     * their largest `day_offset`. Like `RoutineSpan.forConflicts`, a stored order only counts beyond its date
     * when that offset is 2 (`MULTI_DAY_MIN_OFFSET`) or more. Dates are ISO text, so SQLite's `date()` and
     * string comparison work without a schema change.
     */
    @Query(
        """
        SELECT * FROM (
            SELECT o.*,
                   IFNULL((SELECT MAX(a.day_offset) FROM alarm_item a WHERE a.order_id = o.id), 0) AS max_day_offset
            FROM routine_order o
            WHERE o.status IN ('ACTIVE', 'PAUSED') AND o.date <= :end
        )
        WHERE date(date, '+' || (CASE WHEN max_day_offset >= 2 THEN max_day_offset ELSE 0 END) || ' days') >= :start
        ORDER BY created_at, id
        """,
    )
    suspend fun findOpenOverlapping(start: LocalDate, end: LocalDate): List<ConflictingOrderRow>

    @Insert
    suspend fun insert(order: RoutineOrderEntity): Long

    @Query("SELECT * FROM routine_order WHERE id = :id")
    suspend fun getById(id: Long): RoutineOrderEntity?

    @Query("UPDATE routine_order SET status = :status, updated_at = :at WHERE id = :id")
    suspend fun updateStatus(id: Long, status: OrderStatus, at: Instant)

    /** Deletes the order; its alarms are deleted by cascade. */
    @Query("DELETE FROM routine_order WHERE id = :id")
    suspend fun delete(id: Long)
}
