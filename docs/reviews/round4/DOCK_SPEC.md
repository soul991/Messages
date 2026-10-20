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
| **Shape** | Bottom-docked bar spanning full width (`bounds.width: 375`, `bounds.height: 83`) | Bottom-docked edge-to-edge bar with `navigationBarsPadding()` | Bottom-docked per Telegram reference (`x: 0, y: 729`). Zero bottom float margin. |
| **Height** | Content height `49px` + Home Indicator `34px` = `83px` | Content height `52.dp` + `navigationBarsPadding()` | Standard bottom dock content height |
| **Corner Radius** | `0` (bottom docked) | `0.dp` bottom, `0.dp` top (or hairline border top) | In rule 5 set `{4,8,12,20,24,28,32}` or `0.dp` |
| **Elevation** | Drop shadow `0 -0.33px` (hairline divider) | `0.dp` surface elevation with 0.5.dp top hairline border | Border stroke `outlineVariant.copy(alpha = 0.35f)` |
| **Container Fill (Light)** | `#f6f6f6` (100% opacity) | `MaterialTheme.colorScheme.surfaceContainer` | Matches Figma `0:7425` fill `#f6f6f6` |
| **Container Fill (Dark)** | `#1c1c1d` (100% opacity) | `MaterialTheme.colorScheme.surfaceContainer` | Matches Figma `0:3678` fill `#1c1c1d` |
| **Top Hairline Divider** | `#a6a6aa` (light), `#3d3d3f` (dark), height 0.33px | `BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))` | Top border matching Figma drop shadow effect |

---

## 2. Tabs & Layout Geometry

| Parameter | Figma Measurement / Value | Compose Implementation Value | Notes |
|---|---|---|---|
| **Tab Count** | 5 categories: Personal (Inbox), Transactions (Bank), Offers (Promotions), Review, Spam | 5 tabs equally distributed across width | `Modifier.weight(1f)` per tab slot |
| **Tab Slot Height** | `49px` | `52.dp` | Vertical centering for touch target |
| **Tab Padding** | Top `4px`, Bottom `4px`, Horizontal `2px` | `PaddingValues(vertical = 4.dp)` | Touch target >= 48dp minimum |
| **Tab Spacing** | Equal distribution (`bounds.width: 94px` per tab) | Equal flex weight (`1f`) | Symmetrical layout across form factors |

---

## 3. Active Tab Indicator & Pill / Tint

| Parameter | Figma Measurement / Value | Compose Implementation Value | Notes |
|---|---|---|---|
| **Indicator Style** | Tint on active icon & label + subtle sliding container tint | Sliding active background tint with spring transition | Preserves liked dock tab selection animation (Rule 4) |
| **Pill Shape** | Rounded capsule | `RoundedCornerShape(12.dp)` | Value within `{4,8,12,20,24,28,32}` (Rule 5) |
| **Pill Fill (Light)** | Semi-transparent primary container `#037ee5` at 12% alpha | `MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)` | Subtle tint highlighting active tab slot |
| **Pill Fill (Dark)** | Semi-transparent white/primary container at 14% alpha | `MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)` | High contrast dark mode tint |
| **Pill Dimensions** | Width: tab slot width minus 8dp margin; Height: 44dp | Width: `fillMaxWidth(0.85f)`, Height: `44.dp` | Centered within tab slot |

---

## 4. Icons & Optical Weight

| Parameter | Figma Measurement / Value | Compose Implementation Value | Notes |
|---|---|---|---|
| **Icon Size** | `25x25` to `29x29` bounding box | `24.dp` | Standard Android / Telegram navigation icon size |
| **Active Icon Variant** | Filled solid vector shape | `DockItem.filledIcon` | From `Dock Category Logos - Redesign [1223:2]` |
| **Inactive Icon Variant** | Outlined stroke vector shape (stroke 1.8f - 2.0f, rounded caps) | `DockItem.outlinedIcon` | From `Dock Category Logos - Redesign [1223:2]` |
| **Active Icon Color (Light)** | `#037ee5` | `MaterialTheme.colorScheme.primary` | Contrast > 4.5:1 against `#f6f6f6` |
| **Active Icon Color (Dark)** | `#ffffff` / `#007aff` | `MaterialTheme.colorScheme.primary` | Contrast > 4.5:1 against `#1c1c1d` |
| **Inactive Icon Color (Light)**| `#545458` (65% opacity) | `MaterialTheme.colorScheme.onSurfaceVariant` | Contrast > 3.0:1 for icons |
| **Inactive Icon Color (Dark)** | `#ffffff` (45% opacity) | `MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)` | Contrast > 3.0:1 for icons |

---

## 5. Tab Labels & Typography

| Parameter | Figma Measurement / Value | Compose Implementation Value | Notes |
|---|---|---|---|
| **Label Present?** | Yes: "Contacts", "Calls", "Chats", "Settings" | Yes: All 5 tabs display title below icon | Matches labeled Telegram reference |
| **Font Size** | `10px` | `10.sp` | Compact label typography |
| **Font Weight** | `500` (Medium) | `FontWeight.Medium` | Clear readability at 10sp |
| **Letter Spacing** | `0.1px` | `0.1.sp` | Tight, legible navigation label tracking |
| **Spacing from Icon** | `2px` - `4px` | `2.dp` Spacer | Compact vertical rhythm |
| **Active Label Color (Light)** | `#037ee5` | `MaterialTheme.colorScheme.primary` | Contrast > 4.5:1 |
| **Active Label Color (Dark)** | `#ffffff` | `MaterialTheme.colorScheme.onSurface` | Contrast > 4.5:1 |
| **Inactive Label Color (Light)**| `#545458` (65% opacity) | `MaterialTheme.colorScheme.onSurfaceVariant` | Contrast > 4.5:1 |
| **Inactive Label Color (Dark)** | `#ffffff` (45% opacity) | `MaterialTheme.colorScheme.onSurfaceVariant` | Contrast > 4.5:1 |

---

## 6. Unread Badge

| Parameter | Figma Measurement / Value | Compose Implementation Value | Notes |
|---|---|---|---|
| **Badge Height** | `18px` | `18.dp` | Matches `0:3693` & `0:7441` |
| **Badge Min-Width** | `18px` | `18.dp` | Circular for 1 digit, capsule for 2+ digits |
| **Corner Radius** | Oval / Circle (half height = 9px) | `RoundedCornerShape(8.dp)` | Within `{4,8,12,20,24,28,32}` (Rule 5) |
| **Position Offset** | Anchor: top-right of icon (`x: 54, y: 2` relative to tab) | `offset(x = 10.dp, y = (-4).dp)` | Anchored to icon top-right |
| **Background (Light)** | `#fe3b30` (Red badge) / Category container | `MaterialTheme.colorScheme.error` or Category Palette | Matches Figma `0:7442` `#fe3b30` |
| **Background (Dark)** | `#ffffff` (White badge) / Category container | `MaterialTheme.colorScheme.surface` or Category Palette | Matches Figma `0:3694` `#ffffff` |
| **Text Color (Light)** | `#fefffe` (White text) | `MaterialTheme.colorScheme.onError` | Contrast > 4.5:1 |
| **Text Color (Dark)** | `#1c1c1d` (Dark text) | `MaterialTheme.colorScheme.onSurface` | Contrast > 4.5:1 |
| **Font Size & Weight** | `11px - 13px`, Weight `400` / `600` | `10.sp`, `FontWeight.SemiBold` | Clear legibility inside 18dp badge |
