package com.mirkwood.rutinify.ui.navigation

import android.net.Uri
import android.content.Intent
import androidx.core.net.toUri
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** T-40: which intents reaching `MainActivity` are shared text (R-14.1, R-14.4). */
@RunWith(RobolectricTestRunner::class)
class IncomingIntentsTest {

    private fun share(text: String?, type: String = "text/plain") = Intent(Intent.ACTION_SEND).apply {
        setType(type)
        text?.let { putExtra(Intent.EXTRA_TEXT, it) }
    }

    @Test
    fun `un envio de texto trae su texto`() { // R-14.1
        assertThat(IncomingIntents.classify(share("{\"schemaVersion\": 1}")))
            .isEqualTo(IncomingIntent.SharedText("{\"schemaVersion\": 1}"))
    }

    @Test
    fun `un envio sin texto es un envio vacio`() { // R-14.4
        assertThat(IncomingIntents.classify(share(null))).isEqualTo(IncomingIntent.SharedText(null))
    }

    @Test
    fun `un envio reabierto desde recientes no se vuelve a importar`() { // R-14.2
        val intent = share("{}").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        assertThat(IncomingIntents.classify(intent)).isEqualTo(IncomingIntent.Other)
    }

    @Test
    fun `abrir la app o tocar una notificacion no es un envio`() { // R-7.6
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val notification = Intent(Intent.ACTION_VIEW, "rutinify://order/3?alarm=7".toUri())

        assertThat(IncomingIntents.classify(launcher)).isEqualTo(IncomingIntent.Other)
        assertThat(IncomingIntents.classify(notification)).isEqualTo(IncomingIntent.Other)
    }

    @Test
    fun `un enlace rutinify import es un plan para importar`() { // R-19.7
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("rutinify://import?v=1&d=abc"))

        assertThat(IncomingIntents.classify(intent)).isEqualTo(IncomingIntent.ImportLink("rutinify://import?v=1&d=abc"))
    }
}
