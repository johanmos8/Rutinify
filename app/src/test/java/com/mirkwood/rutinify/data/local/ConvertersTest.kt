package com.mirkwood.rutinify.data.local

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `LocalDate se guarda como ISO y se recupera igual`() {
        val date = LocalDate.of(2026, 11, 4)
        assertThat(converters.localDateToString(date)).isEqualTo("2026-11-04")
        assertThat(converters.stringToLocalDate("2026-11-04")).isEqualTo(date)
    }

    @Test
    fun `las fechas ISO se ordenan lexicograficamente igual que cronologicamente`() {
        val dates = listOf(LocalDate.of(2026, 1, 9), LocalDate.of(2025, 12, 31), LocalDate.of(2026, 10, 1))
        val asText = dates.map { converters.localDateToString(it).orEmpty() }
        assertThat(asText.sorted()).isEqualTo(dates.sorted().map { converters.localDateToString(it).orEmpty() })
    }

    @Test
    fun `LocalTime se guarda como HH mm con cero inicial`() {
        assertThat(converters.localTimeToString(LocalTime.of(6, 45))).isEqualTo("06:45")
        assertThat(converters.stringToLocalTime("06:45")).isEqualTo(LocalTime.of(6, 45))
        assertThat(converters.stringToLocalTime("00:00")).isEqualTo(LocalTime.MIDNIGHT)
    }

    @Test
    fun `Instant se guarda como epoch millis`() {
        val instant = Instant.parse("2026-11-04T11:45:00Z")
        assertThat(converters.instantToEpochMillis(instant)).isEqualTo(instant.toEpochMilli())
        assertThat(converters.epochMillisToInstant(instant.toEpochMilli())).isEqualTo(instant)
    }

    @Test
    fun `los nulos se conservan como nulos`() {
        assertThat(converters.localDateToString(null)).isNull()
        assertThat(converters.stringToLocalDate(null)).isNull()
        assertThat(converters.localTimeToString(null)).isNull()
        assertThat(converters.stringToLocalTime(null)).isNull()
        assertThat(converters.instantToEpochMillis(null)).isNull()
        assertThat(converters.epochMillisToInstant(null)).isNull()
    }

    @Test
    fun `los estados se guardan por nombre`() {
        OrderStatus.entries.forEach {
            assertThat(converters.stringToOrderStatus(converters.orderStatusToString(it))).isEqualTo(it)
        }
        AlarmStatus.entries.forEach {
            assertThat(converters.stringToAlarmStatus(converters.alarmStatusToString(it))).isEqualTo(it)
        }
        assertThat(converters.alarmStatusToString(AlarmStatus.PENDING)).isEqualTo("PENDING")
    }
}
