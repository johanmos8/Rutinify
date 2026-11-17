package com.mirkwood.rutinify.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mirkwood.rutinify.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/** "Hecho" (R-7.4), "+5 min" (R-7.5) and "Silenciar" (R-15.9) actions, plus the 10-minute cutoff (R-15.10). */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var handler: ReceiverCommandHandler

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val command = ReceiverCommand.from(intent)
        if (command == null || command is ReceiverCommand.Fire) return
        val pendingResult = goAsync()
        scope.launchReceiverWork(finish = pendingResult::finish) { handler.handle(command) }
    }
}
