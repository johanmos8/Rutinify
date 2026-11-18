package com.mirkwood.rutinify.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.importer.ImportResult
import com.mirkwood.rutinify.domain.model.TemplateScenario
import com.mirkwood.rutinify.testutil.FakeOnDeviceModel
import com.mirkwood.rutinify.testutil.FakeRoutineImporter
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** T-66: "Convertir en el teléfono" (R-22.2, R-22.5, R-22.6, D-80). */
class ConvertOnDeviceUseCaseTest {

    private val model = FakeOnDeviceModel()
    private val importer = FakeRoutineImporter(ImportResult.Failure(emptyList()))
    private val convert = ConvertOnDeviceUseCase(model, { "Instrucciones de ${it.name}\n\nMi rutina:\n[PEGA AQUÍ TU RUTINA]\n" }, importer)

    @Test
    fun `usa la plantilla del escenario sin el marcador y valida la respuesta`() = runTest { // R-22.2
        model.answer = "{\"schemaVersion\":1}"

        val result = convert("Paseos a las 7", TemplateScenario.PUPPY)

        assertThat(model.prompts.single()).isEqualTo("Instrucciones de PUPPY\n\nMi rutina:\n" to "Paseos a las 7")
        assertThat(importer.received).containsExactly("{\"schemaVersion\":1}")
        assertThat(result).isEqualTo(ConvertResult.Converted("{\"schemaVersion\":1}", ImportResult.Failure(emptyList())))
    }

    @Test
    fun `un texto que no cabe no se envia`() = runTest { // R-22.6
        model.fits = false

        assertThat(convert("x".repeat(20_000), TemplateScenario.GENERIC)).isEqualTo(ConvertResult.TooLong)
        assertThat(model.prompts).isEmpty()
    }

    @Test
    fun `si el modelo no responde la conversion falla`() = runTest { // R-22.5
        model.answer = null

        assertThat(convert("Paseos", TemplateScenario.GENERIC)).isEqualTo(ConvertResult.Failed)
        assertThat(importer.received).isEmpty()
    }

    @Test
    fun `dice si el modelo esta disponible`() = runTest { // R-22.1, R-22.4
        assertThat(convert.isAvailable()).isTrue()
        model.available = false
        assertThat(convert.isAvailable()).isFalse()
    }
}
