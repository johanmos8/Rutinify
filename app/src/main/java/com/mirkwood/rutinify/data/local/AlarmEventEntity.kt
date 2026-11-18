package com.mirkwood.rutinify.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mirkwood.rutinify.domain.model.AlarmEventType
import java.time.Instant

/** History of an alarm (R-20.1, D-77); deleted with its plan. */
@Entity(
    tableName = "alarm_event",
    foreignKeys = [
        ForeignKey(entity = RoutineOrderEntity::class, parentColumns = ["id"], childColumns = ["order_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("order_id")],
)
data class AlarmEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "alarm_id") val alarmId: Long,
    @ColumnInfo(name = "order_id") val orderId: Long,
    val type: AlarmEventType,
    val at: Instant,
)
