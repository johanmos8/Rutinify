package com.mirkwood.rutinify.domain.repository

import com.mirkwood.rutinify.domain.model.CategorySetting
import kotlinx.coroutines.flow.Flow

/** Critical categories, global across plans (R-15.1, D-66). */
interface CategorySettingsRepository {

    /** Categories of the saved plans, sorted by name, with their mark. */
    fun observe(): Flow<List<CategorySetting>>

    suspend fun setCritical(category: String, critical: Boolean)

    suspend fun criticalNames(): Set<String>
}
