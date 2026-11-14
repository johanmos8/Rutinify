package com.mirkwood.rutinify.data.system

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.mirkwood.rutinify.data.notification.notificationsAllowed
import com.mirkwood.rutinify.domain.scheduler.AlarmScheduler
import com.mirkwood.rutinify.domain.scheduler.PermissionChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Same checks the scheduler and the publisher use, so the UI never disagrees with them (D-51). */
class AndroidPermissionChecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
    private val scheduler: AlarmScheduler,
) : PermissionChecker {

    override fun notificationsGranted(): Boolean = notificationsAllowed(context, notificationManager)

    override fun exactAlarmsGranted(): Boolean = scheduler.canScheduleExact()

    override fun notificationsRequestable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
}
