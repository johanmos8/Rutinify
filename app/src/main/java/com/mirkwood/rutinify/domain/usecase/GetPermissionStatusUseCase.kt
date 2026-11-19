package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.scheduler.PermissionChecker
import com.mirkwood.rutinify.domain.scheduler.PermissionStatus
import javax.inject.Inject

/** Snapshot of the permissions for the Home banner and the Permissions screen (R-11.3–R-11.5, R-15.11). */
class GetPermissionStatusUseCase @Inject constructor(private val checker: PermissionChecker) {

    operator fun invoke(): PermissionStatus =
        PermissionStatus(
            notifications = checker.notificationsGranted(),
            exactAlarms = checker.exactAlarmsGranted(),
            fullScreenIntent = checker.fullScreenIntentGranted(),
            autoStart = checker.autoStartStatus(),
        )
}
