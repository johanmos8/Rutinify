package com.mirkwood.rutinify.ui.common

import java.time.format.DateTimeFormatter
import java.util.Locale

/** Presentation formats shared by screens; UI texts are Spanish (CLAUDE.md §4). */
internal object DateFormats {
    private val SPANISH: Locale = Locale.forLanguageTag("es-ES")

    /** "jueves 5 de noviembre de 2026". */
    val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", SPANISH)

    /** "06:45". */
    val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
}
