package com.mirkwood.rutinify.ui.settings

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.usecase.DiagnosticsReport
import org.junit.Test

/** T-52: the exported text (R-17.5). */
class DiagnosticsTextTest {

    @Test
    fun `una linea por contador ordenada tras la cabecera`() { // R-17.5
        val report = DiagnosticsReport("1.3", 13L, mapOf("step.validate" to 3, "issue.INVALID_TIME" to 1, "plans.created" to 2))

        assertThat(DiagnosticsText.build("Diagnóstico de Rutinify 1.3 (13)", "Sin datos todavía", report)).isEqualTo(
            """
            Diagnóstico de Rutinify 1.3 (13)
            issue.INVALID_TIME: 1
            plans.created: 2
            step.validate: 3
            """.trimIndent(),
        )
    }

    @Test
    fun `sin contadores lo dice`() { // R-17.5
        assertThat(DiagnosticsText.build("Cabecera", "Sin datos todavía", DiagnosticsReport("1.3", 13L, emptyMap())))
            .isEqualTo("Cabecera\nSin datos todavía")
    }

    @Test
    fun `el texto solo lleva claves estables, nunca nombres ni contenido de rutinas`() { // R-17.2, R-17.5
        val report = DiagnosticsReport("1.3", 13L, mapOf("plans.created" to 1, "step.review" to 1))
        val text = DiagnosticsText.build("Cabecera", "Vacío", report)

        text.lines().drop(1).forEach { assertThat(it).matches("""(issue|step|plans)\.[A-Za-z_]+: \d+""") }
    }
}
