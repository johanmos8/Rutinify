package com.mirkwood.rutinify.domain.scheduler

/** Remembers, across launches, the permission prompts already shown (R-11.1) or dismissed (R-11.2, D-89). */
interface PermissionPromptStore {

    fun notificationsExplained(): Boolean

    fun markNotificationsExplained()

    /** "Ahora no" on the exact-alarm card of Home (D-89). */
    fun exactAlarmsCardDismissed(): Boolean

    fun markExactAlarmsCardDismissed()
}
