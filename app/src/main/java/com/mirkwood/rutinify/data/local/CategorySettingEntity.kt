package com.mirkwood.rutinify.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Whether the alarms of a category ring as real alarms (R-15.1, D-66). Global: keyed by the normalized category name,
 * the same in every plan. A category without a row is not critical.
 */
@Entity(tableName = "category_setting")
data class CategorySettingEntity(
    @PrimaryKey val name: String,
    @ColumnInfo(name = "critical") val critical: Boolean,
)
