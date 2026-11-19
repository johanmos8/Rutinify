package com.mirkwood.rutinify.data.notification

import com.mirkwood.rutinify.R
import androidx.core.app.NotificationCompat
import com.mirkwood.rutinify.testutil.FakePermissionChecker
import com.mirkwood.rutinify.ui.alarm.AlarmActivity
import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.MainActivity
import com.mirkwood.rutinify.data.alarm.PendingIntentFactory
import com.mirkwood.rutinify.data.receiver.NotificationActionReceiver
import com.mirkwood.rutinify.domain.scheduler.AlarmNotification
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Instant
import java.time.LocalTime

/** T-16: channels and alarm notifications (R-5.9, R-7.1–R-7.3, R-7.8, design §8). */
@RunWith(AndroidJUnit4::class)
class AndroidNotificationPublisherTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val shadowManager = shadowOf(manager)
    private val permissions = FakePermissionChecker()
    private val publisher = AndroidNotificationPublisher(app, manager, PendingIntentFactory(app), permissions)

    private val triggerAt = Instant.parse("2026-11-05T11:45:00Z")
    private val siesta = AlarmNotification(
        alarmId = 42L,
        orderId = 3L,
        orderName = "Cachorro",
        title = "Siesta",
        description = "En su cama, luz apagada",
        time = LocalTime.of(6, 45),
        category = "siesta",
        triggerAt = triggerAt,
    )

    @Before
    fun grantNotifications() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowManager.setNotificationsEnabled(true)
    }

    private fun shown(alarmId: Long): Notification? = shadowManager.getNotification("alarm", alarmId.toInt())

    // --- Channels -------------------------------------------------------------------------------

    @Test
    fun `crea el canal de la categoria con nombre capitalizado e importancia alta`() { // R-5.9, design §8
        publisher.ensureChannels(listOf("siesta"))

        val channel = manager.getNotificationChannel("cat_siesta")
        assertThat(channel.name.toString()).isEqualTo("Siesta")
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_HIGH)
        assertThat(channel.group).isEqualTo("routines")
        assertThat(manager.getNotificationChannelGroup("routines").name.toString()).isEqualTo("Rutinas")
    }

    @Test
    fun `crear los canales dos veces deja uno por categoria`() { // R-5.9 (idempotent)
        publisher.ensureChannels(listOf("siesta", "paseo"))
        publisher.ensureChannels(listOf("paseo", "siesta"))

        assertThat(shadowManager.notificationChannels.map { it.id }).containsExactly("cat_siesta", "cat_paseo")
    }

    @Test
    fun `no toca un canal que ya existe`() { // D-15: the user's channel settings win
        manager.createNotificationChannel(NotificationChannel("cat_siesta", "Mi siesta", NotificationManager.IMPORTANCE_LOW))

        publisher.ensureChannels(listOf("siesta"))

        assertThat(manager.getNotificationChannel("cat_siesta").name.toString()).isEqualTo("Mi siesta")
    }

    @Test
    fun `las categorias con guiones se nombran con espacios`() { // design §8
        publisher.ensureChannels(listOf("paseo-largo"))

        assertThat(manager.getNotificationChannel("cat_paseo-largo").name.toString()).isEqualTo("Paseo largo")
    }

    // --- Content --------------------------------------------------------------------------------

    @Test
    fun `publica en el canal de su categoria con titulo texto y subtitulo`() { // R-7.1, R-7.2
        val published = publisher.publish(siesta)

        val notification = shown(42L)!!
        assertThat(published).isTrue()
        assertThat(notification.channelId).isEqualTo("cat_siesta")
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()).isEqualTo("Siesta")
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            .isEqualTo("En su cama, luz apagada")
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString()).isEqualTo("Cachorro")
    }

    @Test
    fun `sin descripcion el texto es la hora`() { // R-7.1
        publisher.publish(siesta.copy(description = null))

        assertThat(shown(42L)!!.extras.getCharSequence(Notification.EXTRA_TEXT).toString()).isEqualTo("06:45")
    }

    @Test
    fun `es un recordatorio con la hora de disparo y se cierra al tocarla`() { // design §8
        publisher.publish(siesta)

        val notification = shown(42L)!!
        assertThat(notification.category).isEqualTo(Notification.CATEGORY_REMINDER)
        assertThat(notification.`when`).isEqualTo(triggerAt.toEpochMilli())
        assertThat(notification.flags and Notification.FLAG_AUTO_CANCEL).isNotEqualTo(0)
    }

    @Test
    fun `crea el canal si todavia no existe al publicar`() { // R-7.2
        publisher.publish(siesta.copy(category = "comida"))

        assertThat(manager.getNotificationChannel("cat_comida")).isNotNull()
        assertThat(shown(42L)!!.channelId).isEqualTo("cat_comida")
    }

    // --- Actions --------------------------------------------------------------------------------

    @Test
    fun `incluye las acciones Hecho y mas 5 min`() { // R-7.3
        publisher.publish(siesta)

        assertThat(shown(42L)!!.actions.map { it.title.toString() }).containsExactly("Hecho", "+5 min").inOrder()
    }

    @Test
    fun `las acciones van al NotificationActionReceiver con el id de la alarma`() { // R-7.3, design §6.1
        publisher.publish(siesta)

        val (done, snooze) = shown(42L)!!.actions.map { shadowOf(it.actionIntent) }
        val receiver = ComponentName(app, NotificationActionReceiver::class.java)
        assertThat(done.isBroadcast).isTrue()
        assertThat(done.requestCode).isEqualTo(42)
        assertThat(done.savedIntent.component).isEqualTo(receiver)
        assertThat(done.savedIntent.action).isEqualTo(PendingIntentFactory.ACTION_DONE)
        assertThat(done.savedIntent.data).isEqualTo(Uri.parse("rutinify://alarm/42/done"))
        assertThat(snooze.requestCode).isEqualTo(42)
        assertThat(snooze.savedIntent.component).isEqualTo(receiver)
        assertThat(snooze.savedIntent.action).isEqualTo(PendingIntentFactory.ACTION_SNOOZE)
        assertThat(snooze.savedIntent.data).isEqualTo(Uri.parse("rutinify://alarm/42/snooze"))
    }

    @Test
    fun `las acciones llevan un id de alarma legible`() { // design §6.1, used by NotificationActionReceiver (T-18)
        publisher.publish(siesta)

        val ids = shown(42L)!!.actions.map { PendingIntentFactory.alarmIdOf(shadowOf(it.actionIntent).savedIntent) }
        assertThat(ids).containsExactly(42L, 42L)
    }

    @Test
    fun `tocar la notificacion abre el detalle de la orden con la alarma resaltada`() { // R-7.6, design §6.1
        publisher.publish(siesta)

        val content = shadowOf(shown(42L)!!.contentIntent)
        assertThat(content.isActivity).isTrue()
        assertThat(content.requestCode).isEqualTo(42)
        assertThat(content.savedIntent.component).isEqualTo(ComponentName(app, MainActivity::class.java))
        assertThat(content.savedIntent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(content.savedIntent.data).isEqualTo(Uri.parse("rutinify://order/3?alarm=42"))
    }

    // --- Permission -----------------------------------------------------------------------------

    @Test
    fun `con las notificaciones desactivadas no publica ni lanza`() { // R-7.8
        shadowManager.setNotificationsEnabled(false)

        val published = publisher.publish(siesta)

        assertThat(published).isFalse()
        assertThat(shadowManager.size()).isEqualTo(0)
    }

    @Test
    fun `sin permiso POST_NOTIFICATIONS no publica ni lanza`() { // R-7.8
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val published = publisher.publish(siesta)

        assertThat(published).isFalse()
        assertThat(shadowManager.size()).isEqualTo(0)
    }

    // --- Cancel ---------------------------------------------------------------------------------

    @Test
    fun `cancel quita solo la notificacion de esa alarma`() { // R-7.4, R-9.3
        publisher.publish(siesta)
        publisher.publish(siesta.copy(alarmId = 43L))

        publisher.cancel(42L)

        assertThat(shown(42L)).isNull()
        assertThat(shown(43L)).isNotNull()
    }

    @Test
    fun `cancel sin notificacion no falla`() {
        publisher.cancel(99L)

        assertThat(shadowManager.size()).isEqualTo(0)
    }

    @Test
    fun `cancelForOrder quita las notificaciones de la orden y deja las demas`() { // R-10.2
        publisher.publish(siesta)
        publisher.publish(siesta.copy(alarmId = 43L))
        publisher.publish(siesta.copy(alarmId = 50L, orderId = 4L))

        publisher.cancelForOrder(3L)

        assertThat(shown(42L)).isNull()
        assertThat(shown(43L)).isNull()
        assertThat(shown(50L)).isNotNull()
    }

    @Test
    fun `una alarma critica suena en el canal Alarmas criticas con sonido de alarma`() { // R-15.6, D-67
        assertThat(publisher.publish(siesta.copy(critical = true))).isTrue()

        val notification = requireNotNull(shown(42L))
        assertThat(notification.channelId).isEqualTo("alarm_critical")
        assertThat(notification.category).isEqualTo(Notification.CATEGORY_ALARM)
        assertThat(notification.flags and Notification.FLAG_INSISTENT).isNotEqualTo(0)
        val channel = manager.getNotificationChannel("alarm_critical")
        assertThat(channel.name.toString()).isEqualTo("Alarmas críticas")
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_HIGH)
        assertThat(channel.audioAttributes.usage).isEqualTo(AudioAttributes.USAGE_ALARM)
        assertThat(channel.sound).isNotNull()
        // The category channel is not needed for a critical alarm
        assertThat(manager.getNotificationChannel("cat_siesta")).isNull()
    }

    @Test
    fun `al publicar una critica tambien crea el canal silenciado sin sonido`() { // D-67
        publisher.publish(siesta.copy(critical = true))

        val silent = manager.getNotificationChannel("alarm_critical_silent")
        assertThat(silent.name.toString()).isEqualTo("Alarmas críticas silenciadas")
        assertThat(silent.sound).isNull()
        assertThat(silent.shouldVibrate()).isFalse()
    }

    @Test
    fun `una critica conserva el contenido y las acciones de siempre`() { // R-15.8, R-7.1, R-7.3
        publisher.publish(siesta.copy(critical = true))

        val notification = requireNotNull(shown(42L))
        assertThat(notification.extras.getString(Notification.EXTRA_TITLE)).isEqualTo("Siesta")
        // "Silenciar" comes after the usual two (T-46)
        assertThat(notification.actions.map { it.title.toString() }.take(2)).containsExactly("Hecho", "+5 min").inOrder()
    }

    @Test
    fun `una alarma no critica sigue igual que antes`() { // R-15.14
        publisher.publish(siesta)

        val notification = requireNotNull(shown(42L))
        assertThat(notification.channelId).isEqualTo("cat_siesta")
        assertThat(notification.category).isEqualTo(Notification.CATEGORY_REMINDER)
        assertThat(notification.flags and Notification.FLAG_INSISTENT).isEqualTo(0)
        assertThat(manager.getNotificationChannel("alarm_critical")).isNull()
    }

    @Test
    fun `una critica que suena ofrece Silenciar y abre la pantalla de alarma`() { // R-15.7, R-15.9
        publisher.publish(siesta.copy(critical = true))

        val notification = requireNotNull(shown(42L))
        assertThat(notification.actions.map { it.title.toString() }).containsExactly("Hecho", "+5 min", "Silenciar").inOrder()
        val screen = shadowOf(requireNotNull(notification.fullScreenIntent)).savedIntent
        assertThat(screen.component).isEqualTo(ComponentName(app, AlarmActivity::class.java))
        assertThat(screen.getLongExtra(AlarmActivity.EXTRA_ORDER_ID, -1)).isEqualTo(3L)
    }

    @Test
    fun `silenciada pasa al canal mudo sin repetir sonido y con Hecho y mas 5 min`() { // R-15.9, D-67
        publisher.publish(siesta.copy(critical = true))

        publisher.publish(siesta.copy(critical = true, silenced = true))

        val notification = requireNotNull(shown(42L))
        assertThat(notification.channelId).isEqualTo("alarm_critical_silent")
        assertThat(notification.flags and Notification.FLAG_INSISTENT).isEqualTo(0)
        assertThat(notification.fullScreenIntent).isNull()
        assertThat(notification.actions.map { it.title.toString() }).containsExactly("Hecho", "+5 min").inOrder()
        // Replaced in place: still one alarm notification (plus the group summary, D-87)
        assertThat(shadowManager.allNotifications.count { it.flags and Notification.FLAG_GROUP_SUMMARY == 0 }).isEqualTo(1)
    }

    private fun summary(): Notification? = shadowManager.getNotification("alarm_summary", 0)

    @Test
    fun `cada alarma va en el grupo propio de Rutinify y deja sonar a cada una`() { // K-13, D-87
        publisher.publish(siesta)
        publisher.publish(siesta.copy(alarmId = 43L, critical = true))

        listOf(42L, 43L).forEach { id ->
            val child = requireNotNull(shown(id))
            assertThat(child.group).isEqualTo("com.mirkwood.rutinify.ALARMS")
            assertThat(child.groupAlertBehavior).isEqualTo(Notification.GROUP_ALERT_CHILDREN)
            assertThat(child.flags and Notification.FLAG_GROUP_SUMMARY).isEqualTo(0)
        }
    }

    @Test
    fun `publica un resumen mudo del grupo para que Android no lo agrupe por su cuenta`() { // K-13, D-87
        publisher.publish(siesta)

        val summary = requireNotNull(summary())
        assertThat(summary.group).isEqualTo("com.mirkwood.rutinify.ALARMS")
        assertThat(summary.flags and Notification.FLAG_GROUP_SUMMARY).isNotEqualTo(0)
        // The summary never alerts: only the alarms themselves ring
        assertThat(summary.groupAlertBehavior).isEqualTo(Notification.GROUP_ALERT_CHILDREN)
        assertThat(summary.flags and Notification.FLAG_INSISTENT).isEqualTo(0)
    }

    @Test
    fun `al quitar la ultima alarma quita tambien el resumen`() { // K-13, R-7.4
        publisher.publish(siesta)
        publisher.publish(siesta.copy(alarmId = 43L))

        publisher.cancel(42L)
        assertThat(summary()).isNotNull()

        publisher.cancel(43L)
        assertThat(summary()).isNull()
    }

    @Test
    fun `cancelar un plan entero quita el resumen si no queda ninguna alarma`() { // K-13, R-10.2
        publisher.publish(siesta)
        publisher.publish(siesta.copy(alarmId = 43L))

        publisher.cancelForOrder(3L)

        assertThat(shadowManager.allNotifications).isEmpty()
    }

    @Test
    fun `cancelar sin nada mostrado no deja un resumen huerfano`() { // K-13
        publisher.cancel(42L)

        assertThat(summary()).isNull()
    }

    @Test
    fun `sin permiso de pantalla completa suena como notificacion emergente con los mismos botones`() { // R-15.11, D-68
        permissions.fullScreenIntent = false

        publisher.publish(siesta.copy(critical = true))

        val notification = requireNotNull(shown(42L))
        assertThat(notification.fullScreenIntent).isNull()
        assertThat(notification.channelId).isEqualTo("alarm_critical")
        assertThat(notification.priority).isEqualTo(Notification.PRIORITY_HIGH)
        assertThat(notification.flags and Notification.FLAG_INSISTENT).isNotEqualTo(0)
        assertThat(notification.actions.map { it.title.toString() }).containsExactly("Hecho", "+5 min", "Silenciar").inOrder()
    }

    @Test
    fun `el permiso de pantalla completa se mira en cada alarma`() { // R-15.11, D-68
        permissions.fullScreenIntent = false
        publisher.publish(siesta.copy(critical = true))
        assertThat(requireNotNull(shown(42L)).fullScreenIntent).isNull()

        permissions.fullScreenIntent = true
        publisher.publish(siesta.copy(alarmId = 43L, critical = true))
        assertThat(requireNotNull(shown(43L)).fullScreenIntent).isNotNull()
    }

    // --- Wear OS (T-70) -------------------------------------------------------------------------

    @Test
    fun `las alarmas se puentean al reloj con Hecho y mas 5 min con icono`() { // R-24.1, D-82
        publisher.publish(siesta)

        val notification = requireNotNull(shown(42L))
        assertThat(notification.flags and Notification.FLAG_LOCAL_ONLY).isEqualTo(0)
        // No watch-only actions: the watch shows the phone's ones
        assertThat(NotificationCompat.WearableExtender(notification).actions).isEmpty()
        assertThat(notification.actions.map { it.getIcon()?.resId }).containsExactly(R.drawable.ic_action_done, R.drawable.ic_action_snooze).inOrder()
    }

    @Test
    fun `una critica tambien se puentea y Silenciar llega al telefono por broadcast`() { // R-24.2, R-24.3, D-82
        publisher.publish(siesta.copy(critical = true))

        val notification = requireNotNull(shown(42L))
        assertThat(notification.flags and Notification.FLAG_LOCAL_ONLY).isEqualTo(0)
        val silence = notification.actions.last()
        assertThat(silence.getIcon()?.resId).isEqualTo(R.drawable.ic_action_silence)
        val intent = shadowOf(silence.actionIntent)
        assertThat(intent.isBroadcast).isTrue()
        assertThat(intent.savedIntent.component).isEqualTo(ComponentName(app, NotificationActionReceiver::class.java))
        assertThat(intent.savedIntent.action).isEqualTo(PendingIntentFactory.ACTION_SILENCE)
        // Every action runs on the phone, wherever it is tapped (R-24.2)
        assertThat(notification.actions.all { shadowOf(it.actionIntent).isBroadcast }).isTrue()
    }
}
