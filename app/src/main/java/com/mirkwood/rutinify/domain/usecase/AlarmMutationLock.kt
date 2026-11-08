package com.mirkwood.rutinify.domain.usecase

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared by every use case that changes alarms, so a receiver and the UI never interleave their
 * read-modify-write sequences (design §6.4). Not reentrant: a use case must not call another inside it.
 */
@Singleton
class AlarmMutationLock @Inject constructor() {

    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
