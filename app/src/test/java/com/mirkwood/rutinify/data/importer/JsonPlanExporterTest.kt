package com.mirkwood.rutinify.data.importer

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.importer.ImportResult
import com.mirkwood.rutinify.domain.model.AlarmItem
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.testutil.testAlarm
import com.mirkwood.rutinify.testutil.testOrder
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** T-58: the shared plan imports back the same (R-19.1, R-19.3). */
class JsonPlanExporterTest {

    private val exporter = JsonPlanExporter()

    private fun reimport(json: String): ImportResult.Success {
        val extracted = requireNotNull(JsonExtractor().extract("Importa este plan en Rutinify\n\n```json\n$json\n```"))
        return RoutineJsonParser().parse(extracted.json, draftId = "d") as ImportResult.Success
    }

    private fun AlarmItem.fields() = listOf(time, dayOffset, title, description, durationMin, category, assignee)

    @Test
    fun `ida y vuelta conserva el plan y cada actividad`() { // R-19.1
        val order = testOrder(id = 1, name = "Cachorro", date = LocalDate.of(2026, 11, 14)).copy(notes = "Primer día en casa")
        val alarms = listOf(
            testAlarm(id = 10, time = LocalTime.of(6, 45), title = "Pis", description = "Jardín", category = "paseo")
                .copy(durationMin = 10, assignee = "Ana"),
            testAlarm(id = 11, time = LocalTime.of(13, 0), title = "Comida", description = null, category = "comida"),
            testAlarm(id = 12, time = LocalTime.of(2, 0), dayOffset = 1, title = "Pis de noche", description = null, category = "paseo")
                .copy(assignee = "Luis"),
        )

        val result = reimport(exporter.export(order, alarms))

        assertThat(result.warnings).isEmpty()
        assertThat(result.draft.name).isEqualTo("Cachorro")
        assertThat(result.draft.date).isEqualTo(LocalDate.of(2026, 11, 14))
        assertThat(result.draft.notes).isEqualTo("Primer día en casa")
        assertThat(result.draft.items.map { listOf(it.time, it.dayOffset, it.title, it.description, it.durationMin, it.category, it.assignee) })
            .containsExactlyElementsIn(alarms.map { it.fields() })
    }

    @Test
    fun `un plan de varios dias se exporta dia a dia`() { // R-19.1, D-55
        val order = testOrder(id = 1, name = "Antibiótico", date = LocalDate.of(2026, 11, 16))
        val alarms = (0..6).flatMap { day ->
            listOf(6, 14, 22).mapIndexed { i, hour ->
                testAlarm(id = day * 10L + i, time = LocalTime.of(hour, 0), dayOffset = day, title = "Amoxicilina", category = "medicación")
            }
        }

        val result = reimport(exporter.export(order, alarms))

        assertThat(result.draft.items).hasSize(21)
        assertThat(result.draft.items.maxOf { it.dayOffset }).isEqualTo(6)
    }

    @Test
    fun `no lleva estados ni posposiciones`() { // R-19.1
        val order = testOrder(id = 1)
        val alarms = listOf(
            testAlarm(id = 10, status = AlarmStatus.DONE, snoozedUntil = Instant.parse("2026-11-05T12:00:00Z")),
        )

        val json = exporter.export(order, alarms)

        assertThat(json).doesNotContain("DONE")
        assertThat(json).doesNotContain("status")
        assertThat(json).doesNotContain("snoozed")
        assertThat(json).contains("\"schemaVersion\": 1")
    }

    @Test
    fun `un plan recurrente se exporta como v2 y vuelve igual`() { // R-19.1, R-23.2
        val recurrence = com.mirkwood.rutinify.domain.model.Recurrence(
            setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.FRIDAY),
            LocalDate.of(2026, 12, 20),
        )
        val order = testOrder(id = 1, date = LocalDate.of(2026, 11, 16)).copy(recurrence = recurrence)

        val result = reimport(exporter.export(order, listOf(testAlarm(id = 1, time = LocalTime.of(7, 30), title = "Paseo"))))

        assertThat(result.draft.recurrence).isEqualTo(recurrence)
        assertThat(result.draft.items.single().title).isEqualTo("Paseo")
    }
}
