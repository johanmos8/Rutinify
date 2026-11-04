package com.mirkwood.rutinify.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** Operaciones básicas de T-05; las consultas observables llegan en T-06. */
@Dao
interface OrderDao {

    @Insert
    suspend fun insert(order: RoutineOrderEntity): Long

    @Query("SELECT * FROM routine_order WHERE id = :id")
    suspend fun getById(id: Long): RoutineOrderEntity?

    /** Borra la orden; sus alarmas caen en cascada. */
    @Query("DELETE FROM routine_order WHERE id = :id")
    suspend fun delete(id: Long)
}
