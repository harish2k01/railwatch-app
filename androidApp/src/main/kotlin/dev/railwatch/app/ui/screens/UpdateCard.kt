package dev.railwatch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.railwatch.app.data.Updater
import dev.railwatch.app.data.Updater.State
import dev.railwatch.app.ui.theme.Rw
import kotlinx.coroutines.launch

/** "Update available" on Today: one card with Update and Later. Nothing shows when updates are off or up to date. */
@Composable
fun UpdateCard() {
    if (!Updater.enabled) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hidden by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { Updater.check(context) }
    val s = Updater.state
    val release = when (s) { is State.Available -> s.release; is State.Downloading -> s.release; is State.Failed -> s.release; else -> null } ?: return
    if (hidden > 0 || (s is State.Available && Updater.dismissed(context) == release.version)) return
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(16.dp)).background(Rw.colors.openBg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Update Available: v${release.version}", color = Rw.colors.openInk, fontWeight = FontWeight.Bold)
        when (s) {
            is State.Downloading -> {
                Text("Downloading… ${s.percent}%", color = Rw.colors.text, style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { s.percent / 100f }, modifier = Modifier.fillMaxWidth())
            }
            is State.Failed -> Text(s.message, color = Rw.colors.text, style = MaterialTheme.typography.bodyMedium)
            else -> Text("You have v${Updater.installed(context)}. Your plans and tickets stay as they are.", color = Rw.colors.text, style = MaterialTheme.typography.bodyMedium)
        }
        if (s !is State.Downloading) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Action("Update") { scope.launch { Updater.install(context, release) } }
            Action("Later", Rw.colors.muted) { Updater.dismiss(context, release.version); hidden++ }
        }
    }
}

/** The Updates row on About: the current state, and a tap checks now (or installs when there's an update). */
@Composable
fun UpdatesRow() {
    if (!Updater.enabled) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (subtitle, tap) = when (val s = Updater.state) {
        State.Checking -> "Checking…" to {}
        State.UpToDate -> "You have the latest version" to { scope.launch { Updater.check(context, force = true) }; Unit }
        is State.Available -> "v${s.release.version} is ready. Tap to update" to { scope.launch { Updater.install(context, s.release) }; Unit }
        is State.Downloading -> "Downloading… ${s.percent}%" to {}
        is State.Failed -> s.message to { scope.launch { s.release?.let { Updater.install(context, it) } ?: Updater.check(context, force = true) }; Unit }
        State.Idle -> "Tap to check for a new version" to { scope.launch { Updater.check(context, force = true) }; Unit }
    }
    ListRow("Updates", subtitle, tap)
}

@Composable
private fun Action(label: String, color: androidx.compose.ui.graphics.Color = Rw.colors.brand, onClick: () -> Unit) =
    Text(label, color = color, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 4.dp))
