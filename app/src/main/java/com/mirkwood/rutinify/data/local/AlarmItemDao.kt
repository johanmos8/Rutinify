package com.mirkwood.rutinify.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** Operaciones básicas de T-05; el resto de consultas de design §5 llegan en T-06. */
@Dao
interface AlarmItemDao {

    @Insert
    suspend fun insertAll(alarms: List<AlarmItemEntity>): List<Long>

    @Query("SELECT * FROM alarm_item WHERE id = :id")
    suspend fun getById(id: Long): AlarmItemEntity?

    @Query("DELETE FROM alarm_item WHERE id = :id")
    suspend fun delete(id: Long)
}
