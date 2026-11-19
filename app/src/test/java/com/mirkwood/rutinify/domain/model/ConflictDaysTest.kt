package com.mirkwood.rutinify.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate

/** T-69: days a plan claims for conflicts, with an open end and weekdays for recurring plans (R-23.9). */
class ConflictDaysTest {

    private val mon16 = LocalDate.of(2026, 11, 16) // a Monday

    private fun recurring(vararg days: DayOfWeek, start: LocalDate = mon16, until: LocalDate? = null) =
        Recurrence(days.toSet(), until).conflictDays(start)

    private fun single(date: LocalDate) = RoutineSpan(date, date).conflictDays()

    @Test
    fun `un plan recurrente choca con un plan de un dia que cae en uno de sus dias`() { // R-23.9
        val mwf = recurring(MONDAY, WEDNESDAY, FRIDAY)

        assertThat(mwf.overlaps(single(mon16.plusDays(2)))).isTrue() // Wednesday
        assertThat(mwf.overlaps(single(mon16.plusDays(1)))).isFalse() // Tuesday
        assertThat(mwf.overlaps(single(mon16.plusWeeks(30)))).isTrue() // no end: any later Monday
        assertThat(single(mon16.plusDays(4)).overlaps(mwf)).isTrue() // symmetric
    }

    @Test
    fun `un plan de un dia antes del inicio del recurrente no choca`() { // R-23.9
        assertThat(recurring(MONDAY).overlaps(single(mon16.minusWeeks(1)))).isFalse()
    }

    @Test
    fun `un plan de un dia despues del until no choca`() { // R-23.9
        val untilNov30 = recurring(MONDAY, until = LocalDate.of(2026, 11, 30))

        assertThat(untilNov30.overlaps(single(LocalDate.of(2026, 11, 30)))).isTrue()
        assertThat(untilNov30.overlaps(single(LocalDate.of(2026, 12, 7)))).isFalse()
    }

    @Test
    fun `un plan de varios dias choca si alguno de sus dias es de la semana del recurrente`() { // R-23.9
        val saturdays = recurring(SATURDAY)
        val tueToThu = RoutineSpan(mon16.plusDays(1), mon16.plusDays(3)).conflictDays()
        val friToSun = RoutineSpan(mon16.plusDays(4), mon16.plusDays(6)).conflictDays()

        assertThat(saturdays.overlaps(tueToThu)).isFalse()
        assertThat(saturdays.overlaps(friToSun)).isTrue()
    }

    @Test
    fun `dos planes recurrentes chocan solo si comparten un dia de la semana en fechas comunes`() { // R-23.9
        val mondays = recurring(MONDAY)

        assertThat(mondays.overlaps(recurring(TUESDAY, WEDNESDAY))).isFalse()
        assertThat(mondays.overlaps(recurring(MONDAY, TUESDAY, start = mon16.plusWeeks(10)))).isTrue()
        // Same weekday but the other plan ended before this one starts
        val ended = recurring(MONDAY, start = mon16.minusWeeks(4), until = mon16.minusDays(1))
        assertThat(mondays.overlaps(ended)).isFalse()
        // Common dates exist but are fewer than a week and miss the shared weekday
        val shortTue = recurring(TUESDAY, MONDAY, start = mon16.plusDays(1), until = mon16.plusDays(3))
        assertThat(mondays.overlaps(shortTue)).isFalse()
    }

    @Test
    fun `los rangos cerrados se comportan como RoutineSpan_overlaps`() { // R-6.6
        val treatment = RoutineSpan(mon16, mon16.plusDays(6)).conflictDays()

        assertThat(treatment.overlaps(single(mon16.plusDays(6)))).isTrue()
        assertThat(treatment.overlaps(single(mon16.plusDays(7)))).isFalse()
        assertThat(treatment.overlaps(single(mon16.minusDays(1)))).isFalse()
    }

    @Test
    fun `el borrador recurrente reclama sus dias de la semana desde la fecha elegida`() { // R-23.9
        val draft = RoutineDraft(
            id = "d",
            name = "Gimnasio",
            date = null,
            notes = null,
            items = emptyList(),
            origin = DraftOrigin.IMPORT,
            recurrence = Recurrence(setOf(MONDAY), until = null),
        )

        assertThat(draft.conflictDaysOn(mon16)).isEqualTo(ConflictDays(mon16, null, setOf(MONDAY)))
    }
}
