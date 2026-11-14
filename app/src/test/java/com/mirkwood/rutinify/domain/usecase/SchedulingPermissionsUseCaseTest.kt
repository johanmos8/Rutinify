package com.mirkwood.rutinify.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.testutil.FakePermissionChecker
import com.mirkwood.rutinify.testutil.FakePermissionPromptStore
import org.junit.Test

/** T-30: which permission explanations come before scheduling (R-11.1, R-11.2). */
class SchedulingPermissionsUseCaseTest {

    private val checker = FakePermissionChecker()
    private val prompts = FakePermissionPromptStore()
    private val permissions = SchedulingPermissionsUseCase(checker, prompts)

    @Test
    fun `explica las notificaciones la primera vez que se pueden pedir`() { // R-11.1
        checker.notificationsRequestable = true

        assertThat(permissions.shouldExplainNotifications()).isTrue()
    }

    @Test
    fun `no vuelve a explicarlas una vez explicadas`() { // R-11.1: "por primera vez"
        checker.notificationsRequestable = true

        permissions.markNotificationsExplained()

        assertThat(permissions.shouldExplainNotifications()).isFalse()
        assertThat(prompts.explained).isTrue()
    }

    @Test
    fun `si no se pueden pedir no las explica`() { // R-11.1: Android 12 or lower, or already granted
        checker.notificationsRequestable = false

        assertThat(permissions.shouldExplainNotifications()).isFalse()
    }

    @Test
    fun `informa si las alarmas exactas estan permitidas`() { // R-11.2
        checker.exactAlarms = false
        assertThat(permissions.exactAlarmsGranted()).isFalse()

        checker.exactAlarms = true
        assertThat(permissions.exactAlarmsGranted()).isTrue()
    }
}
