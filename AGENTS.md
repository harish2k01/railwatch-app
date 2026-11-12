# AGENTS.md

Guidance for any AI coding agent (Claude, Copilot, Codex, Gemini, Cursor and others) and for humans working in this repository. Read it before changing anything.

## What this is

RailWatch is a standalone, offline-first Android app for planning Indian Railways journeys: booking-window
reminders, routines, offline tickets and PNR status. It has no server, account, analytics or ads. The shared
core is Kotlin Multiplatform so an iOS app can follow.

| Path | Purpose |
|---|---|
| `shared/` | Kotlin Multiplatform core: planning rules (`domain/Rules.kt`), reminders (`Reminders.kt`), PNR (`Pnr.kt`), time off and spend (`TimeOff.kt`), screen views (`Views.kt`), ticket parsing, storage (`data/PlannerStore.kt`). **All business logic goes here, with tests.** |
| `shared/src/commonTest`, `jvmTest` | Unit tests and parity tests against `fixtures/rules.json` (synthetic). |
| `androidApp/` | Jetpack Compose UI (`ui/`), Android integrations (`data/`: alarms and notifications, widget, phone calendar, PNR client, ticket files, backup). |
| `.github/workflows/` | `ci.yaml` (pull requests) and `release.yaml` (every merge to `main` makes a release). |

## Build and check

JDK 17+ and Android SDK 37.

```bash
./gradlew :shared:jvmTest :androidApp:lintDebug :androidApp:assembleDebug   # what CI runs
./gradlew :androidApp:assemblePreview    # optimised, debug-signed build for a test phone
```

Lint errors fail CI. Fix them, don't suppress them.

## Hard rules

- **No personal data, ever.** Never commit or embed real tickets, PDFs, PNRs, names, phone numbers or backups,
  including in tests, screenshots or fixtures. Use obvious dummies (`1234567890`, `TEST USER`, `AAA → BBB`).
- **No scraping** of IRCTC, ixigo, RailYatri, erail, indiarailinfo or similar. The only network calls are the
  Indian Railways PNR enquiry, which runs only when the user taps Check PNR, and the user types the CAPTCHA.
  Never solve or bypass CAPTCHAs.
- **No new network calls, trackers, analytics or servers** without the maintainer's agreement. Data stays in
  app-private storage.
- **Standalone.** The app never mentions any website or other RailWatch product in its UI text.
- **Not affiliated** with Indian Railways, IRCTC or CRIS. Don't use their logos or imply endorsement.
- **Don't announce unreleased features** in the README, docs or UI. Live train running status in particular
  isn't mentioned anywhere.
- **Signing:** the release keystore lives only in repository secrets and the maintainer's private backup.
  Never commit keys, passwords or `*.jks`/`*.keystore` files.

## Product and UI conventions

- The goal: glance and know what to do next. Prefer one clear action per card.
- **Title Case** for headings, buttons and labels ("Save Backup", "Check PNR"). Plain, specific words that say
  what will happen.
- Booking opens at a **minute**, not a day: general booking at 08:00, Tatkal at 10:00 (AC) or 11:00 (non-AC)
  the day before the train starts. Use `bookingInstant()`, not just the date.
- Colours in Do Next and the widget: yellow (attention) means act now, blue (open) means coming up, green
  means booked.
- Reminders are notifications scheduled with exact alarms. Every change to the plans goes through
  `AppModel.update`, which reschedules alarms, refreshes the widget and syncs the phone calendar.
- Toasts appear above sheets (they use a popup window). Bottom sheets rather than new screens. Portrait only.
- Accessibility: 48 dp touch targets, content descriptions on icon-only controls, text that survives large
  fonts. See `ACCESSIBILITY.md`.
- Settings live under More › Settings. More holds the tiles, Settings, Help & Tour and About.

## Data and compatibility

- The planner is one JSON document (`planner.json`, `PlannerCodec`). New fields must have defaults so older
  backups still load. Never drop unknown data silently. An unreadable file is kept aside, not overwritten.
- Backups are the planner JSON plus `attachmentFiles` (base64 ticket PDFs). Keep that format compatible.
- The application ID `io.github.harish2k01.railwatch` is permanent. The Kotlin namespace stays
  `dev.railwatch.app`.

## Workflow

1. Branch from `main`. Never push to `main` directly (it's protected).
2. Keep pull requests focused. Add tests in `shared/` for rule changes.
3. Label every pull request with exactly one of `release: major`, `release: minor` or `release: patch`, or
   with `skip-release`. Merging releases automatically (tag, signed APK, generated notes).
4. Squash-merge. The PR title becomes the commit and the release-note line, so write it for users.
5. AI agents identify themselves: commit as the agent (for example `Agent Name <noreply@vendor>`) or add a
   `Co-Authored-By:` trailer naming the agent and model, and say in the pull request body that an AI tool
   helped write it. A human reviews and merges.
