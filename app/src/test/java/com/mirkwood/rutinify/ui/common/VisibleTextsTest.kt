package com.mirkwood.rutinify.ui.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** T-39: the UI says "Plan", never "orden" (R-13.7, D-63). XML comments are not visible and are ignored. */
class VisibleTextsTest {

    // Gradle runs unit tests with the module directory as working directory
    private val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(File("src/main/res/values/strings.xml"))

    /** Text of every `<string>` and plural `<item>`, keyed by resource name. */
    private val visibleTexts: Map<String, String> = buildMap {
        val strings = document.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val node = strings.item(i)
            put(node.attributes.getNamedItem("name").nodeValue, node.textContent)
        }
        val items = document.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val node = items.item(i)
            val plural = node.parentNode.attributes.getNamedItem("name").nodeValue
            val quantity = node.attributes.getNamedItem("quantity").nodeValue
            put("$plural/$quantity", node.textContent)
        }
    }

    @Test
    fun `ningun texto visible dice orden`() { // R-13.7
        val withOrder = visibleTexts.filterValues { Regex("""\b[oó]rden(es)?\b""", RegexOption.IGNORE_CASE).containsMatchIn(it) }

        assertThat(withOrder).isEmpty()
    }

    @Test
    fun `las acciones y titulos del detalle dicen plan`() { // R-13.7
        assertThat(visibleTexts["order_detail_title"]).isEqualTo("Detalle del plan")
        assertThat(visibleTexts["action_delete_order"]).isEqualTo("Eliminar plan")
        assertThat(visibleTexts["delete_order_title"]).isEqualTo("¿Eliminar el plan?")
    }
}
