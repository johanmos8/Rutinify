package com.mirkwood.rutinify.data.importer

import com.mirkwood.rutinify.di.DefaultDispatcher
import com.mirkwood.rutinify.domain.importer.ImportResult
import com.mirkwood.rutinify.domain.importer.IssueKind
import com.mirkwood.rutinify.domain.importer.RoutineImporter
import com.mirkwood.rutinify.domain.importer.ValidationIssue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/**
 * [RoutineImporter] for JSON v1: size check (R-3.20) → [JsonExtractor] → [RoutineJsonParser], all on the
 * default dispatcher (R-3.23). The draft keeps the exact extracted text as `rawJson`.
 */
class RoutineJsonImporter @Inject constructor(
    private val extractor: JsonExtractor,
    private val parser: RoutineJsonParser,
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : RoutineImporter {

    override suspend fun import(text: String): ImportResult = withContext(dispatcher) {
        val bytes = utf8Size(text)
        if (bytes > MAX_INPUT_BYTES) {
            return@withContext ImportResult.Failure(
                listOf(
                    ValidationIssue(
                        kind = IssueKind.INPUT_TOO_LONG,
                        received = ((bytes + BYTES_PER_KB - 1) / BYTES_PER_KB).toString(),
                        limit = MAX_INPUT_KB,
                    ),
                ),
            )
        }
        val extracted = extractor.extract(text)
            ?: return@withContext ImportResult.Failure(listOf(ValidationIssue(kind = IssueKind.NO_JSON_FOUND)))
        when (val result = parser.parse(extracted.json, draftId = UUID.randomUUID().toString())) {
            is ImportResult.Success -> result.copy(draft = result.draft.copy(rawJson = extracted.text))
            is ImportResult.Failure -> result
        }
    }

    /** UTF-8 size without encoding the whole text: stops counting once the limit is exceeded. */
    private fun utf8Size(text: String): Long {
        var bytes = 0L
        var i = 0
        while (i < text.length && bytes <= MAX_INPUT_BYTES) {
            val c = text[i]
            bytes += when {
                c.code < 0x80 -> 1
                c.code < 0x800 -> 2
                Character.isHighSurrogate(c) && i + 1 < text.length && Character.isLowSurrogate(text[i + 1]) -> {
                    i++
                    4
                }
                else -> 3
            }
            i++
        }
        // Past the limit only the KB shown in the message matters: count the rest cheaply as at least 1 byte each
        return if (i < text.length) bytes + (text.length - i) else bytes
    }

    private companion object {
        const val BYTES_PER_KB = 1024L
        const val MAX_INPUT_KB = 100
        const val MAX_INPUT_BYTES = MAX_INPUT_KB * BYTES_PER_KB
    }
}
