package com.mirkwood.rutinify.data.system

import android.content.Context
import androidx.core.content.edit
import com.mirkwood.rutinify.domain.scheduler.PermissionPromptStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** One boolean in a private `SharedPreferences` file; no extra dependency needed (D-52). */
class SharedPrefsPermissionPromptStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PermissionPromptStore {

    private val prefs by lazy { context.getSharedPreferences(FILE, Context.MODE_PRIVATE) }

    override fun notificationsExplained(): Boolean = prefs.getBoolean(KEY_NOTIFICATIONS_EXPLAINED, false)

    override fun markNotificationsExplained() {
        prefs.edit { putBoolean(KEY_NOTIFICATIONS_EXPLAINED, true) }
    }

    private companion object {
        const val FILE = "permission_prompts"
        const val KEY_NOTIFICATIONS_EXPLAINED = "notifications_explained"
    }
}
