package com.mirkwood.rutinify.domain.model

import java.time.LocalDate
import java.time.LocalTime

/** Validated routine not yet scheduled. Lives in memory (`DraftStore`, D-10), not in Room. */
data class RoutineDraft(
    /** UUID; key in `DraftStore`. */
    val id: String,
    val name: String,
    /** null if the JSON had no date (R-4.5). */
    val date: LocalDate?,
    val notes: String?,
    val items: List<DraftItem>,
    val origin: DraftOrigin,
    /** Exact JSON text the draft was imported from, saved as the order's `raw_json` (D-34). */
    val rawJson: String? = null,
    /** Schema v2 repetition by weekday; [items] are then one day type (R-23.2, D-81). */
    val recurrence: Recurrence? = null,
)

data class DraftItem(
    /** Stable within the draft (= original position in the JSON). */
    val key: Int,
    val time: LocalTime,
    val dayOffset: Int,
    val title: String,
    val description: String?,
    val durationMin: Int?,
    val category: String,
    val assignee: String?,
)

/** Where the draft comes from: JSON import or "Duplicar para otra fecha" (R-10.7). */
enum class DraftOrigin { IMPORT, DUPLICATE }
