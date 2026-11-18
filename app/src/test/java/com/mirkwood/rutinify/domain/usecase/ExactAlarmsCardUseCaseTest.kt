package com.mirkwood.rutinify.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.testutil.FakePermissionChecker
import com.mirkwood.rutinify.testutil.FakePermissionPromptStore
import org.junit.Test

/** T-48: the exact-alarm card of Home (R-11.2, D-69 option C, D-89). */
class ExactAlarmsCardUseCaseTest {

    private val checker = FakePermissionChecker(exactAlarms = false)
    private val prompts = FakePermissionPromptStore()
    private val card = ExactAlarmsCardUseCase(checker, prompts)

    @Test
    fun `se muestra con algun plan y sin alarmas exactas`() { // R-11.2
        assertThat(card.shouldShow(hasPlans = true)).isTrue()
        assertThat(card.shouldShow(hasPlans = false)).isFalse()
    }

    @Test
    fun `no se muestra con el permiso concedido`() { // R-11.2
        checker.exactAlarms = true

        assertThat(card.shouldShow(hasPlans = true)).isFalse()
    }

    @Test
    fun `tras Ahora no ya no se muestra`() { // D-89
        card.dismiss()

        assertThat(card.shouldShow(hasPlans = true)).isFalse()
        assertThat(prompts.cardDismissed).isTrue()
    }
}
