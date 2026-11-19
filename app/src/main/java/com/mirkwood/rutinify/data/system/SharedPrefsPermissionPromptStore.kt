package com.mirkwood.rutinify.data.system

import android.content.Context
import androidx.core.content.edit
import com.mirkwood.rutinify.domain.scheduler.PermissionPromptStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Booleans in a private `SharedPreferences` file; no extra dependency needed (D-52, D-89). */
class SharedPrefsPermissionPromptStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PermissionPromptStore {

    private val prefs by lazy { context.getSharedPreferences(FILE, Context.MODE_PRIVATE) }

    override fun notificationsExplained(): Boolean = prefs.getBoolean(KEY_NOTIFICATIONS_EXPLAINED, false)

    override fun markNotificationsExplained() {
        prefs.edit { putBoolean(KEY_NOTIFICATIONS_EXPLAINED, true) }
    }

    override fun exactAlarmsCardDismissed(): Boolean = prefs.getBoolean(KEY_EXACT_CARD_DISMISSED, false)

    override fun markExactAlarmsCardDismissed() {
        prefs.edit { putBoolean(KEY_EXACT_CARD_DISMISSED, true) }
    }

    override fun autoStartCardDismissed(): Boolean = prefs.getBoolean(KEY_AUTO_START_CARD_DISMISSED, false)

    override fun markAutoStartCardDismissed() {
        prefs.edit { putBoolean(KEY_AUTO_START_CARD_DISMISSED, true) }
    }

    private companion object {
        const val FILE = "permission_prompts"
        const val KEY_NOTIFICATIONS_EXPLAINED = "notifications_explained"
        const val KEY_EXACT_CARD_DISMISSED = "exact_alarms_card_dismissed"
        const val KEY_AUTO_START_CARD_DISMISSED = "auto_start_card_dismissed"
    }
}
