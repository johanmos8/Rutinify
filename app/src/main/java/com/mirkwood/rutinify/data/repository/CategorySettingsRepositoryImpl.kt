package com.mirkwood.rutinify.data.repository

import com.mirkwood.rutinify.data.local.CategorySettingDao
import com.mirkwood.rutinify.data.local.CategorySettingEntity
import com.mirkwood.rutinify.domain.model.CategorySetting
import com.mirkwood.rutinify.domain.repository.CategorySettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CategorySettingsRepositoryImpl @Inject constructor(
    private val dao: CategorySettingDao,
) : CategorySettingsRepository {

    override fun observe(): Flow<List<CategorySetting>> =
        dao.observeUsed().map { rows -> rows.map { CategorySetting(it.name, it.critical) } }

    override suspend fun setCritical(category: String, critical: Boolean) {
        dao.upsert(CategorySettingEntity(category, critical))
    }

    override suspend fun criticalNames(): Set<String> = dao.criticalNames().toSet()
}
