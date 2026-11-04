package com.mirkwood.rutinify.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/** Base de datos local: fuente de verdad de órdenes y alarmas (design §1, §5). Esquema en `app/schemas/`. */
@Database(
    entities = [RoutineOrderEntity::class, AlarmItemEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class RutinifyDatabase : RoomDatabase() {

    abstract fun orderDao(): OrderDao

    abstract fun alarmItemDao(): AlarmItemDao

    companion object {
        const val NAME = "rutinify.db"
    }
}
