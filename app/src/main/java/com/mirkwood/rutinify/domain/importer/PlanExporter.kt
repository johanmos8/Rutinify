package com.mirkwood.rutinify.domain.importer

import com.mirkwood.rutinify.domain.model.AlarmItem
import com.mirkwood.rutinify.domain.model.RoutineOrder

/** Writes a plan back as contract JSON (v1), without statuses or snoozes, so another phone can import it (R-19.1). */
interface PlanExporter {
    fun export(order: RoutineOrder, alarms: List<AlarmItem>): String
}
