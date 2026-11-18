package com.mirkwood.rutinify.ui.common

import java.time.format.DateTimeFormatter

/** Presentation formats with no words in them; dates with words use [UiText.Date] and resource patterns (D-71). */
internal object DateFormats {

    /** "06:45": 24-hour time, the same in every language of the app. */
    val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
}
