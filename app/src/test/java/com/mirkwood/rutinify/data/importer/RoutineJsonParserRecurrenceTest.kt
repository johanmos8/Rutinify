package com.mirkwood.rutinify.data.importer

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.importer.ImportResult
import com.mirkwood.rutinify.domain.importer.IssueKind
import com.mirkwood.rutinify.domain.model.Recurrence
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** T-67: schema v2 with `routine.recurrence` (R-23.1–R-23.6, D-81). */
class RoutineJsonParserRecurrenceTest {

    private val parser = RoutineJsonParser()

    private fun parse(text: String): ImportResult = parser.parse(Json.parseToJsonElement(text) as JsonObject, draftId = "d")

    private fun kinds(result: ImportResult) = (result as ImportResult.Failure).issues.map { it.kind }

    private fun v(version: Int, recurrence: String, routineExtra: String = "", items: String = """{"time": "07:30", "title": "Paseo"}""") =
        """{"schemaVersion": $version, "routine": {"name": "Luna", "date": "2026-11-16"$routineExtra, "recurrence": $recurrence}, "items": [$items]}"""

    @Test
    fun `un v2 valido trae su recurrencia`() { // R-23.1, R-23.2
        val result = parser.parse(Fixtures.json("valid_v2.json"), draftId = "d") as ImportResult.Success

        assertThat(result.warnings).isEmpty()
        assertThat(result.draft.recurrence)
            .isEqualTo(Recurrence(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), LocalDate.of(2026, 12, 20)))
        assertThat(result.draft.items).hasSize(2)
    }

    @Test
    fun `un v2 sin recurrencia es como un v1 y sin until no termina`() { // R-23.1, R-23.2
        val plain = parse("""{"schemaVersion": 2, "routine": {"name": "Luna"}, "items": [{"time": "07:30", "title": "Paseo"}]}""") as ImportResult.Success
        assertThat(plain.draft.recurrence).isNull()

        val endless = parse(v(2, """{"weekdays": ["SU"]}""")) as ImportResult.Success
        assertThat(endless.draft.recurrence).isEqualTo(Recurrence(setOf(DayOfWeek.SUNDAY), until = null))
    }

    @Test
    fun `un v1 sigue igual`() { // R-23.1
        val result = parser.parse(Fixtures.json("valid_v1.json"), draftId = "d") as ImportResult.Success

        assertThat(result.draft.recurrence).isNull()
    }

    @Test
    fun `recurrence con schemaVersion 1 pide la version 2`() { // R-23.3
        assertThat(kinds(parse(v(1, """{"weekdays": ["MO"]}""")))).containsExactly(IssueKind.RECURRENCE_NEEDS_V2)
    }

    @Test
    fun `weekdays ausente vacio desconocido o repetido`() { // R-23.4
        listOf("""{}""", """{"weekdays": []}""", """{"weekdays": ["MO", "XX"]}""", """{"weekdays": ["MO", "MO"]}""", """{"weekdays": "MO"}""")
            .forEach { assertThat(kinds(parse(v(2, it)))).containsExactly(IssueKind.RECURRENCE_WEEKDAYS) }
    }

    @Test
    fun `until invalido o anterior a la fecha de la rutina`() { // R-23.5
        assertThat(kinds(parse(v(2, """{"weekdays": ["MO"], "until": "2026-11-15"}""")))).containsExactly(IssueKind.RECURRENCE_UNTIL)
        assertThat(kinds(parse(v(2, """{"weekdays": ["MO"], "until": "20-12-2026"}""")))).containsExactly(IssueKind.RECURRENCE_UNTIL)
        assertThat(parse(v(2, """{"weekdays": ["MO"], "until": "2026-11-16"}"""))).isInstanceOf(ImportResult.Success::class.java)
    }

    @Test
    fun `no se combina con repeatDays ni con dayOffset mayor que 1`() { // R-23.6
        assertThat(kinds(parse(v(2, """{"weekdays": ["MO"]}""", routineExtra = """, "repeatDays": 3""")))).containsExactly(IssueKind.RECURRENCE_COMBINATION)
        assertThat(kinds(parse(v(2, """{"weekdays": ["MO"]}""", items = """{"time": "07:30", "title": "Paseo", "dayOffset": 2}"""))))
            .containsExactly(IssueKind.RECURRENCE_COMBINATION)
        // The early hours of the next day are fine
        assertThat(parse(v(2, """{"weekdays": ["MO"]}""", items = """{"time": "02:00", "title": "Pis", "dayOffset": 1}""")))
            .isInstanceOf(ImportResult.Success::class.java)
    }
}
