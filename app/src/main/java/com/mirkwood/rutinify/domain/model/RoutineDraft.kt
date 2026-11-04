package com.mirkwood.rutinify.domain.model

import java.time.LocalDate
import java.time.LocalTime

/** Rutina validada pero aún no programada. Vive en memoria (`DraftStore`, D-10), no en Room. */
data class RoutineDraft(
    /** UUID; clave en `DraftStore`. */
    val id: String,
    val name: String,
    /** null si el JSON no traía fecha (R-4.5). */
    val date: LocalDate?,
    val notes: String?,
    val items: List<DraftItem>,
    val origin: DraftOrigin,
)

data class DraftItem(
    /** Estable dentro del borrador (= posición original en el JSON). */
    val key: Int,
    val time: LocalTime,
    val dayOffset: Int,
    val title: String,
    val description: String?,
    val durationMin: Int?,
    val category: String,
    val assignee: String?,
)

/** De dónde sale el borrador: importación de JSON o "Duplicar para otra fecha" (R-10.7). */
enum class DraftOrigin { IMPORT, DUPLICATE }
