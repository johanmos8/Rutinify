package com.mirkwood.rutinify.domain.importer

import com.mirkwood.rutinify.domain.model.RoutineDraft

/** Outcome of importing pasted text (design §4.3). Expected failures are types, not exceptions. */
sealed interface ImportResult {

    /** Valid routine; [warnings] are non-blocking (e.g. a truncated title, R-3.10). */
    data class Success(val draft: RoutineDraft, val warnings: List<ValidationIssue>) : ImportResult

    /** Every error found in one pass (R-3.16), in JSON order. */
    data class Failure(val issues: List<ValidationIssue>) : ImportResult
}

/**
 * One problem in the pasted routine. The domain never builds display text: `ErrorMessages` (ui/common)
 * turns this into a Spanish message (T-12).
 */
data class ValidationIssue(
    /** 1-based item number (R-3.17); null for routine-level issues. */
    val itemIndex: Int? = null,
    /** Item title, for messages like "Actividad 3 («Siesta»)". */
    val itemTitle: String? = null,
    /** Item time as received, for messages like "Actividad 3 (a las 06:45)". */
    val itemTime: String? = null,
    /** JSON field path, e.g. "time" or "routine.name". */
    val field: String? = null,
    val kind: IssueKind,
    /** Received value, shortened to [MAX_RECEIVED_LENGTH] characters. */
    val received: String? = null,
    /** Expected value type, for [IssueKind.WRONG_TYPE] (R-3.13). */
    val expected: ValueType? = null,
    /** Maximum allowed, for limit kinds such as [IssueKind.TEXT_TOO_LONG] and [IssueKind.TOO_MANY_CATEGORIES]. */
    val limit: Int? = null,
    val severity: Severity = Severity.ERROR,
) {
    companion object {
        const val MAX_RECEIVED_LENGTH = 30
    }
}

enum class IssueKind {
    NO_JSON_FOUND,
    UNSUPPORTED_SCHEMA_VERSION,
    MISSING_FIELD,
    BLANK_FIELD,
    WRONG_TYPE,
    INVALID_DATE,
    INVALID_TIME,
    DAY_OFFSET_OUT_OF_RANGE,
    DURATION_OUT_OF_RANGE,
    NO_ITEMS,
    TOO_MANY_ITEMS,

    /** Warning: title longer than 80 characters was cut (R-3.10). */
    TITLE_TRUNCATED,

    /** Pasted text over 100 KB, rejected before extraction (R-3.20). */
    INPUT_TOO_LONG,

    /** Text field longer than its maximum, given in [ValidationIssue.limit] (R-3.21). */
    TEXT_TOO_LONG,

    /** More distinct categories than allowed; count in `received`, maximum in `limit` (R-3.22). */
    TOO_MANY_CATEGORIES,
}

enum class Severity { ERROR, WARNING }

/** Value types named in wrong-type messages, in non-technical terms (R-3.13). */
enum class ValueType { TEXT, WHOLE_NUMBER, OBJECT, LIST }
