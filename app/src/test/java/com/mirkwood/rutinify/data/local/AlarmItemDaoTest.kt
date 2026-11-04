package com.mirkwood.rutinify.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
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
class AlarmItemDaoTest {

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
    fun `getPendingForActiveOrders excluye ordenes pausadas y canceladas`() = runTest { // R-12.1, R-10.5
        val active = orderDao.insert(order("Activa", status = OrderStatus.ACTIVE))
        val paused = orderDao.insert(order("Pausada", status = OrderStatus.PAUSED))
        val cancelled = orderDao.insert(order("Cancelada", status = OrderStatus.CANCELLED))
        val activePending = alarmDao.insertAll(listOf(alarm(active, 0), alarm(active, 1)))
        alarmDao.insertAll(listOf(alarm(paused, 0), alarm(cancelled, 0)))

        val pending = alarmDao.getPendingForActiveOrders()

        assertThat(pending.map { it.alarm.id }).containsExactlyElementsIn(activePending)
    }

    @Test
    fun `getPendingForActiveOrders excluye alarmas no pendientes y trae la fecha de la orden`() = runTest {
        val id = orderDao.insert(order(date = DATE))
        val ids = alarmDao.insertAll(
            listOf(
                alarm(id, 0, AlarmStatus.PENDING),
                alarm(id, 1, AlarmStatus.FIRED),
                alarm(id, 2, AlarmStatus.DONE),
                alarm(id, 3, AlarmStatus.SKIPPED),
                alarm(id, 4, AlarmStatus.CANCELLED),
            ),
        )

        val pending = alarmDao.getPendingForActiveOrders()

        assertThat(pending.map { it.alarm.id }).containsExactly(ids[0])
        assertThat(pending.single().orderDate).isEqualTo(DATE)
    }

    @Test
    fun `getPendingByOrder devuelve solo las pendientes de esa orden`() = runTest {
        val id = orderDao.insert(order())
        val other = orderDao.insert(order("Otra"))
        val ids = alarmDao.insertAll(listOf(alarm(id, 0), alarm(id, 1, AlarmStatus.DONE)))
        alarmDao.insertAll(listOf(alarm(other, 0)))

        assertThat(alarmDao.getPendingByOrder(id).map { it.id }).containsExactly(ids[0])
    }

    @Test
    fun `updateStatus cambia varias alarmas y registra el momento`() = runTest {
        val id = orderDao.insert(order())
        val ids = alarmDao.insertAll(listOf(alarm(id, 0), alarm(id, 1), alarm(id, 2)))
        val at = NOW.plusSeconds(300)

        alarmDao.updateStatus(ids.take(2), AlarmStatus.CANCELLED, at)

        val changed = ids.take(2).map { alarmDao.getById(it) }
        changed.forEach {
            assertThat(it?.status).isEqualTo(AlarmStatus.CANCELLED)
            assertThat(it?.statusChangedAt).isEqualTo(at)
        }
        assertThat(alarmDao.getById(ids[2])?.status).isEqualTo(AlarmStatus.PENDING)
    }

    @Test
    fun `updateStatus a FIRED y DONE guarda firedAt y doneAt`() = runTest {
        val id = orderDao.insert(order())
        val alarmId = alarmDao.insertAll(listOf(alarm(id, 0))).single()
        val firedAt = NOW.plusSeconds(60)
        val doneAt = NOW.plusSeconds(120)

        alarmDao.updateStatus(listOf(alarmId), AlarmStatus.FIRED, firedAt)
        alarmDao.updateStatus(listOf(alarmId), AlarmStatus.DONE, doneAt)

        val read = alarmDao.getById(alarmId)
        assertThat(read?.firedAt).isEqualTo(firedAt)
        assertThat(read?.doneAt).isEqualTo(doneAt)
        assertThat(read?.status).isEqualTo(AlarmStatus.DONE)
    }

    @Test
    fun `snooze deja la alarma pendiente con el instante absoluto`() = runTest { // R-7.5, D-12
        val id = orderDao.insert(order())
        val alarmId = alarmDao.insertAll(listOf(alarm(id, 0, AlarmStatus.FIRED))).single()
        val until = NOW.plusSeconds(300)

        alarmDao.snooze(alarmId, until, NOW)

        val read = alarmDao.getById(alarmId)
        assertThat(read?.status).isEqualTo(AlarmStatus.PENDING)
        assertThat(read?.snoozedUntil).isEqualTo(until)
        assertThat(read?.statusChangedAt).isEqualTo(NOW)
    }

    @Test
    fun `insert reinserta una alarma borrada con el mismo id para deshacer`() = runTest { // R-9.4
        val id = orderDao.insert(order())
        val original = alarmDao.getById(alarmDao.insertAll(listOf(alarm(id, 0))).single())
        requireNotNull(original)

        alarmDao.delete(original.id)
        assertThat(alarmDao.getById(original.id)).isNull()

        val reinserted = alarmDao.insert(original)

        assertThat(reinserted).isEqualTo(original.id)
        assertThat(alarmDao.getById(original.id)).isEqualTo(original)
    }
}
