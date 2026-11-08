package com.mirkwood.rutinify.di

import android.app.AlarmManager
import android.content.Context
import com.mirkwood.rutinify.data.alarm.AndroidAlarmScheduler
import com.mirkwood.rutinify.domain.scheduler.AlarmScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AlarmModule {

    @Binds
    abstract fun bindAlarmScheduler(impl: AndroidAlarmScheduler): AlarmScheduler

    companion object {
        @Provides
        fun provideAlarmManager(@ApplicationContext context: Context): AlarmManager =
            context.getSystemService(AlarmManager::class.java)
    }
}
