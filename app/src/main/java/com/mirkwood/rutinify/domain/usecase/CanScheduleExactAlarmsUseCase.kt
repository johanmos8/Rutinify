package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.scheduler.AlarmScheduler
import javax.inject.Inject

/** Whether alarms ring exactly or in a window of up to 10 minutes (R-5.3, R-5.4). */
class CanScheduleExactAlarmsUseCase @Inject constructor(private val scheduler: AlarmScheduler) {
    operator fun invoke(): Boolean = scheduler.canScheduleExact()
}
