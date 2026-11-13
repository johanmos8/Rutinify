package com.mirkwood.rutinify.data.importer

import com.mirkwood.rutinify.domain.importer.DraftItemRules
import com.mirkwood.rutinify.domain.importer.ImportResult
import com.mirkwood.rutinify.domain.importer.IssueKind
import com.mirkwood.rutinify.domain.importer.Severity
import com.mirkwood.rutinify.domain.importer.ValidationIssue
import com.mirkwood.rutinify.domain.importer.ValueType
import com.mirkwood.rutinify.domain.model.DraftItem
import com.mirkwood.rutinify.domain.model.DraftOrigin
import com.mirkwood.rutinify.domain.model.RoutineDraft
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException
import javax.inject.Inject

/**
 * Validates a JSON v1 routine and maps it to a [RoutineDraft] (design §4.3).
 *
 * The tree is walked by hand instead of deserializing `@Serializable` DTOs so that **every** error is
 * collected, per item and field (R-3.13, R-3.16, D-07). Unknown fields are simply never read (R-3.2).
 */
class RoutineJsonParser @Inject constructor() {

    fun parse(root: JsonObject, draftId: String): ImportResult {
        val version = root["schemaVersion"]
        if (!isVersionOne(version)) {
            // R-3.4: nothing else is validated against an unknown format
            return ImportResult.Failure(
                listOf(
                    ValidationIssue(
                        field = "schemaVersion",
                        kind = IssueKind.UNSUPPORTED_SCHEMA_VERSION,
                        received = version.describeOrNull(),
                    ),
                ),
            )
        }

        val issues = mutableListOf<ValidationIssue>()
        val routine = readRoutine(root["routine"], issues)
        val items = readItems(root["items"], issues)

        val errors = issues.filter { it.severity == Severity.ERROR }
        if (errors.isNotEmpty() || routine == null || items == null) {
            return ImportResult.Failure(errors)
        }
        val draft = RoutineDraft(
            id = draftId,
            name = routine.name,
            date = routine.date,
            notes = routine.notes,
            items = items,
            origin = DraftOrigin.IMPORT,
        )
        return ImportResult.Success(draft, warnings = issues.filter { it.severity == Severity.WARNING })
    }

    // ---- Routine level (T-09) ----

    private class RoutineFields(val name: String, val date: LocalDate?, val notes: String?)

    private fun isVersionOne(element: JsonElement?): Boolean =
        element is JsonPrimitive && !element.isString && element.content == "1"

    private fun readRoutine(element: JsonElement?, issues: MutableList<ValidationIssue>): RoutineFields? {
        if (element == null || element is JsonNull) {
            issues += ValidationIssue(field = "routine", kind = IssueKind.MISSING_FIELD)
            return null
        }
        if (element !is JsonObject) {
            issues += wrongType("routine", ValueType.OBJECT, element)
            return null
        }
        val before = issues.size
        val name = readRequiredText(element["name"], "routine.name", issues)
        val date = readDate(element["date"], issues)
        val notes = readOptionalText(element["notes"], "routine.notes", issues, maxLength = MAX_LONG_TEXT)
        if (name == null || issues.size > before) return null
        return RoutineFields(name, date, notes)
    }

    /** R-3.6: absent or null → no date; otherwise strict ISO `yyyy-MM-dd` that must be a real date. */
    private fun readDate(element: JsonElement?, issues: MutableList<ValidationIssue>): LocalDate? {
        if (element == null || element is JsonNull) return null
        val text = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
        val parsed = text?.let {
            try {
                LocalDate.parse(it.trim())
            } catch (_: DateTimeParseException) {
                null
            }
        }
        if (parsed == null) {
            issues += ValidationIssue(field = "routine.date", kind = IssueKind.INVALID_DATE, received = element.describe())
        }
        return parsed
    }

    /** R-3.7, R-3.18, R-3.22. Returns null when the list itself is unusable or any item is invalid. */
    private fun readItems(element: JsonElement?, issues: MutableList<ValidationIssue>): List<DraftItem>? {
        if (element !is JsonArray || element.isEmpty()) {
            issues += ValidationIssue(field = "items", kind = IssueKind.NO_ITEMS)
            return null
        }
        if (element.size > MAX_ITEMS) {
            issues += ValidationIssue(field = "items", kind = IssueKind.TOO_MANY_ITEMS, received = element.size.toString())
            return null
        }
        val items = element.mapIndexedNotNull { position, item -> readItem(position, item, issues) }
        // Counted over the items that parsed, so this error is reported together with item errors (R-3.16)
        val categories = items.mapTo(HashSet()) { it.category }.size
        if (categories > DraftItemRules.MAX_CATEGORIES) {
            issues += ValidationIssue(
                field = "items",
                kind = IssueKind.TOO_MANY_CATEGORIES,
                received = categories.toString(),
                limit = DraftItemRules.MAX_CATEGORIES,
            )
            return null
        }
        return if (items.size == element.size) items.sortedWith(DRAFT_ORDER) else null
    }

    // ---- Item level (T-10) ----

    private fun readItem(position: Int, element: JsonElement, issues: MutableList<ValidationIssue>): DraftItem? {
        val index = position + 1
        if (element !is JsonObject) {
            issues += ValidationIssue(
                itemIndex = index,
                kind = IssueKind.WRONG_TYPE,
                expected = ValueType.OBJECT,
                received = element.describe(),
            )
            return null
        }
        val rawTitle = (element["title"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
        val rawTime = (element["time"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
        val context = ItemContext(index, rawTitle?.takeIf { it.isNotEmpty() }, rawTime)
        val before = issues.size

        val time = readTime(element["time"], context, issues)
        val title = readTitle(element["title"], context, issues)
        val dayOffset = readDayOffset(element["dayOffset"], context, issues)
        val durationMin = readDuration(element["durationMin"], context, issues)
        val description = readOptionalText(element["description"], "description", issues, MAX_LONG_TEXT, context)
        val category = readCategory(element["category"], context, issues)
        val assignee = readOptionalText(element["assignee"], "assignee", issues, MAX_ASSIGNEE_LENGTH, context)

        val hasErrors = issues.drop(before).any { it.severity == Severity.ERROR }
        if (hasErrors || time == null || title == null || dayOffset == null || category == null) return null
        return DraftItem(
            key = position,
            time = time,
            dayOffset = dayOffset,
            title = title,
            description = description,
            durationMin = durationMin,
            category = category,
            assignee = assignee,
        )
    }

    private class ItemContext(val index: Int, val title: String?, val time: String?)

    private fun ItemContext.issue(
        field: String,
        kind: IssueKind,
        received: JsonElement? = null,
        expected: ValueType? = null,
        limit: Int? = null,
        severity: Severity = Severity.ERROR,
    ) = ValidationIssue(
        itemIndex = index,
        itemTitle = title,
        itemTime = time,
        field = field,
        kind = kind,
        received = received.describeOrNull(),
        expected = expected,
        limit = limit,
        severity = severity,
    )

    /** R-3.8, D-13: `H:mm` or `HH:mm`, 00:00–23:59. */
    private fun readTime(element: JsonElement?, ctx: ItemContext, issues: MutableList<ValidationIssue>): LocalTime? {
        if (element == null || element is JsonNull) {
            issues += ctx.issue("time", IssueKind.MISSING_FIELD)
            return null
        }
        if (element !is JsonPrimitive || !element.isString) {
            issues += ctx.issue("time", IssueKind.WRONG_TYPE, element, ValueType.TEXT)
            return null
        }
        val time = DraftItemRules.parseTime(element.content)
        if (time == null) {
            issues += ctx.issue("time", IssueKind.INVALID_TIME, element)
        }
        return time
    }

    /** R-3.9, R-3.10. */
    private fun readTitle(element: JsonElement?, ctx: ItemContext, issues: MutableList<ValidationIssue>): String? {
        if (element == null || element is JsonNull) {
            issues += ctx.issue("title", IssueKind.MISSING_FIELD)
            return null
        }
        if (element !is JsonPrimitive || !element.isString) {
            issues += ctx.issue("title", IssueKind.WRONG_TYPE, element, ValueType.TEXT)
            return null
        }
        return when (val title = DraftItemRules.checkTitle(element.content)) {
            DraftItemRules.TitleCheck.Blank -> {
                issues += ctx.issue("title", IssueKind.BLANK_FIELD)
                null
            }
            is DraftItemRules.TitleCheck.Valid -> {
                if (title.truncated) {
                    issues += ctx.issue("title", IssueKind.TITLE_TRUNCATED, element, severity = Severity.WARNING)
                }
                title.value
            }
        }
    }

    /** R-3.11, R-3.15. */
    private fun readDayOffset(element: JsonElement?, ctx: ItemContext, issues: MutableList<ValidationIssue>): Int? {
        if (element == null || element is JsonNull) return 0
        val value = (element as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
        if (value == null || value !in 0..MAX_DAY_OFFSET) {
            issues += ctx.issue("dayOffset", IssueKind.DAY_OFFSET_OUT_OF_RANGE, element)
            return null
        }
        return value
    }

    /** R-3.12. */
    private fun readDuration(element: JsonElement?, ctx: ItemContext, issues: MutableList<ValidationIssue>): Int? {
        if (element == null || element is JsonNull) return null
        val value = (element as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
        if (value == null || value !in DURATION_RANGE) {
            issues += ctx.issue("durationMin", IssueKind.DURATION_OUT_OF_RANGE, element)
            return null
        }
        return value
    }

    /**
     * R-3.14, R-3.21: absent/null/blank → "general"; otherwise lowercase, trimmed, inner whitespace → "-",
     * and at most [DraftItemRules.MAX_CATEGORY_LENGTH] characters once normalized. Null only when invalid.
     */
    private fun readCategory(element: JsonElement?, ctx: ItemContext, issues: MutableList<ValidationIssue>): String? {
        val before = issues.size
        val raw = readOptionalText(element, "category", issues, ctx = ctx)
        if (issues.size > before) return null
        val category = DraftItemRules.normalizeCategory(raw)
        if (category == null) {
            issues += ctx.issue("category", IssueKind.TEXT_TOO_LONG, limit = DraftItemRules.MAX_CATEGORY_LENGTH)
        }
        return category
    }

    // ---- Shared readers ----

    private fun readRequiredText(element: JsonElement?, field: String, issues: MutableList<ValidationIssue>): String? {
        if (element == null || element is JsonNull) {
            issues += ValidationIssue(field = field, kind = IssueKind.MISSING_FIELD)
            return null
        }
        if (element !is JsonPrimitive || !element.isString) {
            issues += wrongType(field, ValueType.TEXT, element)
            return null
        }
        val text = element.content.trim()
        if (text.isEmpty()) {
            issues += ValidationIssue(field = field, kind = IssueKind.BLANK_FIELD)
            return null
        }
        return text
    }

    /**
     * Optional string: absent, null or blank → null; any other type is recorded as an error (R-3.13).
     * The trimmed text may not exceed [maxLength] characters (R-3.21).
     */
    private fun readOptionalText(
        element: JsonElement?,
        field: String,
        issues: MutableList<ValidationIssue>,
        maxLength: Int = Int.MAX_VALUE,
        ctx: ItemContext? = null,
    ): String? {
        if (element == null || element is JsonNull) return null
        if (element !is JsonPrimitive || !element.isString) {
            issues += ctx?.issue(field, IssueKind.WRONG_TYPE, element, ValueType.TEXT)
                ?: wrongType(field, ValueType.TEXT, element)
            return null
        }
        val text = element.content.trim()
        if (text.length > maxLength) {
            issues += ctx?.issue(field, IssueKind.TEXT_TOO_LONG, limit = maxLength)
                ?: ValidationIssue(field = field, kind = IssueKind.TEXT_TOO_LONG, limit = maxLength)
            return null
        }
        return text.ifEmpty { null }
    }

    private fun wrongType(field: String, expected: ValueType, received: JsonElement) =
        ValidationIssue(field = field, kind = IssueKind.WRONG_TYPE, expected = expected, received = received.describe())

    private fun JsonElement.describe(): String {
        val text = if (this is JsonPrimitive && isString) content else toString()
        return text.take(ValidationIssue.MAX_RECEIVED_LENGTH)
    }

    private fun JsonElement?.describeOrNull(): String? =
        if (this == null || this is JsonNull) null else describe()

    private companion object {
        const val MAX_ITEMS = 200
        const val MAX_LONG_TEXT = 500
        const val MAX_ASSIGNEE_LENGTH = 40
        const val MAX_DAY_OFFSET = 1
        val DURATION_RANGE = 1..1440
        val DRAFT_ORDER = compareBy<DraftItem>({ it.dayOffset }, { it.time }, { it.key })
    }
}
