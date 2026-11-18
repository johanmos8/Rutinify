package com.mirkwood.rutinify.ui.settings

import com.mirkwood.rutinify.domain.usecase.DiagnosticsReport

/**
 * The shared text (R-17.5): a [header] already in the user's language, then one "key: count" line per counter. The
 * keys stay technical and stable (D-72) so exports from different phones can be added up.
 */
internal object DiagnosticsText {

    fun build(header: String, empty: String, report: DiagnosticsReport): String = buildString {
        appendLine(header)
        if (report.counters.isEmpty()) {
            append(empty)
        } else {
            report.counters.toSortedMap().forEach { (key, count) -> appendLine("$key: $count") }
        }
    }.trimEnd()
}
