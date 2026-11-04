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
     * Órdenes con pendientes/total, por fecha desc y creación desc (R-8.1, R-8.2).
     * Observa ambas tablas: se re-emite al cambiar cualquier orden o alarma (R-8.6).
     */
    @Query(
        """
        SELECT o.*,
               COALESCE(SUM(CASE WHEN a.status = 'PENDING' THEN 1 ELSE 0 END), 0) AS pending,
               COUNT(a.id) AS total
        FROM routine_order o
        LEFT JOIN alarm_item a ON a.order_id = o.id
        GROUP BY o.id
        ORDER BY o.date DESC, o.created_at DESC, o.id DESC
        """,
    )
    fun observeSummaries(): Flow<List<OrderSummaryRow>>

    /** Orden con todas sus alarmas; emite null si no existe (R-9.1). */
    @Transaction
    @Query("SELECT * FROM routine_order WHERE id = :id")
    fun observeWithAlarms(id: Long): Flow<OrderWithAlarmsRow?>

    /** Órdenes Activas o Pausadas de esa fecha: candidatas a conflicto (R-6.1, R-6.5). */
    @Query("SELECT * FROM routine_order WHERE date = :date AND status IN ('ACTIVE', 'PAUSED') ORDER BY created_at")
    suspend fun findOpenByDate(date: LocalDate): List<RoutineOrderEntity>

    @Insert
    suspend fun insert(order: RoutineOrderEntity): Long

    @Query("SELECT * FROM routine_order WHERE id = :id")
    suspend fun getById(id: Long): RoutineOrderEntity?

    @Query("UPDATE routine_order SET status = :status, updated_at = :at WHERE id = :id")
    suspend fun updateStatus(id: Long, status: OrderStatus, at: Instant)

    /** Borra la orden; sus alarmas caen en cascada. */
    @Query("DELETE FROM routine_order WHERE id = :id")
    suspend fun delete(id: Long)
}
