package com.mirkwood.rutinify.domain.model

import java.time.LocalTime

/** One activity of a recurring plan's day type; each occurrence becomes an [AlarmItem] (R-23.7, D-81). */
data class RoutineActivity(
    val id: Long,
    val orderId: Long,
    val position: Int,
    val time: LocalTime,
    /** 0 = the day itself, 1 = its early hours (R-23.6). */
    val dayOffset: Int,
    val title: String,
    val description: String?,
    val durationMin: Int?,
    val category: String,
    val assignee: String?,
)
