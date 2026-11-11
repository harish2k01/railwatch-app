package dev.railwatch.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.railwatch.app.data.AppModel
import dev.railwatch.app.ui.components.FloatingTabBar
import dev.railwatch.app.ui.components.LargeTitle
import dev.railwatch.app.ui.components.Tab
import dev.railwatch.app.ui.screens.JourneySheet
import dev.railwatch.app.ui.screens.JourneysScreen
import dev.railwatch.app.ui.screens.NewJourneySheet
import dev.railwatch.app.ui.screens.RoutineSheet
import dev.railwatch.app.ui.screens.TodayScreen
import dev.railwatch.app.ui.screens.YouScreen
import dev.railwatch.app.ui.theme.Rw
import dev.railwatch.shared.domain.Rule

/** What the bottom sheet shows. */
private sealed interface Sheet { data class Journey(val id: String) : Sheet; data class Edit(val id: String) : Sheet; data object New : Sheet; data class Routine(val rule: Rule?) : Sheet }

/** App shell: the current tab under a large title, bottom sheets for details and forms, the floating tab bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RailWatchApp() {
    val context = LocalContext.current
    val model = remember { AppModel(context.applicationContext) }
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var ask by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val confirm: (String, () -> Unit) -> Unit = { q, run -> ask = q to run }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val pad = PaddingValues(start = 16.dp, end = 16.dp, top = top, bottom = 120.dp)
    Box(Modifier.fillMaxSize().background(Rw.colors.ground)) {
        when (tab) {
            Tab.Today -> TodayScreen(model, open = { sheet = Sheet.Journey(it.id) }, newRoutine = { sheet = Sheet.Routine(null) }, seeAll = { tab = Tab.Journeys }, contentPadding = pad)
            Tab.Journeys -> JourneysScreen(model, open = { sheet = Sheet.Journey(it.id) }, confirm = confirm, contentPadding = pad)
            Tab.Tickets -> androidx.compose.foundation.lazy.LazyColumn(contentPadding = pad) { item { LargeTitle("Tickets") }; item { Text("Share an IRCTC ticket PDF to RailWatch to add it (milestone 3).", color = Rw.colors.muted) } }
            Tab.You -> YouScreen(model, editRoutine = { sheet = Sheet.Routine(it) }, contentPadding = pad)
        }
        FloatingTabBar(selected = tab, onSelect = { tab = it }, onNew = { sheet = Sheet.New }, modifier = Modifier.align(Alignment.BottomCenter))
    }
    sheet?.let { s ->
        ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Rw.colors.surface) {
            when (s) {
                is Sheet.Journey -> model.planner.journeys.firstOrNull { it.id == s.id }?.let { j ->
                    JourneySheet(j, model, close = { sheet = null }, confirm = confirm, edit = { sheet = Sheet.Edit(j.id) },
                        ticket = { info = if (dev.railwatch.shared.domain.hasTicket(j)) "Opening the ticket pass arrives with ticket sharing." else "To add the ticket, open the IRCTC PDF in Gmail or Files, tap Share and choose RailWatch." },
                        pnr = { info = if (j.pnr.isEmpty()) "Add the PNR with Edit first." else "PNR checks are coming in a later update." })
                }
                is Sheet.Edit -> model.planner.journeys.firstOrNull { it.id == s.id }?.let { dev.railwatch.app.ui.screens.EditJourneySheet(it, model) { sheet = Sheet.Journey(it.id) } }
                Sheet.New -> NewJourneySheet(model) { sheet = null }
                is Sheet.Routine -> RoutineSheet(model, s.rule) { sheet = null }
            }
        }
    }
    info?.let { msg ->
        AlertDialog(onDismissRequest = { info = null }, containerColor = Rw.colors.surface, text = { Text(msg, color = Rw.colors.text) }, confirmButton = { TextButton({ info = null }) { Text("OK") } })
    }
    ask?.let { (q, run) ->
        AlertDialog(onDismissRequest = { ask = null }, containerColor = Rw.colors.surface, text = { Text(q, color = Rw.colors.text) },
            confirmButton = { TextButton({ run(); ask = null }) { Text("Yes") } }, dismissButton = { TextButton({ ask = null }) { Text("Not now") } })
    }
}
