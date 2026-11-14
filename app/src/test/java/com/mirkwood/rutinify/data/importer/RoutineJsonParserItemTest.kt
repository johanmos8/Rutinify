package com.mirkwood.rutinify.data.importer

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.importer.ImportResult
import com.mirkwood.rutinify.domain.importer.IssueKind
import com.mirkwood.rutinify.domain.importer.Severity
import com.mirkwood.rutinify.domain.importer.ValidationIssue
import com.mirkwood.rutinify.domain.importer.ValueType
import com.mirkwood.rutinify.domain.model.DraftItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import java.time.LocalTime

/** T-10: item-level rules of `RoutineJsonParser` (design §4.3). */
class RoutineJsonParserItemTest {

    private val parser = RoutineJsonParser()

    /** Builds a v1 routine whose items are the given JSON objects (as raw JSON text). */
    private fun parseItems(vararg items: String, notes: JsonElement = JsonNull): ImportResult {
        val root = buildJsonObject {
            put("schemaVersion", 1)
            put("routine", buildJsonObject {
                put("name", "Rutina")
                put("notes", notes)
            })
            put("items", buildJsonArray { items.forEach { add(Json.parseToJsonElement(it)) } })
        }
        return parser.parse(root, draftId = "draft-1")
    }

    private fun item(vararg fields: Pair<String, JsonElement>): String =
        JsonObject(mapOf("time" to JsonPrimitive("06:45"), "title" to JsonPrimitive("Pis")) + fields).toString()

    private fun issuesOf(result: ImportResult): List<ValidationIssue> {
        assertThat(result).isInstanceOf(ImportResult.Failure::class.java)
        return (result as ImportResult.Failure).issues
    }

    private fun successOf(result: ImportResult): ImportResult.Success {
        assertThat(result).isInstanceOf(ImportResult.Success::class.java)
        return result as ImportResult.Success
    }

    private fun singleItem(result: ImportResult): DraftItem = successOf(result).draft.items.single()

    private fun text(value: String) = JsonPrimitive(value)
    private fun number(value: Number) = JsonPrimitive(value)

    // ---- time (R-3.8, D-13) ----

    @Test
    fun `hora con una cifra se normaliza a HH mm`() { // R-3.8
        assertThat(singleItem(parseItems(item("time" to text("6:45")))).time).isEqualTo(LocalTime.of(6, 45))
    }

    @Test
    fun `los extremos 00 00 y 23 59 son horas validas`() { // R-3.8
        val items = successOf(parseItems(item("time" to text("00:00")), item("time" to text("23:59")))).draft.items

        assertThat(items.map { it.time }).containsExactly(LocalTime.MIDNIGHT, LocalTime.of(23, 59)).inOrder()
    }

    @Test
    fun `hora 25 00 no es valida e indica actividad titulo y valor`() { // R-3.8
        val issue = issuesOf(parseItems(item("time" to text("25:00"), "title" to text("Siesta")))).single()

        assertThat(issue).isEqualTo(
            ValidationIssue(
                itemIndex = 1,
                itemTitle = "Siesta",
                itemTime = "25:00",
                field = "time",
                kind = IssueKind.INVALID_TIME,
                received = "25:00",
            ),
        )
    }

    @Test
    fun `horas con formato incorrecto no son validas`() { // R-3.8
        val invalid = listOf("24:00", "6:5", "06:60", "6.45", "06:45:00", "6h45", "", "  ")

        invalid.forEach { value ->
            val issue = issuesOf(parseItems(item("time" to text(value)))).single()
            assertThat(issue.kind).isEqualTo(IssueKind.INVALID_TIME)
        }
    }

    @Test
    fun `sin hora falta el campo time con el titulo como contexto`() { // R-3.8
        val issue = issuesOf(parseItems("""{"title": "Siesta"}""")).single()

        assertThat(issue.kind).isEqualTo(IssueKind.MISSING_FIELD)
        assertThat(issue.field).isEqualTo("time")
        assertThat(issue.itemTitle).isEqualTo("Siesta")
    }

    @Test
    fun `hora numerica es un tipo incorrecto que espera texto`() { // R-3.13
        val issue = issuesOf(parseItems(item("time" to number(645)))).single()

        assertThat(issue.kind).isEqualTo(IssueKind.WRONG_TYPE)
        assertThat(issue.field).isEqualTo("time")
        assertThat(issue.expected).isEqualTo(ValueType.TEXT)
        assertThat(issue.received).isEqualTo("645")
    }

    // ---- title (R-3.9, R-3.10) ----

    @Test
    fun `sin titulo falta el campo title con la hora como contexto`() { // R-3.9
        val issue = issuesOf(parseItems("""{"time": "06:45"}""")).single()

        assertThat(issue).isEqualTo(
            ValidationIssue(itemIndex = 1, itemTime = "06:45", field = "title", kind = IssueKind.MISSING_FIELD),
        )
    }

    @Test
    fun `titulo solo con espacios cuenta como vacio`() { // R-3.9
        val issue = issuesOf(parseItems(item("title" to text("   ")))).single()

        assertThat(issue.kind).isEqualTo(IssueKind.BLANK_FIELD)
        assertThat(issue.field).isEqualTo("title")
        assertThat(issue.itemTitle).isNull()
    }

    @Test
    fun `titulo como lista es un tipo incorrecto`() { // R-3.13
        val issue = issuesOf(parseItems("""{"time": "06:45", "title": ["a", "b"]}""")).single()

        assertThat(issue.kind).isEqualTo(IssueKind.WRONG_TYPE)
        assertThat(issue.field).isEqualTo("title")
        assertThat(issue.expected).isEqualTo(ValueType.TEXT)
    }

    @Test
    fun `titulo de 80 caracteres no se recorta`() { // R-3.10
        val title = "a".repeat(80)
        val success = successOf(parseItems(item("title" to text(title))))

        assertThat(success.draft.items.single().title).isEqualTo(title)
        assertThat(success.warnings).isEmpty()
    }

    @Test
    fun `titulo de 81 caracteres se recorta a 80 con un aviso no bloqueante`() { // R-3.10
        val success = successOf(parseItems(item("title" to text("a".repeat(81)))))

        assertThat(success.draft.items.single().title).hasLength(80)
        val warning = success.warnings.single()
        assertThat(warning.kind).isEqualTo(IssueKind.TITLE_TRUNCATED)
        assertThat(warning.severity).isEqualTo(Severity.WARNING)
        assertThat(warning.itemIndex).isEqualTo(1)
        assertThat(warning.field).isEqualTo("title")
    }

    @Test
    fun `el titulo se recorta sin dejar espacios al final`() { // R-3.10
        val title = "a".repeat(79) + " " + "b".repeat(10)

        assertThat(singleItem(parseItems(item("title" to text(title)))).title).isEqualTo("a".repeat(79))
    }

    // ---- dayOffset (R-3.11, R-3.15) ----

    @Test
    fun `sin dayOffset o con null se usa 0`() { // R-3.15
        val items = successOf(parseItems(item(), item("dayOffset" to JsonNull))).draft.items

        assertThat(items.map { it.dayOffset }).containsExactly(0, 0)
    }

    @Test
    fun `dayOffset 1, 6 y 30 se aceptan`() { // R-3.11, D-55
        listOf(1, 6, 30).forEach { offset ->
            assertThat(singleItem(parseItems(item("dayOffset" to number(offset)))).dayOffset).isEqualTo(offset)
        }
    }

    @Test
    fun `dayOffset fuera de 0 a 30 o no entero se rechaza`() { // R-3.11
        val invalid = listOf<JsonElement>(number(31), number(-1), number(1.5), text("1"), JsonPrimitive(true))

        invalid.forEach { value ->
            val issue = issuesOf(parseItems(item("dayOffset" to value))).single()
            assertThat(issue.kind).isEqualTo(IssueKind.DAY_OFFSET_OUT_OF_RANGE)
            assertThat(issue.field).isEqualTo("dayOffset")
        }
    }

    @Test
    fun `dayOffset 31 indica el valor recibido`() { // R-3.11
        assertThat(issuesOf(parseItems(item("dayOffset" to number(31)))).single().received).isEqualTo("31")
    }

    // ---- durationMin (R-3.12) ----

    @Test
    fun `durationMin ausente o null queda sin duracion`() { // R-3.12
        val items = successOf(parseItems(item(), item("durationMin" to JsonNull))).draft.items

        assertThat(items.map { it.durationMin }).containsExactly(null, null)
    }

    @Test
    fun `durationMin 1 y 1440 son los extremos validos`() { // R-3.12
        val items = successOf(
            parseItems(item("durationMin" to number(1)), item("time" to text("07:00"), "durationMin" to number(1440))),
        ).draft.items

        assertThat(items.map { it.durationMin }).containsExactly(1, 1440).inOrder()
    }

    @Test
    fun `durationMin fuera de rango o no entero se rechaza`() { // R-3.12
        val invalid = listOf<JsonElement>(number(0), number(1441), number(-5), number(10.5), text("10"))

        invalid.forEach { value ->
            val issue = issuesOf(parseItems(item("durationMin" to value))).single()
            assertThat(issue.kind).isEqualTo(IssueKind.DURATION_OUT_OF_RANGE)
            assertThat(issue.field).isEqualTo("durationMin")
        }
    }

    // ---- description, assignee (R-3.13, R-3.21) ----

    @Test
    fun `descripcion y responsable se recortan y vacios quedan en null`() {
        val first = singleItem(parseItems(item("description" to text("  Corto  "), "assignee" to text("  Ana "))))
        val second = singleItem(parseItems(item("description" to text("  "), "assignee" to text(""))))

        assertThat(first.description).isEqualTo("Corto")
        assertThat(first.assignee).isEqualTo("Ana")
        assertThat(second.description).isNull()
        assertThat(second.assignee).isNull()
    }

    @Test
    fun `descripcion numerica es un tipo incorrecto`() { // R-3.13
        val issue = issuesOf(parseItems(item("description" to number(3)))).single()

        assertThat(issue.kind).isEqualTo(IssueKind.WRONG_TYPE)
        assertThat(issue.field).isEqualTo("description")
        assertThat(issue.itemIndex).isEqualTo(1)
    }

    @Test
    fun `responsable como objeto es un tipo incorrecto`() { // R-3.13
        val issue = issuesOf(parseItems("""{"time": "06:45", "title": "Pis", "assignee": {"name": "Ana"}}""")).single()

        assertThat(issue.kind).isEqualTo(IssueKind.WRONG_TYPE)
        assertThat(issue.field).isEqualTo("assignee")
        assertThat(issue.expected).isEqualTo(ValueType.TEXT)
    }

    @Test
    fun `descripcion de 500 caracteres pasa y de 501 falla con el maximo`() { // R-3.21
        assertThat(singleItem(parseItems(item("description" to text("d".repeat(500))))).description).hasLength(500)

        val issue = issuesOf(parseItems(item("description" to text("d".repeat(501))))).single()
        assertThat(issue.kind).isEqualTo(IssueKind.TEXT_TOO_LONG)
        assertThat(issue.field).isEqualTo("description")
        assertThat(issue.itemIndex).isEqualTo(1)
        assertThat(issue.limit).isEqualTo(500)
    }

    @Test
    fun `la longitud se mide tras recortar espacios`() { // R-3.21
        val description = "  " + "d".repeat(500) + "  "

        assertThat(singleItem(parseItems(item("description" to text(description)))).description).hasLength(500)
    }

    @Test
    fun `responsable de 40 caracteres pasa y de 41 falla con el maximo`() { // R-3.21
        assertThat(singleItem(parseItems(item("assignee" to text("a".repeat(40))))).assignee).hasLength(40)

        val issue = issuesOf(parseItems(item("assignee" to text("a".repeat(41))))).single()
        assertThat(issue.kind).isEqualTo(IssueKind.TEXT_TOO_LONG)
        assertThat(issue.field).isEqualTo("assignee")
        assertThat(issue.limit).isEqualTo(40)
    }

    @Test
    fun `notas de 500 caracteres pasan y de 501 fallan a nivel rutina`() { // R-3.21
        assertThat(successOf(parseItems(item(), notes = text("n".repeat(500)))).draft.notes).hasLength(500)

        val issue = issuesOf(parseItems(item(), notes = text("n".repeat(501)))).single()
        assertThat(issue).isEqualTo(
            ValidationIssue(field = "routine.notes", kind = IssueKind.TEXT_TOO_LONG, limit = 500),
        )
    }

    // ---- category (R-3.14, R-3.21, R-3.22) ----

    @Test
    fun `sin categoria o vacia se asigna general`() { // R-3.14
        val items = successOf(
            parseItems(item(), item("category" to JsonNull), item("category" to text("   "))),
        ).draft.items

        assertThat(items.map { it.category }).containsExactly("general", "general", "general")
    }

    @Test
    fun `la categoria se normaliza a minusculas sin espacios y con guiones`() { // R-3.14
        val category = singleItem(parseItems(item("category" to text("  Hora  de\tComer  ")))).category

        assertThat(category).isEqualTo("hora-de-comer")
    }

    @Test
    fun `categoria numerica es un tipo incorrecto`() { // R-3.13
        val issue = issuesOf(parseItems(item("category" to number(7)))).single()

        assertThat(issue.kind).isEqualTo(IssueKind.WRONG_TYPE)
        assertThat(issue.field).isEqualTo("category")
    }

    @Test
    fun `categoria de 30 caracteres tras normalizar pasa y de 31 falla`() { // R-3.21
        val thirty = "  " + "c".repeat(15) + "   " + "c".repeat(14) + "  " // → 15 + "-" + 14 = 30
        assertThat(singleItem(parseItems(item("category" to text(thirty)))).category).hasLength(30)

        val issue = issuesOf(parseItems(item("category" to text("c".repeat(31))))).single()
        assertThat(issue.kind).isEqualTo(IssueKind.TEXT_TOO_LONG)
        assertThat(issue.field).isEqualTo("category")
        assertThat(issue.limit).isEqualTo(30)
    }

    @Test
    fun `10 categorias distintas pasan`() { // R-3.22
        val items = (1..10).map { item("category" to text("Cat $it")) }

        assertThat(successOf(parseItems(*items.toTypedArray())).draft.items.map { it.category }.distinct()).hasSize(10)
    }

    @Test
    fun `11 categorias distintas fallan con el numero recibido`() { // R-3.22
        val items = (1..11).map { item("category" to text("cat-$it")) }

        val issue = issuesOf(parseItems(*items.toTypedArray())).single()
        assertThat(issue).isEqualTo(
            ValidationIssue(field = "items", kind = IssueKind.TOO_MANY_CATEGORIES, received = "11", limit = 10),
        )
    }

    @Test
    fun `las categorias se cuentan tras normalizar y general cuenta como una`() { // R-3.22
        val sameAfterNormalizing = (1..9).flatMap { listOf(item("category" to text("Cat $it")), item("category" to text("cat-$it"))) }
        val withGeneral = sameAfterNormalizing + item()
        assertThat(successOf(parseItems(*withGeneral.toTypedArray())).draft.items).hasSize(19)

        val tooMany = withGeneral + item("category" to text("otra"))
        assertThat(issuesOf(parseItems(*tooMany.toTypedArray())).single().kind).isEqualTo(IssueKind.TOO_MANY_CATEGORIES)
    }

    // ---- Item shape, accumulation and ordering (R-3.13, R-3.16, R-3.17) ----

    @Test
    fun `un item que no es objeto es un tipo incorrecto con su numero`() { // R-3.13, R-3.17
        val issue = issuesOf(parseItems(item(), "\"suelto\"")).single()

        assertThat(issue.itemIndex).isEqualTo(2)
        assertThat(issue.kind).isEqualTo(IssueKind.WRONG_TYPE)
        assertThat(issue.expected).isEqualTo(ValueType.OBJECT)
        assertThat(issue.field).isNull()
    }

    @Test
    fun `acumula todos los errores de todos los items en orden del JSON`() { // R-3.16, R-3.17
        val issues = issuesOf(
            parseItems(
                item("time" to text("25:00"), "dayOffset" to number(31)),
                item(),
                """{"time": 645, "title": "", "durationMin": 0, "category": 1}""",
            ),
        )

        assertThat(issues.map { it.itemIndex to it.kind }).containsExactly(
            1 to IssueKind.INVALID_TIME,
            1 to IssueKind.DAY_OFFSET_OUT_OF_RANGE,
            3 to IssueKind.WRONG_TYPE,
            3 to IssueKind.BLANK_FIELD,
            3 to IssueKind.DURATION_OUT_OF_RANGE,
            3 to IssueKind.WRONG_TYPE,
        ).inOrder()
        assertThat(issues.map { it.field })
            .containsExactly("time", "dayOffset", "time", "title", "durationMin", "category").inOrder()
    }

    @Test
    fun `con errores no se devuelven los avisos`() { // R-3.16
        val issues = issuesOf(parseItems(item("title" to text("a".repeat(81))), item("time" to text("99:99"))))

        assertThat(issues.map { it.severity }).containsExactly(Severity.ERROR)
    }

    @Test
    fun `los items se ordenan por dayOffset hora y posicion`() { // design §4.4
        val items = successOf(
            parseItems(
                item("time" to text("02:00"), "dayOffset" to number(1), "title" to text("A")),
                item("time" to text("22:00"), "title" to text("B")),
                item("time" to text("06:45"), "title" to text("C")),
                item("time" to text("06:45"), "title" to text("D")),
            ),
        ).draft.items

        assertThat(items.map { it.title }).containsExactly("C", "D", "B", "A").inOrder()
        assertThat(items.map { it.key }).containsExactly(2, 3, 1, 0).inOrder()
    }

    @Test
    fun `el ejemplo v1 normaliza el JSON de prueba completo`() {
        val categories = successOf(parser.parse(Fixtures.json("valid_v1.json"), "d")).draft.items.map { it.category }

        assertThat(categories.toSet()).containsExactly("paseo", "comida", "siesta")
    }

    @Test
    fun `parsear el mismo JSON dos veces da el mismo resultado`() {
        val json = Json.parseToJsonElement(Fixtures.text("valid_v1.json")).jsonObject

        assertThat(parser.parse(json, "d")).isEqualTo(parser.parse(json, "d"))
    }
}
