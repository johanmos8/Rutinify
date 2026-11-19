package com.mirkwood.rutinify.data.importer

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.importer.ImportResult
import org.junit.Test
import java.io.File

/**
 * T-11, T-42: the five prompt templates in `res/raw` stay in sync with the v1 contract
 * (R-1.2, R-1.3, R-16.1, R-16.4, R-16.5, design §4.1).
 */
class PromptTemplateTest {

    private val scenarios = listOf("generic", "puppy", "medication", "baby", "study")

    // Gradle runs unit tests with the module directory as working directory
    private fun template(scenario: String): String =
        File("src/main/res/raw/prompt_template_$scenario.txt").readText(Charsets.UTF_8)

    @Test
    fun `hay una plantilla por escenario`() { // R-16.1
        val files = File("src/main/res/raw").list().orEmpty().filter { it.startsWith("prompt_template") }

        assertThat(files).containsExactlyElementsIn(scenarios.map { "prompt_template_$it.txt" })
    }

    @Test
    fun `todas terminan con el marcador para pegar la rutina`() { // R-1.2
        scenarios.forEach { assertThat(template(it).trimEnd()).endsWith("[PEGA AQUÍ TU RUTINA]") }
    }

    @Test
    fun `todas piden el mismo contrato`() { // R-16.4
        val rules = template("generic").substringAfter("Instrucciones:").substringBefore("\n\n")

        scenarios.forEach { assertThat(template(it)).contains(rules) }
        assertThat(rules).contains("schemaVersion: el número 1, o 2 si usas routine.recurrence")
    }

    @Test
    fun `el ejemplo de cada plantilla parsea sin errores ni avisos`() { // R-16.4, design §4.1
        scenarios.forEach { scenario ->
            val extracted = JsonExtractor().extract(template(scenario))

            assertThat(extracted).isNotNull()
            val result = RoutineJsonParser().parse(requireNotNull(extracted).json, draftId = "d")
            assertThat(result).isInstanceOf(ImportResult.Success::class.java)
            assertThat((result as ImportResult.Success).warnings).isEmpty()
        }
    }

    @Test
    fun `cada plantilla tiene su propio ejemplo`() { // R-16.4
        val examples = scenarios.map { requireNotNull(JsonExtractor().extract(template(it))).json }

        assertThat(examples.toSet()).hasSize(scenarios.size)
    }

    @Test
    fun `el ejemplo del cachorro es el mismo que el fixture valid_v1`() { // design §4.1, D-70
        val extracted = requireNotNull(JsonExtractor().extract(template("puppy")))

        assertThat(extracted.json).isEqualTo(Fixtures.json("valid_v1.json"))
    }

    @Test
    fun `la de medicacion pide no proponer dosis ni cambiar la pauta`() { // R-16.5
        assertThat(template("medication"))
            .contains("Usa solo los horarios y las dosis que escribo. No propongas dosis, no cambies la pauta")
    }

    @Test
    fun `todas explican repeatDays y los dias distintos con dayOffset`() { // R-1.2, D-55
        scenarios.forEach {
            assertThat(template(it)).contains("repeatDays")
            assertThat(template(it)).contains("dayOffset: 0, 1, 2")
        }
    }

    // ---- English (T-49, R-13.3) ----

    private fun english(scenario: String): String =
        File("src/main/res/raw-en/prompt_template_$scenario.txt").readText(Charsets.UTF_8)

    @Test
    fun `hay las mismas plantillas en ingles`() { // R-13.3, D-71
        val files = File("src/main/res/raw-en").list().orEmpty().filter { it.startsWith("prompt_template") }

        assertThat(files).containsExactlyElementsIn(scenarios.map { "prompt_template_$it.txt" })
    }

    @Test
    fun `las plantillas inglesas piden el mismo contrato y su ejemplo parsea`() { // R-13.3, R-16.4
        val rules = english("generic").substringAfter("Instructions:").substringBefore("\n\n")
        assertThat(rules).contains("schemaVersion: the number 1, or 2 if you use routine.recurrence")

        scenarios.forEach { scenario ->
            val text = english(scenario)
            assertThat(text).contains(rules)
            assertThat(text.trimEnd()).endsWith("[PASTE YOUR ROUTINE HERE]")
            assertThat(text).contains("dayOffset: 0, 1, 2")
            val result = RoutineJsonParser().parse(requireNotNull(JsonExtractor().extract(text)).json, draftId = "d")
            assertThat(result).isInstanceOf(ImportResult.Success::class.java)
            assertThat((result as ImportResult.Success).warnings).isEmpty()
        }
    }

    @Test
    fun `la de medicacion en ingles tambien pide no proponer dosis`() { // R-16.5
        assertThat(english("medication")).contains("Use only the times and doses I write. Do not suggest doses, do not change the schedule")
    }

    @Test
    fun `todas explican la recurrencia del schema 2`() { // R-23.1, R-23.2
        scenarios.forEach {
            assertThat(template(it)).contains("routine.recurrence con weekdays")
            assertThat(english(it)).contains("routine.recurrence with weekdays")
        }
    }
}
