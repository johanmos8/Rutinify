package com.mirkwood.rutinify.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalTime

/** Day type of a recurring plan (R-23.7, D-81); deleted with its plan. */
@Entity(
    tableName = "routine_activity",
    foreignKeys = [
        ForeignKey(entity = RoutineOrderEntity::class, parentColumns = ["id"], childColumns = ["order_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("order_id")],
)
data class RoutineActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "order_id") val orderId: Long,
    val position: Int,
    val time: LocalTime,
    @ColumnInfo(name = "day_offset") val dayOffset: Int,
    val title: String,
    val description: String?,
    @ColumnInfo(name = "duration_min") val durationMin: Int?,
    val category: String,
    val assignee: String?,
)
