package dev.railwatch.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.railwatch.app.ui.theme.Rw

/** The four destinations of the floating tab bar (canvas NativeToday); New journey sits in the middle. */
enum class Tab(val label: String, val icon: ImageVector) {
    Today("Today", RwIcons.Home), Journeys("Journeys", RwIcons.List), Calendar("Calendar", RwIcons.Calendar), You("You", RwIcons.Person)
}

/** A large title (34 sp) with an optional eyebrow line above and actions on the right. */
@Composable
fun LargeTitle(title: String, eyebrow: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) Text(eyebrow, style = MaterialTheme.typography.bodyMedium, color = Rw.colors.muted)
            Text(title, style = MaterialTheme.typography.headlineLarge, color = Rw.colors.text)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Floating, rounded tab bar inset from the screen edges, with a centre New journey button. */
@Composable
fun FloatingTabBar(selected: Tab, onSelect: (Tab) -> Unit, onNew: () -> Unit, modifier: Modifier = Modifier) {
    val c = Rw.colors
    Row(
        modifier.navigationBarsPadding().padding(12.dp).fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(28.dp))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabItem(Tab.Today, selected, onSelect); TabItem(Tab.Journeys, selected, onSelect)
        Box(Modifier.padding(horizontal = 4.dp).size(54.dp).clip(CircleShape).background(c.brand), contentAlignment = Alignment.Center) {
            IconButton(onClick = onNew, modifier = Modifier.semantics { contentDescription = "New journey" }) {
                Icon(RwIcons.Plus, contentDescription = null, tint = c.brandInk)
            }
        }
        TabItem(Tab.Calendar, selected, onSelect); TabItem(Tab.You, selected, onSelect)
    }
}

@Composable
private fun RowScope.TabItem(tab: Tab, selected: Tab, onSelect: (Tab) -> Unit) {
    val on = tab == selected
    Column(
        Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(20.dp))
            .selectable(selected = on, role = Role.Tab, onClick = { onSelect(tab) }),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, contentDescription = null, tint = if (on) Rw.colors.brand else Rw.colors.muted)
        Text(tab.label, style = MaterialTheme.typography.labelMedium, color = if (on) Rw.colors.text else Rw.colors.muted)
    }
}
