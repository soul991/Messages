# Messages

An Android-first default SMS/MMS app with deterministic, on-device protection
against promotional, spam, scam, and fraud messages.

Messages keeps the Inbox calm without treating filtering as permission to lose
data: every message is stored, every filtering decision is explainable, and the
user can reverse a decision at any time.

## Download

Pre-built release APKs are available on the [Releases page](https://github.com/soul991/Messages/releases).

**[⬇ Download latest APK](https://github.com/soul991/Messages/releases/latest/download/messages-release.apk)**

> Install requires enabling "Install from unknown sources" for your browser or file manager in Android Settings → Apps.
> Minimum Android version: 8.0 (API 26).

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
  One deliberate exception, added after live abuse was observed: a message
  worded like a bank alert loses its protected status when it also carries a
  phishy link (URL shortener, suspicious domain, brand impersonation) plus
  independent scam evidence — registered sender headers are not treated as
  unconditional proof of honesty. OTP protection remains absolute.
- **Every result has a reason.** The app records matched pattern and combination
  IDs for the “Why filtered?” view, and any decision can be reversed in place
  (“Not spam” on filtered messages, “Mark as spam” on missed ones — both teach
  the local sender-reputation record).

## Honest limitation

No filter is literally impossible to evade: scammers can invent new wording.
This app reduces that risk through normalization, phrase-format matching,
sender context, combination rules, a Review folder for uncertain messages, a
never-delete policy, and a versioned pattern library that can grow with app
updates. The required standard is that every known family in
[`PRD_Messages.md`](PRD_Messages.md) is covered and no genuine message is lost.

**Locked chats.** The secret locked space hides conversations inside *this
app*, behind a knowledge-only secret code (PIN/pattern/password; salted
PBKDF2-HMAC-SHA256 ≥600k verifier, escalating attempt cooldown, no recovery
path). It cannot change how Android stores SMS: message content still lives
in the phone's shared message storage, so anyone who makes another app the
default SMS handler can read it there. Locked chats protect against casual
snooping on this app — that limitation is stated verbatim in the in-app
disclaimer the user must scroll through and accept at setup. In backups,
locked chats travel only as a separately-encrypted sub-envelope keyed to the
secret code; account access alone restores the normal chats but cannot open
the locked ones.

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

## Release builds

`./gradlew :app:assembleRelease` produces an R8-minified, resource-shrunk,
baseline-profiled APK, signed when `keystore.properties` is present — see
[`docs/ops/RELEASE_SIGNING.md`](docs/ops/RELEASE_SIGNING.md) for the keystore and
password-handling contract. On the reference device (60 Hz panel) the
release build measures 0% janky frames on list fling and chat scroll.

## Current release gates

- Google Drive backup: sign-in and scheduling are verified on-device
  (owner-registered OAuth client, see
  [`docs/ops/DRIVE_BACKUP_SETUP.md`](docs/ops/DRIVE_BACKUP_SETUP.md)); access control
  is the Google account (WhatsApp-style master-key file in the app data
  folder). Restore onto a second device is still unexercised.
- Passkey-PRF backup unlock is format-reserved but not implemented; under the
  account model it is a nice-to-have, not a gap.
- RCS is not implemented (no public API for third-party default-SMS apps);
  Play Store distribution would additionally need the SMS-permission
  declaration and a privacy policy.

For detailed scope, non-negotiable guardrails, and implementation status, see
[`PRD_Messages.md`](PRD_Messages.md) and [`PROGRESS.md`](PROGRESS.md).
New to the project? [`PROJECT_HANDOFF.md`](PROJECT_HANDOFF.md) is the
orientation layer above both. Everything else lives under
[`docs/`](docs/README.md).
