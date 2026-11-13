package com.mirkwood.rutinify.ui.preview

import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderDisplayStatus
import com.mirkwood.rutinify.domain.importer.IssueKind
import com.mirkwood.rutinify.domain.importer.Severity
import com.mirkwood.rutinify.domain.importer.ValidationIssue
import com.mirkwood.rutinify.ui.common.ErrorMessages
import com.mirkwood.rutinify.ui.common.UiText
import com.mirkwood.rutinify.ui.home.HomeUiState
import com.mirkwood.rutinify.ui.home.OrderCardUi
import com.mirkwood.rutinify.ui.orderdetail.OrderDetailUiState
import com.mirkwood.rutinify.ui.orderdetail.PauseAction
import com.mirkwood.rutinify.ui.orderdetail.TimelineItemUi
import com.mirkwood.rutinify.ui.review.ReviewItemUi
import com.mirkwood.rutinify.ui.review.ReviewUiState

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

    val reviewState = ReviewUiState(
        name = "Cachorro",
        notes = "Llevar bolsas y agua",
        dateLabel = "jueves 5 de noviembre de 2026",
        dateMillisUtc = 1_762_300_800_000,
        dateNotice = UiText.Res(R.string.review_date_defaulted, listOf("jueves 5 de noviembre de 2026")),
        items = listOf(
            ReviewItemUi(0, "06:45", false, "Desayuno", "comida", null, isPast = true, isIncluded = false),
            ReviewItemUi(1, "13:00", false, "Paseo", "paseo", "Con correa larga", isPast = false, isIncluded = true),
            ReviewItemUi(2, "18:00", false, "Cena", "comida", null, isPast = false, isIncluded = false),
            ReviewItemUi(3, "02:00", true, "Salida nocturna", "paseo", null, isPast = false, isIncluded = true),
        ),
        summary = UiText.Plural(R.plurals.review_summary, 2, listOf(2, "13:00", UiText.Res(R.string.review_time_next_day, listOf("02:00")))),
        warnings = ErrorMessages.list(
            listOf(ValidationIssue(itemIndex = 4, itemTitle = "Salida nocturna", kind = IssueKind.TITLE_TRUNCATED, severity = Severity.WARNING)),
        ),
        canSchedule = true,
    )

    val homeState = HomeUiState(
        isLoading = false,
        orders = listOf(
            OrderCardUi(3, "Cachorro", "viernes 13 de noviembre de 2026", 5, 8, OrderDisplayStatus.ACTIVE),
            OrderCardUi(2, "Rutina de estudio para el examen final de matemáticas", "jueves 12 de noviembre de 2026", 4, 6, OrderDisplayStatus.PAUSED),
            OrderCardUi(1, "Mudanza", "miércoles 11 de noviembre de 2026", 0, 3, OrderDisplayStatus.FINISHED),
            OrderCardUi(4, "Viaje", "martes 10 de noviembre de 2026", 0, 2, OrderDisplayStatus.CANCELLED),
        ),
    )

    val orderDetailState = OrderDetailUiState(
        isLoading = false,
        name = "Cachorro",
        dateLabel = "viernes 13 de noviembre de 2026",
        notes = "Llevar bolsas y agua",
        status = OrderDisplayStatus.ACTIVE,
        pending = 2,
        total = 5,
        timeline = listOf(
            TimelineItemUi(1, "06:45", false, "Desayuno", "Medio vaso de pienso", "comida", AlarmStatus.DONE, isNext = false, isHighlighted = false),
            TimelineItemUi(2, "09:00", false, "Paseo corto", null, "paseo", AlarmStatus.FIRED, isNext = false, isHighlighted = true),
            TimelineItemUi(3, "11:00", false, "Juego", null, "juego", AlarmStatus.SKIPPED, isNext = false, isHighlighted = false),
            TimelineItemUi(4, "13:00", false, "Comida", null, "comida", AlarmStatus.PENDING, isNext = true, isHighlighted = false),
            TimelineItemUi(5, "02:00", true, "Salida nocturna", "Solo al jardín", "paseo", AlarmStatus.PENDING, isNext = false, isHighlighted = false),
        ),
        scrollToIndex = 1,
        windowedWarning = true,
        canCancelAll = true,
        pauseAction = PauseAction.PAUSE,
    )
}
