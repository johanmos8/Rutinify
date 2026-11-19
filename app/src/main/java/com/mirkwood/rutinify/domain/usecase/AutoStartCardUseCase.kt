package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.scheduler.AutoStartStatus
import com.mirkwood.rutinify.domain.scheduler.PermissionChecker
import com.mirkwood.rutinify.domain.scheduler.PermissionPromptStore
import javax.inject.Inject

/**
 * The Home card about Xiaomi's "Inicio automático" (R-11.9, D-102): without it, alarms of normal categories never show
 * while the app is closed. Shown after the first plan, like the exact-alarm card, until it is dismissed or the
 * permission is detected as on.
 */
class AutoStartCardUseCase @Inject constructor(
    private val checker: PermissionChecker,
    private val prompts: PermissionPromptStore,
) {

    /** [AutoStartStatus.DENIED] or [AutoStartStatus.UNKNOWN] (the card's two wordings), or null for no card. */
    fun current(hasPlans: Boolean): AutoStartStatus? {
        if (!hasPlans || prompts.autoStartCardDismissed()) return null
        return checker.autoStartStatus().takeIf { it == AutoStartStatus.DENIED || it == AutoStartStatus.UNKNOWN }
    }

    /** "Ahora no" or "Ya lo hice": it does not come back; the Permissions screen keeps the row (R-11.10). */
    fun dismiss() = prompts.markAutoStartCardDismissed()
}
