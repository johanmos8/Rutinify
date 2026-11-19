package com.mirkwood.rutinify.domain.model

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.testutil.testOrder
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** T-68: a recurring plan with no end is never finished (R-23.11). */
class OrderSummaryTest {

    @Test
    fun `un plan sin pendientes esta terminado salvo si se repite sin fin`() { // R-8.3, R-23.11
        val plain = testOrder(id = 1)
        val endless = plain.copy(recurrence = Recurrence(setOf(DayOfWeek.MONDAY), until = null))
        val ending = plain.copy(recurrence = Recurrence(setOf(DayOfWeek.MONDAY), until = LocalDate.of(2026, 11, 30)))

        assertThat(OrderSummary(plain, pending = 0, total = 3).displayStatus).isEqualTo(OrderDisplayStatus.FINISHED)
        assertThat(OrderSummary(endless, pending = 0, total = 3).displayStatus).isEqualTo(OrderDisplayStatus.ACTIVE)
        assertThat(OrderSummary(ending, pending = 0, total = 3).displayStatus).isEqualTo(OrderDisplayStatus.FINISHED)
    }
}
