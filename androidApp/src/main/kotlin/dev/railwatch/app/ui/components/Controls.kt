package dev.railwatch.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.railwatch.app.ui.theme.Rw

/** Segmented control (Upcoming / To book / History). */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Rw.colors.surface2).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(Modifier.weight(1f).heightIn(min = 36.dp).clip(RoundedCornerShape(10.dp)).background(if (on) Rw.colors.surface else Rw.colors.surface2)
                .selectable(on, role = Role.Tab) { onSelect(value) }, contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.titleMedium, color = if (on) Rw.colors.text else Rw.colors.muted)
            }
        }
    }
}

/** Round icon button (header actions). */
@Composable
fun RoundIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(Rw.colors.surface), contentAlignment = Alignment.Center) {
        IconButton(onClick = onClick) { Icon(icon, contentDescription = label, tint = Rw.colors.text) }
    }
}

/** Full-width primary button. */
@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(if (enabled) Rw.colors.brand else Rw.colors.surface2)
        .selectable(false, enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = if (enabled) Rw.colors.brandInk else Rw.colors.muted)
    }
}

/** Secondary (surface) button. */
@Composable
fun SecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(Rw.colors.surface2)
        .selectable(false, role = Role.Button, onClick = onClick).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Rw.colors.text)
    }
}
