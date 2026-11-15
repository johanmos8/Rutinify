package com.mirkwood.rutinify.domain.time

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.DraftItem
import com.mirkwood.rutinify.domain.model.DraftOrigin
import com.mirkwood.rutinify.domain.model.RoutineDraft
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** T-13, T-38: preselected date on the Review screen (R-4.4, R-4.5, D-61). */
class DefaultDatePolicyTest {

    private val bogota = ZoneId.of("America/Bogota")
    private val policy = DefaultDatePolicy(TriggerTimeCalculator())

    /** 2026-11-05 at the given Bogotá wall-clock time (midnight in Bogotá = 05:00Z). */
    private fun nowAt(hour: Int, minute: Int = 0): Instant =
        Instant.parse("2026-11-05T05:00:00Z").plusSeconds(hour * 3_600L + minute * 60L)

    private fun draft(date: LocalDate?, vararg items: Pair<String, Int>) = RoutineDraft(
        id = "d",
        name = "Rutina",
        date = date,
        notes = null,
        items = items.mapIndexed { i, (time, offset) ->
            DraftItem(i, LocalTime.parse(time), offset, "A$i", null, null, "general", null)
        },
        origin = DraftOrigin.IMPORT,
    )

    @Test
    fun `si el JSON trae fecha se usa aunque sea pasada`() { // R-4.4
        val jsonDate = LocalDate.of(2026, 1, 1)

        val result = policy.defaultDate(draft(jsonDate, "06:45" to 0), nowAt(12), bogota)

        assertThat(result).isEqualTo(DefaultDate(jsonDate, chosenByApp = false))
    }

    @Test
    fun `sin fecha y con alguna actividad futura hoy se elige hoy`() { // R-4.5
        val result = policy.defaultDate(draft(null, "06:45" to 0, "22:30" to 0), nowAt(12), bogota)

        assertThat(result).isEqualTo(DefaultDate(LocalDate.of(2026, 11, 5), chosenByApp = true))
    }

    @Test
    fun `sin fecha y con todo pasado se elige manana`() { // R-4.5
        val result = policy.defaultDate(draft(null, "06:45" to 0, "09:30" to 0), nowAt(12), bogota)

        assertThat(result).isEqualTo(DefaultDate(LocalDate.of(2026, 11, 6), chosenByApp = true))
    }

    @Test
    fun `una actividad de madrugada del dia siguiente cuenta como futura`() { // R-4.5
        val result = policy.defaultDate(draft(null, "06:45" to 0, "02:00" to 1), nowAt(23), bogota)

        assertThat(result.date).isEqualTo(LocalDate.of(2026, 11, 5))
    }

    @Test
    fun `una actividad justo a la hora actual ya cuenta como pasada`() { // R-4.5, R-4.7 (<= now)
        val result = policy.defaultDate(draft(null, "12:00" to 0), nowAt(12), bogota)

        assertThat(result.date).isEqualTo(LocalDate.of(2026, 11, 6))
    }

    @Test
    fun `un minuto antes de la actividad todavia se elige hoy`() { // R-4.5
        val result = policy.defaultDate(draft(null, "12:00" to 0), nowAt(11, 59), bogota)

        assertThat(result.date).isEqualTo(LocalDate.of(2026, 11, 5))
    }

    @Test
    fun `hoy se calcula en la zona del telefono`() { // R-4.5, R-12.2
        // 2026-11-06T02:00Z is still 2026-11-05 21:00 in Bogotá but already 03:00 on the 6th in Madrid
        val now = Instant.parse("2026-11-06T02:00:00Z")
        val routine = draft(null, "23:00" to 0)

        assertThat(policy.defaultDate(routine, now, bogota).date).isEqualTo(LocalDate.of(2026, 11, 5))
        assertThat(policy.defaultDate(routine, now, ZoneId.of("Europe/Madrid")).date).isEqualTo(LocalDate.of(2026, 11, 6))
    }

    // ---- Multi-day routines (T-38) ----

    /** 06:00, 14:00 and 22:00 for 7 days (dayOffset 0..6). */
    private val treatment = (0..6).flatMap { day -> listOf("06:00", "14:00", "22:00").map { it to day } }.toTypedArray()

    @Test
    fun `una rutina de varios dias con el dia 1 ya pasado empieza manana`() { // R-4.5, D-61
        val result = policy.defaultDate(draft(null, *treatment), nowAt(23), bogota)

        assertThat(result).isEqualTo(DefaultDate(LocalDate.of(2026, 11, 6), chosenByApp = true))
    }

    @Test
    fun `una rutina de varios dias con tomas del dia 1 por delante empieza hoy`() { // R-4.5
        val result = policy.defaultDate(draft(null, *treatment), nowAt(12), bogota)

        assertThat(result).isEqualTo(DefaultDate(LocalDate.of(2026, 11, 5), chosenByApp = true))
    }

    @Test
    fun `en una rutina de varios dias el dayOffset 1 es el dia 2 y no cuenta`() { // R-4.5, D-61
        // After repeatDays expansion, Day 1's 02:00 and Day 2's activities both have dayOffset 1
        val result = policy.defaultDate(draft(null, "22:00" to 0, "02:00" to 1, "22:00" to 4), nowAt(23), bogota)

        assertThat(result).isEqualTo(DefaultDate(LocalDate.of(2026, 11, 6), chosenByApp = true))
    }
}
