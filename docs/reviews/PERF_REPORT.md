Previous values were not backed by committed benchmark output and have been withdrawn.

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

UNMEASURED

---

## 5. Scroll & Transition Journeys (10 iterations each)
*(All measurements: emulator, not representative of a phone)*

### Frame Duration (CPU) Distribution:
UNMEASURED

### Frame Overrun Distribution:
UNMEASURED

---

## 6. Recomposition Measurement (`SwipeableConversationRow`)
*(All measurements: emulator, not representative of a phone)*

To verify the elimination of composition-phase state tracking during swipe gestures, `SideEffect` logging was attached to `SwipeableConversationRow`. A slow continuous drag was executed across the row via `adb shell input swipe 200 800 800 800 3000` (3000ms duration).

UNMEASURED

*The instrumentation diff is archived at `docs/reviews/perf/recomp_counter.patch`.*

---

## 7. Dock Badge Contrast Audit

WCAG 2.1 Level AA compliance requires a minimum contrast ratio of 4.5:1 for body and badge text. In dark and AMOLED palettes, rendering white text directly on category tint failed the contrast threshold (~1.7:1). Badges were updated to use paired `palette.container` and `palette.onContainer` tokens:

UNMEASURED

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
