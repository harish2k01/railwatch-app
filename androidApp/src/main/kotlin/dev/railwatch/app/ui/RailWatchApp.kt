package dev.railwatch.app.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
    data class Import(val uris: List<Uri>, val forJourney: String? = null) : Sheet
    data class Pass(val id: String) : Sheet
    data class Pdf(val id: String, val file: String) : Sheet
    data class Booking(val id: String) : Sheet
    data class Pnr(val id: String) : Sheet
    data object Add : Sheet
    data object Inbox : Sheet
    data class TimeOff(val range: dev.railwatch.shared.domain.TimeOffRange?) : Sheet
}

/** App shell: the current tab under a large title, bottom sheets for details and forms, the floating tab bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RailWatchApp(shared: List<Uri> = emptyList(), consumeShared: () -> Unit = {}, opened: Pair<String, String>? = null, consumeOpened: () -> Unit = {}) {
    val context = LocalContext.current
    val model = remember { AppModel(context.applicationContext) }
    // Appearance: the saved choice, or the phone's setting.
    val dark = when (model.planner.settings.theme) { "light" -> false; "dark" -> true; else -> androidx.compose.foundation.isSystemInDarkTheme() }
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        (view.context as? android.app.Activity)?.window?.let { w -> androidx.core.view.WindowCompat.getInsetsController(w, view).run { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark } }
    }
    dev.railwatch.app.ui.theme.RailWatchTheme(dark = dark) { AppBody(model, shared, consumeShared, opened, consumeOpened) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppBody(model: AppModel, shared: List<Uri>, consumeShared: () -> Unit, opened: Pair<String, String>?, consumeOpened: () -> Unit) {
    val context = LocalContext.current
    var touring by rememberSaveable { mutableStateOf(!TourState.seen(context)) }
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var ask by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val confirm: (String, () -> Unit) -> Unit = { q, run -> ask = q to run }
    // Saved plans that couldn't be read were set aside, not overwritten: say so, and point to the way back.
    LaunchedEffect(Unit) { if (model.recoveredFromUnreadable) ask = "Your saved plans couldn't be read, so RailWatch started empty. The old file was kept safe on this phone. Restore from your latest backup now?" to { tab = Tab.More } }
    // A ticket shared from another app opens the import sheet.
    LaunchedEffect(shared) { if (shared.isNotEmpty()) { sheet = Sheet.Import(shared); consumeShared() } }
    // A notification opens its journey (booking reminders open the booking-day screen).
    LaunchedEffect(opened) { opened?.let { (id, mode) -> sheet = when (mode) { "booking" -> Sheet.Booking(id); "pnr" -> Sheet.Pnr(id); else -> Sheet.Journey(id) }; consumeOpened() } }
    // Reminders need notification permission (Android 13+); asked once, on first open.
    val askNotify = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (TourState.seen(context) && android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    val openJourney: (dev.railwatch.shared.domain.Journey) -> Unit = { sheet = Sheet.Journey(it.id) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // The last item stops a clear gap above the floating tab bar (which sits above the system navigation bar).
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val pad = PaddingValues(start = 16.dp, end = 16.dp, top = top, bottom = navBar + 128.dp)
    Box(Modifier.fillMaxSize().background(Rw.colors.ground)) {
        // Tabs cross-fade with a slight rise instead of snapping.
        androidx.compose.animation.AnimatedContent(tab, label = "tab", transitionSpec = {
            (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) + androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(220)) { it / 40 })
                .togetherWith(androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)))
        }) { shown -> when (shown) {
            Tab.Today -> TodayScreen(model, open = openJourney, showTicket = { sheet = Sheet.Pass(it.id) }, inbox = { sheet = Sheet.Inbox },
                unread = remember(sheet) { dev.railwatch.app.data.Inbox.items(context).count { !it.read } }, newRoutine = { sheet = Sheet.Routine(null) }, seeAll = { tab = Tab.Journeys }, contentPadding = pad)
            Tab.Journeys -> JourneysScreen(model, open = openJourney, confirm = confirm, contentPadding = pad)
            Tab.Calendar -> dev.railwatch.app.ui.screens.CalendarScreen(model, open = openJourney, contentPadding = pad)
            Tab.More -> YouScreen(model, editRoutine = { sheet = Sheet.Routine(it) }, open = openJourney, showTicket = { sheet = Sheet.Pass(it.id) }, info = { info = it }, contentPadding = pad, editTimeOff = { sheet = Sheet.TimeOff(it) }, tour = { touring = true })
        } }
        // Content scrolls under a strip the colour of the page, so it never runs into the status bar.
        Box(Modifier.fillMaxWidth().height(top).background(Rw.colors.ground))
        FloatingTabBar(selected = tab, onSelect = { tab = it }, onNew = { sheet = Sheet.Add }, modifier = Modifier.align(Alignment.BottomCenter))
    }
    sheet?.let { s ->
        ModalBottomSheet(onDismissRequest = { if (s !is Sheet.Import) sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Rw.colors.surface) {
            val find = { id: String -> model.planner.journeys.firstOrNull { it.id == id } }
            when (s) {
                is Sheet.Journey -> find(s.id)?.let { j ->
                    JourneySheet(j, model, close = { sheet = null }, confirm = confirm, edit = { sheet = Sheet.Edit(j.id) },
                        ticket = { if (dev.railwatch.shared.domain.hasTicket(j)) sheet = Sheet.Pass(j.id) else info = "To add the ticket, open the IRCTC PDF in Gmail or Files, tap Share and choose RailWatch." },
                        pnr = { if (j.pnr.length == 10) sheet = Sheet.Pnr(j.id) else info = "Add the 10-digit PNR with Edit first." },
                        addTicket = { uris -> sheet = Sheet.Import(uris, j.id) })
                }
                is Sheet.Edit -> find(s.id)?.let { j -> EditJourneySheet(j, model, close = { sheet = Sheet.Journey(j.id) }, addTicket = { uris -> sheet = Sheet.Import(uris, j.id) }) }
                Sheet.New -> NewJourneySheet(model) { sheet = null }
                is Sheet.Routine -> RoutineSheet(model, s.rule) { sheet = null }
                is Sheet.Import -> ImportSheet(model, s.uris, s.forJourney) { msg -> sheet = null; if (msg.isNotEmpty()) { info = msg } }
                is Sheet.Pass -> find(s.id)?.let { j -> j.attachments?.firstOrNull { it.type == "application/pdf" }?.let { f -> TicketPass(j, f, model) { sheet = Sheet.Pdf(j.id, f.id) } } }
                is Sheet.Pdf -> find(s.id)?.attachments?.firstOrNull { it.id == s.file }?.let { PdfPages(model, it) }
                Sheet.Add -> dev.railwatch.app.ui.screens.AddSheet(
                    journey = { sheet = Sheet.New }, routine = { sheet = Sheet.Routine(null) },
                    timeOff = { sheet = Sheet.TimeOff(null) },
                    ticket = { uris -> sheet = if (uris.isEmpty()) null else Sheet.Import(uris) })
                Sheet.Inbox -> dev.railwatch.app.ui.screens.InboxSheet { id -> sheet = Sheet.Journey(id) }
                is Sheet.TimeOff -> dev.railwatch.app.ui.screens.TimeOffSheet(model, s.range) { msg -> sheet = null; info = msg }
                is Sheet.Pnr -> find(s.id)?.let { dev.railwatch.app.ui.screens.PnrSheet(it, model, confirm) { sheet = Sheet.Journey(it.id) } }
                is Sheet.Booking -> find(s.id)?.let { dev.railwatch.app.ui.screens.BookingDaySheet(it, model) { sheet = null } }
            }
        }
    }
    // The first-run tour (and its replay from More › Help & Tour), over everything but toasts.
    val restore = dev.railwatch.app.ui.screens.rememberRestore(model, { info = it })
    androidx.compose.animation.AnimatedVisibility(touring, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
        Tour(restore = { TourState.markSeen(context); touring = false; restore() }) { addTrip ->
            TourState.markSeen(context); touring = false; tab = Tab.Today
            if (addTrip) sheet = Sheet.Add
        }
    }
    // Messages and errors show as an app-styled toast above the tab bar, and fade after a few seconds.
    // The toast slides up and fades; it keeps its text while leaving.
    var lastToast by remember { mutableStateOf("") }
    info?.let { msg -> lastToast = msg; LaunchedEffect(msg) { kotlinx.coroutines.delay(4000); if (info == msg) info = null } }
    // The toast lives in its own popup window, so it shows above bottom sheets and dialogs too (they are windows).
    val toastShown = remember { androidx.compose.animation.core.MutableTransitionState(false) }
    toastShown.targetState = info != null
    if (toastShown.currentState || toastShown.targetState) androidx.compose.ui.window.Popup(
        alignment = Alignment.BottomCenter,
        properties = androidx.compose.ui.window.PopupProperties(focusable = false, clippingEnabled = false),
    ) {
        val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        androidx.compose.animation.AnimatedVisibility(toastShown,
            enter = androidx.compose.animation.slideInVertically { it / 3 } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { it / 3 } + androidx.compose.animation.fadeOut()) {
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(bottom = navBar + 96.dp, start = 16.dp, end = 16.dp)
                .background(Rw.colors.surface2, androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                .border(1.dp, Rw.colors.line, androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).clickable { info = null }
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
                Text(lastToast, color = Rw.colors.text, modifier = Modifier.weight(1f))
            }
        }
    }
    ask?.let { (q, run) ->
        AlertDialog(onDismissRequest = { ask = null }, containerColor = Rw.colors.surface, text = { Text(q, color = Rw.colors.text) },
            confirmButton = { TextButton({ run(); ask = null }) { Text("Yes") } }, dismissButton = { TextButton({ ask = null }) { Text("Not Now") } })
    }
}
