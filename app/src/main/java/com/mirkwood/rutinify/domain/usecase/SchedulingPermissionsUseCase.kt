package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.scheduler.PermissionChecker
import com.mirkwood.rutinify.domain.scheduler.PermissionPromptStore
import javax.inject.Inject

/**
 * Decides the permission steps Review runs before "Programar alarmas" (design §9, D-52): first the
 * notification explanation (only once), then the exact-alarm explanation (every time it is missing).
 */
class SchedulingPermissionsUseCase @Inject constructor(
    private val checker: PermissionChecker,
    private val prompts: PermissionPromptStore,
) {

    /** R-11.1: "por primera vez" and only while Android can still ask for it. */
    fun shouldExplainNotifications(): Boolean = checker.notificationsRequestable() && !prompts.notificationsExplained()

    fun markNotificationsExplained() = prompts.markNotificationsExplained()

    /** R-11.2 */
    fun exactAlarmsGranted(): Boolean = checker.exactAlarmsGranted()
}
