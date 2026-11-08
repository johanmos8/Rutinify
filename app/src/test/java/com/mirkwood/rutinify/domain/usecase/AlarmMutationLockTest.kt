package com.mirkwood.rutinify.domain.usecase

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** T-17: alarm mutations from receivers and UI never interleave (design §6.4). */
@OptIn(ExperimentalCoroutinesApi::class) // runCurrent
class AlarmMutationLockTest {

    @Test
    fun `una segunda mutacion espera a que termine la primera`() = runTest {
        val lock = AlarmMutationLock()
        val events = mutableListOf<String>()
        val release = CompletableDeferred<Unit>()

        launch {
            lock.withLock {
                events += "a-start"
                release.await()
                events += "a-end"
            }
        }
        launch { lock.withLock { events += "b" } }
        runCurrent()
        assertThat(events).containsExactly("a-start")

        release.complete(Unit)
        runCurrent()
        assertThat(events).containsExactly("a-start", "a-end", "b").inOrder()
    }

    @Test
    fun `devuelve el resultado del bloque`() = runTest {
        assertThat(AlarmMutationLock().withLock { 42 }).isEqualTo(42)
    }
}
