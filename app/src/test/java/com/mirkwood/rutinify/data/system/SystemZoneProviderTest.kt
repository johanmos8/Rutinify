package com.mirkwood.rutinify.data.system

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import java.time.ZoneId
import java.util.TimeZone

class SystemZoneProviderTest {

    private val original: TimeZone = TimeZone.getDefault()

    @After
    fun restore() {
        TimeZone.setDefault(original)
    }

    // R-12.2: the zone is read on every use, never cached
    @Test
    fun `devuelve la zona del sistema vigente en cada llamada`() {
        val provider = SystemZoneProvider()

        TimeZone.setDefault(TimeZone.getTimeZone("America/Bogota"))
        assertThat(provider.zone()).isEqualTo(ZoneId.of("America/Bogota"))

        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Madrid"))
        assertThat(provider.zone()).isEqualTo(ZoneId.of("Europe/Madrid"))
    }
}
