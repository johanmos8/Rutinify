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
 *
 * Limits (R-3.23, D-32): at most [MAX_CANDIDATES] candidates are tried per call (fences and full scan
 * share the budget), so the cost is bounded to O(MAX_CANDIDATES · n) even for text full of `{`. A
 * candidate nested deeper than [MAX_NESTING] (objects and arrays) is discarded without reaching the
 * recursive `kotlinx` parser, which would otherwise overflow the stack.
 */
class JsonExtractor @Inject constructor() {

    fun extract(text: String): ExtractedJson? {
        val clean = text.filterNot { it.code == BOM_CODE }
        val budget = Budget(MAX_CANDIDATES)
        val fromFences = FENCE.findAll(clean).firstNotNullOfOrNull { scan(it.groupValues[1], budget) }
        return fromFences ?: scan(clean, budget)
    }

    private class Budget(var remaining: Int)

    private fun scan(text: String, budget: Budget): ExtractedJson? {
        var start = text.indexOf('{')
        while (start >= 0 && budget.remaining > 0) {
            budget.remaining--
            val span = findObjectEnd(text, start)
            if (span == null) {
                start = text.indexOf('{', start + 1)
                continue
            }
            if (span.maxNesting <= MAX_NESTING) {
                val candidate = text.substring(start, span.end + 1)
                val parsed = parseObject(candidate)
                if (parsed != null) return ExtractedJson(parsed, candidate)
            }
            start = text.indexOf('{', span.end + 1)
        }
        return null
    }

    /** Closing `}` of an object candidate and the deepest nesting (objects + arrays) seen inside it. */
    private class Span(val end: Int, val maxNesting: Int)

    /** Span of the object opened at [start], or null if it never closes. */
    private fun findObjectEnd(text: String, start: Int): Span? {
        var depth = 0
        var nesting = 0
        var maxNesting = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            when {
                escaped -> escaped = false
                inString && c == '\\' -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' || c == '[' -> {
                    if (c == '{') depth++
                    nesting++
                    if (nesting > maxNesting) maxNesting = nesting
                }
                c == '}' || c == ']' -> {
                    nesting--
                    if (c == '}') {
                        depth--
                        if (depth == 0) return Span(i, maxNesting)
                    }
                }
            }
        }
        return null
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
        const val MAX_CANDIDATES = 20
        const val MAX_NESTING = 32
        val FENCE = Regex("```[^\\n`]*\\n?([\\s\\S]*?)```")
    }
}
