package com.mirkwood.rutinify.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mirkwood.rutinify.domain.model.OrderStatus
import java.time.Instant
import java.time.LocalDate

/** Tabla `routine_order` (design §5). `autoGenerate` emite AUTOINCREMENT: los ids nunca se reutilizan. */
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
    /** JSON original extraído (depuración / compartir en v2). */
    @ColumnInfo(name = "raw_json") val rawJson: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)
