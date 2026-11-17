package com.mirkwood.rutinify.data.receiver

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.data.alarm.PendingIntentFactory
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** T-18: each broadcast maps to the right command with the id from its data URI (design §6.1, §7). */
@RunWith(AndroidJUnit4::class)
class ReceiverCommandTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val factory = PendingIntentFactory(context)

    @Test
    fun `el intent de disparo es Fire con su id`() { // R-7.1
        val intent = shadowOf(factory.fire(42L)).savedIntent

        assertThat(ReceiverCommand.from(intent)).isEqualTo(ReceiverCommand.Fire(42L))
    }

    @Test
    fun `la accion Hecho es Done con su id`() { // R-7.4
        val intent = shadowOf(factory.done(42L)).savedIntent

        assertThat(ReceiverCommand.from(intent)).isEqualTo(ReceiverCommand.Done(42L))
    }

    @Test
    fun `la accion mas 5 min es Snooze con su id`() { // R-7.5
        val intent = shadowOf(factory.snooze(42L)).savedIntent

        assertThat(ReceiverCommand.from(intent)).isEqualTo(ReceiverCommand.Snooze(42L))
    }

    @Test
    fun `una accion desconocida no es ningun comando`() {
        val intent = Intent("com.example.OTHER").setData(Uri.parse("rutinify://alarm/42"))

        assertThat(ReceiverCommand.from(intent)).isNull()
    }

    @Test
    fun `sin un id valido en el data URI no es ningun comando`() {
        val noData = Intent(PendingIntentFactory.ACTION_FIRE)
        val badId = Intent(PendingIntentFactory.ACTION_FIRE).setData(Uri.parse("rutinify://alarm/abc"))
        val otherHost = Intent(PendingIntentFactory.ACTION_DONE).setData(Uri.parse("rutinify://order/42"))

        assertThat(ReceiverCommand.from(noData)).isNull()
        assertThat(ReceiverCommand.from(badId)).isNull()
        assertThat(ReceiverCommand.from(otherHost)).isNull()
    }

    @Test
    fun `los receivers estan declarados y no exportados`() { // design §7.1
        for (receiver in listOf(AlarmReceiver::class.java, NotificationActionReceiver::class.java)) {
            val info = context.packageManager.getReceiverInfo(ComponentName(context, receiver), PackageManager.GET_META_DATA)

            assertThat(info.exported).isFalse()
        }
    }

    @Test
    fun `la accion Silenciar y el corte son Silence con su id`() { // R-15.9, R-15.10
        val intent = shadowOf(factory.silence(42L)).savedIntent

        assertThat(ReceiverCommand.from(intent)).isEqualTo(ReceiverCommand.Silence(42L))
    }
}
