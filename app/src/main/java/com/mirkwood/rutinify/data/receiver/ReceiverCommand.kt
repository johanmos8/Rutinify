package com.mirkwood.rutinify.data.receiver

import android.content.Intent
import com.mirkwood.rutinify.data.alarm.PendingIntentFactory

/** What an alarm or notification-action broadcast asks for (design §6.1). */
sealed interface ReceiverCommand {
    val alarmId: Long

    data class Fire(override val alarmId: Long) : ReceiverCommand
    data class Done(override val alarmId: Long) : ReceiverCommand
    data class Snooze(override val alarmId: Long) : ReceiverCommand
    data class Silence(override val alarmId: Long) : ReceiverCommand

    companion object {
        /** Action picks the command, the data URI carries the id; null for anything unexpected. */
        fun from(intent: Intent): ReceiverCommand? {
            val alarmId = PendingIntentFactory.alarmIdOf(intent) ?: return null
            return when (intent.action) {
                PendingIntentFactory.ACTION_FIRE -> Fire(alarmId)
                PendingIntentFactory.ACTION_DONE -> Done(alarmId)
                PendingIntentFactory.ACTION_SNOOZE -> Snooze(alarmId)
                PendingIntentFactory.ACTION_SILENCE -> Silence(alarmId)
                else -> null
            }
        }
    }
}
