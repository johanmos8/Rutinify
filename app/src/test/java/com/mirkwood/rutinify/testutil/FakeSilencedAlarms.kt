package com.mirkwood.rutinify.testutil

import com.mirkwood.rutinify.domain.scheduler.SilencedAlarms
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSilencedAlarms : SilencedAlarms {

    val ids = MutableStateFlow<Set<Long>>(emptySet())

    override fun observe(): Flow<Set<Long>> = ids

    override fun add(alarmId: Long) {
        ids.value = ids.value + alarmId
    }
}
