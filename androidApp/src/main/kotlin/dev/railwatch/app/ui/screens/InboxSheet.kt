package dev.railwatch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.railwatch.app.data.Inbox
import dev.railwatch.app.ui.components.RwIcons
import dev.railwatch.app.ui.theme.Rw
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime

/** The bell on Today, with a dot and count when something new arrived. */
@Composable
fun Bell(unread: Int, open: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(Rw.colors.surface).clickable(onClickLabel = "Notifications", onClick = open), contentAlignment = Alignment.Center) {
        Icon(RwIcons.Bell, if (unread > 0) "$unread new notifications" else "Notifications", tint = Rw.colors.text, modifier = Modifier.size(22.dp))
        if (unread > 0) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).background(Rw.colors.danger, CircleShape), contentAlignment = Alignment.Center) {
            Text(if (unread > 9) "9+" else "$unread", color = Rw.colors.ground, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Notifications: reminders that were sent, PNR changes and cancellations, newest first. Opening marks them read. */
@Composable
fun InboxSheet(open: (String) -> Unit) {
    val context = LocalContext.current
    val items = remember { Inbox.items(context) }
    DisposableEffect(Unit) { onDispose { Inbox.markAllRead(context) } }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Notifications", style = MaterialTheme.typography.headlineSmall, color = Rw.colors.text)
        if (items.isEmpty()) Text("Nothing yet. Booking reminders, PNR changes and cancellations show up here as well as on your phone.", color = Rw.colors.muted)
        items.forEach { n ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Rw.colors.surface2).clickable(enabled = n.journeyId != null) { n.journeyId?.let(open) }.padding(14.dp)) {
                Box(Modifier.padding(top = 6.dp, end = 12.dp).size(8.dp).background(if (n.read) Rw.colors.surface2 else Rw.colors.brand, CircleShape))
                Column(Modifier.weight(1f)) {
                    Text(n.title, color = Rw.colors.text, style = MaterialTheme.typography.titleMedium)
                    Text(n.body, color = Rw.colors.muted, style = MaterialTheme.typography.bodyMedium)
                    val t = runCatching { Instant.parse(n.at).toLocalDateTime(dev.railwatch.shared.IST) }.getOrNull()
                    t?.let { Text("${dev.railwatch.app.ui.shortDay(it.date.toString())} · ${"%02d:%02d".format(it.hour, it.minute)}", color = Rw.colors.muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
        }
        if (items.isNotEmpty()) Text("Clear All", color = Rw.colors.danger, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clickable { Inbox.clear(context) }.padding(vertical = 8.dp))
    }
}
