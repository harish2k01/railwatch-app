package dev.railwatch.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.railwatch.app.ui.SplashPurple
import dev.railwatch.app.ui.components.Grouped
import dev.railwatch.app.ui.components.RowDivider
import dev.railwatch.app.ui.drawMonogram
import dev.railwatch.app.ui.theme.Rw

private const val SOURCE = "https://github.com/harish2k01/railwatch-app"

/** About: logo, name, version and developer, then the licence, privacy policy and open-source notices. */
@Composable
fun AboutPage() {
    val context = LocalContext.current
    var doc by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = doc != null) { doc = null }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }.getOrDefault("")
    }

    doc?.let { name ->
        val text = remember(name) { runCatching { context.assets.open("legal/$name").use { it.readBytes().decodeToString() } }.getOrDefault("Not available.") }
        Text(text, color = Rw.colors.text, fontSize = 12.sp, lineHeight = 17.sp, fontFamily = if (name == "PRIVACY.md") null else FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 4.dp))
        return
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp)) {
        Canvas(Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(SplashPurple).semantics { contentDescription = "RailWatch logo" }) { drawMonogram() }
        Text("RailWatch", color = Rw.colors.text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 14.dp))
        Text("Plan train trips, book on time, keep tickets offline.", color = Rw.colors.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
    Grouped {
        Info("Version", version)
        RowDivider(); Info("Developer", "Harish T")
        RowDivider(); Info("Licence", "GNU GPL v3.0")
    }
    Spacer(Modifier.height(16.dp))
    Grouped {
        ListRow("Privacy Policy", "Everything stays on your phone") { doc = "PRIVACY.md" }
        RowDivider(); ListRow("Licence", "GNU General Public License, version 3") { doc = "LICENSE" }
        RowDivider(); ListRow("Open-Source Notices", "Manrope font (SIL Open Font License)") { doc = "MANROPE-OFL.txt" }
        RowDivider(); ListRow("Source Code", "github.com/harish2k01/railwatch-app") { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE))) } }
    }
    Text("RailWatch is an independent app. It is not affiliated with, endorsed by or connected to Indian Railways, IRCTC or CRIS. You book on IRCTC; PNR status comes from Indian Railways' public enquiry service as-is.", color = Rw.colors.muted,
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp, top = 16.dp))
    Text("Copyright © 2026 Harish T. RailWatch is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License, version 3. It comes with no warranty.", color = Rw.colors.muted,
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

@Composable
private fun Info(label: String, value: String) = androidx.compose.foundation.layout.Row(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween,
) {
    Text(label, color = Rw.colors.text, style = MaterialTheme.typography.bodyLarge)
    Text(value, color = Rw.colors.muted, style = MaterialTheme.typography.bodyLarge)
}
