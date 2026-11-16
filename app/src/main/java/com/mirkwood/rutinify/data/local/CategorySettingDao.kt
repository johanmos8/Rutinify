package com.mirkwood.rutinify.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategorySettingDao {

    /**
     * Every category used by a saved plan, with its mark (R-15.1): categories without a row are not critical.
     * Re-emits when alarms or marks change.
     */
    @Query(
        """
        SELECT c.category AS name, COALESCE(s.critical, 0) AS critical
        FROM (SELECT DISTINCT category FROM alarm_item) c
        LEFT JOIN category_setting s ON s.name = c.category
        ORDER BY c.category
        """,
    )
    fun observeUsed(): Flow<List<CategorySettingEntity>>

    @Upsert
    suspend fun upsert(setting: CategorySettingEntity)

    @Query("SELECT name FROM category_setting WHERE critical = 1")
    suspend fun criticalNames(): List<String>
}
