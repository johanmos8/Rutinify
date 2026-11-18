package com.mirkwood.rutinify.domain.importer

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Base64

/** T-59: plan links and their QR limit (R-19.4, R-19.5, R-19.8, D-75). */
class PlanLinkTest {

    private val json = """{"schemaVersion":1,"routine":{"name":"Cachorro","date":"2026-11-14","notes":null},"items":[""" +
        """{"time":"06:45","dayOffset":0,"title":"Pis en el jardín","description":null,"durationMin":10,"category":"paseo","assignee":"Ana"}]}"""

    @Test
    fun `ida y vuelta devuelve el mismo JSON`() { // R-19.4, R-19.7
        val link = (PlanLink.encode(json) as PlanLink.Encoded.Ok).link

        assertThat(link).startsWith("rutinify://import?v=1&d=")
        assertThat(PlanLink.decode(link)).isEqualTo(json)
    }

    @Test
    fun `un plan tipico cabe de sobra en un QR`() { // D-75
        val items = (0 until 30).joinToString(",") {
            """{"time":"${"%02d".format(it % 24)}:00","dayOffset":0,"title":"Actividad $it","description":"Detalle de la actividad $it",""" +
                """"durationMin":15,"category":"rutina","assignee":null}"""
        }
        val big = """{"schemaVersion":1,"routine":{"name":"Plan","date":"2026-11-14","notes":null},"items":[$items]}"""

        assertThat(PlanLink.encode(big)).isInstanceOf(PlanLink.Encoded.Ok::class.java)
    }

    @Test
    fun `un plan que pasa de 2331 bytes no cabe`() { // R-19.5
        val random = java.util.Random(1)
        val noise = (0 until 4000).map { 'a' + random.nextInt(26) }.joinToString("")
        val huge = """{"schemaVersion":1,"routine":{"name":"$noise","date":null,"notes":null},"items":[]}"""

        val result = PlanLink.encode(huge)

        assertThat(result).isInstanceOf(PlanLink.Encoded.TooLarge::class.java)
        assertThat((result as PlanLink.Encoded.TooLarge).bytes).isGreaterThan(PlanLink.MAX_QR_BYTES)
    }

    @Test
    fun `enlaces daniados o ajenos no dan ningun plan`() { // R-19.8
        val link = (PlanLink.encode(json) as PlanLink.Encoded.Ok).link

        assertThat(PlanLink.decode(link.dropLast(6))).isNull()
        assertThat(PlanLink.decode("rutinify://import?v=2&d=abc")).isNull()
        assertThat(PlanLink.decode("rutinify://import?v=1&d=%%%")).isNull()
        assertThat(PlanLink.decode("rutinify://import?v=1")).isNull()
        assertThat(PlanLink.decode("https://example.com/?d=abc")).isNull()
        val notDeflate = Base64.getUrlEncoder().withoutPadding().encodeToString("hola".toByteArray())
        assertThat(PlanLink.decode("rutinify://import?v=1&d=$notDeflate")).isNull()
    }

    @Test
    fun `reconoce lo que parece un enlace de Rutinify`() { // R-19.7
        assertThat(PlanLink.isLink("  rutinify://import?v=1&d=x")).isTrue()
        assertThat(PlanLink.isLink("{\"schemaVersion\": 1}")).isFalse()
    }
}
