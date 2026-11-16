package com.mirkwood.rutinify.testutil

import com.mirkwood.rutinify.domain.model.CategorySetting
import com.mirkwood.rutinify.domain.repository.CategorySettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/** Marks in memory; [used] stands for the categories of the saved plans. */
class FakeCategorySettingsRepository : CategorySettingsRepository {

    val used = MutableStateFlow<List<String>>(emptyList())
    private val marks = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    override fun observe(): Flow<List<CategorySetting>> =
        combine(used, marks) { names, critical -> names.sorted().distinct().map { CategorySetting(it, critical[it] == true) } }

    override suspend fun setCritical(category: String, critical: Boolean) {
        marks.value = marks.value + (category to critical)
    }

    override suspend fun criticalNames(): Set<String> = marks.value.filterValues { it }.keys
}
