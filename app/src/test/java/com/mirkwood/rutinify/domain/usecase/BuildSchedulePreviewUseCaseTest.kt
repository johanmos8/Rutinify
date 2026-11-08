package com.mirkwood.rutinify.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.DraftItem
import com.mirkwood.rutinify.domain.model.DraftOrigin
import com.mirkwood.rutinify.domain.model.RoutineDraft
import com.mirkwood.rutinify.domain.time.TriggerTimeCalculator
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** T-14: Review preview — trigger times, past and excluded items, summary (R-4.1, R-4.6–R-4.11). */
class BuildSchedulePreviewUseCaseTest {

    private val bogota = ZoneId.of("America/Bogota")
    private val today = LocalDate.of(2026, 11, 5)
    private val buildPreview = BuildSchedulePreviewUseCase(TriggerTimeCalculator())

    /** [today] at the given Bogotá wall-clock time (midnight in Bogotá = 05:00Z). */
    private fun nowAt(hour: Int, minute: Int = 0): Instant =
        Instant.parse("2026-11-05T05:00:00Z").plusSeconds(hour * 3_600L + minute * 60L)

    /** Puppy routine: 06:45, 09:30, 13:00, 22:30 and 02:00 (+1 day). Keys 0..4. */
    private val draft = RoutineDraft(
        id = "d",
        name = "Cachorro",
        date = null,
        notes = null,
        items = listOf("06:45" to 0, "09:30" to 0, "13:00" to 0, "22:30" to 0, "02:00" to 1).mapIndexed { i, (t, offset) ->
            DraftItem(i, LocalTime.parse(t), offset, "A$i", null, null, "general", null)
        },
        origin = DraftOrigin.IMPORT,
    )

    private fun preview(date: LocalDate = today, excluded: Set<Int> = emptySet(), now: Instant = nowAt(5)) =
        buildPreview(draft, date, excluded, now, bogota)

    @Test
    fun `calcula la hora de disparo de cada actividad para la fecha elegida`() { // R-4.6
        val triggers = preview().items.map { it.triggerAt }

        assertThat(triggers.first()).isEqualTo(Instant.parse("2026-11-05T11:45:00Z"))
        assertThat(triggers.last()).isEqualTo(Instant.parse("2026-11-06T07:00:00Z"))
    }

    @Test
    fun `mantiene el orden del borrador por dayOffset y hora`() { // R-4.1
        assertThat(preview().items.map { it.item.key }).containsExactly(0, 1, 2, 3, 4).inOrder()
    }

    @Test
    fun `antes de la primera actividad todo es futuro e incluido`() { // R-4.7
        val result = preview(now = nowAt(5))

        assertThat(result.items.none { it.isPast }).isTrue()
        assertThat(result.items.all { it.isIncluded }).isTrue()
        assertThat(result.canSchedule).isTrue()
    }

    @Test
    fun `las actividades pasadas se marcan y se excluyen`() { // R-4.7
        val result = preview(now = nowAt(12))

        assertThat(result.items.map { it.isPast }).containsExactly(true, true, false, false, false).inOrder()
        assertThat(result.items.map { it.isIncluded }).containsExactly(false, false, true, true, true).inOrder()
    }

    @Test
    fun `una actividad justo a la hora actual ya paso`() { // R-4.7 (<= now)
        val item = preview(now = nowAt(9, 30)).items[1]

        assertThat(item.isPast).isTrue()
        assertThat(item.isIncluded).isFalse()
    }

    @Test
    fun `una actividad futura se puede excluir sin quitarla de la lista`() { // R-4.8
        val result = preview(excluded = setOf(2))

        assertThat(result.items).hasSize(5)
        assertThat(result.items[2].isIncluded).isFalse()
        assertThat(result.items[2].isPast).isFalse()
        assertThat(result.summary?.count).isEqualTo(4)
    }

    @Test
    fun `excluir una actividad pasada no cambia nada`() { // R-4.8
        assertThat(preview(excluded = setOf(0), now = nowAt(12))).isEqualTo(preview(now = nowAt(12)))
    }

    @Test
    fun `el resumen cuenta las incluidas y da el rango con el dia siguiente`() { // R-4.9
        val summary = preview().summary

        assertThat(summary).isEqualTo(
            ScheduleSummary(
                count = 5,
                first = LocalTime.of(6, 45),
                firstDayOffset = 0,
                last = LocalTime.of(2, 0),
                lastDayOffset = 1,
            ),
        )
    }

    @Test
    fun `el resumen empieza en la primera actividad incluida`() { // R-4.9
        val summary = preview(excluded = setOf(4), now = nowAt(12)).summary

        assertThat(summary).isEqualTo(ScheduleSummary(2, LocalTime.of(13, 0), 0, LocalTime.of(22, 30), 0))
    }

    @Test
    fun `sin actividades futuras incluidas no se puede programar`() { // R-4.10
        val result = preview(excluded = setOf(2, 3, 4), now = nowAt(12))

        assertThat(result.canSchedule).isFalse()
        assertThat(result.summary).isNull()
    }

    @Test
    fun `cambiar la fecha recalcula pasadas e incluidas`() { // R-4.6
        val now = nowAt(12)

        assertThat(preview(date = today, now = now).items.count { it.isPast }).isEqualTo(2)
        assertThat(preview(date = today.plusDays(1), now = now).items.count { it.isPast }).isEqualTo(0)
    }

    @Test
    fun `con fecha pasada y todo pasado se sugiere manana`() { // R-4.11
        val result = preview(date = LocalDate.of(2026, 11, 1), now = nowAt(12))

        assertThat(result.canSchedule).isFalse()
        assertThat(result.suggestTomorrow).isTrue()
    }

    @Test
    fun `con fecha pasada pero alguna actividad futura no se sugiere manana`() { // R-4.11
        // Chosen date yesterday: its 02:00 (+1 day) activity is today at 02:00, still ahead at 01:00
        val result = preview(date = today.minusDays(1), now = nowAt(1))

        assertThat(result.canSchedule).isTrue()
        assertThat(result.suggestTomorrow).isFalse()
    }

    @Test
    fun `con la fecha de hoy no se sugiere manana`() { // R-4.11 applies only to past dates
        val result = buildPreview(draft, today, emptySet(), nowAt(23), bogota)

        assertThat(result.suggestTomorrow).isFalse()
    }

    @Test
    fun `excluir todas las futuras de una fecha pasada no sugiere manana`() { // R-4.11: only when all are past
        val result = preview(date = today.minusDays(1), excluded = setOf(4), now = nowAt(1))

        assertThat(result.canSchedule).isFalse()
        assertThat(result.suggestTomorrow).isFalse()
    }
}
