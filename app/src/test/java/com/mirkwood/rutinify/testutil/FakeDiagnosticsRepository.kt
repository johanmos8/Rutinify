package com.mirkwood.rutinify.testutil

import com.mirkwood.rutinify.domain.diagnostics.DiagnosticEvent
import com.mirkwood.rutinify.domain.diagnostics.DiagnosticsRepository

/** Counters in memory, with the same switch rules as the real one. */
class FakeDiagnosticsRepository(private var enabled: Boolean = false) : DiagnosticsRepository {

    val counts = sortedMapOf<String, Int>()

    override fun isEnabled(): Boolean = enabled

    override suspend fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) counts.clear()
    }

    override suspend fun record(event: DiagnosticEvent) {
        if (enabled) counts[event.key] = (counts[event.key] ?: 0) + 1
    }

    override suspend fun counters(): Map<String, Int> = counts.toMap()

    override suspend fun clear() = counts.clear()
}
