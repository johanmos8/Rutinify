package com.mirkwood.rutinify.data.alarm

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.data.receiver.AlarmReceiver
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** T-15: fire PendingIntent identity (design §6.1). */
@RunWith(AndroidJUnit4::class)
class PendingIntentFactoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val factory = PendingIntentFactory(context)

    @Test
    fun `el requestCode es el id de la alarma`() { // design §6.1
        val pi = factory.fire(42L)

        assertThat(shadowOf(pi).requestCode).isEqualTo(42)
    }

    @Test
    fun `el intent de disparo va al AlarmReceiver con accion y data propias`() { // design §6.1
        val intent = shadowOf(factory.fire(7L)).savedIntent

        assertThat(intent.component).isEqualTo(ComponentName(context, AlarmReceiver::class.java))
        assertThat(intent.action).isEqualTo(PendingIntentFactory.ACTION_FIRE)
        assertThat(intent.data).isEqualTo(Uri.parse("rutinify://alarm/7"))
    }

    @Test
    fun `el PendingIntent es inmutable y de broadcast`() { // design §6.1
        val shadow = shadowOf(factory.fire(7L))

        assertThat(shadow.isBroadcast).isTrue()
        assertThat(shadow.isImmutable).isTrue()
        assertThat(shadow.flags and PendingIntent.FLAG_UPDATE_CURRENT).isNotEqualTo(0)
    }

    @Test
    fun `crear dos veces el mismo id devuelve un PendingIntent equivalente`() { // R-12.6
        assertThat(factory.fire(5L)).isEqualTo(factory.fire(5L))
    }

    @Test
    fun `ids distintos dan PendingIntents distintos`() { // design §6.1
        assertThat(factory.fire(5L)).isNotEqualTo(factory.fire(6L))
    }

    @Test
    fun `sin crear antes no existe PendingIntent`() { // design §6.1 (FLAG_NO_CREATE)
        assertThat(factory.existingFire(99L)).isNull()
    }

    @Test
    fun `existingFire encuentra el PendingIntent ya creado`() { // design §6.1
        val created = factory.fire(8L)

        assertThat(factory.existingFire(8L)).isEqualTo(created)
    }

    @Test
    fun `rechaza ids fuera del rango de requestCode`() { // design §6.1: 1..Int.MAX_VALUE
        assertThrows(IllegalArgumentException::class.java) { factory.fire(0L) }
        assertThrows(IllegalArgumentException::class.java) { factory.fire(Int.MAX_VALUE + 1L) }
    }

    @Test
    fun `lee el id de alarma del intent de disparo`() { // design §6.1, used by AlarmReceiver (T-18)
        val intent = shadowOf(factory.fire(123L)).savedIntent

        assertThat(PendingIntentFactory.alarmIdOf(intent)).isEqualTo(123L)
    }

    @Test
    fun `un intent ajeno no tiene id de alarma`() {
        assertThat(PendingIntentFactory.alarmIdOf(Intent())).isNull()
        assertThat(PendingIntentFactory.alarmIdOf(Intent().setData(Uri.parse("rutinify://alarm/abc")))).isNull()
        assertThat(PendingIntentFactory.alarmIdOf(Intent().setData(Uri.parse("https://alarm/5")))).isNull()
        assertThat(PendingIntentFactory.alarmIdOf(Intent().setData(Uri.parse("rutinify://alarm/0")))).isNull()
    }
}
