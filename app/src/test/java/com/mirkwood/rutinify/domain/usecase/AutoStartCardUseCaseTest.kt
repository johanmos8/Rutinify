package com.mirkwood.rutinify.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.scheduler.AutoStartStatus
import com.mirkwood.rutinify.testutil.FakePermissionChecker
import com.mirkwood.rutinify.testutil.FakePermissionPromptStore
import org.junit.Test

/** D-102: the Home card about Xiaomi's "Inicio automático" (R-11.9). */
class AutoStartCardUseCaseTest {

    private val checker = FakePermissionChecker()
    private val prompts = FakePermissionPromptStore()
    private val card = AutoStartCardUseCase(checker, prompts)

    @Test
    fun `fuera de Xiaomi no hay tarjeta`() { // R-11.9
        checker.autoStart = AutoStartStatus.NOT_APPLICABLE

        assertThat(card.current(hasPlans = true)).isNull()
    }

    @Test
    fun `sin planes no hay tarjeta`() { // R-11.9: only after the first plan, like the exact-alarm card
        checker.autoStart = AutoStartStatus.DENIED

        assertThat(card.current(hasPlans = false)).isNull()
    }

    @Test
    fun `si se detecta desactivado la tarjeta lo dice`() { // R-11.9
        checker.autoStart = AutoStartStatus.DENIED

        assertThat(card.current(hasPlans = true)).isEqualTo(AutoStartStatus.DENIED)
    }

    @Test
    fun `si no se puede saber la tarjeta pide comprobarlo`() { // R-11.9
        checker.autoStart = AutoStartStatus.UNKNOWN

        assertThat(card.current(hasPlans = true)).isEqualTo(AutoStartStatus.UNKNOWN)
    }

    @Test
    fun `activado no muestra tarjeta`() { // R-11.9
        checker.autoStart = AutoStartStatus.ALLOWED

        assertThat(card.current(hasPlans = true)).isNull()
    }

    @Test
    fun `descartada no vuelve`() { // R-11.9
        checker.autoStart = AutoStartStatus.UNKNOWN

        card.dismiss()

        assertThat(prompts.autoStartCardDismissed()).isTrue()
        assertThat(card.current(hasPlans = true)).isNull()
    }
}
