package com.mirkwood.rutinify.data.alarm

import com.mirkwood.rutinify.domain.scheduler.SilencedAlarms
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Process-wide memory of silenced alarms (R-15.9, R-15.10); lost with the process, like the alarm screen. */
@Singleton
class InMemorySilencedAlarms @Inject constructor() : SilencedAlarms {

    private val ids = MutableStateFlow<Set<Long>>(emptySet())

    override fun observe(): Flow<Set<Long>> = ids.asStateFlow()

    override fun add(alarmId: Long) = ids.update { it + alarmId }
}
