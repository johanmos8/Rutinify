package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.model.AlarmItem
import com.mirkwood.rutinify.domain.scheduler.CriticalAlarm

/** How [this] alarm rings given the [criticalNames] categories: an alarm clock or a normal alarm (R-15.3, R-15.14). */
internal fun AlarmItem.criticalIn(criticalNames: Set<String>): CriticalAlarm? =
    if (category in criticalNames) CriticalAlarm(orderId) else null
