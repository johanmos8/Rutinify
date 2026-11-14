package com.mirkwood.rutinify.data.importer

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.importer.ImportResult
import com.mirkwood.rutinify.domain.importer.IssueKind
import com.mirkwood.rutinify.domain.importer.ValidationIssue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import java.time.LocalTime

/** T-33: multi-day routines, `routine.repeatDays` and `dayOffset` up to 30 (R-3.24–R-3.27, D-55). */
class RoutineJsonParserRepeatTest {

    private val parser = RoutineJsonParser()

    private fun parse(fixture: String): ImportResult = parser.parse(Fixtures.json(fixture), draftId = "draft-1")

    private fun parseJson(json: String): ImportResult =
        parser.parse(Json.parseToJsonElement(json).jsonObject, draftId = "draft-1")

    /** A routine with the given `repeatDays` JSON value (or none) and the given raw item objects. */
    private fun routine(repeatDays: String?, vararg items: String): ImportResult {
        val repeat = repeatDays?.let { """, "repeatDays": $it""" }.orEmpty()
        return parseJson("""{"schemaVersion": 1, "routine": {"name": "Pastillas"$repeat}, "items": [${items.joinToString(",")}]}""")
    }

    private fun pill(time: String, extra: String = "") = """{"time": "$time", "title": "Pastilla $time"$extra}"""

    private fun issuesOf(result: ImportResult): List<ValidationIssue> {
        assertThat(result).isInstanceOf(ImportResult.Failure::class.java)
        return (result as ImportResult.Failure).issues
    }

    private fun successOf(result: ImportResult): ImportResult.Success {
        assertThat(result).isInstanceOf(ImportResult.Success::class.java)
        return result as ImportResult.Success
    }

    // ---- Fixtures ----

    @Test
    fun `el antibiotico cada 8 horas con repeatDays 7 da 21 tomas sin avisos`() { // R-3.25
        val success = successOf(parse("valid_repeat.json"))
        val items = success.draft.items

        assertThat(success.warnings).isEmpty()
        assertThat(items).hasSize(21)
        assertThat(items.map { it.dayOffset }.distinct()).containsExactly(0, 1, 2, 3, 4, 5, 6).inOrder()
        assertThat(items.take(3).map { it.time })
            .containsExactly(LocalTime.of(6, 0), LocalTime.of(14, 0), LocalTime.of(22, 0)).inOrder()
        assertThat(items.map { it.description }.distinct()).containsExactly("Con un poco de comida")
    }

    @Test
    fun `una dosis que baja escrita dia a dia se importa sin avisos`() { // R-3.11, D-55
        val success = successOf(parse("valid_multiday.json"))

        assertThat(success.warnings).isEmpty()
        assertThat(success.draft.items).hasSize(9)
        assertThat(success.draft.items.last().dayOffset).isEqualTo(5)
        assertThat(success.draft.items.last().title).isEqualTo("Prednisona media pastilla")
    }

    // ---- repeatDays values (R-3.24) ----

    @Test
    fun `sin repeatDays o con null no se repite`() { // R-3.24
        assertThat(successOf(routine(null, pill("08:00"))).draft.items).hasSize(1)
        assertThat(successOf(routine("null", pill("08:00"))).draft.items).hasSize(1)
        assertThat(successOf(routine("1", pill("08:00"))).draft.items).hasSize(1)
    }

    @Test
    fun `repeatDays 30 se acepta`() { // R-3.24
        val items = successOf(routine("30", pill("08:00"))).draft.items

        assertThat(items.map { it.dayOffset }).isEqualTo((0..29).toList())
    }

    @Test
    fun `repeatDays fuera de 1 a 30 o no entero se rechaza con el valor recibido`() { // R-3.24
        listOf("0", "31", "-2", "1.5", "\"7\"", "true").forEach { value ->
            val issue = issuesOf(routine(value, pill("08:00"))).single()

            assertThat(issue.kind).isEqualTo(IssueKind.REPEAT_DAYS_OUT_OF_RANGE)
            assertThat(issue.field).isEqualTo("routine.repeatDays")
            assertThat(issue.itemIndex).isNull()
            assertThat(issue.received).isEqualTo(value.trim('"'))
        }
    }

    @Test
    fun `un repeatDays invalido se informa junto a los errores de las actividades`() { // R-3.16
        val issues = issuesOf(routine("0", pill("25:00")))

        assertThat(issues.map { it.kind })
            .containsExactly(IssueKind.REPEAT_DAYS_OUT_OF_RANGE, IssueKind.INVALID_TIME).inOrder()
    }

    // ---- Expansion (R-3.25) ----

    @Test
    fun `cada copia conserva los campos y suma el dia a su dayOffset`() { // R-3.25
        val item = """{"time": "09:00", "title": "Vitamina", "description": "Con agua", "durationMin": 5,
            "category": "Salud", "assignee": "Ana"}"""
        val items = successOf(routine("3", item)).draft.items

        assertThat(items.map { it.dayOffset }).containsExactly(0, 1, 2).inOrder()
        items.forEach {
            assertThat(it.time).isEqualTo(LocalTime.of(9, 0))
            assertThat(it.title).isEqualTo("Vitamina")
            assertThat(it.description).isEqualTo("Con agua")
            assertThat(it.durationMin).isEqualTo(5)
            assertThat(it.category).isEqualTo("salud")
            assertThat(it.assignee).isEqualTo("Ana")
        }
    }

    @Test
    fun `la toma de madrugada del ultimo dia cae el dia siguiente al ultimo`() { // R-3.25
        val items = successOf(routine("3", pill("22:00"), pill("02:00", """, "dayOffset": 1""")))
            .draft.items

        assertThat(items.map { it.dayOffset to it.time.hour })
            .containsExactly(0 to 22, 1 to 2, 1 to 22, 2 to 2, 2 to 22, 3 to 2).inOrder()
    }

    @Test
    fun `con repeatDays 30 la madrugada del ultimo dia llega a dayOffset 30`() { // R-3.25, D-55
        val items = successOf(routine("30", pill("02:00", """, "dayOffset": 1"""))).draft.items

        assertThat(items.last().dayOffset).isEqualTo(30)
    }

    @Test
    fun `las copias tienen claves distintas y se ordenan por dia hora y posicion`() { // R-4.16, design §4.4
        val items = successOf(routine("2", pill("20:00"), pill("08:00"))).draft.items

        assertThat(items.map { it.key }.toSet()).hasSize(4)
        assertThat(items.map { it.dayOffset to it.time.hour })
            .containsExactly(0 to 8, 0 to 20, 1 to 8, 1 to 20).inOrder()
    }

    // ---- Mixing repeatDays and dayOffset (R-3.26) ----

    @Test
    fun `repeatDays con dayOffset mayor que 1 se rechaza en esa actividad`() { // R-3.26
        val issue = issuesOf(routine("7", pill("08:00"), pill("09:00", """, "dayOffset": 2"""))).single()

        assertThat(issue.kind).isEqualTo(IssueKind.REPEAT_WITH_DAY_OFFSET)
        assertThat(issue.itemIndex).isEqualTo(2)
        assertThat(issue.field).isEqualTo("dayOffset")
        assertThat(issue.received).isEqualTo("2")
    }

    @Test
    fun `sin repeticion dayOffset mayor que 1 sigue siendo valido`() { // R-3.11
        assertThat(successOf(routine("1", pill("08:00", """, "dayOffset": 5"""))).draft.items.single().dayOffset)
            .isEqualTo(5)
    }

    @Test
    fun `dayOffset 31 con repeatDays da solo el error de rango`() { // R-3.11
        val issue = issuesOf(routine("7", pill("08:00", """, "dayOffset": 31"""))).single()

        assertThat(issue.kind).isEqualTo(IssueKind.DAY_OFFSET_OUT_OF_RANGE)
    }

    // ---- Item limit after expansion (R-3.27) ----

    @Test
    fun `mas de 200 actividades contando las repeticiones se rechaza`() { // R-3.27
        val items = (0 until 70).map { pill("08:00") }.toTypedArray()
        val issue = issuesOf(routine("3", *items)).single()

        assertThat(issue).isEqualTo(
            ValidationIssue(field = "items", kind = IssueKind.TOO_MANY_REPEATED_ITEMS, received = "210"),
        )
    }

    @Test
    fun `exactamente 200 actividades contando las repeticiones se aceptan`() { // R-3.27
        val items = (0 until 40).map { pill("08:00") }.toTypedArray()

        assertThat(successOf(routine("5", *items)).draft.items).hasSize(200)
    }

    @Test
    fun `los errores se numeran sobre las actividades del JSON y no sobre las copias`() { // R-3.27, R-3.17
        val issues = issuesOf(routine("5", pill("08:00"), pill("08:00"), pill("99:00")))

        assertThat(issues.map { it.itemIndex to it.kind }).containsExactly(3 to IssueKind.INVALID_TIME)
    }
}
