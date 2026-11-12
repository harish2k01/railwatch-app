package dev.railwatch.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.railwatch.app.data.AppModel
import dev.railwatch.app.ui.components.Grouped
import dev.railwatch.app.ui.components.RowDivider
import dev.railwatch.app.ui.theme.Rw
import dev.railwatch.shared.domain.Planner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Save everything (trips, routines, time off, settings and ticket PDFs) to one file, or restore from one. */
@Composable
fun BackupPage(model: AppModel, info: (String) -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<Planner, List<Pair<String, ByteArray>>>?>(null) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val msg = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(model.backupJson().encodeToByteArray()) } }
                "Backup saved."
            }.getOrElse { "Couldn't save the backup." }
            busy = false; info(msg)
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val text = context.contentResolver.openInputStream(uri)!!.use { s ->
                        val bytes = s.readBytes(); if (bytes.size > 80_000_000) error("That backup is too large."); bytes.decodeToString()
                    }
                    model.readBackup(text)
                }
            }.onSuccess { pending = it }.onFailure { info(it.message ?: "Couldn't read that file.") }
            busy = false
        }
    }

    val p = model.planner
    val pdfs = p.journeys.sumOf { it.attachments?.size ?: 0 }
    Column {
        Text("Everything in RailWatch goes into one file: ${p.journeys.size} trips, ${p.rules.size} routines, your time off, settings and $pdfs ticket PDFs. " +
            "Keep it somewhere safe, like Drive. The website reads the same file.",
            style = MaterialTheme.typography.bodyMedium, color = Rw.colors.muted, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
        Grouped {
            ListRow(if (busy) "Working…" else "Save Backup", "Choose where to save the file") { if (!busy) save.launch("railwatch-backup-${model.today}.json") }
            RowDivider()
            ListRow("Restore From Backup", "Replaces everything here with the file's contents") { if (!busy) open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        }
    }

    pending?.let { b ->
        AlertDialog(onDismissRequest = { pending = null }, containerColor = Rw.colors.surface,
            title = { Text("Replace Everything?", color = Rw.colors.text) },
            text = { Text("This backup has ${b.first.journeys.size} trips, ${b.first.rules.size} routines and ${b.second.size} ticket PDFs. What's in the app now will be replaced and can't be brought back.", color = Rw.colors.text) },
            confirmButton = { TextButton({ model.restore(b); pending = null; info("Backup restored.") }) { Text("Replace") } },
            dismissButton = { TextButton({ pending = null }) { Text("Cancel") } })
    }
}
