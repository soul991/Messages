# Design Reference Notes — WhatsApp & Telegram UI Files

_Feeds the Phase 5 UI overhaul. Sources: community Figma-export SVG boards + rendered PNG screens extracted to `design-refs/whatsapp/` and `design-refs/telegram/`. Method note: the SVGs are **fully vector-outlined** (all text converted to paths — no `<text>`, `font-size`, or layer names survive export), so geometry/color values below come from SVG attribute statistics, and typography/anatomy from measuring the rendered PNGs (750×1624 px @2× → all numbers below are in points ≈ dp). Values marked **[measured]** were read off the renders; **[svg]** from attribute counts; **[inferred]** are judgment calls._

Boards: WhatsApp SVG = 45 iPhone frames (375×812 each) on a 3326×7871 canvas; Telegram SVG = 76 frames on 5451×6864 (includes dark-mode frames).

---

## 1. Conversation-list row anatomy

Both apps land on nearly identical row geometry — this is the de-facto standard:

| Metric | WhatsApp | Telegram | Our current | Phase 5 target |
|---|---|---|---|---|
| Row height | **74pt** [svg: 51× `375×74` rects; confirmed in render] | **76pt** [svg: 76× `375×76`] | ~72dp | 72–76dp (keep) |
| Avatar | **52pt** circle [measured] | **56pt** circle [measured] | 48dp | 52–56dp (grow) |
| Left margin | 16pt | 16pt | 16dp ✓ | keep |
| Avatar→text gap | 12–16pt | 16pt | 12dp | 16dp |
| Name size | ~17pt semibold [measured] | ~17pt semibold | titleMedium 16sp ✓ | keep |
| Preview size | ~15pt regular, gray | ~15pt | bodyMedium 14sp | 14–15sp |
| Timestamp | ~13pt gray, top-aligned with name | ~15pt gray | labelSmall 11sp | bump to 12–13sp |
| Divider | 0.33pt hairline [svg: 96×], inset to text column | hairline, inset to text column | full-width 0.5dp | **inset divider** (starts at text x, not screen edge) |

Row structure (both): `[16] [avatar] [16] [name / preview column, weight=1] [timestamp + trailing indicators column]`.

**Trailing-column details** [measured]:
- WhatsApp: date top, chevron `›` bottom (iOS-ism — skip on Android).
- Telegram: time top; bottom slot holds **unread badge** OR **pin icon** (pinned chats with no unread show a gray pin glyph where the badge would be) OR nothing. One slot, one indicator — never stacked.
- Telegram **unread badge**: filled circle ~20pt, white text; **blue for normal chats, gray (`#8E8E93`) for muted chats** — muted still shows the count, just de-emphasized. We currently render one badge style; adopt the muted-gray variant.
- Telegram **read state in the list**: outgoing-read shows small green double-tick before the timestamp. WhatsApp shows blue double-tick inline at the start of the preview text instead.
- Telegram **online presence**: ~10pt green (`#21C004`) dot on the avatar's bottom-right corner. Not applicable to SMS (no presence) — correctly omitted for us.
- Telegram mute state: small gray bell-slash glyph inline after the name.

**Swipe actions** [measured, Telegram Chats render]: full-height colored panels behind the row — right-swipe reveals `Unread` (blue) + `Pin` (green); left-swipe reveals `Mute` (orange), `Delete` (red), `Archive` (gray); icon + label stacked, white on color. WhatsApp: `More` (gray) + `Archive` (blue). Our SwipeToDismissBox with colored backgrounds matches; Telegram supports 2–3 stacked actions per side vs our 1 — **[inferred]** not worth copying (our Settings-configurable single action per side is simpler).

---

## 2. Color palettes

### 2.1 Light mode — where both apps agree (de-facto messaging standards)

| Role | WhatsApp | Telegram | M3 mapping for us |
|---|---|---|---|
| Secondary text/icons | `#8E8E93` (129×) | `#8E8E93` (211×) | `onSurfaceVariant` |
| Primary text | `#060606` | `#1C1C1D` | `onSurface` |
| Tertiary text | `#3C3C43` @ 60% | `#3C3C43` @ 60% | `onSurfaceVariant` |
| **Sent bubble** | `#DCF7C5` | `#E1FEC6` | custom `sentBubbleContainer` role |
| Received bubble | white/`#FEFEFE` | white | `surfaceContainerHigh` (current) |
| Danger | `#FF3B30` | `#FE3B30` | `error` |
| Accent/links | `#007AFF` | `#037EE5` | `primary` (dynamic) |
| List surface | `#F6F6F6` | `#EFEFF4` | `surface` |
| Divider | `#D1D1D6` @ ~29% opacity | same family | `outlineVariant` |
| Delivered/read tick | `#4BD763`/`#34C759` (WA blue ticks in chat: `#3497F9`) | `#21C004` green ticks | custom `deliveredTick` |

Opacity system [svg]: text de-emphasis is done via opacity steps on near-black — 1.0 / 0.65 / 0.6 / 0.4 / 0.3 / 0.29 are the dominant values in both files. That's an alpha-ramp approach; M3's equivalent is the `onSurface`/`onSurfaceVariant`/`outline` role ladder — keep roles, don't copy raw alphas.

### 2.2 Telegram dark mode [svg]

`#1C1C1E` background, `#262628`/`#313131` elevated surfaces, `#48484A` received bubbles/dividers, `#2DA430` sent bubbles (darker green, same hue family as light), `#EBEBF5` @65% secondary text. **Not pure black** — our separate AMOLED `#000000` mode is an extra tier above their standard dark, which is correct positioning.

---

## 3. Bubble geometry (chat screen)

[measured from both Chat renders + svg radii]

| Property | WhatsApp | Telegram | Phase 5 target |
|---|---|---|---|
| Corner radius | ~15pt [svg: rx=15 pills; bubble paths consistent] | ~17–18pt (rounder) | 16dp (M3 `large`) |
| Tail | small curved spike, bottom corner, **last bubble of group only** | same | keep current tail-on-last |
| Max width | ~72–75% of screen [measured] | ~78% | ~76% |
| Inner padding | ~12pt h / 8pt v | ~12pt h / 7pt v | 12×8dp |
| Timestamp placement | **inside bubble**, bottom-right, ~12pt gray, with ticks | inside, bottom-right, *italic*, green-on-green for sent | we render meta below group — acceptable alternative; consider inside-bubble for Phase 5 |
| Group spacing | ~2–3pt intra-group, ~8pt between groups | same | match |
| Date pill | gray-lavender rounded pill, centered, ~"Fri, Jul 26" | white pill @ ~90% opacity | current date pills ✓ |
| Wallpaper | beige doodle pattern under all chats | blue-gray doodle pattern | our per-chat wallpapers ✓; add a subtle default pattern option **[inferred]** |
| File attachment in bubble | nested darker-tint rounded card: icon/thumbnail left + filename + size·type meta line | same, with photo thumbnail (rx≈8) left, filename + size right | pattern for our (text-only) document-style rendering if ever needed; photos render inside the bubble card, not edge-to-edge |
| Reply-quote (Telegram) | — | vertical accent bar + sender name (blue) + quoted line, stacked above the message text inside the bubble | good model for our future quote/smart-card layouts |

Composer [measured]:
- WhatsApp: `+` button left **outside** the pill; pill field (rx≈16, ~36pt tall) with sticker icon **inside right**; camera and mic buttons **outside right**. Send replaces mic when text present.
- Telegram: paperclip left outside; pill "Message" (rx≈18) with timer icon inside-right; mic outside right.
- Ours already matches this anatomy (attach outside, pill field, conditional send) ✓.

Chat top bar [measured]:
- WhatsApp: back + **avatar inside the title area** + name with subtitle **"tap here for contact info"** — an explicit affordance for exactly the contact-detail tap we just built. Consider a one-time subtitle hint in Phase 5.
- Telegram: back + centered name/"last seen" + avatar on the far right.
- Both put presence/subtitle under the name at ~13pt gray — our address-under-name does the same ✓.

---

## 4. Radius & shape system [svg]

| Shape | WhatsApp | Telegram | Us |
|---|---|---|---|
| Bubbles / pills | rx=15–15.75 | rx=14–15 (+ rounder in renders) | 16dp |
| Cards / sheets | — | rx=10 | M3 medium 12dp |
| Chips / small tags | rx=6 | rx=6 | 8dp (M3 small) — 6–8 fine |
| Tiny elements | rx=2.5 | rx=2.5/2.17 | 4dp |
| Avatars | circle | circle (rx=51 on 102pt) | circle ✓ |

4pt grid confirmed throughout both files (44/50/58/74/76/88 bars; 16pt margins).

---

## 5. Bars & navigation [svg + measured]

- WhatsApp: bottom tab bar 83pt (incl. home indicator), 5 tabs, active tab tinted + filled icon; top bar 44–88pt with large-title collapse; search 57pt row.
- Telegram: bottom tab bar 50pt + indicator, 4 tabs, **red numeric badge on the Chats tab icon**; top bar fixed 44pt; search pill 36pt tall, full-width, `#EBEBF5`, magnifier + placeholder centered-left.
- Us: folder chips instead of bottom tabs (PRD §9 keeps the calm-Inbox hero + chips). Phase 5 question (per Truecaller report rec A6): chips vs tabs — these references show both apps reserve bottom tabs for *app sections*, not message categories; message-category navigation as top chips/tabs (Truecaller-style) stays the right pattern for us. **[inferred]**
- Telegram's badge-on-tab-icon = our unread-badge-on-chip pattern ✓.

---

## 6. Concrete Phase 5 punch list distilled from these references

1. Inset row dividers (start at text column) or Telegram-style no-divider spacing — not full-width lines.
2. Avatar 52–56dp (up from 48), row height stays ~72–76dp; timestamp up to 12–13sp.
3. Muted conversations: gray unread badge variant + small bell-slash glyph after the name.
4. One trailing indicator slot per row: unread badge > pin icon > nothing (never stacked).
5. Custom `sentBubbleContainer` color role (green-tint family, light `#DCF7C5`–`#E1FEC6` / dark `#2DA430`-adjacent) wired through Material theme + per-chat ChatStyle presets.
6. Bubble: 16dp corners, tail on group-last only (have), timestamp+status *inside* the bubble bottom-right (evaluate against our current below-group meta).
7. Composer: keep current anatomy; pill ~36–40dp tall, rx 16–18.
8. Chat top bar: avatar joins the title cluster (WhatsApp-style) — pairs with our shared-element avatar transition; optional one-time "tap for contact info" subtitle hint.
9. Default subtle wallpaper pattern option (doodle-class, very low contrast) in ChatStyle.
10. Swipe panels: colored full-height with icon+label (have) — add label text under icon to match reference legibility.

---

## 6b. Contact-info page (WhatsApp render) — feeds our ContactDetailScreen

[measured from `WhatsApp Contact Info.png`]

- **Hero**: full-width edge-to-edge contact photo (~square, no top bar overlap in this render); name (~24pt bold) + number (~15pt gray) *below* the photo, left-aligned.
- **Action cluster**: a row of circular tonal buttons (message / video / call), ~44pt, right-aligned on the name row — icon-only, tinted `#007AFF` on light-blue circles. Our detail page uses labeled `FilledTonalButton`s — fine, but the compact circular tonal trio is the pattern to adopt in Phase 5.
- **Grouped list sections** below, iOS-inset style, each row: colored rounded-square icon (~30pt, distinct hue per row) + label + trailing value + chevron. Sections observed: `Media, Links, and Docs (12)`, `Starred Messages (None)`, `Chat Search`, then a separate group with `Mute (No)`.
- **Mapping to us**: our ContactDetailScreen already has identity + call/save actions + mute/lock/block. Phase 5 upgrades: bigger photo hero (we have photos now), circular action trio, and add rows for `Starred messages` and `Search in conversation` (both exist as features — the detail page is their natural home), with per-row colored icon squares translated to M3 tonal icon containers.

---

## 7. Files

```
design-refs/
  whatsapp/  WhatsApp UI Screens (Community).svg (19 MB, 45 frames, text outlined)
             WhatsApp Chats.png · WhatsApp Chat.png · WhatsApp Contact Info.png
             Mask.png · Mask-1.png (assets)
  telegram/  Telegram UI Screens (Community).svg (53 MB, 76 frames incl. dark mode)
             Telegram UI Screens (Community).pdf (same board)
             Telegram Chats.png · Telegram Chat.png · Rectangle.png (asset)
  archives/  the three original community download zips (79 MB total) —
             kept for future redesign passes, see below
```

**The original zips are kept.** They were moved out of the repo root into
`design-refs/archives/` on 2026-07-27 to keep the root clean:

```
design-refs/archives/Telegram UI Screens (Community).zip       (24 MB → .pdf board + 3 PNGs)
design-refs/archives/Telegram UI Screens (Community) (1).zip   (39 MB → .svg board + 3 PNGs)
design-refs/archives/WhatsApp UI Screens (Community).zip       (15 MB → .svg board + 5 PNGs)
```

Their contents are already extracted into `whatsapp/` and `telegram/` above
(verified file-for-file), so nothing needs re-extracting for normal use — the
zips exist so a later redesign pass can start from the pristine downloads.
All of `design-refs/` is gitignored and never committed.

Typography values are render-measured approximations (SVG text is outlined); treat ±1–2pt.
