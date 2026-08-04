# Phase 5 UI Overhaul — Design Plan (Step 1, no code)

_2026-07-23. Sources: `docs/research/DESIGN_REFS_NOTES.md` (WA/TG measured values), `docs/research/TRUECALLER_ANALYSIS.md` §A recommendations, PRD §9. Awaiting approval before any code._

**Ground rules (apply to every screen):** adapt patterns, never clone trade dress (no WA green-on-doodle default, no Telegram blue identity, no Google Messages icon language); WCAG AA on every text/container pair (extend the existing `categoryPalette()` AA discipline); `Motion.kt` spring system and the list↔chat shared-element avatar stay untouched; every Phase 4 feature (notification actions, pinned banner, ⚡ templates, badges, fraud warning, Report action) restyled as a native part of the new design, not an add-on.

**Implementation order (Step 2, after approval):** HomeScreen → ChatScreen → Notifications → Theme/settings → App icon (paused for your pick). On-device screenshots after each screen; PROGRESS.md updated after each verification.

---

## 1. HomeScreen

**Tabs vs chips — decision: keep top folder chips.** Both references reserve bottom tabs for *app sections*, not message categories (REFS §5); Truecaller itself renders categories as top tabs/sections (TC §1). Our chips already are that pattern, and chips scale to 6 folders + the orthogonal Unread filter where fixed tabs don't. Truecaller A6's real lesson — a calm personal-first landing — is already our Inbox-default. No structural change; visual refinement only.

**Row anatomy — changes** (REFS §1 table):
- Avatar 48 → **54dp** (refs: 52–56); row height stays 72–76dp (auto from padding); avatar→text gap 12 → **16dp**.
- Timestamp 11 → **12sp**, top-aligned with the name line (both refs).
- **Inset dividers** starting at the text column (not full-width), or divider-free spacing Telegram-style — will prototype both, ship whichever reads calmer with our category-hued avatars.
- **Single trailing-indicator slot** (REFS punch-list 4): unread badge > pin glyph > nothing — never stacked. Muted chats keep their count but in a **gray badge variant** + small bell-slash after the name (REFS §1); today mute is invisible in the list.
- Preview 14sp stays (refs 15pt, within tolerance). Draft prefix, blue badge-after-name, red/amber/green avatar hues all **stay** — the hue system is our differentiator and already AA (§9).

**FAB — stays** (extended "New message", shrinks to icon at half-collapse). Matches M3; neither ref offers a better pattern (WA's is a plain square FAB).

**Search — stays structurally** (pill on `surfaceContainerHigh`, collapsing large title). Refinements: pill height ~52dp with tighter placeholder, and the existing label chips (OTP/Bank/Delivery/Travel/Bill) restyled to 6–8dp corner tokens (REFS §4). Truecaller A2's Highlights feed is **deferred** (new screen, out of overhaul scope; noted as post-Phase-5 candidate).

**Why:** the row grid is where both apps agree to the point (74–76pt rows, 52–56pt avatars, one trailing slot) — that's the de-facto standard users' hands know; everything distinctive about us (hues, badges, folders) survives on top of it.

## 2. ChatScreen

**Bubbles — changes** (REFS §3):
- Max width → **~76%** of screen; inner padding normalized 12×8dp; 16dp corners + tail-on-group-last **stay** (already match).
- **Timestamp + status move inside the bubble**, bottom-right, 11–12sp (both refs do this; our below-group meta line goes away). Delivered tick inline with the timestamp. This is the single biggest chat-feel change.
- Group spacing tightened: 2–3dp intra-group, 8dp between groups.
- Sent-bubble color: **new `sentBubbleContainer` role** in the theme (REFS punch-list 5). Default remains dynamic-color (Material You is our identity — deliberately NOT the refs' green); the green family (`#DCF7C5`-adjacent light / `#2DA430`-adjacent dark, AA-paired) ships as ChatStyle presets instead.

**Composer — stays structurally** (attach outside left, pill field, conditional spring send button — already the shared anatomy, REFS §3). Refinements: pill 40dp tall rx 18; **⚡ templates button moves inside the pill's right edge** (WA sticker-icon slot) so it reads native rather than bolted-on; SIM selector and schedule keep their places.

**Date pills — stay** (already match both refs). 

**Top bar:** avatar already joins the title cluster (shared element). Add the WA-style **one-time "Tap here for contact info" subtitle hint** (REFS §3), then it reverts to address/number. Fix the queued cosmetic: Business chip wrapping to two lines — chip becomes single-line with ellipsized name.

**Protection surfaces in chat (restyle, no behavior change):** fraud banner keeps red-on-AA-container but adopts the new bubble radius/inset grid; Not-spam/Why?/Report actions become a compact action row in the banner's visual language; pinned-message banner and OTP copy chip restyled to the new tokens.

**Why:** timestamp-inside-bubble and 76% width are what make chats read "native messenger" (REFS §3 both columns agree); keeping dynamic sent-bubble color avoids trade-dress cloning while the presets give users the familiar green if they want it.

## 3. Notifications

- **Proper small icons** — replace placeholder `android.R.drawable` icons (`sym_action_chat`, `stat_sys_warning`) with app-branded monochrome glyphs (message bubble; shield-alert for fraud). Highest-visibility polish item in the whole phase.
- **OTP notifications** (TC A1): code becomes the typographic hero — title `483920 — VM-BANKXX`, body de-emphasized, Copy action stays. Same treatment considered for Transactions amounts using existing Stage-0 amount extraction (TC B5) — deterministic only, degrade to plain text when extraction is uncertain.
- **Fraud warning** (verified working today): keeps persistent-until-dismissed semantics; gets the branded shield-alert icon, red accent, and copy tightened to advice-first. Stays default-ON via `warn_dangerous` (TC A4).
- **Per-category structure stays** as built in item 3 (Inbox always-on, Transactions/Promotions/Review toggles, Spam/Blocked never) — the NotificationSettingsScreen just adopts the new list tokens (section headers, switch rows) so it matches the overhauled Settings.
- MessagingStyle + contact photo + badge subtext all stay.

**Why:** TC §2 shows category-differentiated notifications with extracted-datum emphasis are the expected norm in this space; we get there with regex extraction we already have, no ML.

## 4. Theme features

- **Accent colors:** Material You dynamic stays the default. Add a curated **8-seed accent palette** (blue/teal/green/amber/coral/pink/purple/graphite; each generating full AA light/dark schemes) for non-dynamic devices and brand-consistent screenshots. AMOLED pure-black tier **stays** — Telegram's dark is `#1C1C1E` (REFS §2.2), so true black remains a differentiator.
- **Chat backgrounds:** existing 4 gradients + photo wallpaper stay; add **one subtle default doodle-class pattern** (very low contrast, own artwork, works under the 35% scrim) per REFS punch-list 9.
- **Per-chat customization:** ChatStyle sheet survives with the new `sentBubbleContainer` presets (incl. the green family) and the pattern option; per-chat settings continue to live in the chat overflow.
- **ContactDetailScreen** (REFS §6b): bigger photo hero, **circular tonal action trio** (message/call/save), and new rows for **Starred messages** and **Search in conversation** with M3 tonal icon containers — both features exist, this page is their natural home.
- **Settings screens:** adopt one shared list language (section headers, icon slots, switch/nav rows) so Appearance/Conversations/Notifications/Privacy read as one system.

## 5. App icon — three directions (pick one)

Current icon (white bubble + blue check on blue gradient) is competent but generic. All directions: adaptive + monochrome (themed-icon) layers, distinct from Google Messages/WA/Telegram silhouettes.

- **A. Shield-bubble:** speech bubble whose outline subtly forms a shield at the base; small check inside. Protection-first identity — says "the messenger that guards you."
- **B. Check-tail bubble:** minimalist bubble whose tail is drawn as a checkmark stroke. One-glyph story ("verified messaging"), cleanest monochrome layer, most distinctive at small sizes.
- **C. Calm gradient bubble:** rounded-square gradient (brand blue → teal) with a floating white bubble + notch, no check. Softest/most consumer look; protection story lives in-app instead.

Recommendation: **B** — most ownable silhouette, reads at 24dp, and the check ties to the verified-sender badge language already in the app.

---

_Stopping here per the gate. On approval: implement HomeScreen first, screenshot on-device, wait for OK before ChatScreen._
