package com.mirkwood.rutinify.data.local

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.data.local.DbTestData.alarm
import com.mirkwood.rutinify.data.local.DbTestData.order
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** T-43: critical categories, global across plans (R-15.1, D-66). */
@RunWith(RobolectricTestRunner::class)
class CategorySettingDaoTest {

    private lateinit var db: RutinifyDatabase
    private lateinit var dao: CategorySettingDao

    @Before
    fun setUp() {
        db = DbTestData.inMemoryDb()
        dao = db.categorySettingDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun planWith(vararg categories: String) {
        val orderId = db.orderDao().insert(order())
        db.alarmItemDao().insertAll(categories.mapIndexed { i, category -> alarm(orderId, i).copy(category = category) })
    }

    @Test
    fun `lista una vez cada categoria usada por los planes ordenada y sin marcar`() = runTest { // R-15.1
        planWith("paseo", "comida", "paseo")
        planWith("medicación")

        assertThat(dao.criticalNames()).isEmpty()
        dao.observeUsed().test {
            assertThat(awaitItem().map { it.name }).containsExactly("comida", "medicación", "paseo").inOrder()
        }
    }

    @Test
    fun `marcar una categoria vale para todos los planes y se puede desmarcar`() = runTest { // R-15.1, D-66
        planWith("medicación")
        planWith("medicación", "paseo")

        dao.upsert(CategorySettingEntity("medicación", critical = true))

        assertThat(dao.criticalNames()).containsExactly("medicación")
        dao.observeUsed().test {
            assertThat(awaitItem()).containsExactly(
                CategorySettingEntity("medicación", critical = true),
                CategorySettingEntity("paseo", critical = false),
            ).inOrder()

            dao.upsert(CategorySettingEntity("medicación", critical = false))
            assertThat(awaitItem().first()).isEqualTo(CategorySettingEntity("medicación", critical = false))
        }
        assertThat(dao.criticalNames()).isEmpty()
    }

    @Test
    fun `una marca sin planes que usen la categoria no aparece en la lista`() = runTest { // R-15.1
        planWith("paseo")
        dao.upsert(CategorySettingEntity("medicación", critical = true))

        dao.observeUsed().test {
            assertThat(awaitItem()).containsExactly(CategorySettingEntity("paseo", critical = false))
        }
        // The mark is kept: it applies again as soon as a plan uses the category
        assertThat(dao.criticalNames()).containsExactly("medicación")
    }
}
