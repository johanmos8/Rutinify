package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.model.CategorySetting
import com.mirkwood.rutinify.domain.repository.CategorySettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Categories of the saved plans with their critical mark, sorted by name (R-15.1). */
class ObserveCategorySettingsUseCase @Inject constructor(private val categories: CategorySettingsRepository) {
    operator fun invoke(): Flow<List<CategorySetting>> = categories.observe()
}
