# Security Policy

## Supported versions

Only the [latest release](https://github.com/harish2k01/railwatch-app/releases/latest) receives security
fixes. Updates install over earlier versions and keep your data, so please stay current.

## Reporting a vulnerability

**Please don't report security problems in public issues.**

Report them privately through GitHub:
[**Report a vulnerability**](https://github.com/harish2k01/railwatch-app/security/advisories/new)
(Security tab › Report a vulnerability).

Please include:

- what the problem is and what an attacker could do with it
- the steps to reproduce it, and the app version and Android version
- any proof of concept, using made-up data only: no real tickets, PNRs or personal details

## What to expect

- You'll get an acknowledgement within 3 days.
- You'll get an assessment and a plan within 14 days.
- Fixes ship as a new release, and you'll be credited in the advisory if you'd like to be.

## Scope

In scope:

- the RailWatch Android app and its release APKs
- how it stores your plans, tickets and backups on the phone
- the requests it makes, such as the PNR check
- the build and release workflows in this repository

Out of scope:

- issues in IRCTC, Indian Railways or other third-party services
- problems that need a rooted or already compromised device
- social engineering

## Release integrity

Every release APK is signed with the project's release key and published with a SHA-256 checksum. Check a
download with:

```bash
sha256sum -c RailWatch-x.y.z.apk.sha256
```
