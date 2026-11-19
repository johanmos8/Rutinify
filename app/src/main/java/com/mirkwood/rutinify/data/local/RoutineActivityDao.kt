package com.mirkwood.rutinify.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface RoutineActivityDao {

    @Insert
    suspend fun insertAll(activities: List<RoutineActivityEntity>): List<Long>

    @Query("SELECT * FROM routine_activity WHERE order_id = :orderId ORDER BY day_offset, time, position")
    suspend fun getByOrder(orderId: Long): List<RoutineActivityEntity>
}
