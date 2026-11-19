package com.mirkwood.rutinify.ui.common

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Text to show, described by resource id and arguments so it can be built outside composables (VMs,
 * mappers) and tested. Arguments may themselves be [UiText]; they are resolved first.
 */
sealed interface UiText {

    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /** Text already formatted for display (a date, a name), shown as is. */
    data class Plain(val value: String) : UiText

    data class Plural(@param:PluralsRes val id: Int, val quantity: Int, val args: List<Any> = emptyList()) : UiText

    /** [date] formatted with the pattern in [patternId] and the locale of the current configuration (D-71). */
    data class Date(val date: LocalDate, @param:StringRes val patternId: Int) : UiText

    fun resolve(resources: Resources): String = when (this) {
        is Res -> resources.getString(id, *resolveArgs(args, resources))
        is Plain -> value
        is Plural -> resources.getQuantityString(id, quantity, *resolveArgs(args, resources))
        is Date -> date.format(DateFormatterCache.get(resources.getString(patternId), resources.configuration.locales[0]))
    }

    private fun resolveArgs(args: List<Any>, resources: Resources): Array<Any> =
        args.map { if (it is UiText) it.resolve(resources) else it }.toTypedArray()
}

/**
 * Formatters by pattern and locale. [UiText.Date] is resolved while composing list items, and building a
 * `DateTimeFormatter` parses its pattern every time; the app uses only a handful of patterns.
 */
private object DateFormatterCache {
    private val formatters = ConcurrentHashMap<String, DateTimeFormatter>()

    fun get(pattern: String, locale: Locale): DateTimeFormatter =
        formatters.getOrPut(locale.toLanguageTag() + '\u0000' + pattern) { DateTimeFormatter.ofPattern(pattern, locale) }
}

@Composable
@ReadOnlyComposable
fun UiText.asString(): String = resolve(LocalResources.current)
