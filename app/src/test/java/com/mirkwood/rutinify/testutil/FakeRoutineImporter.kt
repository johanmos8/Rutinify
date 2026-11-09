package com.mirkwood.rutinify.testutil

import com.mirkwood.rutinify.domain.importer.ImportResult
import com.mirkwood.rutinify.domain.importer.RoutineImporter
import kotlinx.coroutines.CompletableDeferred

/** Returns [result]; set [gate] to hold the import until it completes (to observe "validando"). */
class FakeRoutineImporter(var result: ImportResult) : RoutineImporter {

    var gate: CompletableDeferred<Unit>? = null
    val received = mutableListOf<String>()

    override suspend fun import(text: String): ImportResult {
        received += text
        gate?.await()
        return result
    }
}
