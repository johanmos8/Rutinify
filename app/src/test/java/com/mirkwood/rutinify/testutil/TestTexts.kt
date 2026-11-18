package com.mirkwood.rutinify.testutil

import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.ui.common.UiText
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Resolves [UiText] against the app's `strings.xml` on the JVM, without Android, so ViewModel tests can compare the
 * text a user would read in Spanish ([es]) or English ([en]). Only what the app uses: `%n$s`/`%n$d` arguments,
 * quoted values and `one`/`other` plurals.
 */
object TestTexts {

    private class Language(folder: String, val locale: Locale) {
        val strings = mutableMapOf<String, String>()
        val plurals = mutableMapOf<String, Map<String, String>>()

        init {
            // Gradle runs unit tests with the module directory as working directory
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$folder/strings.xml"))
            val stringNodes = document.getElementsByTagName("string")
            for (i in 0 until stringNodes.length) {
                val node = stringNodes.item(i)
                strings[node.attributes.getNamedItem("name").nodeValue] = unescape(node.textContent)
            }
            val pluralNodes = document.getElementsByTagName("plurals")
            for (i in 0 until pluralNodes.length) {
                val node = pluralNodes.item(i)
                val items = mutableMapOf<String, String>()
                val children = node.childNodes
                for (j in 0 until children.length) {
                    val child = children.item(j)
                    if (child.nodeName == "item") items[child.attributes.getNamedItem("quantity").nodeValue] = unescape(child.textContent)
                }
                plurals[node.attributes.getNamedItem("name").nodeValue] = items
            }
        }
    }

    private val spanish by lazy { Language("values", Locale.forLanguageTag("es-ES")) }
    private val english by lazy { Language("values-en", Locale.ENGLISH) }

    private val stringNames: Map<Int, String> by lazy { namesOf(R.string::class.java) }
    private val pluralNames: Map<Int, String> by lazy { namesOf(R.plurals::class.java) }

    fun es(text: UiText): String = resolve(text, spanish)

    fun en(text: UiText): String = resolve(text, english)

    private fun resolve(text: UiText, language: Language): String = when (text) {
        is UiText.Plain -> text.value
        is UiText.Res -> format(language.strings.getValue(stringNames.getValue(text.id)), text.args, language)
        is UiText.Plural -> {
            val forms = language.plurals.getValue(pluralNames.getValue(text.pluralId()))
            format(forms[if (text.quantity == 1) "one" else "other"] ?: forms.getValue("other"), text.args, language)
        }
        is UiText.Date -> text.date.format(
            DateTimeFormatter.ofPattern(language.strings.getValue(stringNames.getValue(text.patternId)), language.locale),
        )
    }

    private fun UiText.Plural.pluralId() = id

    private fun format(template: String, args: List<Any>, language: Language): String {
        val resolved = args.map { if (it is UiText) resolve(it, language) else it }.toTypedArray()
        return String.format(language.locale, template, *resolved)
    }

    /** Android resource escapes: surrounding quotes keep apostrophes; `\'`, `\"` and `\n` are literal. */
    private fun unescape(raw: String): String {
        val text = raw.trim().let { if (it.length >= 2 && it.startsWith('"') && it.endsWith('"')) it.substring(1, it.length - 1) else it }
        return text.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n")
    }

    private fun namesOf(type: Class<*>): Map<Int, String> =
        type.fields.associate { it.getInt(null) to it.name }
}

/** The Spanish text the user reads. */
fun UiText.es(): String = TestTexts.es(this)

/** The English text the user reads. */
fun UiText.en(): String = TestTexts.en(this)
