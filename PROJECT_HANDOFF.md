# PROJECT HANDOFF — "Messages" Android SMS App
_Compiled 2026-07-24. Purpose: full context for continuing development in any new chat/AI session. The repo's `PROGRESS.md` is the authoritative, detailed state — this document is the orientation layer above it._

## 1. What this project is

**Messages** — a full-featured Android default-SMS app (Kotlin, Jetpack Compose M3, MVVM) with a **100% deterministic, offline, no-server, no-ML spam/scam/fraud protection engine** (weighted regex pattern library + sender analysis + combination rules). Built almost entirely by Claude Code, steered by the owner with an advisor AI writing prompts. Spec: `PRD_Messages.md` (v2) in repo root. Personal-use app, tested on the owner's real device (60Hz panel, India, DLT-header SMS ecosystem).

**Core promises (never violate):** no message is ever lost or auto-deleted by the filter; OTP/bank/delivery ("Protected") messages can never be filtered; every verdict is explainable ("Why?" screen, matched pattern IDs); message content never leaves the device; when unsure → Review folder, not Spam.

## 2. Current state: COMPLETE & SHIPPED (personal use)

All PRD milestones M1–M5 done, plus an extended work order (Phases 0–6) finished 2026-07-24. The owner's phone runs a **signed release build** (R8-minified, baseline profile, 3.4MB APK). Performance verified: home fling p50 9ms / 0.00% janky, chat scroll p50 9ms / 0% janky — WhatsApp-class, measured over 3 consistent gfxinfo runs.

**Highlights of what exists:**
- Protection engine (`:protection-engine`, pure Kotlin/JVM): 121+ patterns, 513+ entry labeled corpus, 5 CI gates (zero protected filtered / ≥95% scam caught / promos silent / genuine never in Spam / <50ms median). Sensitivity presets, hot-swappable pattern packs, local sender reputation, first-contact ×1.25 multiplier on scam families.
- Folders: Inbox · Transactions · Promotions · Spam · Review · Blocked (+ Archived screen, Starred, Trash with 60-day restore).
- Google Drive backup (drive.appdata REST client, no heavy libs): **WhatsApp-style account-based access** — AES-256-GCM envelope, data key wrapped by a master key file stored in appDataFolder (`account-plain` wrap method); no password required; legacy password-wrapped snapshots still prompt. Optional **user-held key custody** (V2-5/V2-46): the master key can be sealed under a generated 160-bit recovery code or a password (`MasterKeyVault`, PBKDF2 600k + AES-256-GCM, KDF params AAD-bound), after which the plain key file is deleted from appDataFolder; rotation reseals the same master key so no snapshot is invalidated. Restore is additive + idempotent (dedupe: address+timestamp+direction+body-hash), "Nothing to restore" state, snapshot chooser (last 2 kept), progress %.
- Verified-sender badges: blue check for DLT -S/-T/-G + protected-lane headers, "Business" chip for other alphanumeric IDs, suppressed on fraud/Dangerous verdicts. No name lookup for unknown numbers (impossible offline — deliberate).
- ~21 parity features (Phase 4): OTP copy notification action + opt-in auto-copy, inline reply (gated on canReceiveReplies — alphanumeric headers show "can't reply" bar), per-folder notification config, per-conversation tones, TextClassifier smart actions (never on Spam/Dangerous), pinned messages, message info sheet, quick-reply templates, opt-in link previews (Inbox/contacts only), Starred screen, unread filter, mark-all-read/unread, multi-select + forward picker, text size control, conversation export, TRAI 1909 + 7726 carrier spam reporting with exact-preview dialog, §6.5 auto-clean Spam >90d (opt-in, via Trash), **persistent red fraud-warning notification** (Dangerous only, survives tap, swipe-dismissable), **fully disabled links in Dangerous messages** ("Show link" reveal in Why? screen — selectable, never tappable).
- Full UI redesign (Phase 5), grounded in measured WhatsApp/Telegram SVG references (`docs/research/DESIGN_REFS_NOTES.md`) + Truecaller research (`docs/research/TRUECALLER_ANALYSIS.md`): 54dp avatars/76dp rows/divider-free Home, timestamp+ticks inside bubbles, 76% bubble width, templates ⚡ inside composer pill, branded notification icons with OTP/amount as typographic hero, 8-seed CIELAB accent palette + Material You dynamic (default) + AMOLED tier, restyled settings, ContactDetail hero page, jump-to-top button. App icon: check-tail bubble family (chosen from on-device previews).
- Security: app lock (BiometricPrompt BIOMETRIC_WEAK|DEVICE_CREDENTIAL, auth required to disable too, Lock-after grace 0/1/5min, FLAG_SECURE, suppression for app-initiated camera/picker round trips), hide previews.
- **Secret Chats (2026-07-26): a credential-gated locked space** — second independent layer inside the app (replaces the old per-chat biometric locks; those migrate on first setup). Knowledge factor ONLY (PIN/pattern/password; PBKDF2-HMAC-SHA256 600k salted verifier; escalating cooldown after 5 fails; NO recovery). Entry: 3s hold on the Home "Messages" title. Room v8 `space` column (NORMAL/LOCKED) on messages+conversations, unique `(threadId, space)`; every normal-UI query pins NORMAL (DAO-level `SpaceInvisibilityTest`). Routing rule: once a locked conversation exists for an address, ALL incoming messages from it file there — never the normal thread. Notifications: one fixed-id generic "New message" or full silence. Backups carry locked chats only as a credential-keyed encrypted sub-envelope (`lockedEnvelope` in the backup JSON; restore without the code = opaque "Locked chats present" state). Code: `core-messaging/…/secret/`, `app/…/ui/secret/`, space-aware `MessageRepository`/`BackupManager`/`MessageNotifier`. SMS remains in the shared Telephony provider — honest limitation in disclaimer + README.
- Contact integration (fixed after the Android 11+ `<queries>` package-visibility bug silently broke ALL contact lookups): names/photos everywhere, ContactsContract observer refresh, contact detail page.

**Critical engine fix (last session):** real-world scam texts with registered DLT headers were riding the protected bank-alert lane, so fraud rules never ran. Now: protected status is stripped when bank-worded message + phishy link + scam evidence combine (OTPs remain absolute; genuine alerts keep their lane; all corpus gates green; README documents this exception honestly).

## 3. Key architecture decisions (don't relitigate without reason)

1. **Telephony provider = source of truth; Room = index.** Provider written BEFORE classification (zero message loss). Real Room migrations, schemas committed (v7+).
2. **No E2E encryption claims — SMS is carrier plaintext; impossible.** RCS E2E is Google-proprietary. Never let any session claim otherwise.
3. **MMS: receive pipeline fully working (mandatory for default-SMS role); SENDING disabled** behind `FeatureFlags.MMS_SEND_ENABLED = false` (carrier unreliability, e.g. Jio has no MMS). Code kept, not deleted. Voice notes/multi-attachment shelved with it.
4. **No server, no crowdsourced data, no ML, no ads** — positioning vs Truecaller, documented in research doc + README.
5. Drive OAuth: Android client registered in Google Cloud Console (project `messages-app`), package `com.messages.app`, debug SHA-1 registered, consent screen in **Testing mode** with owner as test user, `drive.appdata` scope. **A release-SHA-1 OAuth client must be added for the release-signed build** (see `docs/ops/DRIVE_BACKUP_SETUP.md`) — check whether this was done for the current release build.
6. JVM tests can't catch Android ICU regex differences — anything regex-new must be smoke-tested on device (historic lesson: an unbounded lookbehind crashed on-device while green on JVM).
7. Debug-only helpers exist (`app/src/debug/`): SMS injection receiver + test-residue cleanup receiver — never ship in release; invaluable for driving the receive pipeline via adb.

## 4. Environment & workflow

- **Machine:** owner's MacBook Air. Repo at `~/…/Messages` (multi-module: `:app`, `:core-messaging`, `:protection-engine`, `:design-system`).
- **Build:** `export JAVA_HOME=/opt/homebrew/opt/openjdk@17 && ./gradlew …` (system Gradle 9.6 incompatible — ALWAYS the wrapper, Gradle 8.9/AGP 8.5.2/Kotlin 2.0.20). Full check: `:protection-engine:test :core-messaging:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`.
- **Device:** wireless debugging (IP:port rotates — re-pair from phone's Wireless debugging screen when it drops; happens often mid-session). Release installs: `adb install --no-incremental -r`.
- **Signing:** keystore `~/keystores/messages-release.jks`, passwords in gitignored `keystore.properties`, docs in `docs/ops/RELEASE_SIGNING.md`. Losing these = can never update the installed release build.
- **Proven workflow:** phased work orders with explicit approval gates; every phase ends with JVM tests + build green + on-device verification + PROGRESS.md update; PROGRESS.md is the single source of truth for resuming after context clears; device-verification checklists are run by the owner (biometrics etc. need human fingers); failures reported by step number.

## 5. Open items

**Immediate (in PROGRESS.md):** Secret Chats user-driven checks — enter space with the owner's PIN, immediate re-lock on background, wrong-PIN cooldown, in-space settings, unlock-chat move-back, and the full backup → wipe → restore → secret-code gate e2e (destructive; owner's call). Then delete test thread 267 (both spaces). NOTE: the phone currently runs the DEBUG build (installed for the injector); reinstall the debug-keystore-signed release APK after verification if wanted.

**Backlog:** Play Store prep (SMS permission declaration, privacy policy, OAuth verification for drive.appdata if published); second-device/fresh-install Drive restore test; RCS (no public API — likely permanently out); MMS send revival decision; doodle chat background (deliberately skipped); passkey-PRF backup unlock (format-reserved, needs hosted RP domain); Archived screen shipped late — check polish; per-part multipart send accounting (known debt); release OAuth client SHA-1 (see §3.5).

## 6. Key repo documents

`PRD_Messages.md` (spec) · `PROGRESS.md` (authoritative state + next steps) · `README.md` (trust guarantees incl. registered-header exception) · `docs/ops/DRIVE_BACKUP_SETUP.md` · `docs/ops/RELEASE_SIGNING.md` · `docs/research/TRUECALLER_ANALYSIS.md` · `docs/research/DESIGN_REFS_NOTES.md` · `docs/design/PHASE5_DESIGN_PLAN.md` · `design-refs/` (WhatsApp/Telegram SVG extractions).
