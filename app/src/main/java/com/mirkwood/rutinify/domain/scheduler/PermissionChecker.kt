package com.mirkwood.rutinify.domain.scheduler

/**
 * Current state of the permissions the app depends on (R-11.4). [fullScreenIntent] only matters for critical alarms
 * (R-15.11), so it is not part of [allGranted], which drives the Home banner (D-88).
 */
data class PermissionStatus(val notifications: Boolean, val exactAlarms: Boolean, val fullScreenIntent: Boolean = true) {
    val allGranted: Boolean get() = notifications && exactAlarms
}

/** Reads permission state on demand; callers re-check on every return to the foreground (R-11.5). */
interface PermissionChecker {

    /** Runtime permission (Android 13+) granted and notifications not turned off for the app. */
    fun notificationsGranted(): Boolean

    /** `canScheduleExactAlarms()`; always true below Android 12. */
    fun exactAlarmsGranted(): Boolean

    /** Android 13+ and the runtime notification permission not granted yet, so the app may ask for it (R-11.1). */
    fun notificationsRequestable(): Boolean

    /** `canUseFullScreenIntent()` on Android 14+; granted at install below (R-15.11, D-68). */
    fun fullScreenIntentGranted(): Boolean
}
