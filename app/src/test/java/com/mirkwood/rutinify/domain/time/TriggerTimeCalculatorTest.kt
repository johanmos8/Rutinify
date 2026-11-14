package com.mirkwood.rutinify.domain.time

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.AlarmItem
import com.mirkwood.rutinify.domain.model.AlarmStatus
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** T-13: wall-clock trigger time (R-5.2, R-5.8, R-12.2, design §6.3). */
class TriggerTimeCalculatorTest {

    private val calculator = TriggerTimeCalculator()
    private val bogota = ZoneId.of("America/Bogota")
    private val madrid = ZoneId.of("Europe/Madrid")
    private val date = LocalDate.of(2026, 11, 5)

    @Test
    fun `fecha y hora en la zona del telefono`() { // R-5.2
        val trigger = calculator.triggerAt(date, LocalTime.of(6, 45), dayOffset = 0, zone = bogota)

        assertThat(trigger).isEqualTo(Instant.parse("2026-11-05T11:45:00Z"))
    }

    @Test
    fun `dayOffset 1 a las 02 00 es la madrugada del dia siguiente`() { // R-5.2
        val trigger = calculator.triggerAt(date, LocalTime.of(2, 0), dayOffset = 1, zone = bogota)

        assertThat(trigger).isEqualTo(Instant.parse("2026-11-06T07:00:00Z"))
    }

    @Test
    fun `medianoche con dayOffset 0 es el inicio del mismo dia`() { // R-5.2
        val trigger = calculator.triggerAt(date, LocalTime.MIDNIGHT, dayOffset = 0, zone = bogota)

        assertThat(trigger).isEqualTo(Instant.parse("2026-11-05T05:00:00Z"))
    }

    @Test
    fun `dayOffset 1 cruza el fin de mes y de ano`() { // R-5.2
        val trigger = calculator.triggerAt(LocalDate.of(2026, 12, 31), LocalTime.of(1, 0), dayOffset = 1, zone = bogota)

        assertThat(trigger).isEqualTo(Instant.parse("2027-01-01T06:00:00Z"))
    }

    @Test
    fun `un tratamiento que cruza el cambio de hora mantiene la hora de pared cada dia`() { // D-55, D-02
        // Europe/Madrid moves to summer time on 2026-03-29: 08:00 is +01:00 before and +02:00 after
        val start = LocalDate.of(2026, 3, 25)

        assertThat(calculator.triggerAt(start, LocalTime.of(8, 0), dayOffset = 0, zone = madrid))
            .isEqualTo(Instant.parse("2026-03-25T07:00:00Z"))
        assertThat(calculator.triggerAt(start, LocalTime.of(8, 0), dayOffset = 6, zone = madrid))
            .isEqualTo(Instant.parse("2026-03-31T06:00:00Z"))
    }

    @Test
    fun `dayOffset 30 cae treinta dias despues`() { // D-55
        val trigger = calculator.triggerAt(date, LocalTime.of(6, 45), dayOffset = 30, zone = bogota)

        assertThat(trigger).isEqualTo(Instant.parse("2026-12-05T11:45:00Z"))
    }

    @Test
    fun `una hora inexistente por el salto de verano se desplaza la duracion del salto`() { // R-5.8
        // Europe/Madrid jumps from 02:00 to 03:00 on 2026-03-29: 02:30 does not exist → 03:30 (+02:00)
        val trigger = calculator.triggerAt(LocalDate.of(2026, 3, 29), LocalTime.of(2, 30), dayOffset = 0, zone = madrid)

        assertThat(trigger).isEqualTo(Instant.parse("2026-03-29T01:30:00Z"))
    }

    @Test
    fun `una hora ambigua por el retraso de hora usa la primera ocurrencia`() { // R-5.8
        // Europe/Madrid goes back from 03:00 to 02:00 on 2026-10-25: first 02:30 is still +02:00
        val trigger = calculator.triggerAt(LocalDate.of(2026, 10, 25), LocalTime.of(2, 30), dayOffset = 0, zone = madrid)

        assertThat(trigger).isEqualTo(Instant.parse("2026-10-25T00:30:00Z"))
    }

    @Test
    fun `cambiar de zona recalcula la misma hora de pared`() { // R-12.2
        val inBogota = calculator.triggerAt(date, LocalTime.of(6, 45), 0, bogota)
        val inMadrid = calculator.triggerAt(date, LocalTime.of(6, 45), 0, madrid)

        assertThat(inMadrid).isEqualTo(Instant.parse("2026-11-05T05:45:00Z"))
        assertThat(inBogota).isNotEqualTo(inMadrid)
    }

    @Test
    fun `una alarma sin posponer usa la hora calculada`() { // design §6.3
        val trigger = calculator.effectiveTrigger(alarm(snoozedUntil = null), date, bogota)

        assertThat(trigger).isEqualTo(Instant.parse("2026-11-05T11:45:00Z"))
    }

    @Test
    fun `una alarma pospuesta usa el instante guardado aunque cambie la zona`() { // D-12
        val snoozed = Instant.parse("2026-11-05T11:50:00Z")

        assertThat(calculator.effectiveTrigger(alarm(snoozedUntil = snoozed), date, bogota)).isEqualTo(snoozed)
        assertThat(calculator.effectiveTrigger(alarm(snoozedUntil = snoozed), date, madrid)).isEqualTo(snoozed)
    }

    private fun alarm(snoozedUntil: Instant?) = AlarmItem(
        id = 1,
        orderId = 1,
        position = 0,
        time = LocalTime.of(6, 45),
        dayOffset = 0,
        title = "Pis",
        description = null,
        durationMin = null,
        category = "general",
        assignee = null,
        status = AlarmStatus.PENDING,
        snoozedUntil = snoozedUntil,
        statusChangedAt = Instant.EPOCH,
    )
}
