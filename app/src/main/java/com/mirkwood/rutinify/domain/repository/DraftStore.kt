package com.mirkwood.rutinify.domain.repository

import com.mirkwood.rutinify.domain.importer.ValidationIssue
import com.mirkwood.rutinify.domain.model.RoutineDraft

/** A validated draft with its non-blocking warnings, handed from New routine to Review (R-3.19). */
data class DraftEntry(val draft: RoutineDraft, val warnings: List<ValidationIssue>)

/** In-memory hand-off between screens; the route only carries the draft id (D-10). */
interface DraftStore {

    fun put(entry: DraftEntry)

    /** null when the process died since validation (D-10). */
    fun get(draftId: String): DraftEntry?

    fun remove(draftId: String)

    /** Drops every draft: a shared text replaces the one being reviewed (R-14.5). */
    fun clear()
}
