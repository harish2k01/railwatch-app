# RailWatch app

A native, lightweight RailWatch for one person on their own phone: plan regular train journeys, get reminded when
booking opens, keep tickets offline. No server, accounts or sync. Android first; the shared core is Kotlin
Multiplatform so iOS can follow.

- `shared/` — rules, parsing, storage and use cases (Kotlin Multiplatform; JVM tests)
- `androidApp/` — Jetpack Compose app
- `PLAN.md` — the plan and progress

Build: `./gradlew :shared:jvmTest :androidApp:assembleDebug` (JDK 17+, Android SDK 37).

Manrope is bundled under the SIL Open Font License (`MANROPE-OFL.txt`).
