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
import com.mirkwood.rutinify.ui.review.ConflictOrderUi
import com.mirkwood.rutinify.ui.review.ReviewDayUi
import com.mirkwood.rutinify.ui.review.ReviewDialog
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

    /** Two single-day routines on the same date (R-6.1). */
    val conflictDialog = ReviewDialog.Conflict(
        listOf(ConflictOrderUi("Gato", UiText.Plain("5 de noviembre")), ConflictOrderUi("Perro", UiText.Plain("5 de noviembre"))),
    )

    /** A treatment that is still running: names show their days and the replace warning appears (R-6.7). */
    val conflictDialogWithRanges = ReviewDialog.Conflict(
        orders = listOf(
            ConflictOrderUi("Antibiótico de Luna", UiText.Res(R.string.date_range, listOf("14", "20 de noviembre"))),
            ConflictOrderUi("Cachorro", UiText.Plain("16 de noviembre")),
        ),
        showsRanges = true,
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

    /** Antibiotic every 8 hours for 3 days, grouped by day (R-4.13, R-4.14). */
    val reviewStateMultiDay: ReviewUiState = run {
        val dates = listOf("sábado 14 de noviembre", "domingo 15 de noviembre", "lunes 16 de noviembre")
        val days = dates.mapIndexed { day, date ->
            ReviewDayUi(
                dayOffset = day,
                title = UiText.Res(R.string.review_day_header, listOf(day + 1, date)),
                items = listOf("06:00", "14:00", "22:00").mapIndexed { i, time ->
                    val past = day == 0 && i == 0
                    ReviewItemUi(day * 3 + i, time, false, "Antibiótico", "salud", "Con comida", isPast = past, isIncluded = !past)
                },
            )
        }
        ReviewUiState(
            name = "Antibiótico de Luna",
            dateLabel = "sábado 14 de noviembre de 2026",
            dateMillisUtc = 1_763_078_400_000,
            items = days.flatMap { it.items },
            days = days,
            summary = UiText.Res(
                R.string.review_summary_days,
                listOf(
                    UiText.Plural(R.plurals.review_alarm_count, 8, listOf(8)),
                    UiText.Plural(R.plurals.review_day_count, 3, listOf(3)),
                    UiText.Res(R.string.date_range, listOf("sábado 14", "lunes 16 de noviembre")),
                ),
            ),
            canSchedule = true,
        )
    }

    val homeState = HomeUiState(
        isLoading = false,
        orders = listOf(
            OrderCardUi(
                5,
                "Antibiótico de Luna",
                UiText.Res(R.string.date_range, listOf("sábado 14", "viernes 20 de noviembre de 2026")),
                18,
                21,
                OrderDisplayStatus.ACTIVE,
            ),
            OrderCardUi(3, "Cachorro", UiText.Plain("viernes 13 de noviembre de 2026"), 5, 8, OrderDisplayStatus.ACTIVE),
            OrderCardUi(2, "Rutina de estudio para el examen final de matemáticas", UiText.Plain("jueves 12 de noviembre de 2026"), 4, 6, OrderDisplayStatus.PAUSED),
            OrderCardUi(1, "Mudanza", UiText.Plain("miércoles 11 de noviembre de 2026"), 0, 3, OrderDisplayStatus.FINISHED),
            OrderCardUi(4, "Viaje", UiText.Plain("martes 10 de noviembre de 2026"), 0, 2, OrderDisplayStatus.CANCELLED),
        ),
    )

    val orderDetailState = OrderDetailUiState(
        isLoading = false,
        name = "Cachorro",
        dateLabel = UiText.Plain("viernes 13 de noviembre de 2026"),
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
        canDuplicate = true,
    )

    /** Antibiotic at 08:00 and 20:00 for 3 days, grouped by day (R-9.6). */
    val orderDetailStateMultiDay: OrderDetailUiState = run {
        val dates = listOf("sábado 14 de noviembre", "domingo 15 de noviembre", "lunes 16 de noviembre")
        val timeline = dates.flatMapIndexed { day, date ->
            listOf("08:00", "20:00").mapIndexed { i, time ->
                val id = day * 2L + i + 1
                TimelineItemUi(
                    id = id,
                    time = time,
                    nextDay = false,
                    title = "Antibiótico",
                    description = "Con comida",
                    category = "salud",
                    status = if (id <= 2) AlarmStatus.DONE else AlarmStatus.PENDING,
                    isNext = id == 3L,
                    isHighlighted = false,
                    dayTitle = if (i == 0) UiText.Res(R.string.review_day_header, listOf(day + 1, date)) else null,
                )
            }
        }
        orderDetailState.copy(
            name = "Antibiótico de Luna",
            dateLabel = UiText.Res(R.string.date_range, listOf("sábado 14", "lunes 16 de noviembre de 2026")),
            notes = null,
            pending = 4,
            total = 6,
            timeline = timeline,
            scrollToIndex = 2,
            windowedWarning = false,
        )
    }
}
