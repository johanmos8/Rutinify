package com.mirkwood.rutinify.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mirkwood.rutinify.domain.model.OrderStatus
import java.time.Instant
import java.time.LocalDate

/** Table `routine_order` (design §5). `autoGenerate` emits AUTOINCREMENT: ids are never reused. */
@Entity(
    tableName = "routine_order",
    indices = [Index("date")],
)
data class RoutineOrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val date: LocalDate,
    val notes: String?,
    val status: OrderStatus,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int,
    /** Original extracted JSON (debugging / sharing in v2). */
    @ColumnInfo(name = "raw_json") val rawJson: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)
