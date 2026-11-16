package com.mirkwood.rutinify.di

import com.mirkwood.rutinify.data.repository.AlarmRepositoryImpl
import com.mirkwood.rutinify.data.repository.CategorySettingsRepositoryImpl
import com.mirkwood.rutinify.data.repository.OrderRepositoryImpl
import com.mirkwood.rutinify.domain.repository.AlarmRepository
import com.mirkwood.rutinify.domain.repository.CategorySettingsRepository
import com.mirkwood.rutinify.domain.repository.OrderRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindOrderRepository(impl: OrderRepositoryImpl): OrderRepository

    @Binds
    @Singleton
    abstract fun bindAlarmRepository(impl: AlarmRepositoryImpl): AlarmRepository

    @Binds
    @Singleton
    abstract fun bindCategorySettingsRepository(impl: CategorySettingsRepositoryImpl): CategorySettingsRepository
}
