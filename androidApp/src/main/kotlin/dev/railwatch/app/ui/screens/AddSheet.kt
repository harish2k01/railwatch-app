package dev.railwatch.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.railwatch.app.ui.components.RwIcons
import dev.railwatch.app.ui.theme.Rw

/** What + adds: a journey, a routine, a day off, or tickets from PDFs (read, filled in and linked to their journeys). */
@Composable
fun AddSheet(journey: () -> Unit, routine: () -> Unit, timeOff: () -> Unit, ticket: (List<Uri>) -> Unit) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { ticket(it) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Add", style = MaterialTheme.typography.headlineSmall, color = Rw.colors.text, modifier = Modifier.padding(bottom = 4.dp))
        Option(RwIcons.Ticket, "Ticket", "Pick IRCTC PDFs. Details are read and each is linked to its journey, or a journey is created for it.") { pick.launch(arrayOf("application/pdf")) }
        Option(RwIcons.Calendar, "Journey", "A one-off trip to plan and get booking reminders for", journey)
        Option(RwIcons.List, "Routine", "A trip you make regularly, planned months ahead", routine)
        Option(RwIcons.Clock, "Holiday or Leave", "Days off; trips on them are flagged and long weekends shown", timeOff)
    }
}

@Composable
private fun Option(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Rw.colors.surface2).clickable(onClick = onClick).heightIn(min = 72.dp).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).background(Rw.colors.brandSoft, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Rw.colors.brandSoftInk) }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Rw.colors.text)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = Rw.colors.muted)
        }
    }
}
