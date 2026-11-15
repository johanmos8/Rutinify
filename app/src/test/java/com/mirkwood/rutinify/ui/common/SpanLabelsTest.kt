package com.mirkwood.rutinify.ui.common

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.domain.model.RoutineSpan
import org.junit.Test
import java.time.LocalDate

/** T-34, T-35: days of an order in the conflict dialog (R-6.7) and the Review summary (R-4.14). */
class SpanLabelsTest {

    private val nov14 = LocalDate.of(2026, 11, 14)

    @Test
    fun `una rutina de un dia muestra solo su fecha aunque tenga madrugada`() { // R-6.7, D-58
        assertThat(SpanLabels.short(RoutineSpan.of(nov14, 1))).isEqualTo(UiText.Plain("14 de noviembre"))
    }

    @Test
    fun `en el mismo mes el mes solo aparece al final`() { // R-6.7
        assertThat(SpanLabels.short(RoutineSpan.of(nov14, 6)))
            .isEqualTo(UiText.Res(R.string.date_range, listOf("14", "20 de noviembre")))
    }

    @Test
    fun `entre dos meses se nombran ambos`() { // R-6.7
        assertThat(SpanLabels.short(RoutineSpan.of(LocalDate.of(2026, 11, 28), 6)))
            .isEqualTo(UiText.Res(R.string.date_range, listOf("28 de noviembre", "4 de diciembre")))
    }

    @Test
    fun `entre dos anos se nombran ambos anos`() { // R-6.7
        assertThat(SpanLabels.short(RoutineSpan.of(LocalDate.of(2026, 12, 29), 5)))
            .isEqualTo(UiText.Res(R.string.date_range, listOf("29 de diciembre de 2026", "3 de enero de 2027")))
    }

    // ---- Review summary with weekdays (T-35) ----

    @Test
    fun `con dia de la semana en el mismo mes`() { // R-4.14
        assertThat(SpanLabels.withWeekdays(nov14, nov14.plusDays(6)))
            .isEqualTo(UiText.Res(R.string.date_range, listOf("sábado 14", "viernes 20 de noviembre")))
    }

    @Test
    fun `con dia de la semana entre meses y entre anos`() { // R-4.14
        assertThat(SpanLabels.withWeekdays(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 2)))
            .isEqualTo(UiText.Res(R.string.date_range, listOf("lunes 30 de noviembre", "miércoles 2 de diciembre")))
        assertThat(SpanLabels.withWeekdays(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 2)))
            .isEqualTo(UiText.Res(R.string.date_range, listOf("jueves 31 de diciembre de 2026", "sábado 2 de enero de 2027")))
    }

    @Test
    fun `un solo dia se nombra con el`() { // R-4.14, D-59
        assertThat(SpanLabels.withWeekdays(nov14, nov14))
            .isEqualTo(UiText.Res(R.string.date_single, listOf("sábado 14 de noviembre")))
    }
}
