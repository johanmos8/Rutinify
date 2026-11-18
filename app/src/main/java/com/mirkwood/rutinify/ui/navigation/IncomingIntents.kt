package com.mirkwood.rutinify.ui.navigation

import android.content.Intent

/** What an intent reaching `MainActivity` asks for. */
sealed interface IncomingIntent {

    /** Text shared from another app (R-14.1); [text] is null when the share carried none (R-14.4). */
    data class SharedText(val text: String?) : IncomingIntent

    /** A `rutinify://import` link opened from another app (R-19.7). */
    data class ImportLink(val link: String) : IncomingIntent

    /** Launcher, notification deep link or anything else: left to the navigation graph. */
    data object Other : IncomingIntent
}

object IncomingIntents {

    fun classify(intent: Intent): IncomingIntent {
        // Reopening the app from recents redelivers the original share: it was already imported
        val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        val link = intent.dataString
        return when {
            fromHistory -> IncomingIntent.Other
            intent.action == Intent.ACTION_SEND && intent.type?.startsWith(MIME_TEXT) == true ->
                // Some apps share styled text: a CharSequence, not a String
                IncomingIntent.SharedText(intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString())
            intent.action == Intent.ACTION_VIEW && link != null && link.startsWith(IMPORT_PREFIX) -> IncomingIntent.ImportLink(link)
            else -> IncomingIntent.Other
        }
    }

    private const val MIME_TEXT = "text/plain"
    private const val IMPORT_PREFIX = "rutinify://import"
}
