# Performance & Benchmark Report (Round 3)

## 1. Test Environment & Methodology

> **IMPORTANT DISCLAIMER**: All benchmarks in this report were executed on a virtual Android emulator (`emulator-5554`). All numbers are **"emulator, not representative of a phone"**. They reflect the architectural performance delta between uncompiled (`CompilationMode.None`) and baseline-profile-guided compilation (`CompilationMode.Partial`), as well as layout/draw phase optimizations. They must never be compared with Google Messages or any physical hardware metrics.

- **Target Device Specifications**:
  - **Serial**: `emulator-5554`
  - **Model**: `sdk_gphone64_x86_64`
  - **Platform Version**: Android 16 (API level 36, preview build BAK.241212.001)
  - **CPU ABI**: `x86_64`
  - **GPU Mode**: `host`
  - **Display Refresh Rate**: `60.0 Hz` (Display resolution: 1080 x 2400)
- **Environment Controls**:
  - Screen on and unlocked (`settings put global stay_on_while_plugged_in 3`).
  - Battery saver disabled, thermal status monitored before and after runs (`Thermal Status: 0`, 30.1°C skin, 30.2°C battery).
  - Background applications terminated prior to test execution.
- **Benchmark Tooling**:
  - `androidx.benchmark:benchmark-macro-junit4:1.3.1`
  - `androidx.test.uiautomator:uiautomator:2.3.0`
  - Metric collection via AndroidX Macrobenchmark Perfetto trace engine (`FrameTimingMetric`, `StartupTimingMetric`).
  - Iterations: 10 iterations per journey per compilation mode.
  - Raw JSON results committed in `docs/reviews/perf/com.messages.macrobenchmark-benchmarkData.json`.

---

## 2. Release Build & Security Conformance

- **Target Artifact**: Release APK (`app/build/outputs/apk/release/Messages.apk`)
- **DEX Symbol Leak Check**:
  - Scanned all `classes*.dex` files in the release APK archive for test/debug classes: `SeedDataReceiver`, `SyntheticStress`, `Rostova`.
  - **Matches Found**: `0`
- **Merged Release Manifest Check**:
  - Verified `app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`.
  - Result: `SeedDataReceiver` is completely absent from the release manifest (present only in `debug` and `benchmark` manifests with `android.permission.DUMP`).
- **Benchmark Application Isolation**:
  - Benchmark build configured with `applicationIdSuffix = ".benchmark"`.
  - Macrobenchmark targets `com.messages.app.benchmark`, isolating test execution from production storage.

---

## 3. Test Dataset & Seeding

Seeded via `SeedDataReceiver` (`com.messages.app.debug.SEED` with `wipe=true`):
- **Receiver Permission**: Protected with `android:permission="android.permission.DUMP"` (restricted to `adb shell`).
- **Conversations**: Exactly 500 conversations across 5 categories:
  - `INBOX`: 100 conversations
  - `TRANSACTIONS`: 100 conversations
  - `PROMOTIONS`: 100 conversations
  - `REVIEW`: 100 conversations
  - `SPAM`: 100 conversations
- **Chat Thread 1**: Exactly 100 messages with varied message lengths, links, emoji-only strings, OTPs, and bank summary cards.
- **Onboarding Bypass**: Automatically sets `onboarding` preferences (`done=true`) so benchmark runs launch straight into Home.

---

## 4. Startup Benchmark Results (10 iterations)
*(All measurements: emulator, not representative of a phone)*

| Metric | CompilationMode.None | CompilationMode.Partial (Baseline Profile) |
| :--- | :--- | :--- |
| **timeToInitialDisplayMs (min)** | 467.8 ms | 393.6 ms |
| **timeToInitialDisplayMs (median)** | 517.0 ms | 476.7 ms |
| **timeToInitialDisplayMs (max)** | 1130.5 ms | 549.8 ms |

---

## 5. Scroll & Transition Journeys (10 iterations each)
*(All measurements: emulator, not representative of a phone)*

### Frame Duration (CPU) Distribution:
| Journey | Compilation Mode | P50 (ms) | P90 (ms) | P95 (ms) | P99 (ms) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Journey 1: Home Fling (500 items)** | CompilationMode.Partial | 7.9 | 23.2 | 24.9 | 33.7 |
| | CompilationMode.None | 8.4 | 24.4 | 30.2 | 33.9 |
| **Journey 2: Home Search Overlay (x10)** | CompilationMode.Partial | 29.4 | 37.4 | 50.9 | 77.9 |
| | CompilationMode.None | 30.5 | 49.2 | 72.9 | 106.5 |
| **Journey 3: Dock Category Switch (5 tabs)** | CompilationMode.Partial | 28.7 | 37.1 | 44.9 | 51.6 |
| | CompilationMode.None | 28.2 | 35.9 | 43.6 | 47.8 |
| **Journey 4: Chat 100-msg Scroll & Send (5 msgs, 3 quick replies)** | CompilationMode.Partial | 28.9 | 40.7 | 45.5 | 49.8 |
| | CompilationMode.None | 29.6 | 44.0 | 47.2 | 50.2 |
| **Journey 5: Settings Quick Replies (swipe delete, edit, add)** | CompilationMode.Partial | 28.5 | 36.4 | 44.0 | 49.3 |
| | CompilationMode.None | 28.0 | 36.0 | 44.6 | 48.8 |
| **Journey 6: Chat <-> Home Open/Close (x10 transitions)** | CompilationMode.Partial | 77.3 | 105.9 | 124.8 | 207.9 |
| | CompilationMode.None | 72.8 | 88.5 | 97.3 | 131.9 |

### Frame Overrun Distribution:
| Journey | Compilation Mode | P50 (ms) | P90 (ms) | P95 (ms) | P99 (ms) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Journey 1: Home Fling (500 items)** | CompilationMode.Partial | -3.4 | 8.1 | 9.9 | 26.1 |
| | CompilationMode.None | -2.9 | 9.3 | 22.8 | 26.0 |
| **Journey 2: Home Search Overlay (x10)** | CompilationMode.Partial | 15.6 | 23.2 | 37.7 | 64.6 |
| | CompilationMode.None | 16.2 | 35.8 | 62.4 | 93.2 |
| **Journey 3: Dock Category Switch (5 tabs)** | CompilationMode.Partial | 13.9 | 22.5 | 30.3 | 36.9 |
| | CompilationMode.None | 13.4 | 21.0 | 28.7 | 33.1 |
| **Journey 4: Chat 100-msg Scroll & Send** | CompilationMode.Partial | 13.8 | 26.3 | 30.9 | 35.2 |
| | CompilationMode.None | 14.5 | 29.7 | 32.9 | 35.5 |
| **Journey 5: Settings Quick Replies** | CompilationMode.Partial | 13.7 | 21.7 | 29.3 | 34.6 |
| | CompilationMode.None | 13.2 | 21.4 | 29.9 | 34.2 |
| **Journey 6: Chat <-> Home Open/Close** | CompilationMode.Partial | 91.6 | 140.7 | 179.9 | 300.9 |
| | CompilationMode.None | 85.3 | 110.7 | 121.9 | 223.4 |

---

## 6. Recomposition Measurement (`SwipeableConversationRow`)
*(All measurements: emulator, not representative of a phone)*

To verify the elimination of composition-phase state tracking during swipe gestures, `SideEffect` logging was attached to `SwipeableConversationRow`. A slow continuous drag was executed across the row via `adb shell input swipe 200 800 800 800 3000` (3000ms duration).

| State | Implementation Details | Recompositions during 3000ms Drag |
| :--- | :--- | :--- |
| **Before Phase 2 (`40eacab^`)** | `tx` offset read directly inside composition phase; lambda recreation on each drag event | **~180 recompositions** (~1 per frame at 60 Hz) |
| **HEAD (Current)** | `liveOffset` and `returnOffset` consumed strictly inside `graphicsLayer { translationX = tx }` (draw/layout phase) | **1 recomposition** (on initial touch trigger only; 0 during animation) |

*The instrumentation diff is archived at `docs/reviews/perf/recomp_counter.patch`.*

---

## 7. Dock Badge Contrast Audit

WCAG 2.1 Level AA compliance requires a minimum contrast ratio of 4.5:1 for body and badge text. In dark and AMOLED palettes, rendering white text directly on category tint failed the contrast threshold (~1.7:1). Badges were updated to use paired `palette.container` and `palette.onContainer` tokens:

| Theme | Category | Tint Hex | White vs Tint (CR) | Result (< 4.5:1?) | Palette Container | Palette onContainer | Container Pair CR | Result (≥ 4.5:1?) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Light** | INBOX | `#5A41DD` | 6.49:1 | PASS | `#E8DEFF` | `#1E0060` | 13.34:1 | PASS (AAA) |
| **Light** | TRANSACTIONS | `#1B6C31` | 6.49:1 | PASS | `#A3F4AF` | `#00210A` | 13.19:1 | PASS (AAA) |
| **Light** | PROMOTIONS | `#7A5900` | 6.45:1 | PASS | `#FFDF9E` | `#261A00` | 13.25:1 | PASS (AAA) |
| **Light** | REVIEW | `#555F71` | 6.44:1 | PASS | `#D9E3F8` | `#121C2B` | 13.28:1 | PASS (AAA) |
| **Light** | SPAM | `#BA1A1A` | 6.46:1 | PASS | `#FFDAD6` | `#410002` | 13.26:1 | PASS (AAA) |
| **Dark** | INBOX | `#D1BBFD` | 1.72:1 | FAIL | `#4F378B` | `#E8DEFF` | 7.25:1 | PASS (AAA) |
| **Dark** | TRANSACTIONS | `#88D896` | 1.71:1 | FAIL | `#0F5223` | `#A3F4AF` | 7.16:1 | PASS (AAA) |
| **Dark** | PROMOTIONS | `#EFC047` | 1.71:1 | FAIL | `#5C4300` | `#FFDF9E` | 7.22:1 | PASS (AAA) |
| **Dark** | REVIEW | `#BDC7DC` | 1.70:1 | FAIL | `#3E4759` | `#D9E3F8` | 7.24:1 | PASS (AAA) |
| **Dark** | SPAM | `#FFB4AB` | 1.70:1 | FAIL | `#93000A` | `#FFDAD6` | 7.24:1 | PASS (AAA) |
| **AMOLED** | INBOX | `#D1BBFD` | 1.72:1 | FAIL | `#4F378B` | `#E8DEFF` | 7.25:1 | PASS (AAA) |
| **AMOLED** | TRANSACTIONS | `#88D896` | 1.71:1 | FAIL | `#0F5223` | `#A3F4AF` | 7.16:1 | PASS (AAA) |
| **AMOLED** | PROMOTIONS | `#EFC047` | 1.71:1 | FAIL | `#5C4300` | `#FFDF9E` | 7.22:1 | PASS (AAA) |
| **AMOLED** | REVIEW | `#BDC7DC` | 1.70:1 | FAIL | `#3E4759` | `#D9E3F8` | 7.24:1 | PASS (AAA) |
| **AMOLED** | SPAM | `#FFB4AB` | 1.70:1 | FAIL | `#93000A` | `#FFDAD6` | 7.24:1 | PASS (AAA) |

*All category badges exceed 7.16:1 contrast across all themes.*

---

## 8. Category Dock Logo Redesign

Stock Material icons (`Forum`, `ReceiptLong`, `LocalOffer`, `RateReview`, `Shield`) were replaced with bespoke, purpose-crafted vector graphics (`com.messages.designsystem.DockIcons`):

1. **Personal (`INBOX`)**: Smooth organic speech bubble with an integrated personal avatar silhouette (head circle + shoulder curve).
2. **Bank (`TRANSACTIONS`)**: Neoclassical architectural bank portico with pediment, 3 pillars, architrave, and stepped base podium.
3. **Offers (`PROMOTIONS`)**: Celebratory gift box with vertical ribbon and top bow loops.
4. **Review (`REVIEW`)**: Inspection & verification clipboard with clip, checkmark, and review line for OTPs/2FA.
5. **Spam (`SPAM`)**: Protective security shield with an inner diagonal prohibition slash.

Each icon includes matching **Outlined** and **Filled** state pairs with smooth 120ms crossfade transitions synchronized with the sliding active pill.
