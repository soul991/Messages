# Detective Dialer — Project Status / Handoff
> Written 2026-07-13, at a deliberate pause point. Read this before doing anything else with 
> the project — it replaces needing to reconstruct context from chat history.

## What this project is
A personal Android dialer replacement (Kotlin/Compose/Hilt/Room/Telecom-InCallService), 
package `com.personal.detectivedialer`, sideloaded on an iQOO Neo 9 Pro. Backend on Railway 
does Gemini-based call screening. Repo: github.com/istakahamed06/DetectiveDialer 
(renamed from CallScreeningApp — remote URL already fixed locally).

## ⚠️ Most important thing to know
**Almost none of the recent work has been verified on the actual device.** Multiple sessions 
of real code changes are sitting in a committed checkpoint (commit `3ced192`) that compiles 
cleanly and passed unit tests, but have never been tested by actually using the phone. Do not 
assume anything below "works" until you've personally tested it. This is not a confirmed-good 
baseline — it's a safe rollback point for where things stood.

## What's confirmed working (built, tested, in production use before this latest batch)
- Core AI call screening: number → Gemini text classification → verdict, via Railway backend
- Full default-dialer replacement: telecom integration, in-call UI, contacts, messages, call log
- Backend policy engine: verdicts carry an explicit action (RING/VOICEMAIL/REJECT), not just 
  a label — "Allowed" badge removed from call log, only exceptions get a badge

## What's built but UNVERIFIED on-device (committed in checkpoint 3ced192)
- **Notification lifecycle**: Answer/Decline notification should cancel/downgrade when a call 
  is answered or rejected (including a call-waiting edge case guard)
- **Live in-call name priority**: contact name should win over carrier CNAP on the live 
  incoming-call screen (previously showed CNAP even for saved contacts)
- **Proximity screen-off**: screen should go dark near the ear during active calls, respecting 
  speakerphone toggles
- **Messages performance fixes**: observer debounce, indexed thread queries, pagination, 
  shared name-resolution cache — should load noticeably faster
- **Messages reply-eligibility**: reply composer should be hidden for non-repliable senders 
  (alphanumeric headers, short codes) — the 7-digit numeric cutoff specifically needs checking 
  against your real inbox's actual senders, flagged explicitly as unverified by the session 
  that built it
- **A "yesterday's hangup fix"** (call teardown behavior) — exists in the checkpoint but has 
  no isolated commit of its own; the Fossify audit (see below) found it's structurally sound 
  but flagged two specific fragilities worth testing: teardown logic uses raw call-list size 
  rather than a self-pruned state, and a surviving call in a call-waiting scenario may not get 
  its notification re-posted with fresh info after the other call ends

## Confirmed done and NOT yet started (design specced, no code written)
- **Session B/C — unified contact/number detail page**: replaces Session 2's inline 
  expand/collapse accordion in the call log with a proper detail page (full history, reachable 
  from both Contacts and Call History tabs). Multi-number contacts resolved to: show a merged 
  timeline across all of a contact's numbers, annotate which number each call used, don't force 
  a number picker. **Not implemented yet.**
- T9 smart dial search — specced, not built
- Favorites/speed dial, missed-call app badge, block-list management screen — identified as 
  backlog ideas, not specced in detail, not built

## The Fossify comparative audit
`FOSSIFY_AUDIT_2026-07.md` (repo root, needs the commit above) — a read-only audit comparing 
Detective Dialer against Fossify Phone (a real, mature open-source Android dialer). Key findings:
- **Biggest real gap**: `PhoneCaller.placeCall()` passes an empty Bundle — no `PhoneAccountHandle`, 
  meaning no in-app SIM selection on a dual-SIM phone. Flagged as the highest-value missing 
  feature, not just polish.
- Dialpad has zero query/search logic (T9 search doesn't exist yet, confirmed).
- The hangup-fix fragilities noted above.
- Full prioritized punch list is in the document itself — audit's own top priority was 
  on-device verification, ahead of any new feature work.

## The parked/rejected ideas (don't resurrect without re-reading why)
- **Call recording**: not feasible for a third-party app — Android restricts the audio source 
  that captures the other party's voice to system-signed apps only. iQOO/vivo's own stock 
  Phone app CAN do it (hidden "Alternate Phone & Contacts" toggle in Settings → Apps), but 
  that's a completely separate, mutually-exclusive default-dialer choice, not something 
  portable into Detective Dialer.
- **Voicemail with a custom TTS greeting**: no public API lets a third-party InCallService 
  inject synthesized audio into a live call. Parked.
- **Live call transcription**: feasibility on Android 16 was never confirmed (mic access 
  during calls may be blocked). Parked, this was always "Stage F" in the original project 
  scope, never started.

## The bigger pivot that was being explored: AI voice assistant answering calls
This is a genuinely different, separate system from Detective Dialer's Android app — not an 
extension of it. Key findings from that exploration, in case it gets picked up again:
- Android gives no third-party app access to live call audio — this is a hard OS wall, not a 
  dev-effort problem. A real "AI answers and talks to the caller" system requires the call to 
  terminate on cloud telephony infrastructure, not on-device.
- **Exotel** (India-native cloud telephony) is the right provider to look at, not Twilio — 
  Twilio's standing for directly handling Indian inbound PSTN traffic is unclear/murky, while 
  Exotel is built around Indian telecom compliance from the ground up.
- Exotel has an official open-source reference — `exotel/voicebot-quick-starter` on GitHub — 
  wiring Google STT + Gemini + Google TTS into a real working voice agent over Exotel's phone 
  infrastructure. This is a real starting point, not a from-scratch build.
- **The actual next step, if resumed, is non-technical**: create an Exotel account and find out 
  whether an individual (non-business) signup can even get a usable inbound number. This 
  couldn't be verified remotely and blocks any further design work.
- Two unresolved design tensions if this proceeds: (1) conditional call forwarding is 
  network-level and can't be selective — any unanswered call from anyone would reach the AI, 
  not just calls Detective Dialer flags as spam; (2) the existing policy engine's REJECT tier 
  actively rejects calls immediately, which likely needs to change to "let it ring out" for 
  no-answer forwarding to trigger at all — unverified how Airtel's network actually distinguishes 
  the two.
- This has a real ongoing cost (telephony number + per-minute rates + AI usage) — unlike 
  everything else in this project so far, which has been free.

## If you come back to this — recommended order
1. Actually run the on-device test pass for the notification/in-call-name/proximity/messages 
   items above. This has been deferred multiple times; it's the actual bottleneck, not a lack 
   of features.
2. Fix whatever the testing turns up.
3. Harden the hangup/teardown fragilities the Fossify audit flagged.
4. Then pick from the punch list: multi-SIM support (probably highest value), the unified 
   contact detail page (Session B/C), or T9 search — in whatever order matters most to you.
5. The AI voice assistant idea is a separate track entirely — pick it up only when you're 
   ready to deal with an actual telephony vendor account, not as a Detective Dialer coding 
   session.
