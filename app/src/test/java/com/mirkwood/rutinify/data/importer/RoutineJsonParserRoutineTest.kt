package com.mirkwood.rutinify.data.importer

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.importer.ImportResult
import com.mirkwood.rutinify.domain.importer.IssueKind
import com.mirkwood.rutinify.domain.importer.ValidationIssue
import com.mirkwood.rutinify.domain.importer.ValueType
import com.mirkwood.rutinify.domain.model.DraftOrigin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** T-09: routine-level rules of `RoutineJsonParser` (design §4.3). */
class RoutineJsonParserRoutineTest {

    private val parser = RoutineJsonParser()

    private fun parse(fixture: String): ImportResult = parser.parse(Fixtures.json(fixture), draftId = "draft-1")

    private fun parseJson(json: String): ImportResult =
        parser.parse(Json.parseToJsonElement(json).jsonObject, draftId = "draft-1")

    private fun issuesOf(result: ImportResult): List<ValidationIssue> {
        assertThat(result).isInstanceOf(ImportResult.Failure::class.java)
        return (result as ImportResult.Failure).issues
    }

    private fun successOf(result: ImportResult): ImportResult.Success {
        assertThat(result).isInstanceOf(ImportResult.Success::class.java)
        return result as ImportResult.Success
    }

    @Test
    fun `el ejemplo v1 completo es valido`() {
        val success = successOf(parse("valid_v1.json"))

        with(success.draft) {
            assertThat(id).isEqualTo("draft-1")
            assertThat(name).isEqualTo("Rutina del cachorro")
            assertThat(date).isEqualTo(LocalDate.of(2026, 11, 5))
            assertThat(notes).isEqualTo("Primer día en casa")
            assertThat(origin).isEqualTo(DraftOrigin.IMPORT)
            assertThat(items).hasSize(6)
        }
        assertThat(success.warnings).isEmpty()
    }

    @Test
    fun `el ejemplo v1 lee los campos de las actividades`() {
        val items = successOf(parse("valid_v1.json")).draft.items
        val first = items.first()

        assertThat(first.key).isEqualTo(0)
        assertThat(first.time).isEqualTo(LocalTime.of(6, 45))
        assertThat(first.dayOffset).isEqualTo(0)
        assertThat(first.title).isEqualTo("Sacar a hacer pis")
        assertThat(first.description).isEqualTo("Paseo corto en el jardín")
        assertThat(first.durationMin).isEqualTo(10)
        assertThat(first.category).isEqualTo("paseo")
        assertThat(first.assignee).isEqualTo("Johan")
        assertThat(items.last().dayOffset).isEqualTo(1)
        assertThat(items.last().time).isEqualTo(LocalTime.of(2, 0))
    }

    // R-3.4
    @Test
    fun `sin schemaVersion se rechaza sin valor recibido`() {
        val issue = issuesOf(parse("missing_schema_version.json")).single()

        assertThat(issue.kind).isEqualTo(IssueKind.UNSUPPORTED_SCHEMA_VERSION)
        assertThat(issue.field).isEqualTo("schemaVersion")
        assertThat(issue.received).isNull()
    }

    @Test
    fun `schemaVersion 2 se rechaza indicando el valor recibido y no se valida nada mas`() { // R-3.4
        val issues = issuesOf(parseJson("""{"schemaVersion": 2, "routine": {"name": ""}, "items": []}"""))

        assertThat(issues).containsExactly(
            ValidationIssue(field = "schemaVersion", kind = IssueKind.UNSUPPORTED_SCHEMA_VERSION, received = "2"),
        )
    }

    @Test
    fun `schemaVersion como texto no es la version 1`() { // R-3.4
        val issue = issuesOf(parse("schema_version_string.json")).single()

        assertThat(issue.kind).isEqualTo(IssueKind.UNSUPPORTED_SCHEMA_VERSION)
        assertThat(issue.received).isEqualTo("1")
    }

    @Test
    fun `schemaVersion decimal no es la version 1`() { // R-3.4
        val issue = issuesOf(parseJson("""{"schemaVersion": 1.5, "routine": {"name": "x"}, "items": [{}]}""")).single()

        assertThat(issue.kind).isEqualTo(IssueKind.UNSUPPORTED_SCHEMA_VERSION)
        assertThat(issue.received).isEqualTo("1.5")
    }

    @Test
    fun `sin routine falta el campo routine`() {
        val issues = issuesOf(parse("missing_routine.json"))

        assertThat(issues).containsExactly(ValidationIssue(field = "routine", kind = IssueKind.MISSING_FIELD))
    }

    @Test
    fun `routine que no es un objeto es un tipo incorrecto`() { // R-3.13
        val issue = issuesOf(parse("routine_not_object.json")).single()

        assertThat(issue.kind).isEqualTo(IssueKind.WRONG_TYPE)
        assertThat(issue.field).isEqualTo("routine")
        assertThat(issue.expected).isEqualTo(ValueType.OBJECT)
    }

    // R-3.5
    @Test
    fun `sin nombre se indica routine name`() {
        val issue = issuesOf(parse("missing_name.json")).single()

        assertThat(issue.kind).isEqualTo(IssueKind.MISSING_FIELD)
        assertThat(issue.field).isEqualTo("routine.name")
    }

    @Test
    fun `nombre solo con espacios cuenta como vacio`() { // R-3.5
        val issue = issuesOf(parse("blank_name.json")).single()

        assertThat(issue.kind).isEqualTo(IssueKind.BLANK_FIELD)
        assertThat(issue.field).isEqualTo("routine.name")
    }

    @Test
    fun `nombre numerico es un tipo incorrecto`() { // R-3.13
        val issue = issuesOf(parse("name_not_text.json")).single()

        assertThat(issue.kind).isEqualTo(IssueKind.WRONG_TYPE)
        assertThat(issue.field).isEqualTo("routine.name")
        assertThat(issue.expected).isEqualTo(ValueType.TEXT)
        assertThat(issue.received).isEqualTo("42")
    }

    @Test
    fun `el nombre y las notas se recortan y unas notas vacias quedan en null`() {
        val draft = successOf(
            parseJson("""{"schemaVersion": 1, "routine": {"name": "  Cachorro  ", "notes": "   "}, "items": [{"time": "06:45", "title": "Pis"}]}"""),
        ).draft

        assertThat(draft.name).isEqualTo("Cachorro")
        assertThat(draft.notes).isNull()
    }

    // R-3.6
    @Test
    fun `sin fecha el borrador queda sin fecha`() {
        assertThat(successOf(parse("no_date.json")).draft.date).isNull()
    }

    @Test
    fun `fecha null equivale a sin fecha`() { // R-3.6
        assertThat(successOf(parse("null_date.json")).draft.date).isNull()
    }

    @Test
    fun `fecha con formato distinto de yyyy-MM-dd no es valida`() { // R-3.6
        val issue = issuesOf(parse("invalid_date_format.json")).single()

        assertThat(issue).isEqualTo(
            ValidationIssue(field = "routine.date", kind = IssueKind.INVALID_DATE, received = "05/11/2026"),
        )
    }

    @Test
    fun `fecha inexistente no es valida`() { // R-3.6
        val issue = issuesOf(parse("impossible_date.json")).single()

        assertThat(issue.kind).isEqualTo(IssueKind.INVALID_DATE)
        assertThat(issue.received).isEqualTo("2026-02-30")
    }

    @Test
    fun `fecha numerica no es valida`() { // R-3.6
        val issue = issuesOf(
            parseJson("""{"schemaVersion": 1, "routine": {"name": "x", "date": 20261105}, "items": [{"time": "06:45", "title": "Pis"}]}"""),
        ).single()

        assertThat(issue.kind).isEqualTo(IssueKind.INVALID_DATE)
        assertThat(issue.received).isEqualTo("20261105")
    }

    // R-3.7
    @Test
    fun `sin items no hay actividades`() {
        assertThat(issuesOf(parse("missing_items.json")))
            .containsExactly(ValidationIssue(field = "items", kind = IssueKind.NO_ITEMS))
    }

    @Test
    fun `items vacio no hay actividades`() { // R-3.7
        assertThat(issuesOf(parse("empty_items.json")))
            .containsExactly(ValidationIssue(field = "items", kind = IssueKind.NO_ITEMS))
    }

    @Test
    fun `items que no es una lista no hay actividades`() { // R-3.7
        assertThat(issuesOf(parse("items_not_list.json")))
            .containsExactly(ValidationIssue(field = "items", kind = IssueKind.NO_ITEMS))
    }

    // R-3.18
    @Test
    fun `mas de 200 actividades se rechaza con el numero recibido`() {
        val issue = issuesOf(parseJson(routineWithItems(201))).single()

        assertThat(issue).isEqualTo(
            ValidationIssue(field = "items", kind = IssueKind.TOO_MANY_ITEMS, received = "201"),
        )
    }

    @Test
    fun `exactamente 200 actividades se aceptan`() { // R-3.18
        assertThat(successOf(parseJson(routineWithItems(200))).draft.items).hasSize(200)
    }

    // R-3.16
    @Test
    fun `acumula todos los errores de nivel rutina en una pasada`() {
        val issues = issuesOf(parse("several_routine_errors.json"))

        assertThat(issues.map { it.field }).containsExactly("routine.name", "routine.date", "items").inOrder()
        assertThat(issues.map { it.kind })
            .containsExactly(IssueKind.BLANK_FIELD, IssueKind.INVALID_DATE, IssueKind.NO_ITEMS)
            .inOrder()
    }

    // R-3.2
    @Test
    fun `ignora campos desconocidos en cualquier nivel`() {
        val success = successOf(parse("unknown_fields.json"))

        assertThat(success.draft.items).hasSize(1)
        assertThat(success.warnings).isEmpty()
    }

    @Test
    fun `el valor recibido se recorta a 30 caracteres`() {
        val longDate = "x".repeat(50)
        val issue = issuesOf(
            parseJson("""{"schemaVersion": 1, "routine": {"name": "x", "date": "$longDate"}, "items": [{"time": "06:45", "title": "Pis"}]}"""),
        ).single()

        assertThat(issue.received).hasLength(ValidationIssue.MAX_RECEIVED_LENGTH)
    }

    private fun routineWithItems(count: Int): String {
        val items = (0 until count).joinToString(",") { """{"time": "06:45", "title": "Actividad $it"}""" }
        return """{"schemaVersion": 1, "routine": {"name": "Muchas"}, "items": [$items]}"""
    }
}
