package com.mirkwood.rutinify.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class OrderSummaryTest {

    private fun summary(status: OrderStatus, pending: Int, total: Int) = OrderSummary(
        order = RoutineOrder(
            id = 1,
            name = "Cachorro",
            date = LocalDate.of(2026, 11, 4),
            notes = null,
            status = status,
            createdAt = Instant.parse("2026-11-04T10:00:00Z"),
        ),
        pending = pending,
        total = total,
    )

    // R-8.3
    @Test
    fun `orden activa sin pendientes se muestra como terminada`() {
        assertThat(summary(OrderStatus.ACTIVE, pending = 0, total = 5).displayStatus)
            .isEqualTo(OrderDisplayStatus.FINISHED)
    }

    @Test
    fun `orden activa con pendientes se muestra como activa`() {
        assertThat(summary(OrderStatus.ACTIVE, pending = 2, total = 5).displayStatus)
            .isEqualTo(OrderDisplayStatus.ACTIVE)
    }

    @Test
    fun `orden activa sin ninguna alarma se muestra como terminada`() {
        assertThat(summary(OrderStatus.ACTIVE, pending = 0, total = 0).displayStatus)
            .isEqualTo(OrderDisplayStatus.FINISHED)
    }

    @Test
    fun `orden pausada sin pendientes sigue pausada`() {
        assertThat(summary(OrderStatus.PAUSED, pending = 0, total = 5).displayStatus)
            .isEqualTo(OrderDisplayStatus.PAUSED)
    }

    @Test
    fun `orden pausada con pendientes se muestra como pausada`() {
        assertThat(summary(OrderStatus.PAUSED, pending = 3, total = 5).displayStatus)
            .isEqualTo(OrderDisplayStatus.PAUSED)
    }

    @Test
    fun `orden cancelada se muestra como cancelada aunque no tenga pendientes`() {
        assertThat(summary(OrderStatus.CANCELLED, pending = 0, total = 5).displayStatus)
            .isEqualTo(OrderDisplayStatus.CANCELLED)
    }
}
