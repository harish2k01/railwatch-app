# Accessibility

RailWatch should be usable by everyone who plans train journeys, including people who use a screen reader,
large text, or switch and keyboard access.

## Our commitment

We aim to meet [WCAG 2.2](https://www.w3.org/TR/WCAG22/) level AA, as it applies to mobile apps, and
Android's [accessibility guidelines](https://developer.android.com/guide/topics/ui/accessibility). New
screens are checked against these before release.

## What's supported

- **TalkBack:** headings, labelled buttons and icons, and messages that are read aloud when they appear.
- **Large text:** text follows the phone's font size. Layouts are built to wrap rather than cut text off.
- **Touch targets:** at least 48 × 48 dp for buttons and rows.
- **Contrast:** dark and light themes designed for readable contrast. Appearance can follow the phone or be
  set to Light or Dark.
- **No timing traps:** nothing disappears before you can act on it. Messages can be tapped away and are also
  kept in the inbox.
- **Colour is never the only signal:** statuses such as Booked, Waitlisted and Cancelled always have text.
- **Portrait orientation:** the app is locked to portrait.

## Known limitations

- A full end-to-end TalkBack review of every screen is still in progress (see the README roadmap).
- Charts on the Spend screen show values as text, but the bars themselves aren't individually announced yet.
- The PNR check needs you to read a CAPTCHA image from Indian Railways. It has no audio alternative, because
  that comes from Indian Railways' service, not RailWatch.
- The app is portrait-only, so it doesn't rotate on phones mounted in landscape.

## Supported environments

Android 8.0 (API 26) and newer, with TalkBack, Select to Speak, Switch Access, font and display size
scaling, and dark theme.

## Reporting a barrier

If something in RailWatch is hard or impossible to use, please
[open an accessibility issue](https://github.com/harish2k01/railwatch-app/issues/new?template=accessibility.yml).
Tell us the screen, what you were trying to do, and the assistive technology and Android version you use.
Accessibility issues are treated as bugs and prioritised.
