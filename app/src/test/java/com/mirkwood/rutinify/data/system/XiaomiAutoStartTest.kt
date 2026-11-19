package com.mirkwood.rutinify.data.system

import android.app.AppOpsManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.scheduler.AutoStartStatus
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowBuild

/** D-102: reading Xiaomi's "Inicio automático" never guesses (R-11.10). */
@RunWith(AndroidJUnit4::class)
class XiaomiAutoStartTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `reconoce Xiaomi Redmi y POCO por fabricante o marca`() { // R-11.10
        assertThat(XiaomiAutoStart.isXiaomi("Xiaomi", "Redmi")).isTrue()
        assertThat(XiaomiAutoStart.isXiaomi("Xiaomi", "POCO")).isTrue()
        assertThat(XiaomiAutoStart.isXiaomi("unknown", "poco")).isTrue()
        assertThat(XiaomiAutoStart.isXiaomi("Google", "google")).isFalse()
        assertThat(XiaomiAutoStart.isXiaomi("samsung", "samsung")).isFalse()
    }

    @Test
    fun `fuera de Xiaomi no aplica`() { // R-11.10
        ShadowBuild.setManufacturer("Google")
        ShadowBuild.setBrand("google")

        assertThat(XiaomiAutoStart.status(app)).isEqualTo(AutoStartStatus.NOT_APPLICABLE)
    }

    @Test
    fun `traduce el modo del permiso sin adivinar`() { // R-11.10
        assertThat(XiaomiAutoStart.fromMode(AppOpsManager.MODE_ALLOWED)).isEqualTo(AutoStartStatus.ALLOWED)
        assertThat(XiaomiAutoStart.fromMode(AppOpsManager.MODE_IGNORED)).isEqualTo(AutoStartStatus.DENIED)
        assertThat(XiaomiAutoStart.fromMode(AppOpsManager.MODE_ERRORED)).isEqualTo(AutoStartStatus.DENIED)
        assertThat(XiaomiAutoStart.fromMode(AppOpsManager.MODE_DEFAULT)).isEqualTo(AutoStartStatus.UNKNOWN)
        assertThat(XiaomiAutoStart.fromMode(null)).isEqualTo(AutoStartStatus.UNKNOWN)
    }

    @Test
    fun `en un Xiaomi donde no se puede leer pide comprobarlo`() { // R-11.10: the emulator has no MIUI op
        ShadowBuild.setManufacturer("Xiaomi")
        ShadowBuild.setBrand("Redmi")

        assertThat(XiaomiAutoStart.status(app)).isNotEqualTo(AutoStartStatus.NOT_APPLICABLE)
    }
}
