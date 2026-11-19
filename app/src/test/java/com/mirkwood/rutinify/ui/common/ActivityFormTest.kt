package com.mirkwood.rutinify.ui.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalTime

/** D-101: the edit form's time travels through a picker, so it always comes back well formed. */
class ActivityFormTest {

    private fun form(time: String) = ActivityForm(time = time, title = "Paseo", category = "paseo")

    @Test
    fun `el selector abre en la hora escrita`() { // R-4.2
        assertThat(form("06:45").pickerTime).isEqualTo(LocalTime.of(6, 45))
        assertThat(form("21:05").pickerTime).isEqualTo(LocalTime.of(21, 5))
    }

    @Test
    fun `con una hora invalida el selector abre a mediodia`() { // R-3.8
        assertThat(form("25:00").pickerTime).isEqualTo(LocalTime.NOON)
        assertThat(form("").pickerTime).isEqualTo(LocalTime.NOON)
    }

    @Test
    fun `la hora elegida vuelve con el formato HH_mm`() { // R-3.8
        assertThat(ActivityForm.timeText(LocalTime.of(6, 5))).isEqualTo("06:05")
        assertThat(ActivityForm.timeText(LocalTime.of(0, 0))).isEqualTo("00:00")
    }
}
