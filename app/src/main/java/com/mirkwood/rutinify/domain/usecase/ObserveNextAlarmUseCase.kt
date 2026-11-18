package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.model.NextAlarm
import com.mirkwood.rutinify.domain.repository.AlarmRepository
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import com.mirkwood.rutinify.domain.time.ZoneProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The next pending alarm of all ACTIVE plans, by effective time (snoozes included), or null for "Nada pendiente"
 * (R-21.1, R-21.3). Late alarms still pending come first: they are what is due now.
 */
class ObserveNextAlarmUseCase @Inject constructor(
    private val alarms: AlarmRepository,
    private val calculator: TriggerTimeCalculator,
    private val zoneProvider: ZoneProvider,
) {
    operator fun invoke(): Flow<NextAlarm?> = alarms.observeUpcoming().map { upcoming ->
        val zone = zoneProvider.zone()
        upcoming
            .map { it to calculator.effectiveTrigger(it.alarm, it.orderDate, zone) }
            .minWithOrNull(compareBy({ it.second }, { it.first.alarm.id }))
            ?.let { (item, triggerAt) -> NextAlarm(item.alarm.id, item.alarm.orderId, item.alarm.title, item.orderName, triggerAt) }
    }.distinctUntilChanged()

    /** The value right now, for a widget render (R-21.1). */
    suspend fun current(): NextAlarm? = invoke().first()
}
