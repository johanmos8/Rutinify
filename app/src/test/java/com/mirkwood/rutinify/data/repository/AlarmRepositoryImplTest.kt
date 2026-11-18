package com.mirkwood.rutinify.data.repository

import com.mirkwood.rutinify.domain.model.AlarmEventType
import com.mirkwood.rutinify.domain.model.AlarmEvent
import java.time.LocalTime
import com.mirkwood.rutinify.domain.model.AlarmScheduleUpdate
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.data.local.DbTestData
import com.mirkwood.rutinify.data.local.DbTestData.DATE
import com.mirkwood.rutinify.data.local.DbTestData.alarm
import com.mirkwood.rutinify.data.local.DbTestData.order
import com.mirkwood.rutinify.data.local.RutinifyDatabase
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class AlarmRepositoryImplTest {

    private val now = Instant.parse("2026-11-04T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private lateinit var db: RutinifyDatabase
    private lateinit var repository: AlarmRepositoryImpl

    @Before
    fun setUp() {
        db = DbTestData.inMemoryDb()
        repository = AlarmRepositoryImpl(db, db.alarmItemDao(), clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertOrderWithAlarms(vararg statuses: AlarmStatus, orderStatus: OrderStatus = OrderStatus.ACTIVE): List<Long> {
        val orderId = db.orderDao().insert(order(status = orderStatus))
        return db.alarmItemDao().insertAll(statuses.mapIndexed { i, s -> alarm(orderId, i, s) })
    }

    @Test
    fun `getById mapea todos los campos a dominio`() = runTest {
        val id = insertOrderWithAlarms(AlarmStatus.PENDING).single()
        val entity = requireNotNull(db.alarmItemDao().getById(id))

        val alarm = requireNotNull(repository.getById(id))

        assertThat(alarm.id).isEqualTo(entity.id)
        assertThat(alarm.orderId).isEqualTo(entity.orderId)
        assertThat(alarm.position).isEqualTo(entity.position)
        assertThat(alarm.time).isEqualTo(entity.time)
        assertThat(alarm.dayOffset).isEqualTo(entity.dayOffset)
        assertThat(alarm.title).isEqualTo(entity.title)
        assertThat(alarm.durationMin).isEqualTo(entity.durationMin)
        assertThat(alarm.category).isEqualTo(entity.category)
        assertThat(alarm.status).isEqualTo(entity.status)
        assertThat(alarm.statusChangedAt).isEqualTo(entity.statusChangedAt)
    }

    @Test
    fun `getPendingForActiveOrders incluye la fecha de la orden`() = runTest { // R-12.1
        val ids = insertOrderWithAlarms(AlarmStatus.PENDING, AlarmStatus.DONE)
        insertOrderWithAlarms(AlarmStatus.PENDING, orderStatus = OrderStatus.PAUSED)

        val pending = repository.getPendingForActiveOrders()

        assertThat(pending.map { it.alarm.id }).containsExactly(ids[0])
        assertThat(pending.single().orderDate).isEqualTo(DATE)
    }

    @Test
    fun `updateStatus usa la hora del reloj`() = runTest {
        val ids = insertOrderWithAlarms(AlarmStatus.PENDING, AlarmStatus.PENDING)

        repository.updateStatus(ids, AlarmStatus.FIRED)

        ids.forEach {
            val alarm = repository.getById(it)
            assertThat(alarm?.status).isEqualTo(AlarmStatus.FIRED)
            assertThat(alarm?.statusChangedAt).isEqualTo(now)
            assertThat(alarm?.firedAt).isEqualTo(now)
        }
    }

    @Test
    fun `snooze deja la alarma pendiente hasta el instante dado`() = runTest { // R-7.5
        val id = insertOrderWithAlarms(AlarmStatus.FIRED).single()
        val until = now.plusSeconds(300)

        repository.snooze(id, until)

        val alarm = repository.getById(id)
        assertThat(alarm?.status).isEqualTo(AlarmStatus.PENDING)
        assertThat(alarm?.snoozedUntil).isEqualTo(until)
    }

    @Test
    fun `restore reinserta la alarma borrada identica`() = runTest { // R-9.3, R-9.4
        val id = insertOrderWithAlarms(AlarmStatus.PENDING).single()
        repository.updateStatus(listOf(id), AlarmStatus.FIRED)
        val original = requireNotNull(repository.getById(id))

        repository.delete(id)
        assertThat(repository.getById(id)).isNull()

        repository.restore(original)

        assertThat(repository.getById(id)).isEqualTo(original)
    }

    @Test
    fun `getPendingByOrder devuelve solo pendientes`() = runTest {
        val ids = insertOrderWithAlarms(AlarmStatus.PENDING, AlarmStatus.SKIPPED)
        val orderId = requireNotNull(repository.getById(ids[0])).orderId

        assertThat(repository.getPendingByOrder(orderId).map { it.id }).containsExactly(ids[0])
    }

    @Test
    fun `updateSchedules mueve solo las pendientes en una transaccion`() = runTest { // R-18.3, D-73
        val (pending, done) = insertOrderWithAlarms(AlarmStatus.PENDING, AlarmStatus.DONE)
        val until = now.plusSeconds(900)

        repository.updateSchedules(
            listOf(
                AlarmScheduleUpdate(pending, LocalTime.of(0, 30), 1, until),
                AlarmScheduleUpdate(done, LocalTime.of(0, 30), 1, null),
            ),
        )

        val moved = requireNotNull(repository.getById(pending))
        assertThat(moved.time).isEqualTo(LocalTime.of(0, 30))
        assertThat(moved.dayOffset).isEqualTo(1)
        assertThat(moved.snoozedUntil).isEqualTo(until)
        assertThat(repository.getById(done)?.dayOffset).isEqualTo(0)
    }

    @Test
    fun `hecho omitida y pospuesta dejan su evento con la hora del reloj`() = runTest { // R-20.1, D-77
        val (done, skipped, snoozed, fired) = insertOrderWithAlarms(AlarmStatus.PENDING, AlarmStatus.PENDING, AlarmStatus.FIRED, AlarmStatus.PENDING)
        val orderId = requireNotNull(repository.getById(done)).orderId

        repository.updateStatus(listOf(done), AlarmStatus.DONE)
        repository.updateStatus(listOf(skipped), AlarmStatus.SKIPPED)
        repository.snooze(snoozed, now.plusSeconds(300))
        repository.updateStatus(listOf(fired), AlarmStatus.FIRED)

        assertThat(repository.getEventsByOrder(orderId)).containsExactly(
            AlarmEvent(done, orderId, AlarmEventType.DONE, now),
            AlarmEvent(skipped, orderId, AlarmEventType.SKIPPED, now),
            AlarmEvent(snoozed, orderId, AlarmEventType.SNOOZED, now),
        ).inOrder()
    }

    @Test
    fun `borrar el plan borra su historial`() = runTest { // D-77
        val id = insertOrderWithAlarms(AlarmStatus.PENDING).single()
        val orderId = requireNotNull(repository.getById(id)).orderId
        repository.updateStatus(listOf(id), AlarmStatus.DONE)

        db.orderDao().delete(orderId)

        assertThat(repository.getEventsByOrder(orderId)).isEmpty()
    }
}
