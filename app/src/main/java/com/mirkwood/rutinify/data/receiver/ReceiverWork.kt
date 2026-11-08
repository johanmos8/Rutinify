package com.mirkwood.rutinify.data.receiver

import android.util.Log
import com.mirkwood.rutinify.data.LOG_TAG
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Stays under the ~10 s a `goAsync()` broadcast may take (CLAUDE.md §6). */
internal const val RECEIVER_TIMEOUT_MILLIS = 9_000L

/**
 * Runs [block] for a `goAsync()` receiver in the application scope. [finish] (the
 * `PendingResult.finish`) always runs; failures are logged at this boundary (CLAUDE.md §5).
 */
internal fun CoroutineScope.launchReceiverWork(
    finish: () -> Unit,
    timeoutMillis: Long = RECEIVER_TIMEOUT_MILLIS,
    block: suspend () -> Unit,
): Job = launch {
    try {
        withTimeout(timeoutMillis) { block() }
    } catch (e: TimeoutCancellationException) {
        Log.e(LOG_TAG, "Receiver work timed out after $timeoutMillis ms", e)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(LOG_TAG, "Receiver work failed", e)
    } finally {
        finish()
    }
}
