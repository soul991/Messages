# Part 3 Specification: Conversation List Swipe Actions

## 1. Overview & Problem Analysis

In v2.1.2, swiping a conversation row on the home screen exhibited visual defects:
1. **Thin Action Strip**: `SwipeActionBackground` was placed in an unconstrained `Box` with `Modifier.fillMaxSize()`, collapsing to ~24dp height at the top edge of the row instead of the full row height (~76dp).
2. **Top-Aligned Label**: The action label was pinned to the top edge rather than vertically centered.
3. **Clipped Sliding Content**: The row content slid sideways without a distinct solid surface matching the card shape, causing avatar and text glyphs to be cut off abruptly against transparent backgrounds.
4. **Non-Theme Saturated Colors**: Actions used category badge colors (`categoryPalette("TRANSACTIONS")` green and `categoryPalette("SPAM")` saturated red) instead of Material 3 theme container tokens.

---

## 2. Figma Reference Measurements

### 2.1 Owner's Design: `Messages — UI Redesign (part 2)`
- **Frame**: `Row — swiping to Unarchive [1:202]` inside `Archived — Light [1:190]`
- **Row Dimensions**: 358dp width × 76dp height.
- **Corner Radius**: 28dp (`RoundedCornerShape(28.dp)`), `clipsContent = true`.
- **Action Background**: Full 76dp height, fills the region revealed behind the sliding row.
- **Action Content Layout**:
  - Horizontal row with 8dp gap between icon and text.
  - Icon: 22dp × 22dp vector.
  - Text: 14sp SemiBold (`fontWeight = 600`), white in light mode, vertically centered at `y = 27dp` ((76 - 22) / 2 = 27dp).
- **Sliding Row**:
  - Full row dimensions (358dp × 76dp).
  - Solid surface (`#ffffff` / `MaterialTheme.colorScheme.groupCard`).
  - Corner radius: 28dp.
  - Slides horizontally as a unified surface (`x = -125dp` in Figma) to uncover the action behind it.

### 2.2 Telegram Reference: `Telegram UI Screens (Community)`
- **Nodes**: `Right Swipe Actions [0:3516]` and `Left Swipe Actions [0:3531]`
- **Height**: 76dp.
- **Action Width**: 74dp per action button.
- **Icon / Label**: 22–26dp vectors, vertically centered, medium text weight.
- **Colors**:
  - Delete: Red (`errorContainer` / `onErrorContainer`).
  - Archive: Secondary tone (`secondaryContainer` / `onSecondaryContainer`).

---

## 3. Architecture & Implementation Plan

### 3.1 Layout & Geometry
1. **Root Row Box**:
   - `Modifier.fillMaxWidth().clip(cardShape)`
   - `cardShape` matches the group index:
     - Single row: `RoundedCornerShape(28.dp)`
     - Top row: `RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)`
     - Bottom row: `RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)`
     - Middle row: `RectangleShape`
     *(All radii strictly within `{4, 8, 12, 20, 24, 28, 32}` per Rule 5).*
2. **Action Background (`SwipeActionBackground`)**:
   - Placed as the first child of the root `Box`.
   - Uses `Modifier.matchParentSize().clip(cardShape).background(visuals.container)`.
   - Guarantees 100% full height and width matching the parent card row.
   - Action content (Icon + Label) is placed in a `Row` with `verticalAlignment = Alignment.CenterVertically` and `padding(horizontal = 24.dp)`.
3. **Sliding Content Box**:
   - Placed as the second child of the root `Box`.
   - `Modifier.fillMaxWidth().graphicsLayer { translationX = liveOffset.floatValue + returnOffset.value }.clip(cardShape).background(MaterialTheme.colorScheme.groupCard)`.
   - Solid `groupCard` surface ensures complete opacity and prevents glyph clipping behind transparent layers.

### 3.2 Thresholds & Gesture Dynamics
- **Reveal Threshold**: Action content begins revealing immediately as the row is dragged.
- **Commit Threshold**: 36% (0.36f) of `rowWidthPx`.
- **Haptic Feedback**: When passing the commit threshold (crossing 36%), a light tactile tick (`Haptics.tick(view)`) is triggered via `LaunchedEffect(willCommit)`.
- **Visual State at Commit**:
  - Icon scales up from 1.0f to 1.25f (`Motion.spatialDefault()`).
  - Icon transitions from Outlined to Filled variant.
- **Release Dynamics**:
  - **Commit (released >= 36%)**: Commits action (`onAction(action, conv)`). Row collapses, list closes gap with `animateItem(spatialDefault())`. Shows 4-second Undo snackbar.
  - **Cancel (released < 36%)**: Springs back smoothly to 0 offset with `Motion.butterSpring()`.
- **Directional Locking**: Horizontal drag consumes touch delta (`change.consume()`), preventing vertical scrolling while swiping.
- **Scroll Guard**: `canCommitSwipe = { !folderListState.isScrollInProgress }` ensures unintentional flings during list scroll cannot commit.

### 3.3 Color System (Theme Tokens Only)
- **Archive**:
  - `container`: `MaterialTheme.colorScheme.secondaryContainer`
  - `tint`: `MaterialTheme.colorScheme.onSecondaryContainer`
- **Delete**:
  - `container`: `MaterialTheme.colorScheme.errorContainer`
  - `tint`: `MaterialTheme.colorScheme.onErrorContainer`
- **Pin**:
  - `container`: `MaterialTheme.colorScheme.primaryContainer`
  - `tint`: `MaterialTheme.colorScheme.onPrimaryContainer`
- **Read**:
  - `container`: `MaterialTheme.colorScheme.tertiaryContainer`
  - `tint`: `MaterialTheme.colorScheme.onTertiaryContainer`
- **Mute**:
  - `container`: `MaterialTheme.colorScheme.surfaceContainerHighest`
  - `tint`: `MaterialTheme.colorScheme.onSurface`
- *No hardcoded Color(0x...), no blur, no gradients.*

### 3.4 Vector Icon Artwork
- Created in `design-system/src/main/kotlin/com/messages/designsystem/SwipeIcons.kt`:
  - `ArchiveOutlined` / `ArchiveFilled`
  - `DeleteOutlined` / `DeleteFilled`
  - `PinOutlined` / `PinFilled`
  - `ReadOutlined` / `ReadFilled`
  - `MuteOutlined` / `MuteFilled`
- Crafted with 2.0dp stroke weight, rounded caps (`StrokeCap.Round`), rounded joins (`StrokeJoin.Round`), and 24dp optical balance.

### 3.5 Accessibility & RTL
- **RTL**: Directions mirror using `LocalLayoutDirection.current == LayoutDirection.Rtl`.
- **Font Scale 2.0**: Vertically centered layout with `maxLines = 1, overflow = TextOverflow.Ellipsis` ensures no clipping or overflow at large font scales.
- **TalkBack**: `CustomAccessibilityAction` exposed on `ConversationRow` for Archive and Delete.
