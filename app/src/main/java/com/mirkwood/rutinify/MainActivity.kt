package com.mirkwood.rutinify

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.mirkwood.rutinify.domain.repository.SharedTextInbox
import com.mirkwood.rutinify.ui.navigation.HomeDestination
import com.mirkwood.rutinify.ui.navigation.IncomingIntent
import com.mirkwood.rutinify.ui.navigation.IncomingIntents
import com.mirkwood.rutinify.ui.navigation.NewRoutineDestination
import com.mirkwood.rutinify.ui.navigation.RutinifyNavHost
import com.mirkwood.rutinify.ui.theme.RutinifyTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sharedTextInbox: SharedTextInbox

    /** Intents to act on once the navigation graph exists, with whether each one launched the activity. */
    private val incoming = Channel<Pair<Intent, Boolean>>(Channel.UNLIMITED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A recreated activity still holds the original intent: it was handled the first time
        if (savedInstanceState == null) incoming.trySend(intent to true)
        setContent {
            RutinifyTheme {
                val navController = rememberNavController()
                LaunchedEffect(navController) {
                    incoming.receiveAsFlow().collect { (intent, launch) -> handle(intent, navController, launch) }
                }
                RutinifyNavHost(navController = navController)
            }
        }
    }

    /** singleTask (D-64): shares and notification taps reach the running activity here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incoming.trySend(intent to false)
    }

    private fun handle(intent: Intent, navController: NavHostController, launch: Boolean) {
        when (val incomingIntent = IncomingIntents.classify(intent)) {
            is IncomingIntent.SharedText -> {
                sharedTextInbox.put(incomingIntent.text)
                // A fresh New routine on top of Home, dropping a draft under review (R-14.2, R-14.5)
                navController.navigate(NewRoutineDestination(fromShare = true)) { popUpTo(HomeDestination) }
            }
            // The launch intent is handled by the graph itself; later ones (a notification tap, R-7.6) are not
            IncomingIntent.Other -> if (!launch) navController.handleDeepLink(intent)
        }
    }
}
