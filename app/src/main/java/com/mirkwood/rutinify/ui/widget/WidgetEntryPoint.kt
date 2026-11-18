package com.mirkwood.rutinify.ui.widget

import com.mirkwood.rutinify.domain.time.ZoneProvider
import com.mirkwood.rutinify.domain.usecase.MarkAlarmDoneUseCase
import com.mirkwood.rutinify.domain.usecase.ObserveNextAlarmUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/** Glance widgets are not created by Hilt, so they reach the use cases through this entry point (D-79). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun observeNextAlarm(): ObserveNextAlarmUseCase
    fun markAlarmDone(): MarkAlarmDoneUseCase
    fun clock(): Clock
    fun zoneProvider(): ZoneProvider
}
