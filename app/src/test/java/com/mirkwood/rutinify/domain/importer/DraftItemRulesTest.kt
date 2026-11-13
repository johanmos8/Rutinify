package com.mirkwood.rutinify.domain.importer

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.importer.DraftItemRules.TitleCheck
import org.junit.Test
import java.time.LocalTime

/** T-22: field rules shared by import and Review editing (R-3.8, R-3.9, R-3.10, R-3.14, R-3.21, R-4.2). */
class DraftItemRulesTest {

    @Test
    fun `acepta horas H mm y HH mm de 24 h`() { // R-3.8, D-13
        assertThat(DraftItemRules.parseTime("6:45")).isEqualTo(LocalTime.of(6, 45))
        assertThat(DraftItemRules.parseTime(" 23:59 ")).isEqualTo(LocalTime.of(23, 59))
        assertThat(DraftItemRules.parseTime("00:00")).isEqualTo(LocalTime.MIDNIGHT)
    }

    @Test
    fun `rechaza horas que no existen o con otro formato`() { // R-3.8
        listOf("24:00", "25:00", "12:60", "6.45", "6:5", "", "06:45:00", "seis").forEach {
            assertThat(DraftItemRules.parseTime(it)).isNull()
        }
    }

    @Test
    fun `un titulo vacio o en blanco no es valido`() { // R-3.9
        assertThat(DraftItemRules.checkTitle("")).isEqualTo(TitleCheck.Blank)
        assertThat(DraftItemRules.checkTitle("   ")).isEqualTo(TitleCheck.Blank)
    }

    @Test
    fun `un titulo largo se recorta a 80 y lo indica`() { // R-3.10
        val result = DraftItemRules.checkTitle("a".repeat(81))

        assertThat(result).isEqualTo(TitleCheck.Valid("a".repeat(80), truncated = true))
    }

    @Test
    fun `un titulo normal se queda igual sin espacios a los lados`() { // R-3.9
        assertThat(DraftItemRules.checkTitle("  Siesta ")).isEqualTo(TitleCheck.Valid("Siesta", truncated = false))
        assertThat(DraftItemRules.checkTitle("a".repeat(80))).isEqualTo(TitleCheck.Valid("a".repeat(80), truncated = false))
    }

    @Test
    fun `normaliza la categoria y usa general si esta vacia`() { // R-3.14
        assertThat(DraftItemRules.normalizeCategory("  Paseo Largo ")).isEqualTo("paseo-largo")
        assertThat(DraftItemRules.normalizeCategory("")).isEqualTo("general")
        assertThat(DraftItemRules.normalizeCategory(null)).isEqualTo("general")
    }

    @Test
    fun `una categoria de mas de 30 caracteres no es valida`() { // R-3.21
        assertThat(DraftItemRules.normalizeCategory("a".repeat(30))).isEqualTo("a".repeat(30))
        assertThat(DraftItemRules.normalizeCategory("a".repeat(31))).isNull()
    }
}
