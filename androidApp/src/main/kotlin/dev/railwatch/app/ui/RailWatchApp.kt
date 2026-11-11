package dev.railwatch.app.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.LaunchedEffect
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
import dev.railwatch.app.ui.components.Tab
import dev.railwatch.app.ui.screens.EditJourneySheet
import dev.railwatch.app.ui.screens.ImportSheet
import dev.railwatch.app.ui.screens.JourneySheet
import dev.railwatch.app.ui.screens.JourneysScreen
import dev.railwatch.app.ui.screens.NewJourneySheet
import dev.railwatch.app.ui.screens.PdfPages
import dev.railwatch.app.ui.screens.RoutineSheet
import dev.railwatch.app.ui.screens.TicketPass
import dev.railwatch.app.ui.screens.TicketsScreen
import dev.railwatch.app.ui.screens.TodayScreen
import dev.railwatch.app.ui.screens.YouScreen
import dev.railwatch.app.ui.theme.Rw
import dev.railwatch.shared.domain.Rule

/** What the bottom sheet shows. */
private sealed interface Sheet {
    data class Journey(val id: String) : Sheet
    data class Edit(val id: String) : Sheet
    data object New : Sheet
    data class Routine(val rule: Rule?) : Sheet
    data class Import(val uris: List<Uri>) : Sheet
    data class Pass(val id: String) : Sheet
    data class Pdf(val id: String, val file: String) : Sheet
    data class Booking(val id: String) : Sheet
    data class Pnr(val id: String) : Sheet
}

/** App shell: the current tab under a large title, bottom sheets for details and forms, the floating tab bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RailWatchApp(shared: List<Uri> = emptyList(), consumeShared: () -> Unit = {}, opened: Pair<String, Boolean>? = null, consumeOpened: () -> Unit = {}) {
    val context = LocalContext.current
    val model = remember { AppModel(context.applicationContext) }
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var ask by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val confirm: (String, () -> Unit) -> Unit = { q, run -> ask = q to run }
    // A ticket shared from another app opens the import sheet.
    LaunchedEffect(shared) { if (shared.isNotEmpty()) { sheet = Sheet.Import(shared); consumeShared() } }
    // A notification opens its journey (booking reminders open the booking-day screen).
    LaunchedEffect(opened) { opened?.let { (id, booking) -> sheet = if (booking) Sheet.Booking(id) else Sheet.Journey(id); consumeOpened() } }
    // Reminders need notification permission (Android 13+); asked once, on first open.
    val askNotify = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    val openJourney: (dev.railwatch.shared.domain.Journey) -> Unit = { sheet = Sheet.Journey(it.id) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val pad = PaddingValues(start = 16.dp, end = 16.dp, top = top, bottom = 120.dp)
    Box(Modifier.fillMaxSize().background(Rw.colors.ground)) {
        when (tab) {
            Tab.Today -> TodayScreen(model, open = openJourney, showTicket = { sheet = Sheet.Pass(it.id) }, newRoutine = { sheet = Sheet.Routine(null) }, seeAll = { tab = Tab.Journeys }, contentPadding = pad)
            Tab.Journeys -> JourneysScreen(model, open = openJourney, confirm = confirm, contentPadding = pad)
            Tab.Calendar -> dev.railwatch.app.ui.screens.CalendarScreen(model, open = openJourney, contentPadding = pad)
            Tab.More -> YouScreen(model, editRoutine = { sheet = Sheet.Routine(it) }, open = openJourney, showTicket = { sheet = Sheet.Pass(it.id) }, info = { info = it }, contentPadding = pad)
        }
        FloatingTabBar(selected = tab, onSelect = { tab = it }, onNew = { sheet = Sheet.New }, modifier = Modifier.align(Alignment.BottomCenter))
    }
    sheet?.let { s ->
        ModalBottomSheet(onDismissRequest = { if (s !is Sheet.Import) sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Rw.colors.surface) {
            val find = { id: String -> model.planner.journeys.firstOrNull { it.id == id } }
            when (s) {
                is Sheet.Journey -> find(s.id)?.let { j ->
                    JourneySheet(j, model, close = { sheet = null }, confirm = confirm, edit = { sheet = Sheet.Edit(j.id) },
                        ticket = { if (dev.railwatch.shared.domain.hasTicket(j)) sheet = Sheet.Pass(j.id) else info = "To add the ticket, open the IRCTC PDF in Gmail or Files, tap Share and choose RailWatch." },
                        pnr = { if (j.pnr.length == 10) sheet = Sheet.Pnr(j.id) else info = "Add the 10-digit PNR with Edit first." })
                }
                is Sheet.Edit -> find(s.id)?.let { EditJourneySheet(it, model) { sheet = Sheet.Journey(it.id) } }
                Sheet.New -> NewJourneySheet(model) { sheet = null }
                is Sheet.Routine -> RoutineSheet(model, s.rule) { sheet = null }
                is Sheet.Import -> ImportSheet(model, s.uris) { msg -> sheet = null; if (msg.isNotEmpty()) { info = msg } }
                is Sheet.Pass -> find(s.id)?.let { j -> j.attachments?.firstOrNull { it.type == "application/pdf" }?.let { f -> TicketPass(j, f, model) { sheet = Sheet.Pdf(j.id, f.id) } } }
                is Sheet.Pdf -> find(s.id)?.attachments?.firstOrNull { it.id == s.file }?.let { PdfPages(model, it) }
                is Sheet.Pnr -> find(s.id)?.let { dev.railwatch.app.ui.screens.PnrSheet(it, model, confirm) { sheet = Sheet.Journey(it.id) } }
                is Sheet.Booking -> find(s.id)?.let { dev.railwatch.app.ui.screens.BookingDaySheet(it, model) { sheet = null } }
            }
        }
    }
    // Messages and errors show as an app-styled toast above the tab bar, and fade after a few seconds.
    info?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(4000); if (info == msg) info = null }
        Box(Modifier.fillMaxSize().padding(bottom = 112.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.BottomCenter) {
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().background(Rw.colors.surface2, androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                .border(1.dp, Rw.colors.line, androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).clickable { info = null }.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(msg, color = Rw.colors.text, modifier = Modifier.weight(1f))
            }
        }
    }
    ask?.let { (q, run) ->
        AlertDialog(onDismissRequest = { ask = null }, containerColor = Rw.colors.surface, text = { Text(q, color = Rw.colors.text) },
            confirmButton = { TextButton({ run(); ask = null }) { Text("Yes") } }, dismissButton = { TextButton({ ask = null }) { Text("Not now") } })
    }
}
