package com.mirkwood.rutinify.ui.common

import androidx.annotation.StringRes
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.domain.importer.IssueKind
import com.mirkwood.rutinify.domain.importer.ValidationIssue
import com.mirkwood.rutinify.domain.importer.ValueType

/**
 * Turns [ValidationIssue]s into Spanish messages from resources (R-3.3–R-3.22, R-13.3). The domain only
 * returns types; this is the single place that decides the wording.
 */
object ErrorMessages {

    const val MAX_SHOWN = 20
    private const val MAX_TITLE_IN_MESSAGE = 30
    private const val ELLIPSIS = "…"

    /** At most [MAX_SHOWN] messages, then "y {k} errores más" (R-3.16). */
    fun list(issues: List<ValidationIssue>): List<UiText> {
        val shown = issues.take(MAX_SHOWN).map(::message)
        val hidden = issues.size - shown.size
        return if (hidden > 0) shown + UiText.Plural(R.plurals.issue_more, hidden, listOf(hidden)) else shown
    }

    fun message(issue: ValidationIssue): UiText {
        val prefix = issue.itemIndex?.let { itemPrefix(issue, it) }
        val received = issue.received.orEmpty()
        val limit = issue.limit ?: 0
        return when (issue.kind) {
            IssueKind.NO_JSON_FOUND -> res(R.string.issue_no_json)
            IssueKind.UNSUPPORTED_SCHEMA_VERSION ->
                if (issue.received != null) res(R.string.issue_version_received, received) else res(R.string.issue_version_missing)
            IssueKind.MISSING_FIELD, IssueKind.BLANK_FIELD -> missing(issue, prefix)
            IssueKind.WRONG_TYPE -> wrongType(issue, prefix)
            IssueKind.INVALID_DATE -> res(R.string.issue_invalid_date, received)
            IssueKind.INVALID_TIME -> res(R.string.issue_invalid_time, prefix ?: routinePrefix(), received)
            IssueKind.DAY_OFFSET_OUT_OF_RANGE -> res(R.string.issue_day_offset, prefix ?: routinePrefix())
            IssueKind.DURATION_OUT_OF_RANGE -> res(R.string.issue_duration, prefix ?: routinePrefix())
            IssueKind.NO_ITEMS -> res(R.string.issue_no_items)
            IssueKind.TOO_MANY_ITEMS -> res(R.string.issue_too_many_items, received)
            IssueKind.TITLE_TRUNCATED -> res(R.string.issue_title_truncated, prefix ?: routinePrefix())
            IssueKind.INPUT_TOO_LONG -> res(R.string.issue_input_too_long, received, limit)
            IssueKind.TEXT_TOO_LONG ->
                if (prefix == null) {
                    res(R.string.issue_text_too_long, field(issue), limit)
                } else {
                    res(R.string.issue_item_text_too_long, prefix, field(issue), limit)
                }
            IssueKind.TOO_MANY_CATEGORIES -> res(R.string.issue_too_many_categories, received, limit)
        }
    }

    private fun missing(issue: ValidationIssue, prefix: UiText?): UiText =
        if (prefix == null) {
            when (issue.field) {
                "routine.name" -> res(R.string.issue_no_name)
                "routine" -> res(R.string.issue_no_routine)
                "items" -> res(R.string.issue_no_items)
                else -> res(R.string.issue_missing_field, field(issue))
            }
        } else {
            when (issue.field) {
                "title" -> res(R.string.issue_item_missing_title, prefix)
                "time" -> res(R.string.issue_item_missing_time, prefix)
                else -> res(R.string.issue_item_missing_field, prefix, field(issue))
            }
        }

    private fun wrongType(issue: ValidationIssue, prefix: UiText?): UiText {
        val type = res(typeName(issue.expected ?: ValueType.TEXT))
        val received = issue.received
        return when {
            prefix == null && received == null -> res(R.string.issue_wrong_type, field(issue), type)
            prefix == null -> res(R.string.issue_wrong_type_received, field(issue), type, received.orEmpty())
            issue.field == null && received == null -> res(R.string.issue_item_wrong_shape, prefix, type)
            issue.field == null -> res(R.string.issue_item_wrong_shape_received, prefix, type, received.orEmpty())
            received == null -> res(R.string.issue_item_wrong_type, prefix, field(issue), type)
            else -> res(R.string.issue_item_wrong_type_received, prefix, field(issue), type, received)
        }
    }

    /** "Actividad 3 («Siesta»)", else "Actividad 3 (a las 09:30)", else "Actividad 3" (R-3.8, R-3.9, R-3.17). */
    private fun itemPrefix(issue: ValidationIssue, index: Int): UiText {
        val title = issue.itemTitle?.takeIf { it.isNotBlank() }
        val time = issue.itemTime?.takeIf { it.isNotBlank() }
        return when {
            title != null -> res(R.string.issue_prefix_title, index, shorten(title))
            time != null -> res(R.string.issue_prefix_time, index, time)
            else -> res(R.string.issue_prefix, index)
        }
    }

    /** Fallback context for item-only kinds reported without an item number (the parser never does this). */
    private fun routinePrefix(): UiText = res(R.string.issue_prefix_routine)

    /** The context keeps the untruncated title, so long ones are cut for display: 29 characters + "…". */
    private fun shorten(title: String): String =
        if (title.length <= MAX_TITLE_IN_MESSAGE) title else title.take(MAX_TITLE_IN_MESSAGE - 1).trimEnd() + ELLIPSIS

    private fun field(issue: ValidationIssue): String = issue.field.orEmpty()

    @StringRes
    private fun typeName(type: ValueType): Int = when (type) {
        ValueType.TEXT -> R.string.issue_type_text
        ValueType.WHOLE_NUMBER -> R.string.issue_type_whole_number
        ValueType.OBJECT -> R.string.issue_type_object
        ValueType.LIST -> R.string.issue_type_list
    }

    private fun res(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())
}
