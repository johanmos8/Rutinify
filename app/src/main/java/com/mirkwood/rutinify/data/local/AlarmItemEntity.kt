package com.mirkwood.rutinify.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mirkwood.rutinify.domain.model.AlarmStatus
import java.time.Instant
import java.time.LocalTime

/**
 * Table `alarm_item` (design §5). The [id] is the `PendingIntent` `requestCode` (design §6.1),
 * hence AUTOINCREMENT: a deleted id is never reused.
 */
@Entity(
    tableName = "alarm_item",
    foreignKeys = [
        ForeignKey(
            entity = RoutineOrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["order_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("order_id"),
        Index("status", "order_id"),
    ],
)
data class AlarmItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "order_id") val orderId: Long,
    val position: Int,
    /** Wall-clock time `HH:mm`. */
    val time: LocalTime,
    @ColumnInfo(name = "day_offset") val dayOffset: Int,
    val title: String,
    val description: String?,
    @ColumnInfo(name = "duration_min") val durationMin: Int?,
    val category: String,
    val assignee: String?,
    val status: AlarmStatus,
    /** Absolute snooze instant; when not null it overrides the computed time (D-12). */
    @ColumnInfo(name = "snoozed_until") val snoozedUntil: Instant?,
    @ColumnInfo(name = "fired_at") val firedAt: Instant?,
    @ColumnInfo(name = "done_at") val doneAt: Instant?,
    @ColumnInfo(name = "status_changed_at") val statusChangedAt: Instant,
)
