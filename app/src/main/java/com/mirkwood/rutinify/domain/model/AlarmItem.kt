package com.mirkwood.rutinify.domain.model

import java.time.Instant
import java.time.LocalTime

/**
 * Una actividad de la orden con su aviso. La hora de disparo no se guarda: se calcula desde
 * fecha de la orden + [dayOffset] + [time] en la zona actual, salvo que haya [snoozedUntil].
 */
data class AlarmItem(
    val id: Long,
    val orderId: Long,
    /** Índice original en el JSON (0-based). */
    val position: Int,
    /** Hora de pared original. */
    val time: LocalTime,
    /** 0 = mismo día, 1 = día siguiente (D-05). */
    val dayOffset: Int,
    val title: String,
    val description: String?,
    val durationMin: Int?,
    /** Normalizada; `general` por defecto. */
    val category: String,
    val assignee: String?,
    val status: AlarmStatus,
    /** Si no es null, manda sobre la hora calculada (D-12). */
    val snoozedUntil: Instant?,
    val statusChangedAt: Instant,
    val firedAt: Instant? = null,
    val doneAt: Instant? = null,
)

/** Transiciones en design §3. Eliminar una alarma la borra; no es un estado. */
enum class AlarmStatus { PENDING, FIRED, DONE, SKIPPED, CANCELLED }
