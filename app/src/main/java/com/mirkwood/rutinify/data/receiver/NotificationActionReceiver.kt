package com.mirkwood.rutinify.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mirkwood.rutinify.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/** "Hecho" (R-7.4) and "+5 min" (R-7.5) notification actions (design §7). */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var handler: ReceiverCommandHandler

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val command = ReceiverCommand.from(intent)
        if (command !is ReceiverCommand.Done && command !is ReceiverCommand.Snooze) return
        val pendingResult = goAsync()
        scope.launchReceiverWork(finish = pendingResult::finish) { handler.handle(command) }
    }
}
