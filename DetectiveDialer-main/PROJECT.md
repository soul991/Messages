# Detective Dialer — Living Project Doc

> **This is the single source of truth.** Any human or AI picking up this project should read
> this file first, then the code. It records the goal, the architecture, what's done, what's in
> progress, known issues + their fixes, and the staged roadmap. **Keep it updated as changes land**
> — when you finish a stage or fix a bug, edit the relevant section and the changelog at the bottom.
> Always verify claims here against the actual repo (`git status && git log --oneline`) before relying on them.
>
> _Last updated: 2026-07-08._

---

## 1. What this is

A **personal, sideloaded** Android AI phone app for an **iQOO Neo 9 Pro** (Android 16 / OriginOS 6),
package `com.personal.detectivedialer`. Not going on the Play Store. Owner: istakahamed, in India.

Today it is a **call + SMS screener**. The goal (see §6) is to grow it into a full **default-dialer,
Truecaller-class AI phone app** — but **AI-driven, not database-driven**.

**How screening works now:** when an unknown number calls, the app's `CallScreeningService` decides
`ALLOW | REJECT | SPAM` — instantly from local signals (contacts, allow/block lists, TRAI prefixes,
international policy) or, for genuine unknowns, by POSTing to a cloud backend that classifies with
Gemini (heuristic fallback). Contacts always ring through with no network call. `SPAM` is rejected
silently, `REJECT` rings silently, `ALLOW` rings normally. An FCM push delivers the verdict to history.
The OS gives the screening service only **~5 seconds** to respond, and the app **fails open to ALLOW**
on any timeout/error so a real call is never dropped by our own bug.

**Honest-scope rules (permanent):**
- **No crowd-sourced caller-ID database.** Identity comes from **contacts + CNAP** (network-verified
  KYC name) only. We cannot name arbitrary strangers.
- **No live AI conversation with callers** (infeasible on a non-root app — see §5, RISK-1). The AI
  provides spam *judgment*, *reasoning*, and *summaries* — not a live answering agent.
- **Nothing is ever silently dropped.** Contacts, allowlist, and TRAI-160 always ring through.

---

## 2. Repository layout

| Path | What |
|---|---|
| `backend/` | Node 20 + Express. `POST /screen` (Gemini + heuristic classify), `POST /screen-sms`, app JSON API (`/api/*`), FCM push, JSON-file store. Deployed on **Railway**. `npm test` = 28 tests. |
| `android/` | Kotlin, Jetpack Compose, Room, Hilt, Retrofit/Moshi. `CallScreeningService`, FCM service, default-SMS stack, Compose UI. minSdk 29 / target 34→(bumping to 36). |
| `PROJECT.md` | **This file** — the living source of truth. |
| `README.md` | Short public-facing intro; points here. |
| `android/README.md`, `android/BUILD_AND_SIDELOAD.md`, `backend/README.md` | Component-level build/run/sideload docs. |

**Android package map** (`com.personal.detectivedialer`):
- `service/` — `ScreeningService` (CallScreeningService), `AppFirebaseMessagingService` (FCM post-call
  summaries), `HeadlessSmsSendService`, `SmsDeliverReceiver`, `MmsWapPushReceiver`, `ContactsHelper`.
- `data/repository/` — `CallRepository` (single source of truth; local Room is authoritative for the
  fast decision, backend synced on demand), `SmsRepository`.
- `data/screening/ScreeningMatcher.kt` — pure-Kotlin offline tier (TRAI prefixes, domestic-vs-intl).
  Mirrors `backend/src/services/classifier.js` — **keep the two in sync.**
- `data/remote/` — `BackendApi` (Retrofit), `ApiKeyInterceptor`.
- `data/local/` — Room (DB v4, 5 entities/5 DAOs): call log, blocked, allowed, SMS, SMS sender prefs.
- `data/prefs/SettingsRepository.kt` — DataStore prefs (backend URL, API key, `gate_code`,
  `international_policy`, persona keys, screening-rules JSON cache, onboarding flag).
- `di/AppModule.kt` — Hilt. Two singleton OkHttp clients: default (15s/20s) and `@ScreenClient`
  (2s/3s/3s, for the deadline-bound `/screen` call).
- `ui/` — Compose: onboarding, dashboard, call detail, lists (allow/block), messages (list/thread/
  compose/blocked), settings. `MainActivity` + `AppNav` (deep links for call/sms notifications + SMS
  composer entry points).

**Backend map** (`backend/src/`): `server.js` (Express app, health at `/` and `/healthz`, optional
`X-Api-Key` auth), `config.js`, `routes/{screen,screenSms,api}.js`, `services/{classifier,gemini,
fcm,delivery}.js`, `store/db.js` (JSON-file), `utils/logger.js`.

---

## 3. Current status

### ✅ Done & verified
- **Backend hardening** — `cd backend && npm test` green (**28 tests**). Model `gemini-flash-lite-latest`,
  Gemini timeout race, respond-before-FCM-push, optional `X-Api-Key` auth, `POST /api/device` token
  registration, stricter `normalizeNumber`, Twilio purged.
- **Android builds clean** — JDK 17 (Temurin) at `/Library/Java/JavaVirtualMachines/temurin-17.jdk`;
  `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleDebug` compiles zero-error. R8 release
  build config in place (debug-signed for sideload).
- **Verified on-device (iQOO Neo 9 Pro)** — event-driven screening fires; FCM verdicts arrive. This is
  the `4f08841` checkpoint. `dc33982` added a Truecaller-style UI redesign (presentation only).
- **4 India features implemented in code** (device-testing was pending, partially overtaken by Phase 2):
  1. TRAI 140/160 prefix tier (`data/screening/` + `PREFIX_RULES` in `classifier.js` + `GET /api/screening-rules`).
  2. International call policy (Settings + ScreeningService; default SILENCE).
  3. CNAP caller name (`callerId` flows through `/screen` + `CallDto`).
  4. Default-SMS stack (receivers, headless send service, Blocked folder, `/screen-sms`, `-P/-S/-T/-G` suffix rules).

### 🟢 In progress — Phase 2 (this effort)
See §6 for the full roadmap. Current work: **Stage 0** (SDK bump to 36) and **Stage A** (fix the
inline-verdict latency bug). Status tracked in the changelog (§8).

---

## 4. Known issues → fixes

| # | Issue | Root cause | Fix | Status |
|---|---|---|---|---|
| **1** | Inline screening verdict times out in real conditions — REJECT/SPAM verdicts only arrive via async FCM *after* the ring, so silencing doesn't actually fire for genuine unknowns. | **Railway cold start.** Railway "Serverless" sleeps a service after ~10 min with no *outbound* traffic; inbound requests don't reset the timer. First hit after sleep = 15–22 s, well past the 3.5 s screening budget → timeout → fail-open ALLOW. | Backend: `services/keepWarm.js` self-fetches `/healthz` every 5 min (outbound → never sleeps; auto-off on local/`KEEP_WARM=off`). Client: `CallRepository.warmUp()` pings `/healthz` on the short-timeout screen client whenever the app foregrounds (`ProcessLifecycleOwner` in `RootViewModel`), priming the OkHttp TCP+TLS pool the real `/screen` reuses. Offline-first tier already decides known callers with zero network. | ✅ **Stage A done** (code-complete, builds; on-device latency measurement pending) |
| **2** | `callerDisplayName` (CNAP) is empty at screening time even though the stock dialer shows the KYC name. | Architectural: `CallScreeningService.onScreenCall()` runs *before the call is added and before ringing*; CNAP arrives over network signaling that often hasn't landed in that pre-ring window. There's no "details changed" callback in the screening service. | Becoming the **default dialer** fixes it: an `InCallService` receives `onDetailsChanged()` *after* the call is added, where CNAP populates. Show the KYC name live in the in-call overlay from there; keep using the number + STIR/SHAKEN status for the pre-ring block decision. | **Stage D (planned)** |

---

## 5. Feasibility findings (researched 2026-07-08) — read before planning audio features

- **`ROLE_DIALER` + `InCallService`** is stable public API; a full Truecaller-class in-call UX is
  feasible. Contract is strict: handle `ACTION_DIAL` (dialpad + both intent filters), fully implement
  the in-call UI, **never return a null binding** (Telecom silently falls back to the stock dialer),
  place calls via `TelecomManager.placeCall`. Disabling your own InCallService at runtime auto-strips
  the role.
- **RISK-1 — Live AI call-answering is INFEASIBLE without root/system.** Capturing the caller's
  downlink audio needs `CAPTURE_AUDIO_OUTPUT` (system/signature-only since Android 6); injecting TTS
  into the call uplink has **no public API at all**. This is why Google Call Screen is Pixel/Tensor-only
  (privileged system dialer, HAL audio) and Truecaller Assistant answers on the **carrier/network side**.
  The only non-root path (speaker-loopback: answer → speaker → TTS out loudspeaker → MIC-capture caller)
  is degraded (room echo/noise, one-app-captures-audio concurrency, Bluetooth breakage, audible in room).
  **→ The "AI answers the call and talks to the caller" feature is replaced by reject-to-voicemail +
  on-device voicemail transcription + optional gate-code SMS (Stage E). Nothing depends on live audio.**
- **RISK-2 — OriginOS/FuntouchOS background-kill** is aggressive (~5 min). An `InCallService` reaped
  mid-call breaks the UI. Mitigate with foreground-service discipline + onboarding that sets Auto-start
  + Unrestricted battery. Test survival on the iQOO.
- **RISK-3 — `ROLE_DIALER` is sticky on vivo.** Stock dialer resists being displaced; the hidden
  `*#*#556688#*#*` "Alternate Phone" toggle may be the real gate. Verify the role actually flips on
  OriginOS 6 before building UI on top.
- **RISK-4 — Carrier voicemail/VVM is carrier-specific.** Stage E assumes rejected calls forward to
  carrier voicemail and (for transcription) Visual Voicemail (OMTP) support. Verify with the SIM early.
- **RISK-5 — Holding `ROLE_DIALER` + `ROLE_CALL_SCREENING` + `ROLE_SMS` together** is allowed by Android
  but needs on-device verification (audio focus, what the screener sees once we're the dialer).

_Sources are captured in the git history of this file's predecessor; the load-bearing ones:
[Build a default phone app](https://developer.android.com/develop/connectivity/telecom/dialer-app),
[Screen calls](https://developer.android.com/develop/connectivity/telecom/dialer-app/screen-calls),
[Sharing audio input](https://developer.android.com/media/platform/sharing-audio-input),
[AudioRecord VOICE_CALL / CAPTURE_AUDIO_OUTPUT (HN)](https://news.ycombinator.com/item?id=11679297),
[Railway app-sleeping](https://docs.railway.com/reference/app-sleeping),
[Fossify Phone](https://github.com/FossifyOrg/Phone), [Koler](https://github.com/Chooloo/koler)._

---

## 6. The goal & staged roadmap

**Target architecture:** one app holding **three roles at once** — `ROLE_CALL_SCREENING` (pre-ring
gate, the only place a call can be blocked before it rings), `ROLE_DIALER` (`InCallService` + full
in-call UI, where rich info is *rendered* once a call is allowed to ring), and `ROLE_SMS` (existing).
The screening service and dialer share **one verdict model and one Room store** — no divergence.

Stages are sized to **build → sideload → verify on the iQOO independently**, ordered so value lands
early and the risky audio work is last and isolated. **Do not let any feature depend on Stage F.**

| Stage | Goal | Depends on | Status |
|---|---|---|---|
| **0** | Bump `compileSdk`/`targetSdk` 34 → 36 to match Android 16; fix deprecations. Baseline still builds/screens. | — | ⏸ **blocked**: needs the `android-36` platform (~130 MB). Only `android-34` is installed and there are no `cmdline-tools`. Install via Android Studio → SDK Manager (or `sdkmanager "platforms;android-36" "build-tools;36.0.0"`), then bump the two values in `android/app/build.gradle.kts`. Not required for Stage A. |
| **A** | Kill the latency bug (#1): backend outbound keep-warm; client warm-up ping; offline-first tier decides known callers with zero network. Fixes the *existing* app. | — | ✅ **done** (2026-07-08): backend `keepWarm.js` + client `warmUp()`; backend 31 tests green; debug APK builds. On-device latency check pending. |
| **B** | Become the dialer: `ROLE_DIALER` via `RoleManager`; dialpad for `ACTION_DIAL` + outgoing via `TelecomManager.placeCall`; `InCallService` feeding a `@Singleton CallManager`; over-lockscreen `InCallActivity` with incoming answer/reject + ongoing hangup. Never returns null binding. | 0 | ✅ **done** (2026-07-08, builds; on-device pending) |
| **C** | Full in-call UX: hold, mute, speaker, DTMF keypad, multi-call banner/swap, call timer, audio routing via `CallAudioState`. | B | ✅ **done** (2026-07-08, builds; on-device pending) |
| **D** | Live AI verdict + CNAP overlay (fixes #2): `ScreeningService` stashes its verdict in `CallManager`; in-call screen shows verdict+reason chip and live CNAP/contact name via `onDetailsChanged()`. | B | ✅ **done** (2026-07-08, builds; on-device pending) |
| **E** | Screened-calls / voicemail inbox (the achievable "assistant"): reject-to-voicemail for suspected spam, each a visible inbox entry with AI reason; if carrier VVM confirmed, ingest voicemail audio → **on-device** transcribe → classify → file robocall/scam as voicemail-with-transcript; optional gate-code auto-SMS. | A, D | ⬜ planned |
| **F** | Live-answer feasibility **spike only** (time-boxed, isolated, expected to be shelved): measure the speaker-loopback hack quality. No product feature depends on it. | — | ⬜ deferred |

**Also planned (dialer depth, slots into B–D):** contacts (READ/WRITE_CONTACTS), call log
(READ_CALL_LOG), search, post-call actions (AI summary, one-tap block, add-to-contacts).

### On-device test checklist (one APK covers Stage A + Phases B/C/D)

Sideload `android/app/build/outputs/apk/debug/app-debug.apk`, run onboarding, then test in order:

**Stage A — latency (needs the updated backend deployed with keep-warm):**
- [ ] Deploy backend; confirm logs show `Keep-warm enabled: self-ping …`.
- [ ] Leave the phone idle 15+ min, then have an unknown number call. Verdict should apply
      *before/at* the ring (SPAM rejected, REJECT silenced) — not arrive late via FCM.

**Phase B — become the dialer:**
- [ ] Onboarding → "Set as default phone app" flips the role (RISK-3: if it doesn't stick on
      OriginOS 6, try the hidden `*#*#556688#*#*` Alternate Phone toggle).
- [ ] `adb shell cmd role holders android.app.role.DIALER` lists `com.personal.detectivedialer`.
- [ ] Calls tab → dialpad FAB → type a number → green call button places the call through our UI.
- [ ] An incoming call shows our full-screen UI (Answer / Reject); Answer connects, Reject → voicemail.
- [ ] Screening (SPAM/REJECT) and SMS still work — three roles coexist (RISK-5).
- [ ] Incoming call while the phone is locked shows our screen over the lock screen.

**Phase C — in-call controls:** (on an active call)
- [ ] Mute toggles (other side stops hearing you); Speaker toggles; Hold/Resume works.
- [ ] Keypad sends DTMF tones (test against an IVR); call timer counts up.
- [ ] A second incoming call shows the "waiting/on hold" banner; tap to swap.

**Phase D — AI verdict + CNAP overlay:**
- [ ] Unknown caller that screened as SPAM/REJECT but still rings shows the verdict chip + reason.
- [ ] A caller with CNAP/contact shows the KYC/contact name (may pop in ~0.5–1 s into the ring).

**Known caveat:** OriginOS may reap the InCallService — verify it survives with the app swiped away
(RISK-2). If the in-call screen fails to appear, that's the likely cause; check battery/auto-start.

---

## 7. Open questions to resolve before/early in building

1. Does the carrier forward `Call.reject()`ed calls to **carrier voicemail**, and does the iQOO/carrier
   support **Visual Voicemail (OMTP)**? (Gates Stage E — RISK-4.)
2. Does `createRequestRoleIntent(ROLE_DIALER)` cleanly set us default on **OriginOS 6**, or is the
   hidden `*#*#556688#*#*` path required? (RISK-3, verify in Stage B.)
3. Does the `InCallService` **survive OriginOS background-kill** during an active call? (RISK-2.)
4. Keep-warm hosting decision: outbound self-traffic on Railway vs. always-on host with a spend cap. (Stage A.)
5. On-device STT choice for voicemail transcription (Android `SpeechRecognizer` vs. bundled model). (Stage E.)

---

## 8. Changelog (append newest at top; keep this honest)

- **2026-07-08** — **Phases B + C + D built (default-dialer AI phone app).** New `telecom/` package:
  `CallManager` (@Singleton hub — StateFlow of `CallUi` snapshots + actions, verdict stash),
  `DialerInCallService` (InCallService feeding it + launching the UI), `CallModels` (CallUi/CallState/
  AudioUi). New `ui/incall/` (`InCallActivity` over-lockscreen + `InCallScreen` with answer/reject,
  mute/speaker/hold/DTMF/multi-call swap/timer + Phase-D verdict chip + CNAP header, `InCallViewModel`)
  and `ui/dialer/` (`DialerScreen` dialpad + `DialerViewModel` `placeCall`). Manifest: `CALL_PHONE`,
  `DialerInCallService` (`BIND_INCALL_SERVICE` + `IN_CALL_SERVICE_UI`), `InCallActivity`, `ACTION_DIAL`
  filters on MainActivity. Onboarding requests `ROLE_DIALER`. Dashboard FAB → dialpad. `ScreeningService`
  now stashes each verdict in `CallManager` (Phase-D bridge). Debug APK builds clean (69 MB). All three
  phases ship in one APK; test per the checklist above. **On-device unverified** — RISK-1 audio work is
  NOT included (Stage E/F remain); RISK-2/3/5 need device confirmation.
- **2026-07-08** — **Stage A (latency fix) landed.** Backend: new `src/services/keepWarm.js` self-pings
  `/healthz` every 5 min (config in `config.js` `keepWarm`), wired into `server.js` startup; +3 tests
  (`test/keepWarm.test.js`) → 31 green. Android: `BackendApi.health()` (`GET /healthz`),
  `CallRepository.warmUp()` on the `@ScreenClient`, invoked from `RootViewModel` via `ProcessLifecycleOwner`
  on every foreground; added `androidx.lifecycle:lifecycle-process`. Debug APK builds clean.
  Still to verify: real on-device verdict latency (target <2 s warm) — confirm REJECT/SPAM now silence
  *before* the ring on the iQOO.
- **2026-07-08** — Consolidated six root planning docs (FinalPlan, Plan, PROGRESS, HANDOFF,
  CHAT_SUMMARY, NEXT_SESSION, PHASE2_PLAN) into this single living `PROJECT.md`; trimmed `README.md`
  to point here. Stage 0 (SDK→36) blocked on the missing `android-36` platform download.

---

## 9. Build & test quick-reference

```bash
# Backend
cd backend && npm test            # 28 tests, must stay green
cd backend && npm start           # local run (see backend/README.md for env)

# Android (JDK 17 required)
cd android
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleDebug --console=plain
# APK → android/app/build/outputs/apk/debug/app-debug.apk
# Sideload: adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

On-device debug loop (wireless adb; never stream logcat into a chat — write to a file and grep slices):
`adb logcat -v time > /tmp/dd-logcat.txt &` then
`grep -iE "detectivedialer|ScreeningService|FATAL" /tmp/dd-logcat.txt | tail -80`.
Role check: `adb shell cmd role holders android.app.role.CALL_SCREENING`.
The app must stay **event-driven** (no standing foreground service / persistent notification) except
during an active call once it's the dialer.
</content>
