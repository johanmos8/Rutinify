package com.mirkwood.rutinify.data.importer

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class JsonExtractorTest {

    private val extractor = JsonExtractor()

    private fun extractedName(text: String): String? =
        extractor.extract(text)?.json?.get("name")?.jsonPrimitive?.content

    // R-3.1
    @Test
    fun `extrae un JSON limpio`() {
        val result = extractor.extract("""{"schemaVersion": 1, "name": "Cachorro"}""")

        assertThat(result?.json?.get("schemaVersion")?.jsonPrimitive?.int).isEqualTo(1)
        assertThat(result?.text).isEqualTo("""{"schemaVersion": 1, "name": "Cachorro"}""")
    }

    @Test
    fun `extrae de un bloque markdown con json`() { // R-3.1
        val text = """
            Aquí tienes tu rutina:
            ```json
            {"name": "Cachorro"}
            ```
            ¡Que la disfrutes!
        """.trimIndent()

        assertThat(extractedName(text)).isEqualTo("Cachorro")
    }

    @Test
    fun `extrae de un bloque markdown sin lenguaje`() { // R-3.1
        val text = "Rutina:\n```\n{\"name\": \"Sin lenguaje\"}\n```"

        assertThat(extractedName(text)).isEqualTo("Sin lenguaje")
    }

    @Test
    fun `el bloque markdown tiene prioridad sobre un objeto anterior en el texto`() {
        val text = "Ejemplo de formato {\"name\": \"fuera\"}\n```json\n{\"name\": \"dentro\"}\n```"

        assertThat(extractedName(text)).isEqualTo("dentro")
    }

    @Test
    fun `si el bloque markdown no tiene JSON valido busca en el resto del texto`() {
        val text = "```\nno es json\n```\nPero aquí sí: {\"name\": \"después\"}"

        assertThat(extractedName(text)).isEqualTo("después")
    }

    @Test
    fun `extrae con texto antes y despues sin bloque markdown`() { // R-3.1
        val text = "Claro, aquí va: {\"name\": \"Cachorro\"} Avísame si quieres cambios."

        val result = extractor.extract(text)

        assertThat(result?.json?.get("name")?.jsonPrimitive?.content).isEqualTo("Cachorro")
        assertThat(result?.text).isEqualTo("""{"name": "Cachorro"}""")
    }

    @Test
    fun `respeta llaves dentro de strings`() {
        val text = """Texto {"name": "con } y { dentro", "notes": "{{}}"} fin"""

        assertThat(extractedName(text)).isEqualTo("con } y { dentro")
    }

    @Test
    fun `respeta comillas escapadas dentro de strings`() {
        val text = """{"name": "dijo \"hola\" } y se fue", "x": 1}"""

        assertThat(extractedName(text)).isEqualTo("""dijo "hola" } y se fue""")
    }

    @Test
    fun `respeta barras invertidas escapadas al final de un string`() {
        val text = """{"name": "ruta C:\\", "x": {"y": 1}} resto"""

        assertThat(extractedName(text)).isEqualTo("""ruta C:\""")
    }

    @Test
    fun `con dos objetos validos gana el primero`() { // R-3.1
        val text = """{"name": "primero"} y luego {"name": "segundo"}"""

        assertThat(extractedName(text)).isEqualTo("primero")
    }

    @Test
    fun `si el primer candidato esta roto gana el segundo valido`() { // R-3.1
        val text = """{"name": "roto",} y luego {"name": "valido"}"""

        assertThat(extractedName(text)).isEqualTo("valido")
    }

    @Test
    fun `un candidato roto sin cerrar no impide encontrar el siguiente`() {
        val text = """{"name": "sin cerrar"  ... {"name": "valido"}"""

        assertThat(extractedName(text)).isEqualTo("valido")
    }

    @Test
    fun `no devuelve un objeto interior de un candidato cerrado pero invalido`() {
        val text = """{"schemaVersion": 1, "items": [{"time": "06:45"}],}"""

        assertThat(extractor.extract(text)).isNull()
    }

    @Test
    fun `sin JSON devuelve null`() { // R-3.3
        assertThat(extractor.extract("Lo siento, no puedo ayudar con eso.")).isNull()
        assertThat(extractor.extract("")).isNull()
        assertThat(extractor.extract("   \n  ")).isNull()
    }

    @Test
    fun `un array suelto no cuenta como objeto`() { // R-3.3
        assertThat(extractor.extract("""[1, 2, 3]""")).isNull()
    }

    @Test
    fun `ignora el BOM al inicio`() {
        val text = "\uFEFF{\"name\": \"con BOM\"}"

        val result = extractor.extract(text)

        assertThat(result?.json?.get("name")?.jsonPrimitive?.content).isEqualTo("con BOM")
        assertThat(result?.text).doesNotContain("\uFEFF")
    }

    @Test
    fun `comillas tipograficas como delimitadores no son JSON valido`() { // design §4.2
        assertThat(extractor.extract("{“name”: “Cachorro”}")).isNull()
    }

    @Test
    fun `conserva objetos anidados completos`() {
        val text = """```json
            {"routine": {"name": "Cachorro"}, "items": [{"time": "06:45"}]}
            ```""".trimIndent()

        val routine = extractor.extract(text)?.json?.get("routine")?.jsonObject

        assertThat(routine?.get("name")?.jsonPrimitive?.content).isEqualTo("Cachorro")
    }
}
