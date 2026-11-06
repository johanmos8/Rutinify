package com.mirkwood.rutinify.data.importer

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.importer.ImportResult
import com.mirkwood.rutinify.domain.importer.IssueKind
import com.mirkwood.rutinify.domain.importer.ValidationIssue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID
import kotlin.coroutines.CoroutineContext

/** T-11: `RoutineJsonImporter` = size check + extractor + parser (R-3.19, R-3.20, R-3.23). */
@OptIn(ExperimentalCoroutinesApi::class)
class RoutineJsonImporterTest {

    private val testDispatcher = StandardTestDispatcher()
    private val countingDispatcher = CountingDispatcher(testDispatcher)
    private val importer = RoutineJsonImporter(JsonExtractor(), RoutineJsonParser(), countingDispatcher)

    private fun test(block: suspend TestScope.() -> Unit) = runTest(testDispatcher) { block() }

    private val validJson = Fixtures.text("valid_v1.json").trim()

    private fun successOf(result: ImportResult): ImportResult.Success {
        assertThat(result).isInstanceOf(ImportResult.Success::class.java)
        return result as ImportResult.Success
    }

    private fun issuesOf(result: ImportResult): List<ValidationIssue> {
        assertThat(result).isInstanceOf(ImportResult.Failure::class.java)
        return (result as ImportResult.Failure).issues
    }

    @Test
    fun `importa la respuesta de la IA envuelta en markdown`() = test { // R-3.1, R-3.19
        val text = "¡Claro! Aquí está:\n```json\n$validJson\n```\nAvísame si quieres cambios."

        val draft = successOf(importer.import(text)).draft

        assertThat(draft.name).isEqualTo("Rutina del cachorro")
        assertThat(draft.items).hasSize(6)
    }

    @Test
    fun `el borrador guarda el JSON exacto extraido`() = test {
        val draft = successOf(importer.import("Texto previo $validJson texto final")).draft

        assertThat(draft.rawJson).isEqualTo(validJson)
    }

    @Test
    fun `cada importacion genera un id de borrador UUID distinto`() = test {
        val first = successOf(importer.import(validJson)).draft.id
        val second = successOf(importer.import(validJson)).draft.id

        assertThat(UUID.fromString(first).toString()).isEqualTo(first)
        assertThat(second).isNotEqualTo(first)
    }

    @Test
    fun `sin JSON devuelve NO_JSON_FOUND`() = test { // R-3.3
        assertThat(issuesOf(importer.import("Lo siento, no puedo ayudarte con eso.")))
            .containsExactly(ValidationIssue(kind = IssueKind.NO_JSON_FOUND))
    }

    @Test
    fun `los errores del parser llegan tal cual`() = test { // R-3.16
        val issues = issuesOf(importer.import("""{"schemaVersion": 1, "routine": {}, "items": []}"""))

        assertThat(issues.map { it.kind }).containsExactly(IssueKind.MISSING_FIELD, IssueKind.NO_ITEMS).inOrder()
    }

    @Test
    fun `un texto de exactamente 100 KB se analiza`() = test { // R-3.20
        val text = padToBytes(validJson, MAX_BYTES)
        assertThat(text.encodeToByteArray()).hasLength(MAX_BYTES)

        successOf(importer.import(text))
    }

    @Test
    fun `un texto de 100 KB mas un byte se rechaza sin analizarlo`() = test { // R-3.20
        val text = padToBytes(validJson, MAX_BYTES + 1)

        val issue = issuesOf(importer.import(text)).single()

        assertThat(issue).isEqualTo(ValidationIssue(kind = IssueKind.INPUT_TOO_LONG, received = "101", limit = 100))
    }

    @Test
    fun `el limite se mide en bytes UTF-8 y no en caracteres`() = test { // R-3.20
        val text = "ñ".repeat(MAX_BYTES / 2 + 1) // fewer chars than the limit, but 2 bytes each

        assertThat(issuesOf(importer.import(text)).single().kind).isEqualTo(IssueKind.INPUT_TOO_LONG)
    }

    @Test
    fun `un texto enorme indica su tamano en KB redondeado hacia arriba`() = test { // R-3.20
        val issue = issuesOf(importer.import("x".repeat(5 * 1024 * 1024))).single()

        assertThat(issue.received).isEqualTo("5120")
    }

    @Test
    fun `el analisis se ejecuta en el dispatcher inyectado`() = test { // R-3.23
        importer.import(validJson)

        assertThat(countingDispatcher.dispatches).isGreaterThan(0)
    }

    @Test
    fun `200 actividades se validan en menos de 300 ms`() = test { // R-13.2 (indicative on JVM)
        val items = (0 until 200).joinToString(",") {
            """{"time": "${"%02d".format(it % 24)}:${"%02d".format(it % 60)}", "title": "Actividad $it", "description": "Detalle", "durationMin": 10, "category": "cat-${it % 10}", "assignee": "Ana"}"""
        }
        val text = """{"schemaVersion": 1, "routine": {"name": "Grande"}, "items": [$items]}"""
        importer.import(validJson) // warm-up: class loading is not part of the measure

        val start = System.nanoTime()
        val result = importer.import(text)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertThat(successOf(result).draft.items).hasSize(200)
        assertThat(elapsedMs).isLessThan(300L)
    }

    /** Pads [text] with ASCII spaces until its UTF-8 size is exactly [bytes]. */
    private fun padToBytes(text: String, bytes: Int): String = text + " ".repeat(bytes - text.encodeToByteArray().size)

    /** Delegates to [delegate] and counts dispatches, to prove the work leaves the caller's context. */
    private class CountingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        var dispatches = 0
            private set

        override fun isDispatchNeeded(context: CoroutineContext): Boolean = true

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatches++
            delegate.dispatch(context, block)
        }
    }

    private companion object {
        const val MAX_BYTES = 100 * 1024
    }
}
