package com.mirkwood.rutinify.domain.model

import java.time.LocalDate
import java.time.LocalTime

/** Datos para crear una orden (sin id ni marcas de tiempo, que pone el repositorio). */
data class NewOrder(
    val name: String,
    val date: LocalDate,
    val notes: String?,
    /** JSON original extraído; se guarda para depuración / v2. */
    val rawJson: String?,
    val schemaVersion: Int = 1,
)

/** Datos para crear una alarma de una [NewOrder]. Pasadas/excluidas llegan ya como SKIPPED (R-5.5). */
data class NewAlarm(
    val position: Int,
    val time: LocalTime,
    val dayOffset: Int,
    val title: String,
    val description: String?,
    val durationMin: Int?,
    val category: String,
    val assignee: String?,
    val status: AlarmStatus,
)

/** Resultado de crear una orden: lo que hay que programar y lo que hay que cancelar en el sistema. */
data class CreatedOrder(
    val order: RoutineOrder,
    /** Alarmas creadas, con sus ids (= requestCode, design §6.1). */
    val alarms: List<AlarmItem>,
    /** Alarmas antes pendientes de las órdenes reemplazadas, ahora CANCELLED (R-6.2). */
    val cancelledAlarmIds: List<Long>,
)

/** Alarma pendiente con la fecha de su orden, para calcular la hora de disparo (design §7.2). */
data class PendingAlarm(
    val alarm: AlarmItem,
    val orderDate: LocalDate,
)
