package com.mirkwood.rutinify.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.OrderStatus
import com.mirkwood.rutinify.testutil.FakeOrderRepository
import com.mirkwood.rutinify.testutil.testOrder
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

/** T-20: orders that trigger the "Reemplazar / Mantener ambas" dialog (R-6.1, R-6.5). */
class FindConflictingOrdersUseCaseTest {

    private val date = LocalDate.of(2026, 11, 5)
    private val orders = FakeOrderRepository()
    private val findConflicts = FindConflictingOrdersUseCase(orders)

    @Test
    fun `devuelve las ordenes activas y pausadas de la misma fecha`() = runTest { // R-6.1, R-6.5
        orders.add(
            testOrder(id = 1L, name = "Cachorro", date = date),
            testOrder(id = 2L, name = "Gato", date = date, status = OrderStatus.PAUSED),
        )

        assertThat(findConflicts(date).map { it.name }).containsExactly("Cachorro", "Gato")
    }

    @Test
    fun `ignora las canceladas y las de otras fechas`() = runTest { // R-6.1
        orders.add(
            testOrder(id = 1L, date = date, status = OrderStatus.CANCELLED),
            testOrder(id = 2L, date = date.plusDays(1)),
        )

        assertThat(findConflicts(date)).isEmpty()
    }
}
