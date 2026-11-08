package com.mirkwood.rutinify.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mirkwood.rutinify.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/** An alarm went off (R-7.1): delegates to `HandleAlarmFiredUseCase` (design §7). */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var handler: ReceiverCommandHandler

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val command = ReceiverCommand.from(intent) as? ReceiverCommand.Fire ?: return
        val pendingResult = goAsync()
        scope.launchReceiverWork(finish = pendingResult::finish) { handler.handle(command) }
    }
}
