package com.mirkwood.rutinify.di

import com.mirkwood.rutinify.domain.system.AppInfo
import com.mirkwood.rutinify.data.system.AndroidAppInfo
import com.mirkwood.rutinify.domain.diagnostics.DiagnosticsRepository
import com.mirkwood.rutinify.domain.diagnostics.DiagnosticsRecorder
import com.mirkwood.rutinify.data.repository.DiagnosticsRepositoryImpl
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

    @Binds
    abstract fun bindDiagnosticsRepository(impl: DiagnosticsRepositoryImpl): DiagnosticsRepository

    @Binds
    abstract fun bindDiagnosticsRecorder(impl: DiagnosticsRepositoryImpl): DiagnosticsRecorder

    @Binds
    abstract fun bindAppInfo(impl: AndroidAppInfo): AppInfo
}
