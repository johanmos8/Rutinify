package com.mirkwood.rutinify.ui.common

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.Recurrence
import com.mirkwood.rutinify.testutil.en
import com.mirkwood.rutinify.testutil.es
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** T-68: how a recurring plan is described (R-23.10). */
class RecurrenceLabelsTest {

    private val start = LocalDate.of(2026, 11, 16)
    private val mwf = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)

    @Test
    fun `dias en orden desde la fecha de inicio`() { // R-23.10
        assertThat(RecurrenceLabels.label(Recurrence(mwf, null), start).es())
            .isEqualTo("Se repite lunes, miércoles y viernes, desde el 16 de noviembre")
        assertThat(RecurrenceLabels.label(Recurrence(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), LocalDate.of(2026, 12, 20)), start).es())
            .isEqualTo("Se repite sábado y domingo, desde el 16 de noviembre hasta el 20 de diciembre")
    }

    @Test
    fun `todos los dias y en ingles`() { // R-23.10, R-13.3
        assertThat(RecurrenceLabels.label(Recurrence(DayOfWeek.entries.toSet(), null), start).es())
            .isEqualTo("Se repite todos los días, desde el 16 de noviembre")
        assertThat(RecurrenceLabels.label(Recurrence(mwf, null), start).en())
            .isEqualTo("Repeats Monday, Wednesday and Friday, from November 16")
    }
}
