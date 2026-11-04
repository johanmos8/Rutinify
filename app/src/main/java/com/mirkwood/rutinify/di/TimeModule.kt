package com.mirkwood.rutinify.di

import com.mirkwood.rutinify.data.system.SystemZoneProvider
import com.mirkwood.rutinify.domain.time.ZoneProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
abstract class TimeModule {

    @Binds
    abstract fun bindZoneProvider(impl: SystemZoneProvider): ZoneProvider

    companion object {
        /** Only used to get the current instant; the zone comes from [ZoneProvider]. */
        @Provides
        fun provideClock(): Clock = Clock.systemDefaultZone()
    }
}
