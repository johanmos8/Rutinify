package com.mirkwood.rutinify.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Relation
import java.time.LocalDate

/** Row of [OrderDao.observeSummaries]: order + alarm counts + largest `day_offset` for its span (R-8.2). */
data class OrderSummaryRow(
    @Embedded val order: RoutineOrderEntity,
    val pending: Int,
    val total: Int,
    @ColumnInfo(name = "max_day_offset") val maxDayOffset: Int,
)

/** Order in conflict with its largest `day_offset` (0 without alarms), to rebuild its span (R-6.7). */
data class ConflictingOrderRow(
    @Embedded val order: RoutineOrderEntity,
    @ColumnInfo(name = "max_day_offset") val maxDayOffset: Int,
)

/** Order with its alarms. The order of [alarms] is not guaranteed: the repository sorts them (D-27). */
data class OrderWithAlarmsRow(
    @Embedded val order: RoutineOrderEntity,
    @Relation(parentColumn = "id", entityColumn = "order_id")
    val alarms: List<AlarmItemEntity>,
)

/** Pending alarm with its order date, to compute the trigger time when rescheduling (design §7.2). */
data class PendingAlarmRow(
    @Embedded val alarm: AlarmItemEntity,
    @ColumnInfo(name = "order_date") val orderDate: LocalDate,
)

/** A pending alarm of an ACTIVE order with the order's date and name (R-21.1). */
data class UpcomingAlarmRow(
    @Embedded val alarm: AlarmItemEntity,
    @ColumnInfo(name = "order_date") val orderDate: LocalDate,
    @ColumnInfo(name = "order_name") val orderName: String,
)
