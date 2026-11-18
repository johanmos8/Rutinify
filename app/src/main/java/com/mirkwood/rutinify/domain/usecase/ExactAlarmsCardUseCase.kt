package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.scheduler.PermissionChecker
import com.mirkwood.rutinify.domain.scheduler.PermissionPromptStore
import javax.inject.Inject

/**
 * The Home card that asks for exact alarms once the first plan exists (R-11.2, D-69 option C, D-89): Android 14+
 * denies them by default on new installs, and without them every alarm may ring minutes late.
 */
class ExactAlarmsCardUseCase @Inject constructor(
    private val checker: PermissionChecker,
    private val prompts: PermissionPromptStore,
) {

    /** [hasPlans]: at least one plan was scheduled ("tras programar el primer plan"). */
    fun shouldShow(hasPlans: Boolean): Boolean =
        hasPlans && !checker.exactAlarmsGranted() && !prompts.exactAlarmsCardDismissed()

    fun dismiss() = prompts.markExactAlarmsCardDismissed()
}
