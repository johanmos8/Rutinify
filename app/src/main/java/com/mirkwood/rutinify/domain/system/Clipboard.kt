package com.mirkwood.rutinify.domain.system

/** System clipboard (R-1.2, R-2.2). Implemented in `data/system`. */
interface Clipboard {

    /** Plain text currently in the clipboard; null when it is empty or holds no text (R-2.3). */
    fun readText(): String?

    fun copyText(text: String)
}
