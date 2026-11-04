package com.mirkwood.rutinify.di

import android.content.Context
import androidx.room.Room
import com.mirkwood.rutinify.data.local.AlarmItemDao
import com.mirkwood.rutinify.data.local.OrderDao
import com.mirkwood.rutinify.data.local.RutinifyDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RutinifyDatabase =
        Room.databaseBuilder(context, RutinifyDatabase::class.java, RutinifyDatabase.NAME).build()

    @Provides
    fun provideOrderDao(db: RutinifyDatabase): OrderDao = db.orderDao()

    @Provides
    fun provideAlarmItemDao(db: RutinifyDatabase): AlarmItemDao = db.alarmItemDao()
}
