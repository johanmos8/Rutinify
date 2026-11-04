package com.mirkwood.rutinify.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class RutinifyDatabaseTest {

    private lateinit var db: RutinifyDatabase
    private lateinit var orderDao: OrderDao
    private lateinit var alarmDao: AlarmItemDao

    private val now = Instant.parse("2026-11-04T10:00:00Z")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RutinifyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        orderDao = db.orderDao()
        alarmDao = db.alarmItemDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun order(name: String = "Cachorro") = RoutineOrderEntity(
        name = name,
        date = LocalDate.of(2026, 11, 4),
        notes = "Notas",
        status = OrderStatus.ACTIVE,
        schemaVersion = 1,
        rawJson = """{"schemaVersion":1}""",
        createdAt = now,
        updatedAt = now,
    )

    private fun alarm(orderId: Long, position: Int) = AlarmItemEntity(
        orderId = orderId,
        position = position,
        time = LocalTime.of(6, 45).plusMinutes(position.toLong()),
        dayOffset = 0,
        title = "Actividad $position",
        description = null,
        durationMin = 10,
        category = "general",
        assignee = null,
        status = AlarmStatus.PENDING,
        snoozedUntil = null,
        firedAt = null,
        doneAt = null,
        statusChangedAt = now,
    )

    @Test
    fun `inserta y lee una orden con todos sus campos`() = runTest { // R-5.1
        val id = orderDao.insert(order())

        val read = orderDao.getById(id)

        assertThat(read).isEqualTo(order().copy(id = id))
    }

    @Test
    fun `inserta y lee alarmas con todos sus campos`() = runTest { // R-5.1
        val orderId = orderDao.insert(order())
        val snoozed = Instant.parse("2026-11-04T11:50:00Z")
        val original = alarm(orderId, 0).copy(
            dayOffset = 1,
            description = "Paseo corto",
            assignee = "Johan",
            snoozedUntil = snoozed,
            status = AlarmStatus.FIRED,
            firedAt = snoozed,
        )

        val ids = alarmDao.insertAll(listOf(original))

        assertThat(alarmDao.getById(ids.single())).isEqualTo(original.copy(id = ids.single()))
    }

    @Test
    fun `borrar una orden borra sus alarmas en cascada`() = runTest {
        val keep = orderDao.insert(order("Se queda"))
        val gone = orderDao.insert(order("Se borra"))
        val keptIds = alarmDao.insertAll(listOf(alarm(keep, 0)))
        val goneIds = alarmDao.insertAll(listOf(alarm(gone, 0), alarm(gone, 1)))

        orderDao.delete(gone)

        assertThat(orderDao.getById(gone)).isNull()
        goneIds.forEach { assertThat(alarmDao.getById(it)).isNull() }
        assertThat(alarmDao.getById(keptIds.single())).isNotNull()
    }

    @Test
    fun `AUTOINCREMENT no reutiliza el id de una alarma borrada`() = runTest { // design §6.1
        val orderId = orderDao.insert(order())
        val ids = alarmDao.insertAll(listOf(alarm(orderId, 0), alarm(orderId, 1)))
        val highest = ids.max()

        alarmDao.delete(highest)
        val newId = alarmDao.insertAll(listOf(alarm(orderId, 2))).single()

        assertThat(newId).isGreaterThan(highest)
    }

    @Test
    fun `AUTOINCREMENT no reutiliza el id de una orden borrada`() = runTest {
        val first = orderDao.insert(order())
        orderDao.delete(first)

        val second = orderDao.insert(order())

        assertThat(second).isGreaterThan(first)
    }

    @Test
    fun `una alarma con orden inexistente se rechaza por la clave foranea`() = runTest {
        val result = runCatching { alarmDao.insertAll(listOf(alarm(orderId = 999, position = 0))) }

        assertThat(result.exceptionOrNull()).isInstanceOf(android.database.sqlite.SQLiteConstraintException::class.java)
    }
}
