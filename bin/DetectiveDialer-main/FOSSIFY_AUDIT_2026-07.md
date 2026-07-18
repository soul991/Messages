# Fossify Phone — Comparative Audit (2026-07)

**Purpose:** compare Detective Dialer (DD) against [Fossify Phone](https://github.com/FossifyOrg/Phone)
(GPL-3.0, cloned to `~/Desktop/Projects/fossify-reference/`, HEAD `585c79a`) to find gaps,
borrow *patterns* (never code — GPL stays quarantined), and produce a prioritized punch list
for follow-up sessions.

**Scope reminder:** DD is a **personal, sideloaded, single-user** dialer for one iQOO Neo 9 Pro
(Android 16 / OriginOS 6, India, dual-SIM). Fossify is a general-purpose Play-Store app for
thousands of devices. Many Fossify features exist only to serve that generality and are
**not relevant** here — this audit calls that out per item rather than dumping the feature list.

**Architecture gap that colours everything below:** Fossify is XML Views + a global `object`
`CallManager` + EventBus + synchronous `Cursor`/`ContentResolver` reads. DD is Compose +
Hilt-injected `@Singleton` + `StateFlow` + coroutines. **No Fossify code ports directly.** Every
useful idea is a *pattern to reimplement*, and this document flags where the framework mismatch
makes an idea more work than it looks.

**This is a read-only audit. No DD code was changed in this session.** Findings are scoped into
separate future sessions, one at a time.

---

## ⚠️ Baseline caveat (read first)

The current `main` HEAD (`3ced192`, "checkpoint: bundle multiple prior sessions' fixes") is a
**rollback point, not a verified baseline.** It bundles several sessions of work that has **never
been run on a real device.** Specifically **unverified on-device**:

- Incoming-call notification lifecycle (post/downgrade/cancel on answer/reject/disconnect)
- Proximity sensor screen-off/acquire-release
- Live in-call name priority (contact → CNAP → Unknown)
- Messages loading + reply-composer behaviour
- **Yesterday's (Jul 10) call-hangup/teardown changes** — see §c, which is the highest-scrutiny item.

Everything in §a–§d below is a *static* read of the code against Fossify's approach. None of it
substitutes for putting a call through the phone.

---

## a) Features Fossify has that Detective Dialer lacks

Prioritized: **relevant to a personal dual-SIM sideloaded dialer** first, then explicitly-not-relevant.

### High relevance — genuinely missing capability

| # | Feature | Fossify approach | Relevance to DD |
|---|---------|------------------|-----------------|
| A1 | **Per-call SIM selection (multi-SIM)** | On placeCall, if >1 `PhoneAccountHandle`, show a SIM picker; remembers per-contact SIM. | **HIGH.** DD's `PhoneCaller.placeCall()` passes an **empty `Bundle`** — no `EXTRA_PHONE_ACCOUNT_HANDLE`. On a dual-SIM Indian phone every outgoing call either falls to the system default SIM or triggers the OEM's own picker, with **zero in-app control**. This is the single most relevant Fossify feature for this specific device. |
| A2 | **Dialpad T9 / contact search** | Dialpad filters contacts+call-log live as you type (name or number). | **HIGH.** DD's `DialerViewModel` is 35 lines with **no search/filter/contact query** at all. Typing a number does not surface matching contacts. Core dialer UX that's simply absent. |
| A3 | **Call confirmation before dialing** | Optional "are you sure" dialog to prevent pocket-dials/misdials. | **MEDIUM.** Cheap safety net; genuinely useful on a personal phone. One boolean setting + a dialog. |
| A4 | **Swipe-to-answer toggle + proximity toggle** | Settings expose `disableSwipeToAnswer`, `disableProximitySensor`. | **MEDIUM.** DD already *has* proximity + a slide-to-answer UI, but **no user escape hatch** if either misbehaves on OriginOS. Given DD's hardware is unverified, a kill-switch setting is worth more here than in Fossify. |
| A5 | **Conference / call merge + swap** | `ConferenceActivity`, `merge()`, `swap()`, `TwoCalls` state. | **MEDIUM.** DD's `CallManager` tracks a `List<CallUi>` and has `hold`/`unhold` but **no merge/conference**. Call-waiting *swap* is partially modelled; true conference is not. Relevant but not urgent for one user. |

### Medium relevance — nice-to-have, defensible to skip

| # | Feature | Note |
|---|---------|------|
| A6 | **Speed dial** (`ManageSpeedDialActivity`) | Convenience only; a personal user has few numbers. Low effort if wanted, low value. |
| A7 | **Dialpad beeps + haptics** (`dialpadBeeps`, `dialpadVibration`) | Small polish. Trivial to add; matches muscle memory from stock dialers. |
| A8 | **Phone-number formatting** (`formatPhoneNumbers`, libphonenumber) | DD normalizes to digits+`+` for matching but doesn't pretty-print for display. Cosmetic. |
| A9 | **Group subsequent calls toggle** (`groupSubsequentCalls`) | DD reportedly already does consecutive-same-number grouping (verify — see §Ground-truth). Fossify makes it a setting. |
| A10 | **Export/import call history** | Backup convenience. DD stores history in Room; a personal user rarely needs CSV export. Skip. |

### Low / not relevant — Fossify generality that doesn't apply

| # | Feature | Why it doesn't apply to DD |
|---|---------|----------------------------|
| A11 | Font-size / start-name-with-surname / language toggle | Play-Store i18n + accessibility surface. DD is one user, one locale. Not relevant. |
| A12 | Configurable default tab / open-dialpad-at-launch | Personalization for a broad user base. DD can hard-code the owner's preference. |
| A13 | Hide-dialpad-numbers, themes engine | Fossify's shared-commons theming. DD has its own Compose theme. Not relevant. |
| A14 | Full contacts-management app surface | Fossify overlaps with a contacts app. DD deliberately **hands off to the system Contacts editor** (`ACTION_INSERT_OR_EDIT`) — the right call for a personal app. Not a gap. |

**DD advantages Fossify lacks** (worth stating so we don't "fix" toward Fossify blindly): AI/backend
call *screening* with an explicit RING/VOICEMAIL/REJECT policy engine, CNAP-aware live in-call name
resolution, FCM post-call verdicts, and an integrated SMS stack. Fossify has **none** of this — it's
a pure telecom dialer. DD is a screener that grew a dialer; keep that identity.

---

## b) Known DD rough edges — Fossify's approach, adapted

Sourced from `PROJECT.md §4`, `detective_dialer_bug_batch_09:07.md`, and the code read.

### b1. Multi-SIM outgoing (bug latent, not yet filed)
**Fossify idea:** enumerate `telecomManager.callCapablePhoneAccounts`; if >1, present a chooser and
pass the chosen handle via `Bundle().putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, …)`.
**Adapting to DD:** `PhoneCaller.placeCall(number)` gains an optional `PhoneAccountHandle` param;
the dialer surfaces a Compose SIM-picker bottom-sheet when accounts.size > 1, and remembers the
last choice in `SettingsRepository` (DataStore). Fossify's chooser is an XML dialog — **UI does not
port**, but the Telecom plumbing (`EXTRA_PHONE_ACCOUNT_HANDLE`) is identical and is the load-bearing
part. Needs `READ_PHONE_STATE` (already held).

### b2. Inline screening verdict latency (`PROJECT.md #1`)
Fossify **has no equivalent** — it doesn't screen calls, so there's no pattern to borrow. DD's own
fix (backend keep-warm + client warm-up ping) is already coded (Stage A). This stays a
DD-specific concern; Fossify is silent here. Flag: *don't* look to Fossify for this.

### b3. CNAP empty at screening time (`PROJECT.md #2`)
Fossify reads the display name **inside the InCallService** via `Call.Details.callerDisplayName`
and its `CallContactHelper` — i.e. *after* the call is added, exactly the window where CNAP has
landed. DD already reached the same conclusion (resolve live in `CallManager.onDetailsChanged`,
which the code does). **Convergent design, already implemented.** Nothing to port; use Fossify only
as confirmation the approach is sound.

### b4. Default incoming-call wallpaper (bug batch #1)
Fossify uses per-contact photos, not a device-wallpaper fallback, so **no direct pattern**. DD's
own `DeviceWallpaperProvider` (needs all-files access) is a DD invention. Fossify does confirm the
sane fallback: a neutral themed background when no image is available — which DD already does. Low
priority; leave as-is.

### b5. Slide-to-answer position + gesture-inset collision (bug batch #2)
Fossify sidesteps this by offering a **plain two-button** answer/reject as an alternative to swipe
(`disableSwipeToAnswer`). **Adapting:** rather than only nudging the slider up by 24–32dp and
honouring `systemGestureInsets` (the filed fix), also add Fossify's escape hatch — a settings
toggle that swaps the slider for tap buttons. On an unverified OriginOS gesture-nav device, the
toggle is cheap insurance. Pure-Compose reimplementation; nothing ports.

### b6. Disconnect-cause UX — "call just cuts" on unreachable numbers (bug batch #9)
**Fossify pattern (directly relevant):** on `STATE_DISCONNECTED`, Fossify reads
`call.details.disconnectCause`, shows the cause label, and **holds the call screen briefly before
finishing** rather than dismissing instantly. DD currently maps DISCONNECTED → "Call ended" text
but `InCallActivity` calls `finishAndRemoveTask()` **the instant `calls.isEmpty()`** — no dwell.
**Adapting:** surface `disconnectCause` in `CallUi`, keep the screen up ~1–1.5s on a
disconnect (a `delay` in the `LaunchedEffect(calls.isEmpty())` before finishing, or a terminal
"ended" state the VM emits). This is a real, well-matched borrow. (Note: the *audio* announcement
from the carrier is not something the app controls — only the on-screen dwell is in scope.)

---

## c) Assessment of yesterday's (Jul 10) call-hangup / teardown fix

**What changed (Jul 10 file mtimes):** `CallManager.kt`, `DialerInCallService.kt`,
`CallActionReceiver.kt`, `CallNotificationManager.kt`, `ProximityScreenLock.kt`. All prior sessions'
work was uncommitted until this session's checkpoint, so there is **no isolated diff** — the fix
lives inside the checkpoint. Reconstructed from the code as it stands.

**What the fix does (as read):**
1. `CallActionReceiver.ACTION_HANGUP` → `callManager.hangup(id)` → `call.disconnect()`. The code and
   its comment correctly note that `reject()` is a **no-op past RINGING**, so an ongoing call must be
   `disconnect()`ed, not `reject()`ed. The ongoing notification wires its end-button to `ACTION_HANGUP`;
   the incoming notification wires decline to `ACTION_DECLINE` (→ `reject()`). **This split is correct.**
2. Notification lifecycle in `DialerInCallService.updateForState`: RINGING→incoming CallStyle;
   ACTIVE/DIALING/HOLDING→ongoing CallStyle (no answer/decline buttons); **DISCONNECTING/DISCONNECTED
   → `cancelActiveCall()` immediately** so buttons don't outlive the call. Final cleanup in
   `onCallRemoved` (cancel + proximity release when `calls.isEmpty()`).
3. `userRejectedIds` set on reject/decline so `CallLogWriter` logs REJECTED vs MISSED correctly.

**Cross-reference vs Fossify's teardown:**
- Fossify's `CallService.callListener` does the same core move: `DISCONNECTED || DISCONNECTING →
  cancelNotification()`, else `setupNotification()`. **DD matches this.** ✅
- Fossify's `reject()` is **one state-guarded method** (RINGING→reject, else→disconnect). DD splits
  into `reject()` and `hangup()` at the call sites and relies on each caller picking the right one.
  DD's callers currently pick correctly, but this is **more fragile by construction** — a future
  caller that calls `reject()` on an active call gets a silent no-op (call stays live). *Consider*
  consolidating to one Fossify-style guarded method later (low priority, not a bug today).

**Fragilities to flag:**

1. **`calls` = framework `getCalls()`, not a self-pruned list.** DD's disconnect guard is
   `if (calls.size <= 1) notifications.cancelActiveCall()` and teardown is `if (calls.isEmpty())`.
   `InCallService.getCalls()` can still contain a DISCONNECTED call momentarily. **Fossify deliberately
   maintains its own list and prunes** `calls.removeAll { getStateCompat() == STATE_DISCONNECTED }`,
   deciding teardown from `getPhoneState() == NoCall` rather than trusting the framework list size.
   DD leans on `onCallRemoved` firing to reach `isEmpty()`, which normally holds — but the size-based
   guard is **less robust than Fossify's state-based one**. Verify on-device that a plain hangup fully
   clears the notification (this is exactly the kind of thing that only shows up on a real call).

2. **Survivor notification goes stale in the call-waiting case.** When two calls exist and the user
   hangs up one, DD's `onCallRemoved` else-branch calls **only `updateProximity()`** — it does **not
   re-post** the ongoing notification for the surviving call. Fossify's else-branch calls
   `setupNotification()` to refresh it. So after ending one of two calls, DD's notification may keep
   showing the **ended** party until the survivor's next state change re-posts. Minor, but a concrete
   gap versus Fossify. **Flag for the call-waiting test.**

3. **No dwell on disconnect (ties to §b6).** `finishAndRemoveTask()` fires the instant `calls.isEmpty()`,
   so the "Call ended" / disconnect-cause text is effectively never seen. Fossify holds briefly.

4. **`identityHashCode` as the stable call id.** `CallManager.idFor` uses `System.identityHashCode(call)`.
   Fine while the `Call` instance is alive (which is the whole tracked window), but identity hashes are
   **not guaranteed unique** across the process and can theoretically collide. Very low risk; noted for
   completeness, not action.

**Verdict:** the hangup fix is **structurally sound and matches Fossify on the core teardown move**
(cancel on DISCONNECTING/DISCONNECTED, disconnect-not-reject for ongoing). The two things that would
make it *robust* rather than *probably-correct* are (a) decide teardown from a **self-pruned call
list / phone-state** like Fossify instead of the raw framework `getCalls()` size, and (b) **re-post
the survivor notification** in the call-waiting removal path. Neither is a confirmed bug on paper —
but **none of it has been exercised on a device**, and item 1 + item 2 are precisely the cases that
only fail live. **Highest-scrutiny item in this audit; must be verified on the iQOO before trusting.**

---

## d) Prioritized punch list (input for follow-up sessions, one at a time)

Ordered by **risk/value**. Each line is a candidate session, not a batch.

| Rank | Item | Type | Why this rank |
|------|------|------|---------------|
| **1** | **On-device verification pass** of the whole checkpoint: hangup, notification lifecycle (incl. call-waiting survivor from §c#2), proximity acquire/release, live CNAP/contact name, Messages load+reply. | **Verify** | Everything downstream assumes this works; none of it is device-tested. Blocks trusting the baseline. Do this **before** any new feature. |
| **2** | **Harden call teardown** (§c#1 + §c#2): decide teardown from a self-pruned call list / phone-state (Fossify pattern), and re-post the surviving call's notification in the call-waiting removal path. | **Robustness fix** | Directly de-risks the highest-scrutiny area. Small, well-scoped, high value. Do after #1 confirms current behaviour. |
| **3** | **Multi-SIM outgoing (A1/b1):** `PhoneAccountHandle` support in `PhoneCaller` + Compose SIM picker + remembered choice. | **Missing capability** | Highest-value *new* feature for this specific dual-SIM device. Self-contained. |
| **4** | **Dialpad T9 / contact search (A2):** filter contacts + call log as the user types. | **Missing capability** | Core dialer UX currently absent; high daily-use value. Needs a real `DialerViewModel` + contacts query. |
| **5** | **Disconnect-cause dwell (b6/§c#3):** surface `disconnectCause`, hold the in-call screen ~1–1.5s before finishing. | **UX fix** | Fixes the "call just cuts" complaint; well-matched Fossify borrow; small. |
| **6** | **Slide-to-answer polish + tap-button fallback (b5/A4):** reposition + `systemGestureInsets`, plus a settings toggle for tap buttons and a proximity kill-switch. | **UX + safety** | Insurance on unverified OriginOS gesture-nav hardware. |
| **7** | **Call confirmation before dial (A3)** + **dialpad beeps/haptics (A7)**. | **Polish** | Cheap, pleasant, low risk. Bundle-able. |
| **8** | **Conference/merge (A5)** and **speed dial (A6)**. | **Nice-to-have** | Lower value for one user; more surface area. Defer until 1–5 land. |

**Explicitly *not* on the list** (Fossify has them, DD shouldn't chase them): font-size/i18n/theme
engine, export/import, full contacts management, configurable default tab. These serve Play-Store
generality that a personal sideloaded app doesn't need.

---

### Provenance / hygiene
- Fossify Phone (GPL-3.0) is cloned to a **sibling** dir `~/Desktop/Projects/fossify-reference/`,
  **outside** this repo. No GPL code is copied into or committed alongside Detective Dialer. Every
  item above is a *pattern to reimplement* in DD's Kotlin/Compose/Hilt/StateFlow style.
- This document is a static read plus Fossify cross-reference. **On-device verification (punch-list
  #1) supersedes any "looks correct on paper" judgement here** — especially §c.
