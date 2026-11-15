package com.mirkwood.rutinify.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** T-34: span of an order and the days compared for conflicts (R-6.6, D-55, D-58). */
class RoutineSpanTest {

    private val nov14 = LocalDate.of(2026, 11, 14)

    @Test
    fun `el rango va de la fecha a la fecha mas el mayor dayOffset`() { // D-55
        val span = RoutineSpan.of(nov14, maxDayOffset = 6)

        assertThat(span).isEqualTo(RoutineSpan(nov14, LocalDate.of(2026, 11, 20)))
        assertThat(span.dayCount).isEqualTo(7)
    }

    @Test
    fun `una rutina de un dia con o sin madrugada no es de varios dias`() { // D-55
        assertThat(RoutineSpan.of(nov14, 0).isMultiDay).isFalse()
        assertThat(RoutineSpan.of(nov14, 1).isMultiDay).isFalse()
        assertThat(RoutineSpan.of(nov14, 2).isMultiDay).isTrue()
    }

    @Test
    fun `para conflictos una rutina de un dia solo cuenta su fecha`() { // R-6.6, D-58
        assertThat(RoutineSpan.of(nov14, 1).forConflicts()).isEqualTo(RoutineSpan(nov14, nov14))
        assertThat(RoutineSpan.of(nov14, 6).forConflicts()).isEqualTo(RoutineSpan.of(nov14, 6))
    }

    @Test
    fun `se solapan si comparten al menos un dia`() { // R-6.6
        val treatment = RoutineSpan.of(nov14, 6) // 14–20

        assertThat(treatment.overlaps(RoutineSpan(nov14.plusDays(3), nov14.plusDays(3)))).isTrue() // contained
        assertThat(treatment.overlaps(RoutineSpan(nov14.minusDays(2), nov14))).isTrue() // touches the first day
        assertThat(treatment.overlaps(RoutineSpan(nov14.plusDays(6), nov14.plusDays(9)))).isTrue() // touches the last
        assertThat(treatment.overlaps(RoutineSpan(nov14.plusDays(7), nov14.plusDays(7)))).isFalse() // day after
        assertThat(treatment.overlaps(RoutineSpan(nov14.minusDays(1), nov14.minusDays(1)))).isFalse() // day before
    }

    @Test
    fun `el rango de un borrador usa todas sus actividades`() { // D-55
        val draft = RoutineDraft(
            id = "d",
            name = "Pastillas",
            date = null,
            notes = null,
            items = listOf(0, 4, 2).mapIndexed { i, offset ->
                DraftItem(i, LocalTime.NOON, offset, "T$i", null, null, "general", null)
            },
            origin = DraftOrigin.IMPORT,
        )

        assertThat(draft.spanOn(nov14)).isEqualTo(RoutineSpan.of(nov14, 4))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un fin anterior al inicio es un error de programacion`() {
        RoutineSpan(nov14, nov14.minusDays(1))
    }
}
