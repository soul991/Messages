# PROGRESS — "Messages" (Android SMS app with deterministic spam/scam protection)

_Last updated: 2026-07-17 (late evening — compose flow, MMS receive, M3 settings UI). Source spec: `PRD_Messages.md` (v2)._

## Current state at a glance

- **Toolchain**: installed on this Mac (OpenJDK 17 via Homebrew at `/opt/homebrew/opt/openjdk@17`, Gradle 8.9 wrapper, Android SDK cmdline-tools + platform 35 + build-tools 35 installing to `~/Library/Android/sdk`).
- **Protection engine (`:protection-engine`)**: implemented, pure Kotlin/JVM, **48 of 48 tests passing** incl. all corpus CI gates. Corpus: **506 entries**, pattern library **121 patterns** (v1). Sensitivity + library both hot-swappable.
- **Android app**: **compiles** (`:app:assembleDebug` green; `local.properties` with `sdk.dir` required). Compose flow, real MMS receive, Settings (sensitivity/rules/pattern-pack) are in.
- Run engine tests: `export JAVA_HOME=/opt/homebrew/opt/openjdk@17 && ./gradlew :protection-engine:test`

## Done

### Project scaffold
- Multi-module Gradle project per PRD §10: `:app`, `:core-messaging`, `:protection-engine` (pure Kotlin, no Android deps), `:design-system`.
- Version catalog (`gradle/libs.versions.toml`): Kotlin 2.0.20, AGP 8.5.2, Compose BOM 2024.09, Room 2.6.1, WorkManager, kotlinx-serialization.

### Protection engine (PRD §3, milestone M2 core) — `protection-engine/src/main/kotlin/com/messages/protection/`
- `Normalizer.kt` — Stage 0: NFKC, zero-width strip, homoglyph map (Cyrillic/Greek), leet map (token-aware so OTP digits and amounts survive), separator collapse (`F.R.E.E`→free), repeat collapse (letters only), URL/phone/amount/digit-run extraction (incl. scheme-less IP URLs).
- `SenderAnalyzer.kt` — Stage 3: DLT header parsing (`-S/-T/-P/-G` suffixes), personal/international/short-code/email-gateway detection, spam multipliers (personal ×1.5, international ×2 on scam families), local reputation adjustment.
- `PatternMatcher.kt` — compiled-regex cache over `patterns.json`.
- `LinkAnalyzer.kt` — §5.6 in code: shorteners, suspicious TLDs, brand-impersonation domains (with official-domain whitelist), IP-literal, punycode, `.apk` links, wa.me/t.me + money words, format signals (CAPS ratio, `!!`, emoji stuffing, forward-chains).
- `ComboRules.kt` — all 10 combination rules C1–C10 from §5.8.
- `ProtectionEngine.kt` — full pipeline: user rules → Protected (Stage 2, incl. fake-OTP-phishing warning-banner exception) → contact short-circuit (unless fraud combo) → weighted scoring → thresholds with `Sensitivity` presets (DEFAULT/RELAXED/STRICT) for the slider.
- **Pattern library** `protection-engine/src/main/resources/patterns.json` — v1, **107 patterns** across all §5 families **plus §7 additions**: charity scams, romance/gift/customs, wrong-number openers, inheritance/419, FASTag/e-challan, fake-APK prompts, screen-share apps (AnyDesk/TeamViewer, weight 12), betting apps, family-emergency money, unusual-activity baits, Hinglish variants throughout. Every pattern has ≥1 positive example; non-protected patterns also have ≥1 near-miss negative (CI-enforced).

### Engine tests — `protection-engine/src/test/`
- `OTP_and_bank_alerts_can_never_be_filtered` (the §13 named non-negotiable) — OTPs pass from *any* sender incl. international; fake-OTP phishing gets Inbox + red banner, never buried.
- Normalizer obfuscation corpus (leet, dotted, spaced, zero-width, homoglyph, repetition), sender analyzer, combo rules, scam/promo/genuine classification, explainability (matched IDs present), pattern integrity (compiles + own examples pass).
- `CorpusRegressionTest` with the §7.4/§11 CI gates: **gate1** zero protected filtered, **gate2** ≥95% scam caught, **gate3** promos silent, **gate4** genuine never in Spam, **gate5** median <50ms, corpus-size floor.
- Labeled corpus `src/test/resources/corpus.json`: 166 entries (scam / promo / genuine / protected). At last full run all classification gates were green (catch rate ≥95%, zero protected filtered, zero genuine spammed); only the corpus-size floor fails.

### Android app (milestone M1/M2, written but NOT yet compiled)
- `app/` manifest with all 4 mandatory default-SMS components (§10): `SmsDeliverReceiver` (receive→store→classify→notify with goAsync), `MmsDeliverReceiver` (full download/parse pipeline, see below), send-to activity intent filters on `MainActivity` (data URI honored), `HeadlessSmsSendService`; RoleManager default-SMS request flow in `MainActivity`.
- `:core-messaging`: Room DB (messages index with category/labels/matched-pattern-IDs, conversations, sender reputation, user rules), `MessageRepository` — writes incoming SMS to the system Telephony provider FIRST (zero message loss), then classifies (loads `patterns.json` from assets), updates conversation; `moveToInbox`/`moveToSpam` with reputation adjustment (§6.3); user-delete-only DAO (filter never deletes, §6).
- `MessageNotifier`: channels personal/transactions/review only — Promotions/Spam/Blocked have **no channel** (silent, badge-only per §4); Review gets one quiet batched notification; fraud-warning text on dangerous messages.
- UI (Compose M3): `HomeScreen` — collapsible large title, search bar, folder chips (Inbox·Transactions·Promotions·Spam·Review·Blocked) with unread badges, avatar-first conversation rows, category-hued avatars (fraud=red/promo=amber/protected=green per §9), spam-search results under separator, polished empty states ("No spam today — enjoy the silence"), default-app banner. `ChatScreen` — bubbles with tails, fraud warning banner on dangerous messages, OTP copy chip (§8.2), Not-spam / Why? actions, resend-on-failure, composer. `Theme.kt` — Material You dynamic color, light/dark/AMOLED.

## In progress

_(nothing mid-flight)_

### Recently completed (2026-07-17, this session — commits `fc757ce`, `63ff679`, `84cdf00`)
- **New-message compose flow** (`app/ui/compose/NewMessageScreen.kt`): FAB → recipient picker (contacts from the Contacts provider, deduped, name/number filter, "Send to \<number\>" row for raw numbers) → `repo.threadIdFor` resolves/creates the system thread → `chat/{threadId}?address={address}`; ChatViewModel takes a fallback address for threads with no conversation row (the row is created on first send, so no empty threads in the list). ACTION_SENDTO `sms:/smsto:/mms:/mmsto:` intent data now honored (was silently ignored).
- **Real MMS receive** (replaced the stub): `MmsPduParser` (core-messaging/mms/, minimal WSP/MMS binary parser for m-notification-ind + m-retrieve-conf, generic header-skip for carrier quirks) → `SmsManager.downloadMultimediaMessage` into a FileProvider cache file → `MmsDownloadReceiver` parses, `repo.onIncomingMms` writes Telephony.Mms provider first (pdu+parts+addr), dedupes by transaction ID, classifies the text part, notifies. Undownloadable MMS stored as a placeholder (never-lose). First media attachment saved to filesDir; ChatScreen renders images via Coil. Room bumped to v2: nullable `smsId` (fixes latent unique(-1) collision), `mmsId`/`mmsTransactionId`/`mediaUri`/`mediaMimeType`.
- **M3 settings UI** (`app/ui/settings/SettingsScreen.kt`, gear on Home): 3-step sensitivity slider wired to `Sensitivity` presets (persisted in prefs, hot-applied via new `engine.updateSensitivity`); allow/block/custom-rules management (add dialog with target/category chips, delete, Flow-backed); pattern-pack import via SAF (validate → persist `patterns_imported.json` → `engine.updateLibrary`) with revert-to-bundled. `SensitivityUpdateTest` added (48 tests).

### Previously completed (2026-07-17, earlier session)
- First `:app` build compiling (missing `local.properties` + coroutine imports in `OtherReceivers.kt`).
- `WhyFilteredScreen.kt` (`app/ui/why/`) — verdict header, message card, explanations + pattern IDs from `MessageEntity`, not-spam action.
- First-run backfill worker (`core-messaging/.../backfill/BackfillWorker.kt`) — newest-first keyset pagination over Telephony provider, checkpointed to prefs (resumable), no notifications/unread bumps; enqueued from `MainActivity` once READ_SMS granted; progress exposed for onboarding.
- Onboarding (`app/ui/onboarding/OnboardingScreen.kt`) — 3 pages (intro → set-default via RoleManager, auto-advance → done with live backfill counter); one-time prefs flag.
- Corpus 166→**506**; floor 170→**500**; `DebugMissesTest.kt` deleted; 14 new patterns (419/inheritance, cyber-cell/contraband, compensation-fund, N-hour block, IVR press-digit, electricity/meter, job fees, IPO allotment, charity-to-UPI, wrong-number openers, mistaken-transfer clawback, lost-phone/stranded-abroad emergencies). All gates green.
- **Bug fix:** `patterns.json` is a JVM resource in the engine jar, not an Android asset — repository now loads it via classloader (was a guaranteed first-SMS crash).

## Next (per PRD §12 milestones)

- **Finish M1/M2 (immediate):**
  1. MMS **send** (receive is done) and dual-SIM send.
  2. Group messaging.
- **M3 — remaining:** GitHub Actions CI running the corpus gates (slider/rules/import UI done).
- **M4 — extras:** OTP auto-delete after 24h (opt-in, `expiredOtps` DAO query already exists), scheduled send, snooze, protection-stats dashboard + widget, app lock (biometric dep already declared), backup/restore, conversation bubbles/shortcuts.
- **M5 — polish/parity:** RCS via available Android APIs, per-chat customization, animation pass (spring transitions, 120Hz), accessibility pass, Play Store SMS-permission declaration + privacy policy.

## Decisions made that are not in the PRD

1. **Stage-2 protected-lane gating**: §5.7 says bank/delivery/bill protection applies from registered headers. Implemented as: OTP patterns protect from **any** sender (absolute, per §5.7); BANK/DELIVERY/BILL/TRAVEL/GOV patterns ride the protected lane only when the sender is a registered DLT header, alphanumeric header, short code, or saved contact — a personal/international number saying "your parcel shipped, pay fee" falls through to scoring. This closed a real bypass found in testing (gift-parcel scam from +1 number matching "shipped").
2. **Leet-map safety rule**: leet substitution applies per token, only when letters ≥ digits, and symbol leets (`@ $ !`) convert only when followed by a letter — so "Offer!" keeps its `!`, OTP digits and `₹5,000` amounts are never rewritten.
3. **Repeated-char collapse restricted to letters** — collapsing digits corrupted amounts/OTPs ("4,999"→"4,99").
4. **KBC pattern requires amount/link/call context** (not the bare word "KBC") so TV-show chatter from friends isn't flagged; same philosophy applied to "digital arrest" (negative-lookahead for "scam" so articles *about* the scam don't match), "sasta"→"sabse sasta", etc.
5. **Engine library JSON id/family conventions**: families are kebab-case strings centralized in `Families`; link/format signals are synthetic pattern IDs (`link-shortener`, `format-caps`…) so the Why? screen can explain them uniformly.
6. **Room is an index, Telephony provider is source of truth** (per §10); incoming messages are written to the provider *before* classification so a crash mid-pipeline can't lose a message.
7. **Thresholds**: Sensitivity presets RELAXED (18/13/7) and STRICT (12/8/4) around the PRD's DEFAULT (15/10/5). C10 promo-pileup routes to Promotions even when raw score < review threshold.
8. **`fallbackToDestructiveMigration()`** on the Room index during development — acceptable only because the Telephony provider holds the real data; must be replaced with real migrations before release.
9. **Versions**: minSdk 26, targetSdk/compileSdk 35, Kotlin 2.0.20, AGP 8.5.2 with Gradle 8.9 (system Gradle 9.6 is incompatible with AGP 8.5 — always use `./gradlew`).
10. **MMS receive**: hand-rolled minimal WSP/MMS PDU parser (`MmsPduParser`) instead of a library — parses only the two PDUs a receiver sees (m-notification-ind, m-retrieve-conf), skips unknown headers via the generic WSP rule. No m-notifyresp-ind ack is sent; carrier redelivery is handled by transaction-ID dedupe instead. MMS **send** still pending.
11. **Sensitivity setting** lives in SharedPreferences `"settings"` (key `sensitivity` = DEFAULT|RELAXED|STRICT); an imported pattern pack is persisted as `filesDir/patterns_imported.json` and wholesale-replaces the bundled library at engine creation (revert deletes the file and hot-reloads bundled).

## Known gaps / debt

- `SmsSentReceiver` multipart send reuses one PendingIntent for all parts (fine for status, not per-part accounting).
- No delivery reports, drafts, scheduled send, MMS send, group-MMS, or dual-SIM send yet.
- MMS: no m-notifyresp-ind ack (dedupe covers redelivery); only the first attachment is surfaced in the chat UI (all parts are in the provider); audio/video attachments show as a mime-label row, not players.
- `MainActivity` is `singleTask` but has no `onNewIntent` — notification taps and SENDTO while the activity is alive don't navigate.
- App icon is a placeholder vector.
