package com.mirkwood.rutinify.domain.importer

import com.mirkwood.rutinify.domain.model.AlarmItem
import com.mirkwood.rutinify.domain.model.RoutineOrder

/** Writes a plan back as contract JSON (v1), without statuses or snoozes, so another phone can import it (R-19.1). */
interface PlanExporter {
    /** [pretty] for text a person reads (R-19.3); compact for links and QR codes (R-19.4). */
    fun export(order: RoutineOrder, alarms: List<AlarmItem>, pretty: Boolean = true): String
}
