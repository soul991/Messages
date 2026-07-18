# Detective Dialer — Bug/Feature Batch (2026-07-09 post-update)

Item #8 pending — user's message got cut off, needs follow-up before this is complete.
Everything below is written as a Claude Code session prompt, not just a restated list —
each item has a root-cause hypothesis and a concrete fix pattern, not just "fix this."

Recommend splitting into **2-3 sessions**, not one: items 2/3/5/9(partial) are small UI/data
fixes; item 4 is a policy-engine change touching backend + screening + UI; item 7 is a
state-management bug that needs careful isolation; item 4c is out of scope for this batch
entirely (see note below).

---

## 1. Default incoming-call wallpaper (fallback to device wallpaper)

**Constraint:** `WallpaperManager.getDrawable()` requires `MANAGE_EXTERNAL_STORAGE` on
Android 13+ — Google restricts it to privileged system apps otherwise (confirmed via
their own issue tracker discussion; still true on current API docs). A normal runtime
permission won't unlock it.

**Fix:**
- Add `MANAGE_EXTERNAL_STORAGE` to the manifest, request via
  `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` (fine for a sideloaded personal
  app — this would block Play Store publishing, which is moot here).
- If the user has set a custom incoming-call wallpaper → use it (existing behavior).
- Else, if permission granted → `WallpaperManager.getInstance(context).getDrawable()`,
  cache the bitmap, invalidate on `Intent.ACTION_WALLPAPER_CHANGED`.
- Else (permission denied) → fall back to a neutral solid/gradient background, not blank —
  never let this path crash or show nothing.

## 2. Slide-to-answer/reject vertical position

Move the slider track up ~24-32dp from its current bottom offset. Keep it inside the
comfortable one-handed thumb-reach zone, and respect
`WindowInsets.systemGestureInsets` so it doesn't collide with the gesture-nav strip on
OriginOS.

## 3. CNAP/contact name missing in call history

**Root cause hypothesis:** `CallLogWriter` is likely writing the log row using just the
raw number at call start/end, before CNAP resolves or a contact match completes.

**Fix:** at call termination (`DISCONNECTED` state), resolve display name in this
priority order and write *that* into the `call_log` row — not before:
1. Contact match via `ContactsContract`
2. CNAP name captured through `onDetailsChanged()` during the call
3. `null` → show as "Unknown"

Handle the race where CNAP arrives right as the call is tearing down — if it lands after
the row's already written, patch the existing row rather than dropping it.

## 4. Call history policy + labels

### 4a. Drop the "Allowed" badge
Only show a badge for non-default outcomes (Suspected Spam / Blocked / Voicemail).
Normal contact/legit calls get no badge — matches how Google/Samsung dialers only
flag exceptions, not the default case.

### 4b. Real policy tiers
This needs an explicit **action** field from the verdict, not just a label. Update the
Gemini backend response schema from `{ verdict, reason }` to
`{ verdict, reason, action }` where `action` is one of:

| Caller type | Action |
|---|---|
| Saved contact | `RING` — bypass screening delay entirely |
| Verified/known-good number, delivery partner | `RING` |
| Suspected spam (customer care / promo pattern) | `VOICEMAIL` — don't ring, divert |
| Robocall / reminder-call pattern | `REJECT` — no ring, no voicemail |

`ScreeningService` and `CallManager` need to branch on `action`, not `verdict.label`, when
deciding whether to let the call ring, silently reject it, or route it.

### 4c. Voicemail with TTS greeting — OUT OF SCOPE for this batch
No public Android API lets a third-party `InCallService` inject synthesized audio into
a live call's audio path or record the far end — this is the same class of restriction
already flagged as Stage F risk (mic access during calls) in the project doc. Two real
options, as a separate spike:
- Scope down: `VOICEMAIL` action = silent reject, relying on the carrier's actual
  voicemail box if configured on the SIM (no custom greeting).
- Or: spend a session specifically testing whether auto-answer + local audio
  playback/record is possible at all on this OEM before committing to it.

## 5. Save unknown numbers

Add a "Save contact" action on unknown-number rows (call log + in-call screen), using
`ACTION_INSERT_OR_EDIT` to hand off to the system Contacts UI — fastest and most
reliable route; skip writing directly to `ContactsContract` unless you specifically want
an in-app quick-add flow later.

## 6. Call log grouping — corrected pattern

**What modern dialers actually do** (this is the part the last prompt got wrong):
- **Primary grouping stays date-based**: Today / Yesterday / older — keep this exactly
  as already built.
- **Secondary grouping**: only *strictly consecutive* calls to/from the same number
  (no other caller in between) collapse into a single row with a count badge and an
  expand chevron. Non-consecutive calls from the same number, or calls separated by any
  other caller, stay as separate rows in their date section.
- Do **not** merge all history by contact across days — that breaks chronological
  browsing, which is the actual bug to avoid repeating.

## 7. Tab transition animation direction bug

**Symptom:** Contacts→Messages slides correctly, but Messages→Contacts slides the same
direction instead of reversing; Calls↔Contacts is the only pair that's correct both ways.

**Likely cause:** the slide direction is being derived reactively from the tab index
*after* `selectedTabIndex` state has already updated, or the sign logic is only
correct for specific index deltas rather than a general `newIndex - oldIndex` comparison.

**Fix pattern:**
```kotlin
var previousIndex by remember { mutableStateOf(selectedTabIndex) }

fun navigateTo(newIndex: Int) {
    val direction = if (newIndex > previousIndex) 1 else -1
    previousIndex = selectedTabIndex   // capture BEFORE mutating
    pendingDirection = direction        // fixed value the transitionSpec reads
    selectedTabIndex = newIndex
}
```
Compute `direction` once, at the moment of the navigation event — not inside the
`AnimatedContent` `transitionSpec` lambda where it'll be re-evaluated after
recomposition. Before shipping, manually log the computed direction for all 6 possible
transitions (Calls→Contacts, Contacts→Calls, Contacts→Messages, Messages→Contacts,
Calls→Messages, Messages→Calls) to confirm the sign is consistent for every pair, not
just the two you've been testing.

## 9. Call log direct-call button, failed-call UX, dialpad size, clipboard paste

- **Direct call button**: add a call icon on the right edge of each call log row,
  wired straight to the existing `DialerViewModel.placeCall(number)` — skip the dialpad
  entirely, same as Google/Samsung.
- **"Call just cuts" on unreachable numbers**: the "this number cannot be reached"
  announcement is normally played by the carrier network itself before teardown — it's
  not something the app generates. If it's cutting silently, check whether the app is
  tearing down the call UI/audio focus the instant `DISCONNECTED` fires. Read
  `Call.Details.getDisconnectCause()` and hold the call screen up briefly showing the
  reason text instead of instantly dismissing — but note actual network announcement
  audio isn't something you can control from the dialer.
- **Dialpad size**: bump touch targets to Material spec (~72dp digit buttons) — current
  layout is undersized for one-handed use.
- **Clipboard paste-to-dial**: on dialer screen open, check
  `ClipboardManager.primaryClip` for a phone-number-shaped string (regex validate), show
  a "Dial copied number" suggestion chip above the dialpad — mirrors Google Dialer's
  clipboard suggestion behavior.

---

## Suggested session split
1. Small/independent fixes: #2, #3, #5, #6, #9 (minus disconnect-cause UX), #1
2. Policy engine: #4a + #4b (backend schema change + screening/CallManager branching)
3. Animation state bug: #7 (isolate carefully, this is a state-timing bug not a visual tweak)
4. Separate spike (not this batch): #4c voicemail feasibility
