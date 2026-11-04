package com.mirkwood.rutinify.data.importer

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

/** First JSON object found in the pasted text, with the exact text it was parsed from (stored as `raw_json`). */
data class ExtractedJson(
    val json: JsonObject,
    val text: String,
)

/**
 * Tolerant extraction of the first JSON object in free text (R-3.1, design §4.2).
 *
 * 1. Markdown code fences (with or without a language) are tried first, in order.
 * 2. Otherwise (or if no fence holds an object) the whole text is scanned: for each `{`, braces are
 *    counted while honoring strings and escapes until the object closes, and the candidate is parsed.
 *    A closed but invalid candidate is skipped entirely, so objects nested inside it are never returned.
 *    An unclosed candidate is skipped by one character, so a later valid object can still be found.
 * 3. BOM characters are removed. Typographic quotes are left untouched (invalid JSON, R-3.3).
 */
class JsonExtractor @Inject constructor() {

    fun extract(text: String): ExtractedJson? {
        val clean = text.filterNot { it.code == BOM_CODE }
        val fromFences = FENCE.findAll(clean).firstNotNullOfOrNull { scan(it.groupValues[1]) }
        return fromFences ?: scan(clean)
    }

    private fun scan(text: String): ExtractedJson? {
        var start = text.indexOf('{')
        while (start >= 0) {
            val end = findObjectEnd(text, start)
            if (end < 0) {
                start = text.indexOf('{', start + 1)
                continue
            }
            val candidate = text.substring(start, end + 1)
            val parsed = parseObject(candidate)
            if (parsed != null) return ExtractedJson(parsed, candidate)
            start = text.indexOf('{', end + 1)
        }
        return null
    }

    /** Index of the `}` closing the object opened at [start], or -1 if it never closes. */
    private fun findObjectEnd(text: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            when {
                escaped -> escaped = false
                inString && c == '\\' -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    private fun parseObject(candidate: String): JsonObject? =
        try {
            Json.parseToJsonElement(candidate) as? JsonObject
        } catch (_: SerializationException) {
            // Not valid JSON: the caller moves on to the next candidate
            null
        }

    private companion object {
        /** U+FEFF byte-order mark (kept as a code point so lint does not flag a literal BOM). */
        const val BOM_CODE = 0xFEFF
        val FENCE = Regex("```[^\\n`]*\\n?([\\s\\S]*?)```")
    }
}
