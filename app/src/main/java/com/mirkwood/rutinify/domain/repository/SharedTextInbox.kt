package com.mirkwood.rutinify.domain.repository

/**
 * Text shared to the app (R-14), handed from `MainActivity` to New routine. The route only says that a share
 * arrived, so a long text never travels in the navigation arguments (D-64).
 */
interface SharedTextInbox {

    /** Replaces any text not taken yet; null when the share carried no text (R-14.4). */
    fun put(text: String?)

    /** Returns the pending text once, then empties the inbox. */
    fun take(): String?
}
