package com.mirkwood.rutinify.data.receiver

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/** T-18: `goAsync()` work always finishes the broadcast (CLAUDE.md §6). Robolectric for `Log`. */
@OptIn(ExperimentalCoroutinesApi::class) // advanceTimeBy, runCurrent
@RunWith(AndroidJUnit4::class)
class ReceiverWorkTest {

    @Test
    fun `termina el broadcast cuando el trabajo acaba`() = runTest {
        var finished = 0
        var ran = false

        launchReceiverWork(finish = { finished++ }) { ran = true }
        advanceUntilIdle()

        assertThat(ran).isTrue()
        assertThat(finished).isEqualTo(1)
    }

    @Test
    fun `termina el broadcast aunque el trabajo falle`() = runTest {
        var finished = 0

        launchReceiverWork(finish = { finished++ }) { error("boom") }
        advanceUntilIdle()

        assertThat(finished).isEqualTo(1)
    }

    @Test
    fun `corta el trabajo a los 9 segundos y termina el broadcast`() = runTest {
        var finished = 0
        var completed = false

        launchReceiverWork(finish = { finished++ }) {
            delay(10_000)
            completed = true
        }
        advanceTimeBy(RECEIVER_TIMEOUT_MILLIS - 1)
        runCurrent()
        assertThat(finished).isEqualTo(0)

        advanceTimeBy(2)
        runCurrent()
        assertThat(finished).isEqualTo(1)
        assertThat(completed).isFalse()
    }

    @Test
    fun `si se cancela el scope termina el broadcast y propaga la cancelacion`() = runTest {
        var finished = 0

        val job = launchReceiverWork(finish = { finished++ }) { awaitCancellation() }
        runCurrent()
        job.cancel()
        runCurrent()

        assertThat(finished).isEqualTo(1)
        assertThat(job.isCancelled).isTrue()
    }
}
