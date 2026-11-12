package dev.railwatch.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import dev.railwatch.app.ui.RailWatchApp
import dev.railwatch.app.ui.theme.RailWatchTheme

class MainActivity : ComponentActivity() {
    /** PDFs shared to RailWatch, waiting for the import sheet. */
    private val shared = mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) take(intent)
        // The splash plays on a fresh start (not when the app is brought back or reopened after rotation).
        val splash = mutableStateOf(savedInstanceState == null)
        setContent { RailWatchTheme {
            androidx.compose.foundation.layout.Box {
                RailWatchApp(shared = shared.value, consumeShared = { shared.value = emptyList() }, opened = opened.value, consumeOpened = { opened.value = null })
                if (splash.value) dev.railwatch.app.ui.Splash { splash.value = false }
            }
        } }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); take(intent) }

    private fun take(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(stream(intent))
            Intent.ACTION_SEND_MULTIPLE -> streams(intent)
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            else -> emptyList()
        }
        if (uris.isNotEmpty()) shared.value = uris
        intent.getStringExtra("journey")?.let { opened.value = it to intent.getBooleanExtra("bookingDay", false) }
    }

    /** A journey opened from a notification: (id, show the booking-day screen). */
    private val opened = mutableStateOf<Pair<String, Boolean>?>(null)

    @Suppress("DEPRECATION")
    private fun stream(i: Intent): Uri? = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableExtra(Intent.EXTRA_STREAM)
    @Suppress("DEPRECATION")
    private fun streams(i: Intent): List<Uri> = (if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableArrayListExtra(Intent.EXTRA_STREAM)).orEmpty()
}
