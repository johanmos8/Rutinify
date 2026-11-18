package com.mirkwood.rutinify.data.importer

import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.TemplateScenario
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** T-42: each scenario reads its own `res/raw` file (R-16.1, R-16.3). */
@RunWith(RobolectricTestRunner::class)
class RawPromptTemplateSourceTest {

    @Test
    fun `cada escenario carga su fichero`() = runTest {
        val source = RawPromptTemplateSource(ApplicationProvider.getApplicationContext(), StandardTestDispatcher(testScheduler))
        val files = mapOf(
            TemplateScenario.GENERIC to "generic",
            TemplateScenario.PUPPY to "puppy",
            TemplateScenario.MEDICATION to "medication",
            TemplateScenario.BABY to "baby",
            TemplateScenario.STUDY to "study",
        )

        files.forEach { (scenario, name) ->
            assertThat(source.load(scenario))
                .isEqualTo(File("src/main/res/raw/prompt_template_$name.txt").readText(Charsets.UTF_8))
        }
        assertThat(files.keys).containsExactlyElementsIn(TemplateScenario.entries)
    }

    @Test
    @Config(qualifiers = "en")
    fun `con el telefono en ingles carga la plantilla inglesa`() = runTest { // R-13.3, D-71
        val source = RawPromptTemplateSource(ApplicationProvider.getApplicationContext(), StandardTestDispatcher(testScheduler))

        assertThat(source.load(TemplateScenario.MEDICATION))
            .isEqualTo(File("src/main/res/raw-en/prompt_template_medication.txt").readText(Charsets.UTF_8))
    }
}
