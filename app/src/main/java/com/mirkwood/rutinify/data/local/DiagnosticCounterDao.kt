package com.mirkwood.rutinify.data.local

import androidx.room.Dao
import androidx.room.Query

@Dao
interface DiagnosticCounterDao {

    /** Adds one to [key], creating it at 1 (R-17.2). */
    @Query("INSERT INTO diagnostic_counter (`key`, count) VALUES (:key, 1) ON CONFLICT(`key`) DO UPDATE SET count = count + 1")
    suspend fun increment(key: String)

    @Query("SELECT * FROM diagnostic_counter ORDER BY `key`")
    suspend fun getAll(): List<DiagnosticCounterEntity>

    @Query("DELETE FROM diagnostic_counter")
    suspend fun clear()
}
