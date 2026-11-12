package dev.railwatch.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.railwatch.app.data.Alarms
import dev.railwatch.app.data.PhoneCalendar
import dev.railwatch.app.ui.components.RwIcons
import dev.railwatch.app.ui.theme.Rw
import kotlinx.coroutines.launch

/** Whether the first-run tour has been seen on this phone. */
object TourState {
    private fun prefs(c: Context) = c.getSharedPreferences("tour", Context.MODE_PRIVATE)
    fun seen(c: Context) = prefs(c).getBoolean("seen", false)
    fun markSeen(c: Context) = prefs(c).edit().putBoolean("seen", true).apply()
}

/**
 * The first-run tour: welcome (or restore a backup), then one page each on booking windows, the + sheet, routines and
 * time off, after booking, and the permissions reminders need. Replayable from More › Help & Tour.
 */
@Composable
fun Tour(restore: () -> Unit, done: (addTrip: Boolean) -> Unit) {
    val pager = rememberPagerState { 6 }
    val scope = rememberCoroutineScope()
    BackHandler(enabled = pager.currentPage > 0) { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }
    fun next() = scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }

    Box(Modifier.fillMaxSize().background(if (pager.currentPage == 0) SplashPurple else Rw.colors.ground)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> Welcome(start = ::next, restore = restore)
                1 -> Step("Never Miss the Day Booking Opens",
                    "Tickets open a set number of days before travel. RailWatch works out that day for each trip and reminds you before the window opens, and again for Tatkal.") { WindowPicture() }
                2 -> Step("Everything Starts From +",
                    "Tap + to add a ticket, a trip, a routine or a holiday. A ticket you add finds its trip by itself, or makes a new one. You can also share an IRCTC PDF to RailWatch from Gmail or Files.") { AddPicture() }
                3 -> Step("Regular Trips Plan Themselves",
                    "Set up a routine once and every trip appears ahead of time. Add your holidays and leave: RailWatch flags trips that clash and spots long weekends.") { RoutinePicture() }
                4 -> Step("After You Book",
                    "Check PNR status for every passenger (you type the code shown, RailWatch reads the result). Open your ticket without signal, and see what you spend. Waitlisted trips get a reminder to check again.") { AfterPicture() }
                else -> Permissions(active = pager.currentPage == 5)
            }
        }
        // Skip, dots and the main button.
        if (pager.currentPage > 0) TextButton({ done(false) }, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
            Text("Skip", color = Rw.colors.brand, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
        if (pager.currentPage > 0) Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 18.dp).semantics { contentDescription = "Page ${pager.currentPage} of 5" }) {
                for (i in 1..5) Box(Modifier.animateContentSize().height(7.dp).width(if (i == pager.currentPage) 22.dp else 7.dp).clip(CircleShape)
                    .background(if (i == pager.currentPage) Rw.colors.brand else Rw.colors.line))
            }
            if (pager.currentPage < 5) Primary("Next") { next() }
            else {
                Primary("Add My First Trip") { done(true) }
                Spacer(Modifier.height(10.dp))
                Secondary("Go to Today") { done(false) }
            }
        }
    }
}

@Composable
private fun Welcome(start: () -> Unit, restore: () -> Unit) {
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(120.dp).semantics { contentDescription = "RailWatch logo" }) { drawMonogram() }
            Text("RailWatch", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 12.dp).semantics { heading() })
            Text("Know when to book, what's booked and what to do next, for every train trip.", color = Color(0xFFE3E3FF), fontSize = 16.sp,
                lineHeight = 24.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 12.dp).width(290.dp))
        }
        Column(Modifier.align(Alignment.BottomCenter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(start, Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF3B3CC4))) {
                Text("Show Me Around", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            OutlinedButton(restore, Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B8CF6)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                Text("Restore From Backup", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun Step(title: String, body: String, picture: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 64.dp)) {
        picture()
        Text(title, color = Rw.colors.text, fontSize = 30.sp, lineHeight = 35.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 32.dp).semantics { heading() })
        Text(body, color = Rw.colors.muted, fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Rw.colors.surface).border(1.dp, Rw.colors.line, RoundedCornerShape(18.dp)).padding(16.dp)) { content() }

@Composable private fun Chip(text: String, bg: Color, ink: Color) =
    Text(text, color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 4.dp))

@Composable private fun Line(title: String, sub: String) = Column {
    Text(title, color = Rw.colors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    Text(sub, color = Rw.colors.muted, fontSize = 13.sp)
}

@Composable
private fun WindowPicture() = Card {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Travel Sat 16 Jan", color = Rw.colors.muted, fontSize = 13.sp); Chip("Opens in 2 Days", Rw.colors.attentionBg, Rw.colors.attentionInk)
        }
        val brand = Rw.colors.brand; val line = Rw.colors.line; val surface = Rw.colors.surface
        Canvas(Modifier.fillMaxWidth().height(24.dp)) {
            val y = size.height / 2
            drawLine(line, Offset(0f, y), Offset(size.width, y), 4.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(brand, Offset(0f, y), Offset(size.width * .64f, y), 4.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
            drawCircle(surface, 12.dp.toPx(), Offset(size.width * .64f, y)); drawCircle(brand, 8.dp.toPx(), Offset(size.width * .64f, y))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Today", color = Rw.colors.muted, fontSize = 13.sp); Text("Opens 08:00", color = Rw.colors.brand, fontSize = 13.sp, fontWeight = FontWeight.Bold); Text("Travel", color = Rw.colors.muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun AddPicture() = Card {
    Column {
        listOf(Triple(RwIcons.Ticket, "Ticket", "Pick an IRCTC PDF, or share it to RailWatch"), Triple(RwIcons.ChevronRight, "Journey", "A trip you still need to book"),
            Triple(RwIcons.List, "Routine", "Trips you make every week"), Triple(RwIcons.Calendar, "Holiday", "Days off, so trips plan around them")).forEachIndexed { i, (icon, t, s) ->
            Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) { IconBox(icon); Spacer(Modifier.width(12.dp)); Line(t, s) }
            if (i < 3) Box(Modifier.fillMaxWidth().height(1.dp).background(Rw.colors.line))
        }
    }
}

@Composable private fun IconBox(icon: ImageVector) = Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Rw.colors.brandSoft), contentAlignment = Alignment.Center) {
    Icon(icon, null, tint = Rw.colors.brandSoftInk, modifier = Modifier.size(20.dp))
}

@Composable
private fun RoutinePicture() = Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    @Composable fun row(t: String, s: String, chip: @Composable () -> Unit) = Card { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Line(t, s); chip() } }
    row("Every Friday · MDU → MS", "Plans months ahead") { Chip("Routine", Rw.colors.brandSoft, Rw.colors.brandSoftInk) }
    row("Fri 13 Nov", "Planned from your routine") { Chip("Booked", Rw.colors.bookedBg, Rw.colors.bookedInk) }
    row("Fri 20 Nov", "You're on leave that day") { Chip("Skip?", Rw.colors.attentionBg, Rw.colors.attentionInk) }
}

@Composable
private fun AfterPicture() = Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("PNR 1234567890", color = Rw.colors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp); Chip("WL 4", Rw.colors.attentionBg, Rw.colors.attentionInk)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Chip("P1 CNF B2 34", Rw.colors.bookedBg, Rw.colors.bookedInk); Chip("P2 WL 4", Rw.colors.attentionBg, Rw.colors.attentionInk) }
        }
    }
    Card { Row(verticalAlignment = Alignment.CenterVertically) { IconBox(RwIcons.Ticket); Spacer(Modifier.width(12.dp)); Line("Ticket Pass", "Your PDF, offline, one tap from the trip") } }
}

@Composable
private fun Permissions(active: Boolean) {
    val context = LocalContext.current
    fun openAlarms() { if (Build.VERSION.SDK_INT >= 31) context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) }
    var version by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { version++; onPauseOrDispose { } }
    val notify = remember(version) { Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED }
    val exact = remember(version) { Alarms.canExact(context) }
    val calendar = remember(version) { PhoneCalendar.allowed(context) }
    // After the notification popup, straight on to the alarms switch (Android has no popup for that one).
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { version++; if (!Alarms.canExact(context)) openAlarms() }
    // On reaching this page: give a moment to read why, then ask for what is missing, once.
    var asked by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(active) {
        if (!active || asked) return@LaunchedEffect
        kotlinx.coroutines.delay(1200); asked = true
        when {
            !notify && Build.VERSION.SDK_INT >= 33 -> askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
            !exact -> openAlarms()
        }
    }
    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        version++
        // Allowed: start with the first calendar; it can be changed in More › Phone Calendar.
        if (r.values.all { it }) PhoneCalendar.calendars(context).firstOrNull()?.let { PhoneCalendar.choose(context, it.id) }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 64.dp)) {
        Text("RailWatch Needs Two Permissions", color = Rw.colors.text, fontSize = 30.sp, lineHeight = 35.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.semantics { heading() })
        Text("Booking windows open at a set minute, so reminders only help if they arrive on time. Allow notifications in the popup, then turn on Alarms & Reminders on the page that opens and come back. Calendar is optional.", color = Rw.colors.muted, fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 12.dp, bottom = 24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Grant(RwIcons.Bell, "Notifications", "Booking, Tatkal and PNR reminders", notify, "Allow") { if (Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS) }
            Grant(RwIcons.Clock, "Alarms & Reminders", "So reminders come on the minute (no ringing, only notifications)", exact, "Allow") { openAlarms() }
            Grant(RwIcons.Calendar, "Phone Calendar", "Add trips to your calendar (optional)", calendar, "Allow") {
                askCalendar.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
            }
        }
    }
}

@Composable
private fun Grant(icon: ImageVector, title: String, sub: String, granted: Boolean, action: String, ask: () -> Unit) = Card {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBox(icon); Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) { Line(title, sub) }
        if (granted) Chip("Allowed", Rw.colors.bookedBg, Rw.colors.bookedInk)
        else Button(ask, Modifier.heightIn(min = 44.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Rw.colors.brand, contentColor = Rw.colors.brandInk)) { Text(action, fontWeight = FontWeight.Bold) }
    }
}

@Composable private fun Primary(text: String, onClick: () -> Unit) =
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Rw.colors.brand, contentColor = Rw.colors.brandInk)) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }

@Composable private fun Secondary(text: String, onClick: () -> Unit) =
    OutlinedButton(onClick, Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Rw.colors.line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Rw.colors.text)) { Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
