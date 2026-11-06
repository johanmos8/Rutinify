package com.mirkwood.rutinify.domain.importer

/** Turns pasted text (an AI answer) into a validated draft or the list of problems found (design §4.4). */
interface RoutineImporter {

    /** Safe to call from the main thread: the analysis runs on a background dispatcher (R-3.23). */
    suspend fun import(text: String): ImportResult
}
