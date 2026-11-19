package com.mirkwood.rutinify.domain.scheduler

/**
 * Current state of the permissions the app depends on (R-11.4). [fullScreenIntent] only matters for critical alarms
 * (R-15.11), so it is not part of [allGranted], which drives the Home banner (D-88).
 */
data class PermissionStatus(
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val fullScreenIntent: Boolean = true,
    /** Xiaomi's "Inicio automático" (R-11.10); not part of [allGranted], since it can rarely be read for sure. */
    val autoStart: AutoStartStatus = AutoStartStatus.NOT_APPLICABLE,
) {
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

    /** Xiaomi's "Inicio automático" for this app, read on a best-effort basis (R-11.10, D-102). */
    fun autoStartStatus(): AutoStartStatus
}

/**
 * Xiaomi (also Redmi and POCO) blocks starting an app that is closed unless its "Inicio automático" is on, so alarms
 * of normal categories never show; critical ones, set as alarm clocks, still ring (D-102).
 */
enum class AutoStartStatus {
    /** Not a Xiaomi phone: nothing to check. */
    NOT_APPLICABLE,
    ALLOWED,
    DENIED,

    /** A Xiaomi phone where the setting could not be read: the user is asked to check it. */
    UNKNOWN,
}
