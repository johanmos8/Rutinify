package com.mirkwood.rutinify.testutil

import com.mirkwood.rutinify.domain.diagnostics.DiagnosticEvent
import com.mirkwood.rutinify.domain.diagnostics.DiagnosticsRecorder

/** Keeps every recorded event, in order. */
class FakeDiagnosticsRecorder : DiagnosticsRecorder {

    val events = mutableListOf<DiagnosticEvent>()

    override suspend fun record(event: DiagnosticEvent) {
        events += event
    }
}
