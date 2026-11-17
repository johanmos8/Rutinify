package com.mirkwood.rutinify.ui.alarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mirkwood.rutinify.ui.theme.RutinifyTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Full-screen alarm of a critical alarm (R-15.7), opened by the notification's full-screen intent. Shows over the
 * lock screen and turns the screen on; its own task, out of recents, so it never mixes with the app's screens.
 * Closing it with Back leaves the alarm ringing in its notification.
 */
@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        setContent {
            RutinifyTheme { AlarmRoute(onClose = ::finish) }
        }
    }

    companion object {
        const val EXTRA_ALARM_ID = "com.mirkwood.rutinify.extra.ALARM_ID"
        const val EXTRA_ORDER_ID = "com.mirkwood.rutinify.extra.ORDER_ID"
    }
}
