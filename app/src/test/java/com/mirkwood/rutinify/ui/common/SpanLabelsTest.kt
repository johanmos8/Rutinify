package com.mirkwood.rutinify.ui.common

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.RoutineSpan
import com.mirkwood.rutinify.testutil.en
import com.mirkwood.rutinify.testutil.es
import org.junit.Test
import java.time.LocalDate

/**
 * T-34–T-36, T-49: days of an order in the conflict dialog (R-6.7), the Review summary (R-4.14) and Home (R-8.2), in
 * Spanish and in English (R-13.3, D-71).
 */
class SpanLabelsTest {

    private val nov14 = LocalDate.of(2026, 11, 14)

    @Test
    fun `una rutina de un dia muestra solo su fecha aunque tenga madrugada`() { // R-6.7, D-58
        assertThat(SpanLabels.short(RoutineSpan.of(nov14, 1)).es()).isEqualTo("14 de noviembre")
    }

    @Test
    fun `en el mismo mes el mes solo aparece al final`() { // R-6.7
        assertThat(SpanLabels.short(RoutineSpan.of(nov14, 6)).es()).isEqualTo("del 14 al 20 de noviembre")
    }

    @Test
    fun `entre dos meses se nombran ambos`() { // R-6.7
        assertThat(SpanLabels.short(RoutineSpan.of(LocalDate.of(2026, 11, 28), 6)).es())
            .isEqualTo("del 28 de noviembre al 4 de diciembre")
    }

    @Test
    fun `entre dos anos se nombran ambos anos`() { // R-6.7
        assertThat(SpanLabels.short(RoutineSpan.of(LocalDate.of(2026, 12, 29), 5)).es())
            .isEqualTo("del 29 de diciembre de 2026 al 3 de enero de 2027")
    }

    // ---- Review summary with weekdays (T-35) ----

    @Test
    fun `con dia de la semana en el mismo mes`() { // R-4.14
        assertThat(SpanLabels.withWeekdays(nov14, nov14.plusDays(6)).es()).isEqualTo("del sábado 14 al viernes 20 de noviembre")
    }

    @Test
    fun `con dia de la semana entre meses y entre anos`() { // R-4.14
        assertThat(SpanLabels.withWeekdays(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 2)).es())
            .isEqualTo("del lunes 30 de noviembre al miércoles 2 de diciembre")
        assertThat(SpanLabels.withWeekdays(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 2)).es())
            .isEqualTo("del jueves 31 de diciembre de 2026 al sábado 2 de enero de 2027")
    }

    @Test
    fun `un solo dia se nombra con el`() { // R-4.14, D-59
        assertThat(SpanLabels.withWeekdays(nov14, nov14).es()).isEqualTo("el sábado 14 de noviembre")
    }

    // ---- Home (T-36) ----

    @Test
    fun `en Inicio una rutina de un dia muestra la fecha larga`() { // R-8.2
        assertThat(SpanLabels.long(RoutineSpan.of(nov14, 1)).es()).isEqualTo("sábado 14 de noviembre de 2026")
    }

    @Test
    fun `en Inicio el rango lleva el ano al final`() { // R-8.2
        assertThat(SpanLabels.long(RoutineSpan.of(nov14, 6)).es()).isEqualTo("del sábado 14 al viernes 20 de noviembre de 2026")
        assertThat(SpanLabels.long(RoutineSpan.of(LocalDate.of(2026, 11, 28), 6)).es())
            .isEqualTo("del sábado 28 de noviembre al viernes 4 de diciembre de 2026")
    }

    @Test
    fun `cabecera de dia y fecha de Revision`() { // R-4.2, R-4.13
        assertThat(SpanLabels.dayHeader(2, LocalDate.of(2026, 11, 16)).es()).isEqualTo("Día 3 · lunes 16 de noviembre")
        assertThat(SpanLabels.fullDate(nov14).es()).isEqualTo("sábado 14 de noviembre de 2026")
    }

    // ---- English (T-49) ----

    @Test
    fun `en ingles las fechas cortas llevan el mes delante`() { // R-13.3, D-71
        assertThat(SpanLabels.short(RoutineSpan.of(nov14, 1)).en()).isEqualTo("November 14")
        assertThat(SpanLabels.short(RoutineSpan.of(nov14, 6)).en()).isEqualTo("from November 14 to 20")
        assertThat(SpanLabels.short(RoutineSpan.of(LocalDate.of(2026, 11, 28), 6)).en()).isEqualTo("from November 28 to December 4")
        assertThat(SpanLabels.short(RoutineSpan.of(LocalDate.of(2026, 12, 29), 5)).en())
            .isEqualTo("from December 29, 2026 to January 3, 2027")
    }

    @Test
    fun `en ingles con dia de la semana`() { // R-13.3, D-71
        assertThat(SpanLabels.withWeekdays(nov14, nov14.plusDays(6)).en()).isEqualTo("from Saturday, November 14 to Friday 20")
        assertThat(SpanLabels.withWeekdays(nov14, nov14).en()).isEqualTo("on Saturday, November 14")
        assertThat(SpanLabels.long(RoutineSpan.of(nov14, 1)).en()).isEqualTo("Saturday, November 14, 2026")
        assertThat(SpanLabels.long(RoutineSpan.of(nov14, 6)).en()).isEqualTo("from Saturday, November 14 to Friday 20, 2026")
        assertThat(SpanLabels.dayHeader(2, LocalDate.of(2026, 11, 16)).en()).isEqualTo("Day 3 · Monday, November 16")
    }
}
