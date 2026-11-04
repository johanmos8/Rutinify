package com.mirkwood.rutinify.domain.repository

import com.mirkwood.rutinify.domain.model.AlarmItem
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.PendingAlarm
import java.time.Instant

interface AlarmRepository {

    suspend fun getById(alarmId: Long): AlarmItem?

    /** Pendientes de órdenes Activas, con la fecha de su orden (R-12.1). */
    suspend fun getPendingForActiveOrders(): List<PendingAlarm>

    suspend fun getPendingByOrder(orderId: Long): List<AlarmItem>

    /** Cambia el estado de varias alarmas a la vez, con la hora actual como momento del cambio. */
    suspend fun updateStatus(alarmIds: List<Long>, status: AlarmStatus)

    /** Vuelve a PENDING con un instante absoluto de disparo (R-7.5, D-12). */
    suspend fun snooze(alarmId: Long, until: Instant)

    /** Borra la alarma (R-9.3). */
    suspend fun delete(alarmId: Long)

    /** Reinserta una alarma borrada con su mismo id ("Deshacer", R-9.4). */
    suspend fun restore(alarm: AlarmItem)
}
