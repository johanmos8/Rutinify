package com.mirkwood.rutinify.testutil

import com.mirkwood.rutinify.domain.scheduler.PermissionChecker

/** Permissions a test can flip, as if the user changed them in system settings. */
class FakePermissionChecker(
    var notifications: Boolean = true,
    var exactAlarms: Boolean = true,
) : PermissionChecker {

    override fun notificationsGranted(): Boolean = notifications

    override fun exactAlarmsGranted(): Boolean = exactAlarms
}
