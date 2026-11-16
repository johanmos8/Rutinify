package com.mirkwood.rutinify.data.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.mirkwood.rutinify.domain.system.TextSharer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Asks the package manager for `SEND text/plain` targets; visible thanks to the manifest `<queries>` (D-65). */
class AndroidTextSharer @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : TextSharer {

    override fun canShareText(): Boolean {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        // This app also receives text (R-14.1): it does not count as a place to send the template
        return context.packageManager.queryIntentActivities(send, PackageManager.MATCH_DEFAULT_ONLY)
            .any { it.activityInfo.packageName != context.packageName }
    }
}
