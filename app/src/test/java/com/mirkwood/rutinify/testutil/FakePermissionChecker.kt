package com.mirkwood.rutinify.testutil

import com.mirkwood.rutinify.domain.scheduler.AutoStartStatus
import com.mirkwood.rutinify.domain.scheduler.PermissionChecker

/** Permissions a test can flip, as if the user changed them in system settings. */
class FakePermissionChecker(
    var notifications: Boolean = true,
    var exactAlarms: Boolean = true,
    var notificationsRequestable: Boolean = false,
    var fullScreenIntent: Boolean = true,
    var autoStart: AutoStartStatus = AutoStartStatus.NOT_APPLICABLE,
) : PermissionChecker {

    override fun notificationsGranted(): Boolean = notifications

    override fun exactAlarmsGranted(): Boolean = exactAlarms

    override fun notificationsRequestable(): Boolean = notificationsRequestable

    override fun fullScreenIntentGranted(): Boolean = fullScreenIntent

    override fun autoStartStatus(): AutoStartStatus = autoStart
}
