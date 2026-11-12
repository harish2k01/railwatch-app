# RailWatch app â€” plan (Android first, iOS-ready)

Repo: **`railwatch-app`** (new). A native, lightweight, phone-only RailWatch for one person, with no server, accounts
or sync. The web app and the homelab deployment stay as they are and are separate.

Design source: the canvas row **"Native mobile direction (proposal)"** (NativeToday, NativeJourneys,
NativeJourneySheet, NativeBookingDay, NativeTicket), plus the existing mobile artboards for screens it doesn't cover.

---

## Progress

| Milestone | Status | Notes |
|---|---|---|
| M0 Foundations | Done | Repo, Gradle 9.4.1 / AGP 9.2.1 / Kotlin 2.2.10, KMP shared module, Compose app with the web palette, Manrope, code-drawn icons, large title, floating tab bar, CI. Runs on the second test phone (Android 16, 10.10.10.7, wireless ADB). The Redmi Note 7 needs "Install via USB" enabled in MIUI Developer options before it can install. |
| M1 Core rules and storage | Done | Models (web JSON shape), booking day / instant / Tatkal, routine generation (weekly, every N weeks, daily, monthly by date or weekday incl. last, yearly; months or count horizon; end and excluded dates), saveRule / removeRule / extendRoutines, lifecycle (complete, cancel dates, archive), transitions, trip timing. Parity: 6 fixture suites exported from the web code pass. Storage: one JSON document (decision change below). 10 tests. |
| M2 Today, Journeys, sheet, new journey | Done | Today (boarding pass, Do next, this week, first-run routine prompt), Journeys (Upcoming / To book / History, months, status dots, swipe Booked / Skip / Cancel), journey sheet, New journey, routines, You. Checked on the test phone. From feedback: routines ask no times (times come from tickets); return trip is an optional switch; a routine can be linked to another (two-way) for separate onward and return routines; many open bookings show as one "N trips to book" card; the sheet says "Booking opened" for past dates and uses the planned blue. |
| M3 Tickets | Done | Share target (SEND, SEND_MULTIPLE, VIEW for PDFs) as the only way in; PDFs stored privately; read on the phone with PdfRenderer + ML Kit text (word boxes rebuilt into rows/columns like the web) + ML Kit QR, through Play services (no bundled models). Import sheet proposes the journey (matched / check / new booked journey) and attaches in one save. Tickets tab by month, ticket pass (brightness up, coach and berth large, Share via FileProvider), in-app PDF viewer with the QR. Retention deletes PDFs N days after a trip ends. Checked on the phone with a real IRCTC ticket: every field read correctly and it matched its journey automatically. |
| M4 Reminders and booking day | Done | Shared reminder schedule (web rules: evening/morning/opening, Tatkal AC/non-AC, cancellation follow-up, quiet hours) with tests; exact alarms (inexact fallback), two channels, reboot/update/time-change rescheduling; notification opens the booking-day countdown (Open IRCTC, Booked, Try Tatkal, Skip). Verified on A001. |
| M5 Calendar, time off, spend, settings | Done | Calendar is its own tab (replaces Tickets; tickets open from Today's Show ticket and each journey); Today has a 7-day strip. Shared time-off ICS/CSV import, conflicts and spend with tests; Calendar segment in Journeys (month grid, travel/booking/time-off, day agenda); You: time off (add, import, Keep), spend by month, reminder toggles, quiet hours, ticket retention, week start. |
| M6 PNR, widget, backup | Not started | Next. The owner's web planner and 14 ticket PDFs were moved onto the test phone (12 Nov) by hand over adb; the in-app backup restore comes with M6. Goal: once the PNR check lands, every flow works end to end in the app. |
| M7 Polish and release | Not started | |

### Feedback round, 12 Nov (done in phases before the rest of M6)

| Phase | What | Status |
|---|---|---|
| A | Show the arrival **date above the time** everywhere instead of "+1"; gap between the last item and the tab bar on every screen; smooth Journeys scrolling; Journeys lists **Upcoming / Planned (grouped by when booking opens) / History**, plus a way to see **Cancelled**; "Not booked" only when the trip has no PNR and no ticket | Done: lists are Upcoming / Planned / Cancelled / History; scrolling 0.4% janky frames on the A001 (new optimised `preview` build type for testing) |
| B | **+ asks what to add**: journey, routine, holiday/leave or ticket; adding a ticket from a PDF picker reads it, fills every detail and links it to its journey, creating the journey for that date if none exists; PNR checks handle **every passenger** (WL, RAC, CNF, partly cancelled shown per passenger and flagged; whole PNR cancelled moves the trip to Cancelled) | Done (time-off sheet also edits) |
| C | **More** redesigned: proper screens, not plain lists. Settings separate from Routines / Holidays / Tickets / Spend. **Spend** with a monthly chart, totals, averages and per-trip detail. Holidays editable after adding. Every setting explains what it does (e.g. "Plan routines 6 months ahead: trips are created this far ahead so their booking days are known") | Done: More is four tiles (Routines, Holidays & leave, Tickets, Spend) with Settings below; routine cards; Spend with totals and a 12-month chart; holidays open to edit; planning settings explain themselves |
| D | Reminders as customisable as the web: reminder times per kind (evening / morning / opening, with times), booking-schedule rows (days before + time) for general and Tatkal, cancellation time, quiet hours. **Per-journey override** in New journey and Edit, defaulting to the settings | Done: general booking as simple times or own schedule, Tatkal AC and non-AC schedules, cancellation time, quiet hours, each explained; per-journey reminders (as in settings / custom / off) in New journey and Edit, with a preview of when they arrive; routine overrides honoured as on the web |
| E | **Notifications inbox** (bell on Today: reminders sent, PNR changes, cancellations); **animations** where they help (sheet and list transitions, status changes, toast, countdown); **logo options** drawn on the design canvas to choose from | Not started |

## 1. Decisions

| Area | Choice | Why |
|---|---|---|
| Language / UI | Kotlin + **Jetpack Compose** (Material 3, custom RailWatch theme) | Native, small (target APK < 10 MB), fast start, no web view |
| Shared code | **Kotlin Multiplatform** module `shared/` for domain rules, parsing, storage and use cases | iOS later reuses it with a SwiftUI shell; no second rewrite of the rules |
| Storage | **One JSON document** (the web planner/backup shape), private file written atomically | Decided in M1 instead of SQLDelight: lighter (no database library), backup and restore are a file copy, a few hundred journeys load in milliseconds |
| Dates | `kotlinx-datetime`, all booking maths in IST | Same as the web app's rules |
| Networking | **Ktor client**, only for the PNR check | The app talks to nothing else |
| DI | Manual constructor wiring (small app) | No framework weight |
| Navigation | Navigation Compose with type-safe routes | Standard, back-stack aware |
| Ticket reading | Android **PdfRenderer** + **ML Kit** text recognition and barcode scanning (on device); iOS later: PDFKit + Vision | Faster and more accurate than browser OCR; no network |
| Reminders | **AlarmManager** exact alarms (`setExactAndAllowWhileIdle`) with a WorkManager fallback; notification channels | Fires with the app closed; respects Doze as far as Android allows |
| Widget | **Glance** app widget | Next trip on the home screen |
| Min SDK / target | minSdk 26 (Android 8), target latest | Covers the Redmi Note 7 (Android 10) and newer |
| Distribution | Signed APK first (sideload); Play Store later | No store account needed to start |
| Parity with the web | **Shared test fixtures**: JSON cases (routines â†’ journeys, booking dates, fares from OCR text, ticket fields) exported from the web repo and run in both test suites | Guards against the two rule copies drifting |

Repo layout:

```
railwatch-app/
  shared/            Kotlin Multiplatform: domain/, parsing/, data/ (SQLDelight), usecases/, fixtures tests
  androidApp/        Compose UI, ML Kit, PdfRenderer, alarms, widget, share target
  iosApp/            (later) SwiftUI shell over shared/
  fixtures/          JSON parity cases synced from the web repo
```

---

## 2. Features (everything the web app does, standalone)

| Feature | In the app | Notes |
|---|---|---|
| Routines | Create, edit, pause, linked onward/return; generates journeys N months ahead | Same horizon setting (months or count) |
| Journeys lifecycle | Planned â†’ booking open â†’ booked â†’ travelled; cancellation needed â†’ cancelled; skipped; archive after N days | Same rules and retention settings |
| Booking rules | Normal window (default 60 days), Tatkal (day before, 10:00 AC / 11:00 non-AC), train origin date | Shared rules |
| Time off | Leave and company holidays; ICS / CSV import from a file; conflicts with planned trips ("skip or keep?") | Import via the system file picker |
| Today | Boarding-pass next trip, Do next cards, this week | NativeToday |
| Journeys | Upcoming / To book / History, month headings, status dots, swipe actions (Ticket, Cancel, Booked, Skip) | NativeJourneys |
| Journey sheet | Bottom sheet with quick actions (Ticket, Check PNR, Share, Edit), progress, ticket details, PNR card, notes | NativeJourneySheet |
| New / edit journey | Bottom-sheet form; station search over the bundled list; app date and time pickers | |
| Calendar | Week strip + agenda, month view; layers (journeys, booking dates, time off) | |
| Booking day | Full-screen countdown, checklist, Open IRCTC, "I've booked it" (status + PDF), Try Tatkal, Skip | NativeBookingDay |
| Tickets | Share the IRCTC PDF to RailWatch (one or several); the app reads train, PNR, coach, seat, berth, class, status, fare and arrival from text, QR and OCR and matches the journey | The only way in; ML Kit; same parsing rules via fixtures |
| Ticket pass | Full-screen pass, offline, brightness up, Open PDF, Share | NativeTicket |
| Ticket vault | All stored PDFs by month; PDFs kept N days after travel (setting) | App-private storage |
| Spend | Month / 6 months / year, by month, where it goes (fare, fees, wallet, insurance), refunds | Shared fare rules |
| PNR status | Check on tap: show the enquiry's CAPTCHA, you answer, app fetches from the phone; suggestions near travel; cancelled â†’ asks first | Same honest client as the web; may stop working if the site changes |
| Reminders | Booking opens (configurable lead times), cancellation follow-ups, quiet hours, per-type channels | Phone notifications only |
| Notifications centre | In-app list of reminders and changes, cleared when resolved | |
| Widget | Next trip, booking countdown on the day | Glance |
| Backup / restore | Export and import a file compatible with the web app's backup (journeys, routines, time off, settings, ticket PDFs) | Move between phone and web this way |
| Settings | Reminder times, booking window, routine horizon, retention, theme (dark / light / system), first day of week | Phone-only settings, no accounts |
| Stations | Bundled station list (NTES-derived); updated with app releases or a downloaded refresh | Same data as the web |
| First run | Set up a routine, reminder times, notification permission | |

Kept simple on purpose: one person, one phone, no server. No accounts, users, admin or sync, now or later. Tickets come
in only by sharing the PDF to the app (the system Share button, from Gmail, Files or anywhere). No email tickets, and
reminders are phone notifications only (no Telegram, WhatsApp or email).

---

## 3. Milestones

Each milestone ends with a signed APK installed on the Redmi Note 7 and checked against the canvas.

**M0 â€” Foundations**
Repo, Gradle (version catalog), CI (build, unit tests, lint), signing config, theme tokens from the RailWatch palette
and Manrope, Compose component kit (large title, floating tab bar, sheet, boarding pass, segmented control, swipe row,
status dot, app date/time pickers). Fixture export script in the web repo.
*Done when:* an empty app with the tab bar and theme installs; CI green.

**M1 â€” Core rules and storage**
Port the domain to `shared/`: journeys, routines and generation, booking/Tatkal dates, lifecycle and retention, time off
and conflicts, stations lookup. SQLDelight schema and migrations. Parity fixtures passing.
*Done when:* all fixtures pass on JVM; data survives app restarts.

**M2 â€” Today, Journeys, journey sheet, new journey**
NativeToday, NativeJourneys (with swipe actions), NativeJourneySheet, new/edit journey sheet, routines screens, first run.
*Done when:* a routine creates journeys, they show on Today and Journeys, swipe actions work, everything persists.

**M3 â€” Tickets**
Share target for IRCTC PDFs (one or several at once) as the only way tickets come in, ML Kit text + QR, the shared
parsing (fare reconciliation, arrival, berth), journey matching, ticket vault, ticket pass, retention.
*Done when:* the 11 test PDFs parse to the same results as the web app (fixtures), and the pass works in airplane mode.

**M4 â€” Reminders and booking day**
Alarm scheduling for booking-open and cancellation reminders, quiet hours, notification channels, exact-alarm
permission flow, notifications centre, NativeBookingDay.
*Done when:* reminders fire with the app closed (checked after a reboot and in Doze), booking day flows to booked.

**M5 â€” Calendar, time off, spend, settings**
Calendar (week/agenda/month), time off with ICS/CSV import and conflicts, Spend, settings.
*Done when:* feature parity with the web for these screens.

**M6 â€” PNR, widget, backup**
PNR check with the user-answered CAPTCHA, suggestions, ask-before-cancel; Glance widget; backup/restore compatible with
the web app.
*Done when:* a web backup restores on the phone and back.

**M7 â€” Polish and release**
Accessibility (TalkBack labels, 48 dp targets, contrast), light theme, motion, startup and size budget, crash-free
checks, release APK; Play Store listing optional.
*Done when:* release APK under 10 MB, cold start under 1 s on the Redmi Note 7.

**Later â€” iOS**
`iosApp/` SwiftUI over `shared/` (PDFKit, Vision, UNUserNotificationCenter, a share extension for PDFs).

---

## 4. Risks and open points

- **Two rule copies (TypeScript and Kotlin).** Mitigation: shared fixtures run in both CIs; any rule change lands with a
  fixture.
- **Exact alarms on Android 12+.** `SCHEDULE_EXACT_ALARM` needs user consent; Xiaomi's MIUI also kills background work.
  Mitigation: guide the user to allow it on first run; WorkManager fallback; test on the Redmi.
- **PNR enquiry** may change or block clients. Mitigation: official-page fallback, failures logged on device.
- **Station list freshness** without a server: the list ships with each app release.
- **iOS** needs a Mac and an Apple developer account ($99/year) when we get there.
