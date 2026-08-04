# Review findings — Messages

**Branch:** `codex/s`
**Reviewed:** 2026-07-18
**Scope:** product guardrails, default-SMS reliability, MMS intake, release readiness, and M5 polish.

This document distinguishes fixes already applied in this branch from work that
still needs a device, owner action, or a future implementation pass.

## Applied findings

### F-01 — MMS could disappear from the app after a classifier failure

**Severity:** High — violates the “never lose, never bury” guardrail.

Incoming SMS already defaulted to Inbox if the classifier crashed. Incoming MMS
did not: it wrote to the Telephony provider, then directly called `classify()`.
If that threw, the Room index, folder placement, and notification could be
skipped. The MMS receiver also had several silent early-return paths for missing
or malformed metadata.

**Resolution:** applied. All incoming-message paths now use the same safe
classification fallback. Parse/download/processing failures produce a visible,
recoverable placeholder; an unknown sender is explicit rather than silently
dropped.

**Relevant files:**

- `core-messaging/src/main/kotlin/com/messages/core/MessageRepository.kt`
- `app/src/main/kotlin/com/messages/app/receiver/OtherReceivers.kt`
- `app/src/test/kotlin/com/messages/app/receiver/MmsReceiveFallbackTest.kt`

##

### F-02 — MMS attachment filename collision

**Severity:** Medium — two attachments with the same timestamp and byte count
could overwrite each other in local storage.

**Resolution:** applied. Media filenames now include a UUID.

**Relevant file:** `core-messaging/src/main/kotlin/com/messages/core/MessageRepository.kt`

##

### F-03 — Theme modes existed but were not user-selectable

**Severity:** Medium — an explicit M5/PRD appearance gap.

`MessagesTheme` supported System, Light, Dark, and AMOLED modes, but the root
composition always used the default mode and Settings exposed no control.

**Resolution:** applied. Settings now has an Appearance picker. The selection
persists in `settings` preferences and immediately updates the root Material
theme.

**Relevant files:**

- `app/src/main/kotlin/com/messages/app/ThemePreferences.kt`
- `app/src/main/kotlin/com/messages/app/MainActivity.kt`
- `app/src/main/kotlin/com/messages/app/ui/settings/SettingsScreen.kt`

##

### F-04 — Missing root README and inaccurate project status

**Severity:** Medium — the PRD explicitly requires an honest engineering note
in a README. `PROGRESS.md` also contained stale/conflicting corpus, pattern,
feature, and build-status claims.

**Resolution:** applied. A root `README.md` now explains trust guarantees,
limitations, build steps, and known release gates. `PROGRESS.md` now records
the current corpus/pattern figures and this branch’s fixes.

**Relevant files:** `README.md`, `PROGRESS.md`

## Remaining release risks and known debt

### R-01 — Physical-device verification remains mandatory

**Severity:** High for release readiness.

The JVM suite and debug build cannot validate the default-SMS role, carrier MMS
behavior, dual SIM, reboot/Doze behavior, notification delivery, or Android ICU
regex compatibility. A real-device matrix is required before release.

**Owner action:** connect a test device and verify receive/send SMS and MMS,
backfill, reboot, Doze, default-role switching, dual-SIM selection, scheduled
send, undo, drafts, delivery reports, and all notification categories.

##

### R-02 — Google Drive backup is not yet testable end to end

**Severity:** High for the backup feature; not a blocker for offline messaging.

Google Sign-In requires Android OAuth-client registration for package
`com.messages.app` and the signing SHA-1. Passkey-PRF backup unlock is still
format-reserved rather than implemented.

**Owner action:** follow `docs/ops/DRIVE_BACKUP_SETUP.md`, then test sign-in,
manual backup, automatic checkpoint backup, password restore on a second
profile/device, and failure recovery.

##

### R-03 — MMS protocol and media feature debt

**Severity:** Medium.

The app does not send `m-notifyresp-ind`; only the first attachment receives a
full chat presentation; gallery selection is single-image; and audio/video
attachments are labels rather than players. Transaction-ID dedupe mitigates
redelivery but does not replace a full protocol implementation.

##

### R-04 — Multipart/group SMS delivery accounting

**Severity:** Medium.

Multipart and group sends share a `PendingIntent`, so a partial recipient/part
failure can mark the whole message as failed. This needs per-part and
per-recipient result tracking.

##

### R-05 — Release configuration and compliance still needed

**Severity:** High for Play Store submission.

- Prepare the Play SMS-permission declaration and a privacy policy.
- Resolve the AGP 8.5.2 warning for compile SDK 35 through a tested toolchain
  upgrade (do not merely suppress it).
- Complete RCS, per-folder notification controls, and the remaining bubbles /
  shortcuts polish only after the reliability matrix is green.

## Verification performed on this branch

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew :app:testDebugUnitTest :core-messaging:testDebugUnitTest :protection-engine:test :app:assembleDebug
```

Result: **BUILD SUCCESSFUL** after the fixes documented here.
