package com.mirkwood.rutinify.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe navigation keys (design §10, D-23)

@Serializable
data object HomeDestination

/** [fromShare]: opened by text shared from another app, waiting in `SharedTextInbox` (R-14, D-64). */
@Serializable
data class NewRoutineDestination(val fromShare: Boolean = false)

@Serializable
data class ReviewDestination(val draftId: String)

/** Notification body link to an order with one alarm highlighted (R-7.6, design §6.1). */
const val ORDER_DEEP_LINK = "rutinify://order/{orderId}?alarm={highlightAlarmId}"

@Serializable
data class OrderDetailDestination(
    val orderId: Long,
    val highlightAlarmId: Long? = null,
    /** Set when arriving from Review, to show "{n} alarmas programadas" (R-5.6, D-44). */
    val scheduledCount: Int? = null,
)

@Serializable
data object PermissionsDestination
