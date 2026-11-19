package com.mirkwood.rutinify.data.receiver

import com.mirkwood.rutinify.data.alarm.PendingIntentFactory
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mirkwood.rutinify.di.ApplicationScope
import com.mirkwood.rutinify.domain.usecase.RescheduleAllUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/**
 * System events after which `AlarmManager` may be out of date (design §7.1, §7.2). Exported because
 * these are system broadcasts; any other action is ignored.
 */
@AndroidEntryPoint
class RescheduleReceiver : BroadcastReceiver() {

    @Inject lateinit var rescheduleAll: RescheduleAllUseCase

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (!isRescheduleAction(intent.action)) return
        val pendingResult = goAsync()
        scope.launchReceiverWork(finish = pendingResult::finish) { rescheduleAll() }
    }

    companion object {
        // The API 31 action is only a string constant: on API 28-30 that broadcast simply never arrives
        @SuppressLint("InlinedApi")
        private val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED, // R-12.1
            Intent.ACTION_TIME_CHANGED, // R-12.2 ("android.intent.action.TIME_SET")
            Intent.ACTION_TIMEZONE_CHANGED, // R-12.2
            Intent.ACTION_MY_PACKAGE_REPLACED, // R-12.3
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED, // R-11.6
            PendingIntentFactory.ACTION_MATERIALIZE, // R-23.7: the app's own daily refresh
        )

        fun isRescheduleAction(action: String?): Boolean = action in ACTIONS
    }
}
