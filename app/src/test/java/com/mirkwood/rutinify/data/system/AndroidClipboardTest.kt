package com.mirkwood.rutinify.data.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.data.importer.RawPromptTemplateSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/** T-21: real clipboard and template source (R-1.2, R-1.3, R-2.2, R-2.3). */
@RunWith(AndroidJUnit4::class)
class AndroidClipboardTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(ClipboardManager::class.java)
    private val clipboard = AndroidClipboard(context)

    @Test
    fun `lo copiado se puede volver a leer`() { // R-1.2, R-2.2
        clipboard.copyText("{\"schemaVersion\":1}")

        assertThat(clipboard.readText()).isEqualTo("{\"schemaVersion\":1}")
    }

    @Test
    fun `un portapapeles vacio o en blanco se lee como null`() { // R-2.3
        manager.clearPrimaryClip()
        assertThat(clipboard.readText()).isNull()

        manager.setPrimaryClip(ClipData.newPlainText("x", "   "))
        assertThat(clipboard.readText()).isNull()
    }

    @Test
    fun `un portapapeles sin texto se lee como null`() { // R-2.3
        manager.setPrimaryClip(ClipData.newRawUri("x", Uri.parse("content://nada/1")))

        assertThat(clipboard.readText()).isNull()
    }

    @Test
    fun `la plantilla se carga del recurso raw`() = runTest { // R-1.3
        val template = RawPromptTemplateSource(context, Dispatchers.Unconfined).load()

        assertThat(template).contains("[PEGA AQUÍ TU RUTINA]")
    }
}
