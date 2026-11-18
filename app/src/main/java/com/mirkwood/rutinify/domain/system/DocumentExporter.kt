package com.mirkwood.rutinify.domain.system

/** Writes shareable documents on the phone, without network (R-20.5, D-78). */
interface DocumentExporter {
    /** A PDF with [title] and [lines]; the content URI to share, or null if it could not be written (R-20.6). */
    suspend fun writePdf(title: String, lines: List<String>): String?
}
