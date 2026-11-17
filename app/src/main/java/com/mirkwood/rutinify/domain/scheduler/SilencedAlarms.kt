package com.mirkwood.rutinify.domain.scheduler

import kotlinx.coroutines.flow.Flow

/**
 * Critical alarms silenced while this process runs (R-15.9, R-15.10), so an open alarm screen closes even when the
 * silence comes from the notification or from the 10-minute cutoff. Not persisted: the screen does not outlive it.
 */
interface SilencedAlarms {
    fun observe(): Flow<Set<Long>>
    fun add(alarmId: Long)
}
