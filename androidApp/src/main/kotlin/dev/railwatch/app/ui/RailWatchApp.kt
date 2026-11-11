package dev.railwatch.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.railwatch.app.ui.components.FloatingTabBar
import dev.railwatch.app.ui.components.LargeTitle
import dev.railwatch.app.ui.components.Tab
import dev.railwatch.app.ui.theme.Rw

/** App shell: the current tab's screen under a large title, with the floating tab bar on top. */
@Composable
fun RailWatchApp() {
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    Box(Modifier.fillMaxSize().background(Rw.colors.ground)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
            LargeTitle(title = tab.label)
            Text("Coming in the next milestones.", color = Rw.colors.muted, modifier = Modifier.padding(top = 12.dp))
        }
        FloatingTabBar(selected = tab, onSelect = { tab = it }, onNew = {}, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
