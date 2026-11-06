package com.mirkwood.rutinify.data.importer

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.importer.ImportResult
import org.junit.Test
import java.io.File

/** T-11: the prompt template in `res/raw` stays in sync with the v1 contract (R-1.2, R-1.3, design §4.1). */
class PromptTemplateTest {

    // Gradle runs unit tests with the module directory as working directory
    private val template = File("src/main/res/raw/prompt_template.txt").readText(Charsets.UTF_8)

    @Test
    fun `la plantilla termina con el marcador para pegar la rutina`() { // R-1.2
        assertThat(template.trimEnd()).endsWith("[PEGA AQUÍ TU RUTINA]")
    }

    @Test
    fun `el ejemplo embebido en la plantilla parsea sin errores`() { // design §4.1
        val extracted = JsonExtractor().extract(template)

        assertThat(extracted).isNotNull()
        val result = RoutineJsonParser().parse(requireNotNull(extracted).json, draftId = "d")
        assertThat(result).isInstanceOf(ImportResult.Success::class.java)
        assertThat((result as ImportResult.Success).warnings).isEmpty()
    }

    @Test
    fun `el ejemplo embebido es el mismo que el fixture valid_v1`() { // design §4.1
        val extracted = requireNotNull(JsonExtractor().extract(template))

        assertThat(extracted.json).isEqualTo(Fixtures.json("valid_v1.json"))
    }
}
