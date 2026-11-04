package com.mirkwood.rutinify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mirkwood.rutinify.ui.theme.RutinifyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RutinifyTheme {
                // Pantalla vacía hasta T-03 (RutinifyNavHost)
                Surface(modifier = Modifier.fillMaxSize()) { }
            }
        }
    }
}
