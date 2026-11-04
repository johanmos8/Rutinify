package com.mirkwood.rutinify.data.repository

import android.database.sqlite.SQLiteException
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.data.local.AlarmItemDao
import com.mirkwood.rutinify.data.local.AlarmItemEntity
import com.mirkwood.rutinify.data.local.DbTestData
import com.mirkwood.rutinify.data.local.RutinifyDatabase
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.NewAlarm
import com.mirkwood.rutinify.domain.model.NewOrder
import com.mirkwood.rutinify.domain.model.OrderDisplayStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class OrderRepositoryImplTest {

    private val now = Instant.parse("2026-11-04T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val date = LocalDate.of(2026, 11, 4)

    private lateinit var db: RutinifyDatabase
    private lateinit var repository: OrderRepositoryImpl

    @Before
    fun setUp() {
        db = DbTestData.inMemoryDb()
        repository = OrderRepositoryImpl(db, db.orderDao(), db.alarmItemDao(), clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun newOrder(name: String = "Cachorro") =
        NewOrder(name = name, date = date, notes = "Notas", rawJson = """{"schemaVersion":1}""")

    private fun newAlarm(
        position: Int,
        time: LocalTime = LocalTime.of(6, 45),
        dayOffset: Int = 0,
        status: AlarmStatus = AlarmStatus.PENDING,
    ) = NewAlarm(
        position = position,
        time = time,
        dayOffset = dayOffset,
        title = "Actividad $position",
        description = "Desc $position",
        durationMin = 15,
        category = "siesta",
        assignee = "Johan",
        status = status,
    )

    @Test
    fun `createOrder guarda la orden activa y sus alarmas con todos los campos`() = runTest { // R-5.1, R-5.5
        val result = repository.createOrder(
            newOrder(),
            listOf(newAlarm(0), newAlarm(1, LocalTime.of(7, 0), status = AlarmStatus.SKIPPED)),
        ).getOrThrow()

        assertThat(result.order.name).isEqualTo("Cachorro")
        assertThat(result.order.date).isEqualTo(date)
        assertThat(result.order.notes).isEqualTo("Notas")
        assertThat(result.order.status).isEqualTo(OrderStatus.ACTIVE)
        assertThat(result.order.createdAt).isEqualTo(now)
        assertThat(result.cancelledAlarmIds).isEmpty()

        val stored = repository.observeOrderWithAlarms(result.order.id).first()
        assertThat(stored?.order).isEqualTo(result.order)
        assertThat(stored?.alarms).isEqualTo(result.alarms)

        val first = result.alarms.first()
        assertThat(first.id).isGreaterThan(0L)
        assertThat(first.orderId).isEqualTo(result.order.id)
        assertThat(first.time).isEqualTo(LocalTime.of(6, 45))
        assertThat(first.title).isEqualTo("Actividad 0")
        assertThat(first.description).isEqualTo("Desc 0")
        assertThat(first.durationMin).isEqualTo(15)
        assertThat(first.category).isEqualTo("siesta")
        assertThat(first.assignee).isEqualTo("Johan")
        assertThat(first.status).isEqualTo(AlarmStatus.PENDING)
        assertThat(first.statusChangedAt).isEqualTo(now)
        assertThat(result.alarms.map { it.status }).containsExactly(AlarmStatus.PENDING, AlarmStatus.SKIPPED).inOrder()
    }

    @Test
    fun `createOrder guarda el JSON original y la version de esquema`() = runTest {
        val id = repository.createOrder(newOrder(), listOf(newAlarm(0))).getOrThrow().order.id

        val entity = db.orderDao().getById(id)

        assertThat(entity?.rawJson).isEqualTo("""{"schemaVersion":1}""")
        assertThat(entity?.schemaVersion).isEqualTo(1)
    }

    @Test
    fun `si falla la insercion de alarmas no queda ninguna orden escrita`() = runTest { // R-5.7
        val failing = OrderRepositoryImpl(db, db.orderDao(), FailingInsertAlarmDao(db.alarmItemDao()), clock)

        val result = failing.createOrder(newOrder(), listOf(newAlarm(0)))

        assertThat(result.isFailure).isTrue()
        assertThat(repository.observeSummaries().first()).isEmpty()
    }

    @Test
    fun `si falla un reemplazo la orden anterior queda intacta`() = runTest { // R-5.7, R-6.2
        val previous = repository.createOrder(newOrder("Anterior"), listOf(newAlarm(0))).getOrThrow()
        val failing = OrderRepositoryImpl(db, db.orderDao(), FailingInsertAlarmDao(db.alarmItemDao()), clock)

        val result = failing.createOrder(newOrder("Nueva"), listOf(newAlarm(0)), replaceOrderIds = listOf(previous.order.id))

        assertThat(result.isFailure).isTrue()
        val summaries = repository.observeSummaries().first()
        assertThat(summaries.map { it.order.name }).containsExactly("Anterior")
        assertThat(summaries.single().order.status).isEqualTo(OrderStatus.ACTIVE)
        assertThat(summaries.single().pending).isEqualTo(1)
    }

    @Test
    fun `reemplazar cancela las ordenes previas y solo sus alarmas pendientes`() = runTest { // R-6.2, R-6.5
        val a = repository.createOrder(newOrder("A"), listOf(newAlarm(0), newAlarm(1))).getOrThrow()
        val b = repository.createOrder(newOrder("B"), listOf(newAlarm(0))).getOrThrow()
        val done = a.alarms.first().id
        db.alarmItemDao().updateStatus(listOf(done), AlarmStatus.DONE, now)

        val created = repository.createOrder(
            newOrder("Nueva"),
            listOf(newAlarm(0)),
            replaceOrderIds = listOf(a.order.id, b.order.id),
        ).getOrThrow()

        assertThat(created.cancelledAlarmIds).containsExactly(a.alarms[1].id, b.alarms[0].id)
        assertThat(repository.getOrder(a.order.id)?.status).isEqualTo(OrderStatus.CANCELLED)
        assertThat(repository.getOrder(b.order.id)?.status).isEqualTo(OrderStatus.CANCELLED)
        assertThat(db.alarmItemDao().getById(done)?.status).isEqualTo(AlarmStatus.DONE)
        assertThat(db.alarmItemDao().getById(a.alarms[1].id)?.status).isEqualTo(AlarmStatus.CANCELLED)
        assertThat(repository.getOrder(created.order.id)?.status).isEqualTo(OrderStatus.ACTIVE)
    }

    @Test
    fun `observeOrderWithAlarms ordena por dayOffset hora y posicion`() = runTest { // R-9.1, D-27
        val created = repository.createOrder(
            newOrder(),
            listOf(
                newAlarm(0, LocalTime.of(2, 0), dayOffset = 1),
                newAlarm(1, LocalTime.of(9, 0)),
                newAlarm(2, LocalTime.of(6, 45)),
                newAlarm(3, LocalTime.of(6, 45)),
            ),
        ).getOrThrow()

        val alarms = repository.observeOrderWithAlarms(created.order.id).first()?.alarms.orEmpty()

        assertThat(alarms.map { it.position }).containsExactly(2, 3, 1, 0).inOrder()
    }

    @Test
    fun `observeSummaries mapea recuentos y estado derivado`() = runTest { // R-8.2, R-8.3
        val created = repository.createOrder(newOrder(), listOf(newAlarm(0), newAlarm(1, status = AlarmStatus.SKIPPED)))
            .getOrThrow()

        repository.observeSummaries().test {
            val summary = awaitItem().single()
            assertThat(summary.order).isEqualTo(created.order)
            assertThat(summary.pending).isEqualTo(1)
            assertThat(summary.total).isEqualTo(2)
            assertThat(summary.displayStatus).isEqualTo(OrderDisplayStatus.ACTIVE)

            db.alarmItemDao().updateStatus(listOf(created.alarms.first().id), AlarmStatus.DONE, now)

            assertThat(awaitItem().single().displayStatus).isEqualTo(OrderDisplayStatus.FINISHED)
        }
    }

    @Test
    fun `findOpenByDate mapea a dominio`() = runTest { // R-6.1
        val created = repository.createOrder(newOrder(), listOf(newAlarm(0))).getOrThrow()

        assertThat(repository.findOpenByDate(date)).containsExactly(created.order)
        assertThat(repository.findOpenByDate(date.plusDays(1))).isEmpty()
    }

    @Test
    fun `updateStatus y delete actuan sobre la orden`() = runTest { // R-10.4
        val created = repository.createOrder(newOrder(), listOf(newAlarm(0))).getOrThrow()

        repository.updateStatus(created.order.id, OrderStatus.PAUSED)
        assertThat(repository.getOrder(created.order.id)?.status).isEqualTo(OrderStatus.PAUSED)

        repository.delete(created.order.id)
        assertThat(repository.getOrder(created.order.id)).isNull()
        assertThat(db.alarmItemDao().getById(created.alarms.single().id)).isNull()
    }

    private class FailingInsertAlarmDao(delegate: AlarmItemDao) : AlarmItemDao by delegate {
        override suspend fun insertAll(alarms: List<AlarmItemEntity>): List<Long> =
            throw SQLiteException("fallo simulado")
    }
}
