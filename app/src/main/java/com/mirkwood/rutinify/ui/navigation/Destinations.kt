package com.mirkwood.rutinify.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe navigation keys (design §10, D-23)

@Serializable
data object HomeDestination

@Serializable
data object NewRoutineDestination

@Serializable
data class ReviewDestination(val draftId: String)

@Serializable
data class OrderDetailDestination(
    val orderId: Long,
    val highlightAlarmId: Long? = null,
    /** Set when arriving from Review, to show "{n} alarmas programadas" (R-5.6, D-44). */
    val scheduledCount: Int? = null,
)

@Serializable
data object PermissionsDestination
