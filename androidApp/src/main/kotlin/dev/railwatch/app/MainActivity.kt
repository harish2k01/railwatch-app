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
        publishShortcuts()
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
        // Home-screen icon shortcuts.
        when (intent.action) {
            ACTION_ADD -> opened.value = "" to "add"
            ACTION_NEXT -> opened.value = "" to "next"
            ACTION_PNR -> opened.value = "" to "checkpnr"
        }
        intent.getStringExtra("journey")?.let { opened.value = it to when { intent.getBooleanExtra("pnr", false) -> "pnr"; intent.getBooleanExtra("bookingDay", false) -> "booking"; else -> "journey" } }
    }

    /** A journey opened from a notification or the widget: (id, "journey" | "booking" | "pnr"). */
    private val opened = mutableStateOf<Pair<String, String>?>(null)

    /** Long-press shortcuts on the app icon: Add Ticket, Next Trip, Check PNR. */
    private fun publishShortcuts() = runCatching {
        fun shortcut(id: String, label: String, long: String, icon: Int, action: String) =
            androidx.core.content.pm.ShortcutInfoCompat.Builder(this, id).setShortLabel(label).setLongLabel(long)
                .setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(this, icon))
                .setIntent(Intent(this, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)).build()
        androidx.core.content.pm.ShortcutManagerCompat.setDynamicShortcuts(this, listOf(
            shortcut("add", "Add Ticket", "Add a ticket or trip", R.drawable.ic_shortcut_add, ACTION_ADD),
            shortcut("next", "Next Trip", "Open your next trip", R.drawable.ic_shortcut_trip, ACTION_NEXT),
            shortcut("pnr", "Check PNR", "Check PNR for your next trip", R.drawable.ic_shortcut_pnr, ACTION_PNR),
        ))
    }

    companion object {
        const val ACTION_ADD = "dev.railwatch.app.action.ADD"
        const val ACTION_NEXT = "dev.railwatch.app.action.NEXT_TRIP"
        const val ACTION_PNR = "dev.railwatch.app.action.CHECK_PNR"
    }

    @Suppress("DEPRECATION")
    private fun stream(i: Intent): Uri? = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableExtra(Intent.EXTRA_STREAM)
    @Suppress("DEPRECATION")
    private fun streams(i: Intent): List<Uri> = (if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableArrayListExtra(Intent.EXTRA_STREAM)).orEmpty()
}
