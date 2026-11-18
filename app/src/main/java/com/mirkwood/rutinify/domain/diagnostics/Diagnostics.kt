package com.mirkwood.rutinify.domain.diagnostics

import com.mirkwood.rutinify.domain.importer.IssueKind

/**
 * Something counted by the optional local diagnostics (R-17.2, R-17.3, D-72). [key] is stable across versions and
 * never carries identifiers or routine content.
 */
sealed interface DiagnosticEvent {
    val key: String

    /** A failed validation, once per kind of error it had. */
    data class ValidationFailed(val kind: IssueKind) : DiagnosticEvent {
        override val key: String get() = "issue.${kind.name}"
    }

    /** Steps of the flow, to see where people stop. */
    enum class Step(override val key: String) : DiagnosticEvent {
        NEW_ROUTINE("step.new_routine"),
        VALIDATE("step.validate"),
        REVIEW("step.review"),
        SCHEDULE_TAP("step.schedule_tap"),
        SCHEDULED("step.scheduled"),
    }

    /** One more plan scheduled: tells whether people create a second routine (R-17.3). */
    data object PlanCreated : DiagnosticEvent {
        override val key: String = "plans.created"
    }
}

/** Counts [DiagnosticEvent]s; does nothing while the switch is off (R-17.1). */
interface DiagnosticsRecorder {
    suspend fun record(event: DiagnosticEvent)
}

/** The switch and the counters themselves, for Settings (R-17.1, R-17.5–R-17.7). */
interface DiagnosticsRepository : DiagnosticsRecorder {

    fun isEnabled(): Boolean

    /** Turning it off also deletes every counter (R-17.7). */
    suspend fun setEnabled(enabled: Boolean)

    /** Every counter by [DiagnosticEvent.key], sorted by key. */
    suspend fun counters(): Map<String, Int>

    suspend fun clear()
}
