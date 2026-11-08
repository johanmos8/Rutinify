package com.mirkwood.rutinify.data.receiver

import com.mirkwood.rutinify.domain.usecase.HandleAlarmFiredUseCase
import com.mirkwood.rutinify.domain.usecase.MarkAlarmDoneUseCase
import com.mirkwood.rutinify.domain.usecase.SnoozeAlarmUseCase
import javax.inject.Inject

/** Routes a [ReceiverCommand] to its use case, so the receivers stay thin (CLAUDE.md §3). */
class ReceiverCommandHandler @Inject constructor(
    private val handleFired: HandleAlarmFiredUseCase,
    private val markDone: MarkAlarmDoneUseCase,
    private val snooze: SnoozeAlarmUseCase,
) {

    suspend fun handle(command: ReceiverCommand) {
        when (command) {
            is ReceiverCommand.Fire -> handleFired(command.alarmId)
            is ReceiverCommand.Done -> markDone(command.alarmId)
            is ReceiverCommand.Snooze -> snooze(command.alarmId)
        }
    }
}
