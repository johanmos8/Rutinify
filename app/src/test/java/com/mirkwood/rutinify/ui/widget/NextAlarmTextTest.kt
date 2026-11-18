package com.mirkwood.rutinify.ui.widget

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.NextAlarm
import com.mirkwood.rutinify.testutil.en
import com.mirkwood.rutinify.testutil.es
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** T-63: the widget's time line (R-21.1). */
class NextAlarmTextTest {

    private val bogota = ZoneId.of("America/Bogota")
    private val today = LocalDate.of(2026, 11, 14)

    private fun at(instant: String) = NextAlarm(1, 1, "Toma", "Antibiótico", Instant.parse(instant))

    @Test
    fun `hoy solo la hora`() { // R-21.1
        assertThat(NextAlarmText.time(at("2026-11-14T19:00:00Z"), today, bogota).es()).isEqualTo("14:00")
    }

    @Test
    fun `manana y otros dias llevan el dia`() { // R-21.1
        assertThat(NextAlarmText.time(at("2026-11-15T07:00:00Z"), today, bogota).es()).isEqualTo("02:00 · mañana")
        assertThat(NextAlarmText.time(at("2026-11-16T11:00:00Z"), today, bogota).es()).isEqualTo("06:00 · lunes 16 de noviembre")
        assertThat(NextAlarmText.time(at("2026-11-15T07:00:00Z"), today, bogota).en()).isEqualTo("02:00 · tomorrow")
    }
}
