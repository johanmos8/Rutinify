package com.mirkwood.rutinify.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.scheduler.PermissionStatus
import com.mirkwood.rutinify.testutil.FakePermissionChecker
import org.junit.Test

/** T-29: permission snapshot (R-11.4). */
class GetPermissionStatusUseCaseTest {

    private val checker = FakePermissionChecker()
    private val getStatus = GetPermissionStatusUseCase(checker)

    @Test
    fun `con ambos permisos todo esta concedido`() { // R-11.4
        assertThat(getStatus()).isEqualTo(PermissionStatus(notifications = true, exactAlarms = true))
        assertThat(getStatus().allGranted).isTrue()
    }

    @Test
    fun `basta con que falte uno para que no este todo concedido`() { // R-11.3
        checker.notifications = false
        assertThat(getStatus().allGranted).isFalse()

        checker.notifications = true
        checker.exactAlarms = false
        assertThat(getStatus().allGranted).isFalse()
    }

    @Test
    fun `refleja los cambios en cada llamada`() { // R-11.5
        checker.exactAlarms = false
        assertThat(getStatus().exactAlarms).isFalse()

        checker.exactAlarms = true
        assertThat(getStatus().exactAlarms).isTrue()
    }

    @Test
    fun `incluye la pantalla completa sin contarla para el banner`() { // R-15.11, D-88
        checker.fullScreenIntent = false

        assertThat(getStatus().fullScreenIntent).isFalse()
        assertThat(getStatus().allGranted).isTrue()
    }
}
