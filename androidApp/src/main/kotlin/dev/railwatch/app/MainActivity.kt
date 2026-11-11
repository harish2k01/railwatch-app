package dev.railwatch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.railwatch.app.ui.RailWatchApp
import dev.railwatch.app.ui.theme.RailWatchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { RailWatchTheme { RailWatchApp() } }
    }
}
