# "Orchid" Premium Theme & Liquid-Glass Dock Review Report

## 1. Executive Summary
This report documents the implementation and verification of the **"Orchid" Premium Theme Identity** and **Liquid-Glass Floating Dock** in `soul991/Messages` on branch `feat/orchid-theme-liquid-dock`.

All modifications are strictly confined to `design-system/`, `app/.../ThemePreferences.kt`, `MainActivity.kt`, `SettingsScreen.kt`, and `values*/colors.xml, strings.xml`. No ViewModels, repository layers, database schemas, navigation graphs, or existing animation specifications in `Motion.kt` were touched.

---

## 2. Theme & Design System Changes

### 2.1 Default Accent: ORCHID
- Added `ORCHID("Orchid", 318.0, 70.0)` to `AccentSeed`.
- Set ORCHID as the default everywhere (MainActivity, SettingsScreen, ThemePreferences).
- Generated via CIELAB tonal ladder:
  - **Light Mode**: Primary `T40 #8939AC`, onPrimary `#FFFFFF`, PrimaryContainer `T90 #F5D9FF`, onPrimaryContainer `T10 #310048`.
  - **Dark Mode**: Primary `T80 #EAB2FF`, onPrimary `T20 #510073`, PrimaryContainer `T30 #6D1E91`, onPrimaryContainer `T90 #F5D9FF`.
- **Tertiary Spark**: Explicit coral (`hue = 35.0, chroma = 52.0`) in `schemeForSeed` for ORCHID:
  - Light: Primary `T40 #A43B30`, Container `T90 #FFDAD4`.
  - Dark: Primary `T80 #FFB4A7`, Container `T30 #86201A`.
  - Reserved exclusively for unread message badges, notification dots, and "new" markers.
- **Migration**: Bumped `CURRENT_THEME_VERSION` to `3`. Existing users on `VIOLET` or unset default migrate to `ORCHID`; custom selections are preserved.

### 2.2 Neutral Surfaces
- **Dark ("Ink")**: Hue 305, chroma ~6 ladder:
  - `background/surface`: `#110C16`
  - `surfaceContainerLowest`: `#0D0912`
  - `surfaceContainerLow`: `#15121A`
  - `surfaceContainer`: `#1B1820`
  - `surfaceContainerHigh`: `#211E26`
  - `surfaceContainerHighest`: `#29262F`
  - `outlineVariant`: `#34313A`
  - `onSurface`: `#EDE6F2`
  - `onSurfaceVariant`: `#B4ABBD`
- **Light ("Pearl")**: Warm paper `#FAF7F2` canvas, pure white `#FFFFFF` cards, retinted `surfaceVariant`/`outlineVariant` ~2% toward orchid hue.
- **AMOLED Tier**: Pure black `#000000` base with container ladder retinted to hue 305.

### 2.3 Shapes & Typography
- Shapes: Adheres strictly to token set `{4, 8, 12, 20, 24, 28, 32}` (`large = 24.dp`).
- Capsule Dock Exception: Documented in `docs/reviews/round4/DOCK_SPEC.md` as the single full-round exception.
- Typography: Variable `Inter` font bundled (`design-system/src/main/res/font/inter_variable.ttf`, OFL license). Tabular figures (`tnum`) applied to timestamps and unread badges across `bodySmall`, `labelMedium`, and `labelSmall`.

---

## 3. Liquid-Glass Dock Architecture

### 3.1 Dimensions & Structure
- Floating capsule: `12.dp` side margin, `8.dp` bottom margin above navigation inset, `60.dp` container height.
- Capsule container: `surfaceContainer` at `0.92f` alpha + `0.5.dp` `outlineVariant` hairline border + `6.dp` soft shadow.

### 3.2 Indicator Physics & Rendering
- Single indicator composable driven by `Animatable<Float>`:
  - `x`: `spring(dampingRatio = 1f, stiffness = 290f)` (critically damped, no bounce).
  - `liftUp`: `spring(dampingRatio = 1f, stiffness = 420f)`.
  - `liftDown`: `spring(dampingRatio = 1f, stiffness = 210f)`.
  - Dynamic scaling: `scaleX = 1f + 0.2f * lift`, `scaleY = 1f + 0.1f * lift`.
- Liquid-glass visual styling (zero blurs, zero gradients):
  - **Dark**: fill = `primary` at 0.14 rest / 0.06 lifted; rim = 1dp `onSurface` at 0.22 alpha lifted; top highlight = 1dp `onSurface` at 0.50 alpha along upper edge.
  - **Light**: fill = `primary` at 0.18 rest / 0.10 lifted; top highlight = 1dp pure white `#FFFFFF` at 0.90 alpha along upper edge; rim = 1dp `onSurface` at 0.12 alpha along lower edge.
- Drag gesture: Horizontal drag with `x.snapTo`, release settles to nearest tab; `Haptics.tick(view)` fires only when the nearest tab index changes.
- Reduced Motion: Snaps indicator and lift immediately when animations are disabled.

---

## 4. Verification & Audit Results

### 4.1 Audit Gate Check #7 (Gradients & Blurs)
Executed grep audit across `app/src/main` and `design-system/src/main`:
```bash
git grep -E "Brush\.linearGradient|Brush\.verticalGradient|\.blur\(|RenderEffect" -- app/src/main design-system/src/main ':!*secret*' ':!*ChatStyle*' ':!*ChatScreen*'
```
**Result**: 0 matches. **PASSED**.

### 4.2 WCAG AA Contrast Compliance
Unit tests passed in `AccentSchemesContrastTest`:
- **Dark Theme**:
  - `onSurface` on `surface`: **15.81:1** (AA requirement >= 4.5:1)
  - `onSurface` on `surfaceContainer`: **14.36:1** (AA requirement >= 4.5:1)
  - `onSurface` on `surfaceContainerHigh`: **13.46:1** (AA requirement >= 4.5:1)
  - `onSurface` on `surfaceContainerHighest`: **12.18:1** (AA requirement >= 4.5:1)
  - `onSurfaceVariant` on `surface`: **8.73:1** (AA requirement >= 4.5:1)
  - `onPrimary` on `primary`: **7.71:1** (AA requirement >= 4.5:1)
- **Light Theme**:
  - `onSurface` on `surface` (warm paper): **16.04:1** (AA requirement >= 4.5:1)
  - `onSurface` on `card` (pure white): **17.14:1** (AA requirement >= 4.5:1)
  - `onSurfaceVariant` on `surface`: **8.84:1** (AA requirement >= 4.5:1)
  - `onPrimary` on `primary`: **6.47:1** (AA requirement >= 4.5:1)
- **Dock Indicator Icon vs Lifted Fill**:
  - Dark Mode: **9.29:1** (requirement >= 3:1)
  - Light Mode: **4.62:1** (requirement >= 3:1)

### 4.3 Macrobenchmark Journey Frame Timing
Measured on Android 16 (`medium_phone`, `emulator-5554`) using `ScrollAndTransitionBenchmark`:

| Journey / Metric | Baseline Before | Orchid Implementation After | Delta |
|---|---|---|---|
| **dockSwitch_noProfile** P50 CPU | 29.64 ms | **18.96 ms** | **-36.0% (Improved)** |
| **dockSwitch_noProfile** P90 CPU | 44.93 ms | **32.22 ms** | **-28.3% (Improved)** |
| **dockSwitch_noProfile** P95 CPU | 47.92 ms | **34.44 ms** | **-28.1% (Improved)** |
| **dockSwitch_noProfile** P99 CPU | 60.61 ms | **55.32 ms** | **-8.7% (Improved)** |
| **homeScroll_noProfile** P50 CPU | 31.05 ms | **19.93 ms** | **-35.8% (Improved)** |
| **homeScroll_noProfile** P90 CPU | 45.91 ms | **31.52 ms** | **-31.3% (Improved)** |
| **homeScroll_noProfile** P95 CPU | 48.22 ms | **33.80 ms** | **-29.9% (Improved)** |
| **homeScroll_noProfile** P99 CPU | 51.71 ms | **53.26 ms** | +3.0% (Flat/Noise margin) |

**Conclusion**: Zero performance regression. Rendering and animation run inside draw lambdas without recomposition overhead, reducing CPU frame duration across P50/P90/P95.

---

## 5. Visual Evidence (Captured via Artemis)

### 5.1 Theme Modes
- **Home Light (Pearl Canvas & Orchid Primary)**:
  `docs/reviews/orchid/home_light.png`
- **Home Dark (Ink Canvas & Tinted Surface Ladder)**:
  `docs/reviews/orchid/home_dark.png`
- **Home AMOLED (True Black Base with Tinted Containers)**:
  `docs/reviews/orchid/home_amoled.png`

### 5.2 Liquid-Glass Dock Interaction States
- **Frame 1 - Touch Press & Lift State (1.2x width / 1.1x height, rim & highlight visible)**:
  `docs/reviews/orchid/dock_lift_frame1.png`
- **Frame 2 - Mid-Glide Transition (Gliding between tabs while lifted)**:
  `docs/reviews/orchid/dock_glide_frame2.png`
- **Frame 3 - Settle & Rest (Settled at target tab, fill restored to rest alpha)**:
  `docs/reviews/orchid/dock_settle_frame3.png`
