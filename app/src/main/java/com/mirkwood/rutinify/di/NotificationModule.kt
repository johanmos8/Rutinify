package com.mirkwood.rutinify.di

import android.app.NotificationManager
import android.content.Context
import com.mirkwood.rutinify.data.notification.AndroidNotificationPublisher
import com.mirkwood.rutinify.domain.scheduler.NotificationPublisher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {

    @Binds
    abstract fun bindNotificationPublisher(impl: AndroidNotificationPublisher): NotificationPublisher

    companion object {
        @Provides
        fun provideNotificationManager(@ApplicationContext context: Context): NotificationManager =
            context.getSystemService(NotificationManager::class.java)
    }
}
