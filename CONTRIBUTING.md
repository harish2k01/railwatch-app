# Contributing to RailWatch

Thanks for helping. RailWatch is a small, privacy-first Android app, so contributions keep it simple, offline
and respectful of people's data.

## Before you start

- **Bugs and ideas:** open an [issue](https://github.com/harish2k01/railwatch-app/issues/new/choose) first,
  so we can agree on the approach before you write code.
- **Security problems:** don't open a public issue. See [SECURITY.md](SECURITY.md).
- Everyone taking part follows the [Code of Conduct](CODE_OF_CONDUCT.md).

## Setting up

You need JDK 17 or newer and the Android SDK (compile SDK 37). Android Studio includes both.

```bash
git clone https://github.com/harish2k01/railwatch-app.git
cd railwatch-app
./gradlew :shared:jvmTest :androidApp:assembleDebug
```

| Path | What it holds |
|---|---|
| `shared/` | Kotlin Multiplatform core: rules, reminders, parsing, storage. Business logic goes here, with tests. |
| `androidApp/` | Jetpack Compose UI and Android integrations: notifications, widget, calendar. |
| `fixtures/` | Synthetic test cases shared with the rules. |

## Making a change

1. Fork the repository and create a branch from `main`.
2. Keep the change focused: one fix or feature per pull request.
3. Add or update tests in `shared/` for any rule or parsing change.
4. Run the same checks as CI:
   ```bash
   ./gradlew :shared:jvmTest :androidApp:lintDebug :androidApp:assembleDebug
   ```
5. Open a pull request against `main` and fill in the template.

## Guidelines

- **No personal data, ever.** Don't commit real tickets, PDFs, PNRs, names or backups. Tests use made-up
  values such as `1234567890`.
- **Offline first.** Don't add analytics, ads, trackers or a server. New network calls need a clear reason
  and must only happen when the user asks.
- **No scraping** of IRCTC or third-party railway sites. Don't automate CAPTCHAs.
- **Copy:** Title Case for headings and buttons, plain words, and say what will happen ("Save Backup", not
  "Proceed"). The app never mentions any other RailWatch product.
- **Accessibility:** touch targets of at least 48 dp, content descriptions on icon-only controls, and text
  that still works at large font sizes. See [ACCESSIBILITY.md](ACCESSIBILITY.md).
- **Code:** follow the existing style, Kotlin official conventions, and comments that explain why rather
  than what.

## Versions and releases

A maintainer adds one label to each pull request; merging it to `main` publishes a release automatically:

| Label | Meaning | Example |
|---|---|---|
| `release: major` | A breaking change | 1.4.2 → 2.0.0 |
| `release: minor` | A new feature | 1.4.2 → 1.5.0 |
| `release: patch` | A fix or improvement | 1.4.2 → 1.4.3 |
| `skip-release` | Docs, CI or a dependency bump to ship later | no release |

Your commit messages don't need any particular format. Pull requests are squash-merged using their title.

## Licence

By contributing, you agree that your contributions are licensed under the
[GNU GPL v3.0](LICENSE), the same licence as the project.
