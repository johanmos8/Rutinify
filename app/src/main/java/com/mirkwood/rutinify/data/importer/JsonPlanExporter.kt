package com.mirkwood.rutinify.data.importer

import com.mirkwood.rutinify.domain.model.Recurrence
import com.mirkwood.rutinify.domain.importer.PlanExporter
import com.mirkwood.rutinify.domain.model.AlarmItem
import com.mirkwood.rutinify.domain.model.RoutineOrder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Contract v1 (design §4.1) with every field, nulls included, in the same order as the prompt templates. Each day is
 * written with its `dayOffset`: `repeatDays` is not rebuilt, which keeps days edited after import (R-19.1).
 */
class JsonPlanExporter @Inject constructor() : PlanExporter {

    override fun export(order: RoutineOrder, alarms: List<AlarmItem>, pretty: Boolean): String {
        val root = buildJsonObject {
            put("schemaVersion", if (order.recurrence != null) 2 else 1)
            put(
                "routine",
                buildJsonObject {
                    put("name", order.name)
                    put("date", order.date.toString())
                    put("notes", text(order.notes))
                    order.recurrence?.let { recurrence ->
                        put(
                            "recurrence",
                            buildJsonObject {
                                put("weekdays", buildJsonArray { recurrence.weekdays.sorted().forEach { add(JsonPrimitive(codeOf(it))) } })
                                put("until", text(recurrence.until?.toString()))
                            },
                        )
                    }
                },
            )
            put(
                "items",
                buildJsonArray {
                    alarms.sortedWith(compareBy({ it.dayOffset }, { it.time }, { it.position })).forEach { alarm ->
                        add(
                            buildJsonObject {
                                put("time", alarm.time.format(TIME))
                                put("dayOffset", alarm.dayOffset)
                                put("title", alarm.title)
                                put("description", text(alarm.description))
                                put("durationMin", alarm.durationMin?.let(::JsonPrimitive) ?: JsonNull)
                                put("category", alarm.category)
                                put("assignee", text(alarm.assignee))
                            },
                        )
                    }
                },
            )
        }
        return (if (pretty) PRETTY else Json).encodeToString(JsonElement.serializer(), root)
    }

    private fun codeOf(day: java.time.DayOfWeek): String = Recurrence.CODES.entries.first { it.value == day }.key

    private fun text(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        @Suppress("JSON_FORMAT_REDUNDANT")
        val PRETTY = Json { prettyPrint = true }
    }
}
