package com.mirkwood.rutinify.ui.preview

import com.mirkwood.rutinify.domain.importer.IssueKind
import com.mirkwood.rutinify.domain.importer.ValidationIssue
import com.mirkwood.rutinify.ui.common.ErrorMessages
import com.mirkwood.rutinify.ui.common.UiText

/** Fake data for screen and component `@Preview`s. Filled in as screens are added. */
internal object PreviewData {

    const val TEMPLATE = "Convierte mi rutina en este JSON…\n\n{\n  \"schemaVersion\": 1,\n  \"routine\": { … }\n}\n\n[PEGA AQUÍ TU RUTINA]"

    const val INVALID_JSON = "{\n  \"schemaVersion\": 1,\n  \"routine\": { \"items\": [ { \"time\": \"25:00\" } ] }\n}"

    val issues: List<UiText> = ErrorMessages.list(
        listOf(
            ValidationIssue(field = "routine.name", kind = IssueKind.MISSING_FIELD),
            ValidationIssue(itemIndex = 1, itemTime = "25:00", field = "time", kind = IssueKind.INVALID_TIME, received = "25:00"),
            ValidationIssue(itemIndex = 1, itemTime = "25:00", field = "title", kind = IssueKind.MISSING_FIELD),
        ),
    )
}
