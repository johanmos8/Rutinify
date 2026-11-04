package com.mirkwood.rutinify.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.data.local.DbTestData.DATE
import com.mirkwood.rutinify.data.local.DbTestData.NOW
import com.mirkwood.rutinify.data.local.DbTestData.alarm
import com.mirkwood.rutinify.data.local.DbTestData.order
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrderDaoTest {

    private lateinit var db: RutinifyDatabase
    private lateinit var orderDao: OrderDao
    private lateinit var alarmDao: AlarmItemDao

    @Before
    fun setUp() {
        db = DbTestData.inMemoryDb()
        orderDao = db.orderDao()
        alarmDao = db.alarmItemDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `el resumen cuenta pendientes y total por orden`() = runTest { // R-8.2
        val id = orderDao.insert(order())
        alarmDao.insertAll(
            listOf(
                alarm(id, 0, AlarmStatus.DONE),
                alarm(id, 1, AlarmStatus.PENDING),
                alarm(id, 2, AlarmStatus.PENDING),
                alarm(id, 3, AlarmStatus.SKIPPED),
            ),
        )

        orderDao.observeSummaries().test {
            val row = awaitItem().single()
            assertThat(row.order.id).isEqualTo(id)
            assertThat(row.pending).isEqualTo(2)
            assertThat(row.total).isEqualTo(4)
        }
    }

    @Test
    fun `el resumen incluye ordenes sin alarmas con cero y cero`() = runTest {
        orderDao.insert(order())

        orderDao.observeSummaries().test {
            val row = awaitItem().single()
            assertThat(row.pending).isEqualTo(0)
            assertThat(row.total).isEqualTo(0)
        }
    }

    @Test
    fun `el resumen se re-emite al cambiar el estado de una alarma`() = runTest { // R-8.6
        val id = orderDao.insert(order())
        val alarmIds = alarmDao.insertAll(listOf(alarm(id, 0), alarm(id, 1)))

        orderDao.observeSummaries().test {
            assertThat(awaitItem().single().pending).isEqualTo(2)

            alarmDao.updateStatus(listOf(alarmIds.first()), AlarmStatus.DONE, NOW.plusSeconds(60))

            assertThat(awaitItem().single().pending).isEqualTo(1)
        }
    }

    @Test
    fun `el resumen se re-emite al cambiar el estado de una orden`() = runTest { // R-8.6
        val id = orderDao.insert(order())

        orderDao.observeSummaries().test {
            assertThat(awaitItem().single().order.status).isEqualTo(OrderStatus.ACTIVE)

            orderDao.updateStatus(id, OrderStatus.PAUSED, NOW.plusSeconds(60))

            val updated = awaitItem().single().order
            assertThat(updated.status).isEqualTo(OrderStatus.PAUSED)
            assertThat(updated.updatedAt).isEqualTo(NOW.plusSeconds(60))
        }
    }

    @Test
    fun `las ordenes se listan por fecha desc y a igual fecha por creacion desc`() = runTest { // R-8.1
        val oldDate = orderDao.insert(order("Ayer", date = DATE.minusDays(1), createdAt = NOW.plusSeconds(500)))
        val sameDateFirst = orderDao.insert(order("Hoy 1", date = DATE, createdAt = NOW))
        val sameDateLater = orderDao.insert(order("Hoy 2", date = DATE, createdAt = NOW.plusSeconds(100)))
        val future = orderDao.insert(order("Mañana", date = DATE.plusDays(1), createdAt = NOW.minusSeconds(100)))

        orderDao.observeSummaries().test {
            assertThat(awaitItem().map { it.order.id })
                .containsExactly(future, sameDateLater, sameDateFirst, oldDate)
                .inOrder()
        }
    }

    @Test
    fun `observeWithAlarms devuelve la orden con todas sus alarmas y se actualiza`() = runTest { // R-9.1
        val id = orderDao.insert(order())
        val other = orderDao.insert(order("Otra"))
        val ids = alarmDao.insertAll(listOf(alarm(id, 0), alarm(id, 1)))
        alarmDao.insertAll(listOf(alarm(other, 0)))

        orderDao.observeWithAlarms(id).test {
            val first = awaitItem()
            assertThat(first?.order?.id).isEqualTo(id)
            assertThat(first?.alarms?.map { it.id }).containsExactlyElementsIn(ids)

            alarmDao.updateStatus(listOf(ids.first()), AlarmStatus.FIRED, NOW)

            val second = awaitItem()
            assertThat(second?.alarms?.first { it.id == ids.first() }?.status).isEqualTo(AlarmStatus.FIRED)
        }
    }

    @Test
    fun `observeWithAlarms emite null si la orden no existe o se borra`() = runTest {
        val id = orderDao.insert(order())

        orderDao.observeWithAlarms(id).test {
            assertThat(awaitItem()).isNotNull()
            orderDao.delete(id)
            assertThat(awaitItem()).isNull()
        }
    }

    @Test
    fun `findOpenByDate devuelve activas y pausadas de esa fecha pero no canceladas`() = runTest { // R-6.1, R-6.5
        val active = orderDao.insert(order("Activa", status = OrderStatus.ACTIVE))
        val paused = orderDao.insert(order("Pausada", status = OrderStatus.PAUSED))
        orderDao.insert(order("Cancelada", status = OrderStatus.CANCELLED))
        orderDao.insert(order("Otra fecha", date = DATE.plusDays(1)))

        val open = orderDao.findOpenByDate(DATE)

        assertThat(open.map { it.id }).containsExactly(active, paused)
    }
}
