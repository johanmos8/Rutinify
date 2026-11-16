package com.mirkwood.rutinify.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/** Local database: source of truth for orders and alarms (design §1, §5). Schema in `app/schemas/`. */
@Database(
    entities = [RoutineOrderEntity::class, AlarmItemEntity::class, CategorySettingEntity::class],
    // Versions and their migrations: design D-83 and `Migrations`
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class RutinifyDatabase : RoomDatabase() {

    abstract fun orderDao(): OrderDao

    abstract fun alarmItemDao(): AlarmItemDao

    abstract fun categorySettingDao(): CategorySettingDao

    companion object {
        const val NAME = "rutinify.db"
    }
}
