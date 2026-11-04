package com.mirkwood.rutinify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mirkwood.rutinify.ui.navigation.RutinifyNavHost
import com.mirkwood.rutinify.ui.theme.RutinifyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RutinifyTheme {
                RutinifyNavHost()
            }
        }
    }
}
