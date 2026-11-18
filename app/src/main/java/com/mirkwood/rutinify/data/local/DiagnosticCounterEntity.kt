package com.mirkwood.rutinify.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** One local diagnostic counter (R-17.2, D-72). */
@Entity(tableName = "diagnostic_counter")
data class DiagnosticCounterEntity(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "count") val count: Int,
)
