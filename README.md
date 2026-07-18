# Messages

An Android-first default SMS/MMS app with deterministic, on-device protection
against promotional, spam, scam, and fraud messages.

Messages keeps the Inbox calm without treating filtering as permission to lose
data: every message is stored, every filtering decision is explainable, and the
user can reverse a decision at any time.

## Trust promises

- **No AI or cloud classification.** Classification is offline and deterministic:
  a versioned word, phrase, regex, link, and sender-context pattern library.
- **Nothing is silently deleted or buried.** Spam, Promotions, Blocked, and
  Review remain browsable and searchable. User deletions go to Trash for 60
  days; the only opt-in exception is expired OTP cleanup for unstarred
  OTP-labelled Inbox messages.
- **Protected messages come first.** OTPs, qualifying bank alerts, deliveries,
  travel, bills, and government alerts are protected from normal filtering.
  Suspicious links from unregistered senders receive a visible warning instead.
- **Every result has a reason.** The app records matched pattern and combination
  IDs for the “Why filtered?” view.

## Honest limitation

No filter is literally impossible to evade: scammers can invent new wording.
This app reduces that risk through normalization, phrase-format matching,
sender context, combination rules, a Review folder for uncertain messages, a
never-delete policy, and a versioned pattern library that can grow with app
updates. The required standard is that every known family in
[`PRD_Messages.md`](PRD_Messages.md) is covered and no genuine message is lost.

## Development

Requirements: JDK 17, Android SDK platform 35, and a local `local.properties`
with `sdk.dir` set. The wrapper, not the system Gradle installation, is the
supported build path.

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew :protection-engine:test :core-messaging:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
```

The project is split into:

- `:protection-engine` — pure Kotlin normalization, matching, combinations, and scoring.
- `:core-messaging` — Telephony-provider integration, Room index, backups, search, and retention workers.
- `:app` — default-SMS components, Compose UI, notifications, scheduling, MMS, and device integrations.
- `:design-system` — Material 3 theme, motion, and shared visual primitives.

## Current release gates

- Google Drive backup needs Android OAuth-client registration before it can be
  tested end to end. See [`docs/DRIVE_BACKUP_SETUP.md`](docs/DRIVE_BACKUP_SETUP.md).
- Passkey-PRF backup unlock is format-reserved but not yet implemented; the
  encrypted password route is available.
- Default-role, MMS, multi-SIM, Doze/reboot, and restore flows still require
  physical-device verification before a production release.

For detailed scope, non-negotiable guardrails, and implementation status, see
[`PRD_Messages.md`](PRD_Messages.md) and [`PROGRESS.md`](PROGRESS.md).
