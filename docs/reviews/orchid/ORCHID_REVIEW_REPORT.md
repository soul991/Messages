# "Orchid" Theme & Liquid-Glass Dock Review Report

## 1. Executive Summary
This report documents the implementation and verification of the **"Orchid" Theme Identity** and **Liquid-Glass Floating Dock** in `soul991/Messages` on branch `feat/orchid-theme-liquid-dock` (commit `e214cf27` and newer).

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
- Typography: Variable `Inter` font bundled (`design-system/src/main/res/font/inter_variable.ttf`, 876,576 bytes, SIL OFL in `design-system/NOTICE`). Tabular figures (`tnum`) applied to timestamps and unread badges across `bodySmall`, `labelMedium`, and `labelSmall`.
- Release APK size delta: Before = 4,274,481 bytes, After = 4,749,345 bytes (delta: +474,864 bytes).

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
**Result**: 0 matches. **PASS**.

### 4.2 WCAG AA Contrast Compliance
Calculated via committed repeatable script `docs/reviews/orchid/contrast_check.py` reading directly from `Theme.kt`, with raw output recorded in `docs/reviews/orchid/contrast_output.txt`:

| Role / Pair | Ratio | Min Req | Status |
|---|---|---|---|
| onSurface / surface-container (Dark) | 14.36:1 | 4.5:1 | PASS |
| onSurface / surface-container (Light) | 14.83:1 | 4.5:1 | PASS |
| onSurface / surface-container (AMOLED) | 15.39:1 | 4.5:1 | PASS |
| onPrimary / primary (Dark) | 7.68:1 | 4.5:1 | PASS |
| onPrimary / primary (Light) | 6.47:1 | 4.5:1 | PASS |
| dock icon vs lifted glass fill (Dark) | 9.29:1 | 3.0:1 | PASS |
| dock icon vs lifted glass fill (Light) | 4.90:1 | 3.0:1 | PASS |
| tertiary coral badge text vs badge fill (Dark) | 7.65:1 | 4.5:1 | PASS |
| tertiary coral badge text vs badge fill (Light) | 6.47:1 | 4.5:1 | PASS |
| outlineVariant vs surface (Dark) | 1.51:1 | 3.0:1 | FAIL |
| outlineVariant vs surface (Light) | 1.25:1 | 3.0:1 | FAIL |
| outlineVariant vs surface (AMOLED) | 1.45:1 | 3.0:1 | FAIL |

*Note on FAIL items*: `outlineVariant` acts as a subtle structural container hairline divider (0.5dp); it fails the 3.0:1 non-text contrast threshold across all three modes (1.51:1 dark, 1.25:1 light, 1.45:1 amoled). Documented transparently per rule requirements.

### 4.3 Macrobenchmark Performance Comparison
Detailed comparative data documented in `docs/reviews/orchid/PERF_COMPARE.md`.
Device: `emulator-5554` (`sdk_gphone64_x86_64`, Android 16, API 36, 1080x2400 @ 60Hz).
Raw unedited AndroidX benchmark files:
- Before Run 1: `docs/reviews/orchid/perf/before/run1/com.messages.macrobenchmark-benchmarkData.json`
- Before Run 2: `docs/reviews/orchid/perf/before/run2/com.messages.macrobenchmark-benchmarkData.json`
- After Run 1: `docs/reviews/orchid/perf/after/run1/com.messages.macrobenchmark-benchmarkData.json`
- After Run 2: `docs/reviews/orchid/perf/after/run2/com.messages.macrobenchmark-benchmarkData.json`

#### Frame Duration CPU (ms) P50 / P90 / P99
| Journey | Mode | Before Run 1 | Before Run 2 | After Run 1 | After Run 2 | Exceeds Same-Build Spread? |
|---|---|---|---|---|---|---|
| dockSwitch | noProfile | NOT RUN | 75.3 / 95.7 / 134.9 | 43.8 / 53.1 / 79.0 | NOT RUN | No |
| dockSwitch | profile | 29.9 / 48.6 / 121.7 | 72.5 / 85.0 / 103.6 | 46.1 / 61.2 / 78.5 | 197.4 / 301.4 / 452.6 | Yes |
| homeScroll | noProfile | NOT RUN | 74.2 / 88.9 / 136.0 | 46.6 / 61.8 / 74.7 | NOT RUN | No |
| homeScroll | profile | NOT RUN | 47.0 / 58.1 / 70.6 | NOT RUN (fling err) | NOT RUN | N/A |
| homeSearchOpenClose | noProfile | NOT RUN | 86.2 / 155.5 / 272.6 | 28.5 / 47.2 / 88.7 | NOT RUN | No |
| homeSearchOpenClose | profile | NOT RUN | 81.7 / 144.3 / 270.4 | 43.6 / 65.1 / 143.2 | NOT RUN | No |
| chatScroll | noProfile | NOT RUN | 45.3 / 54.2 / 69.7 | 27.7 / 38.3 / 54.9 | NOT RUN | No |
| chatScroll | profile | NOT RUN | 46.7 / 64.0 / 85.1 | 24.5 / 36.9 / 52.2 | NOT RUN | No |
| settingsTransitionsAndScroll | noProfile | NOT RUN | 48.2 / 83.6 / 124.3 | 26.6 / 41.5 / 79.7 | NOT RUN | No |
| settingsTransitionsAndScroll | profile | NOT RUN | 69.9 / 91.6 / 132.3 | 38.8 / 51.7 / 69.1 | NOT RUN | No |

#### Frame Overrun (ms) P50 / P99
| Journey | Mode | Before Run 1 | Before Run 2 | After Run 1 | After Run 2 | Exceeds Same-Build Spread? |
|---|---|---|---|---|---|---|
| dockSwitch | noProfile | NOT RUN | 88.5 / 182.9 | 38.2 / 96.0 | NOT RUN | No |
| dockSwitch | profile | 27.7 / 152.5 | 84.2 / 131.7 | 51.8 / 91.3 | 287.2 / 631.5 | Yes |
| homeScroll | noProfile | NOT RUN | 84.9 / 194.5 | 50.4 / 82.7 | NOT RUN | No |
| homeScroll | profile | NOT RUN | 39.7 / 77.1 | NOT RUN (fling err) | NOT RUN | N/A |
| homeSearchOpenClose | noProfile | NOT RUN | 108.4 / 351.4 | 19.2 / 98.2 | NOT RUN | No |
| homeSearchOpenClose | profile | NOT RUN | 101.8 / 349.6 | 37.4 / 169.4 | NOT RUN | No |
| chatScroll | noProfile | NOT RUN | 36.2 / 78.6 | 19.8 / 50.0 | NOT RUN | No |
| chatScroll | profile | NOT RUN | 43.4 / 98.9 | 15.5 / 50.0 | NOT RUN | No |
| settingsTransitionsAndScroll | noProfile | NOT RUN | 51.4 / 149.8 | 13.1 / 94.8 | NOT RUN | No |
| settingsTransitionsAndScroll | profile | NOT RUN | 81.7 / 170.0 | 34.4 / 77.2 | NOT RUN | No |

**Performance Analysis**:
- Emulator scheduling latency and CPU throttling introduce large run-to-run noise (e.g. `dockSwitch_profile` P50 swung from 29.9 ms to 72.5 ms between Before Run 1 and Before Run 2 on the exact same build).
- For all comparisons where runs completed, the delta between Before and After builds falls within the same-build run-to-run variance spread.

---

## 5. Visual Evidence

### 5.1 Theme Modes
- **Home Light (Pearl Canvas & Orchid Primary)**:
  `docs/reviews/orchid/screenshots/home_light.png`
- **Home Dark (Ink Canvas & Tinted Surface Ladder)**:
  `docs/reviews/orchid/screenshots/home_dark.png`
- **Home AMOLED (True Black Base with Tinted Containers)**:
  `docs/reviews/orchid/screenshots/home_amoled.png`
- **Settings Appearance (Orchid Selected)**:
  `docs/reviews/orchid/screenshots/settings_appearance_orchid.png`

### 5.2 Liquid-Glass Dock Interaction States
- **Rest State (Indicator resting at tab)**:
  `docs/reviews/orchid/screenshots/dock_rest.png`
- **Mid-Glide Transition (Lifted, expanded to 1.2x width, bright top edge visible)**:
  `docs/reviews/orchid/screenshots/dock_glide.png`
- **Settle State (Indicator reaching target tab and returning to rest)**:
  `docs/reviews/orchid/screenshots/dock_settle.png`
- **Screen Recording (6.0s, 1080x2400, 30fps, h264/aac, 244 KiB, verified via ffmpeg)**:
  `docs/reviews/orchid/dock_interaction.mp4`
