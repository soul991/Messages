# Dock Redesign Specification (Telegram Reference)

**Source Reference**: Figma file `Telegram UI Screens (Community)` (`unsaved-mvgn0rf0-b1sl9ztt`)
- Dark Mode Frame: `Tab Bar [0:3677]` (inside `Telegram Chats [0:3515]`)
- Light Mode Frame: `Tab Bar [0:7424]` (inside `Telegram Chat [0:7412]`)
- Vector Icons Frame: `Dock Category Logos - Redesign [1223:2]`
- License: Material Symbols / Android Open Source Project (Apache-2.0)

---

## 1. Dock Bar Structure & Shape

| Parameter | Figma Measurement / Value | Compose Implementation Value | Notes / WCAG / Rules |
|---|---|---|---|
| **Shape** | Floating capsule bar with side margins and lift | Floating capsule: `padding(horizontal = 12.dp, bottom = 8.dp)` above nav bar | Floating capsule per Orchid 2026 refresh |
| **Height** | Content height `60px` | `60.dp` content height | Expanded comfort height for liquid-glass indicator |
| **Corner Radius** | Full round capsule (`CircleShape`) | `CircleShape` | **Explicit Design System Exception**: The dock floating capsule is the one permitted full-round (`CircleShape`) exception to the standard radius set `{4, 8, 12, 20, 24, 28, 32}`. |
| **Elevation** | Soft drop shadow | `shadowElevation = 6.dp` | Soft shadow elevation |
| **Container Fill (Light)** | `#F2EEE9` at 92% alpha | `MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f)` | Retinted Pearl neutral surface container |
| **Container Fill (Dark)** | `#1B1820` at 92% alpha | `MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f)` | Tinted Ink neutral surface container |
| **Hairline Outline** | Subtle outline border | `BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)` | Hairline container definition |

---

## 2. Tabs & Layout Geometry

| Parameter | Figma Measurement / Value | Compose Implementation Value | Notes |
|---|---|---|---|
| **Tab Count** | 5 categories: Personal (Inbox), Transactions (Bank), Offers (Promotions), Review, Spam | 5 tabs equally distributed across width | `Modifier.weight(1f)` per tab slot |
| **Tab Slot Height** | `49px` | `52.dp` | Vertical centering for touch target |
| **Tab Padding** | Top `4px`, Bottom `4px`, Horizontal `2px` | `PaddingValues(vertical = 4.dp)` | Touch target >= 48dp minimum |
| **Tab Spacing** | Equal distribution (`bounds.width: 94px` per tab) | Equal flex weight (`1f`) | Symmetrical layout across form factors |

---

## 3. Active Tab Indicator & Liquid-Glass Pill
 
| Parameter | Figma Measurement / Value | Compose Implementation Value | Notes |
|---|---|---|---|
| **Indicator Style** | Liquid-glass floating capsule with lift & glide physics | `GlassIndicator` driven by `Animatable(x)` and `Animatable(lift)` | Physical lift on press (1.2x wide, 1.1x tall), glide, settle back to rest |
| **Spring Constants** | Reference recording (~56 fps, 300-350ms switch) | x: `spring(1f, 290f)`; lift up: `spring(1f, 420f)`; lift down: `spring(1f, 210f)` | Critically damped horizontal glide; lift and settle |
| **Pill Shape** | Rounded capsule | Full capsule (`r = height / 2`) | **Explicit Exception**: Capsule full-round exception to `{4,8,12,20,24,28,32}` |
| **Pill Fill (Light)** | Translucent primary fill | `primary` at 0.18 alpha rest, 0.10 while lifted | Retains >= 3:1 contrast against pale canvas |
| **Pill Fill (Dark)** | Translucent primary fill | `primary` at 0.14 alpha rest, 0.06 while lifted | High contrast dark mode tint |
| **Glass Highlights & Rims** | Zero blur/gradient audit compliance | Dark: 1dp onSurface rim (0.22 alpha) + top highlight (0.50 alpha); Light: 1dp pure white top highlight (0.90 alpha) + lower rim (0.12 alpha) | Layered canvas drawing without AGSL/RenderEffect |
| **Drag & Retargeting** | Fluid finger tracking | Pointer drag with `x.snapTo`, release to nearest tab, re-targets mid-glide without jump | Emits `Haptics.tick(view)` on tab crossing |

---

## 4. Icons & Optical Weight

| Parameter | Figma Measurement / Value | Compose Implementation Value | Notes |
|---|---|---|---|
| **Icon Size** | `25x25` to `29x29` bounding box | `24.dp` | Standard Android / Telegram navigation icon size |
| **Active Icon Variant** | Filled solid vector shape | `DockItem.filledIcon` | From `Dock Category Logos - Redesign [1223:2]` |
| **Inactive Icon Variant** | Outlined stroke vector shape (stroke 1.8f - 2.0f, rounded caps) | `DockItem.outlinedIcon` | From `Dock Category Logos - Redesign [1223:2]` |
| **Active Icon Color (Light)** | `#8939AC` | `MaterialTheme.colorScheme.primary` | Contrast > 4.5:1 against canvas |
| **Active Icon Color (Dark)** | `#EAB2FF` | `MaterialTheme.colorScheme.primary` | Contrast > 4.5:1 against dark surface |
| **Inactive Icon Color (Light)**| `#675F6F` | `MaterialTheme.colorScheme.onSurfaceVariant` | Contrast > 3.0:1 for icons |
| **Inactive Icon Color (Dark)** | `#B4ABBD` | `MaterialTheme.colorScheme.onSurfaceVariant` | Contrast > 3.0:1 for icons |

---

## 5. Tab Labels & Typography

| Parameter | Figma Measurement / Value | Compose Implementation Value | Notes |
|---|---|---|---|
| **Label Present?** | Yes: "Contacts", "Calls", "Chats", "Settings" | Yes: All 5 tabs display title below icon | Matches labeled Telegram reference |
| **Font Size** | `10px` | `10.sp` | Compact label typography |
| **Font Weight** | `500` (Medium) | `FontWeight.Medium` | Clear readability at 10sp |
| **Letter Spacing** | `0.1px` | `0.1.sp` | Tight, legible navigation label tracking |
| **Spacing from Icon** | `2px` - `4px` | `2.dp` Spacer | Compact vertical rhythm |
| **Active Label Color (Light)** | `#8939AC` | `MaterialTheme.colorScheme.primary` | Contrast > 4.5:1 |
| **Active Label Color (Dark)** | `#EDE6F2` | `MaterialTheme.colorScheme.onSurface` | Contrast > 4.5:1 |
| **Inactive Label Color (Light)**| `#675F6F` | `MaterialTheme.colorScheme.onSurfaceVariant` | Contrast > 4.5:1 |
| **Inactive Label Color (Dark)** | `#B4ABBD` | `MaterialTheme.colorScheme.onSurfaceVariant` | Contrast > 4.5:1 |

---

## 6. Unread Badge

| Parameter | Figma Measurement / Value | Compose Implementation Value | Notes |
|---|---|---|---|
| **Badge Height** | `18px` | `18.dp` | Standard badge height |
| **Badge Min-Width** | `18px` | `18.dp` | Circular for 1 digit, capsule for 2+ digits |
| **Corner Radius** | Oval / Circle (half height = 9px) | `RoundedCornerShape(8.dp)` | Within `{4,8,12,20,24,28,32}` (Rule 5) |
| **Position Offset** | Anchor: top-right of icon (`x: 54, y: 2` relative to tab) | `offset(x = 10.dp, y = (-4).dp)` | Anchored to icon top-right |
| **Background (Light & Dark)** | Coral warm tertiary spark | `MaterialTheme.colorScheme.tertiary` (#A43B30 light / #FFB4A7 dark) | Dedicated tertiary accent |
| **Text Color (Light & Dark)** | High contrast on-tertiary | `MaterialTheme.colorScheme.onTertiary` (white light / #510073 dark) | Contrast > 4.5:1 |
| **Font Size & Weight** | `10sp`, Weight `600` | `10.sp`, `FontWeight.SemiBold`, tabular `tnum` figures | Clear legibility inside 18dp badge |
