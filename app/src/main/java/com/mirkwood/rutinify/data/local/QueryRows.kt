package com.mirkwood.rutinify.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Relation
import java.time.LocalDate

/** Fila de [OrderDao.observeSummaries]: orden + recuento de alarmas (R-8.2). */
data class OrderSummaryRow(
    @Embedded val order: RoutineOrderEntity,
    val pending: Int,
    val total: Int,
)

/** Orden con sus alarmas. El orden de [alarms] no está garantizado: lo fija el repositorio (D-27). */
data class OrderWithAlarmsRow(
    @Embedded val order: RoutineOrderEntity,
    @Relation(parentColumn = "id", entityColumn = "order_id")
    val alarms: List<AlarmItemEntity>,
)

/** Alarma pendiente con la fecha de su orden, para calcular la hora de disparo al reprogramar (design §7.2). */
data class PendingAlarmRow(
    @Embedded val alarm: AlarmItemEntity,
    @ColumnInfo(name = "order_date") val orderDate: LocalDate,
)
