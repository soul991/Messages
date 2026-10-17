# Phase 7 Check & Audit Verification

This document logs the step-by-step verification, raw command outputs, and audit findings for PR #28 (`feat/ui-motion-round3`).

---

## Part 1: CI Failure & Dependency Vulnerability Scan

### 1. Root Cause Analysis
- **Failing CI Run**: 39062683363 (CI #198)
- **Failing Job**: `Dependency vulnerability scan` (Step: `Scan against OSV`, exit code 1)
- **Identified Advisories**:
  1. `GHSA-7xpr-hc2w-34m9` (Wire: `skipGroup()` missing negative-length check allows 10-byte payload to crash any Wire-decoding service, CVSS 7.5 HIGH)
     - Artifacts: `com.squareup.wire:wire-runtime-jvm:4.9.7`, `com.squareup.wire:wire-runtime:4.9.7`
  2. `GHSA-9rm7-3qhh-h2mc` (Wire: Unauthenticated decoder crash via 32-bit length integer overflow in ByteArrayProtoReader32, CVSS 7.5 HIGH)
     - Artifacts: `com.squareup.wire:wire-runtime:4.9.7`
- **Dependency Origin**:
  - `androidx.benchmark:benchmark-macro-junit4:1.3.1` -> `androidx.benchmark:benchmark-common:1.3.1` -> `com.squareup.wire:wire-runtime:4.9.7`.
- **Is it NEW in this PR?**:
  - **YES**. It was introduced in Phase 6 (commit `8150a42`) when adding the `:macrobenchmark` module with `androidx.benchmark:benchmark-macro-junit4:1.3.1` and generating verification metadata entries in `gradle/verification-metadata.xml`. It was never present in `main`.
- **Runtime Classpath Scoping Check**:
  - Run `./gradlew :app:shippedDependencies` -> Wire is completely absent from `app/build/reports/shipped-dependencies.txt`.
  - It does NOT leak into `:app` release APK; it is scoped strictly to `:macrobenchmark` for Perfetto trace parsing during on-device benchmarking.
- **Root Fix & Justification**:
  - Upstream fixes for `GHSA-7xpr-hc2w-34m9` and `GHSA-9rm7-3qhh-h2mc` require Wire 6.3.0 and 6.4.5. However, `androidx.benchmark:benchmark-common` versions 1.3.1 through 1.3.4 strictly vendor Wire 4.9.7.
  - As permitted by policy in `scripts/scan-dependencies.py`, recorded a reviewed exception under `gradle/accepted-advisories.json` for `com.squareup.wire:*` under scope `build`, with expiry `2027-02-01`.
- **`libs.versions.toml` Version Check**:
  - Diff against `origin/main` confirms neither `composeBom` nor `navigationCompose` were bumped or modified.

### 2. Raw Command Outputs

#### `gh run view 39062683363 --log-failed`
```
Dependency vulnerability scan	Scan against OSV	2026-10-17T19:30:09.3436221Z wrote app/build/reports/dependency-scan.json
Dependency vulnerability scan	Scan against OSV	2026-10-17T19:30:09.3436482Z 
Dependency vulnerability scan	Scan against OSV	2026-10-17T19:30:09.3436614Z FAIL: 3 finding(s) over threshold.
Dependency vulnerability scan	Scan against OSV	2026-10-17T19:30:09.3437161Z Fix by upgrading, or record a reviewed exception with an expiry date in gradle/accepted-advisories.json.
Dependency vulnerability scan	Scan against OSV	2026-10-17T19:30:09.3511157Z ##[error]Process completed with exit code 1.
```

#### Local Scan Before Fix:
```
--- build-time only (not in the APK): 43 finding(s) ---
  FAIL [HIGH] com.squareup.wire:wire-runtime-jvm:4.9.7  GHSA-7xpr-hc2w-34m9
  FAIL [HIGH] com.squareup.wire:wire-runtime:4.9.7  GHSA-7xpr-hc2w-34m9
  FAIL [HIGH] com.squareup.wire:wire-runtime:4.9.7  GHSA-9rm7-3qhh-h2mc
FAIL: 3 finding(s) over threshold.
Fix by upgrading, or record a reviewed exception with an expiry date in gradle/accepted-advisories.json.
```

#### Local Scan After Fix:
```
scanning 625 resolved components (148 shipped, 477 build-time) against OSV

--- shipped code (in the APK): 0 finding(s) ---
  none

--- build-time only (not in the APK): 40 finding(s) ---

--- accepted, with expiry: 31 finding(s) ---
  until 2027-02-01: 3 finding(s)
      wire-runtime 4.9.7 via androidx.benchmark:benchmark-common:1.3.1 <- androidx.benchmark:benchmark-macro-junit4:1.3.1 <- :macrobenchmark. Wire is used by AndroidX Benchmark's Perfetto trace processor to parse device trace files during local benchmark runs. It is entirely scoped to the :macrobenchmark test module, is not packaged into the :app release APK, and never parses untrusted network inputs. Fixed upstream in Wire 6.3.0/6.4.5, but AndroidX Benchmark 1.3.x vendors 4.9.7.

wrote app\build\reports\dependency-scan.json

OK: no findings over threshold.
```

#### `git diff origin/main...HEAD gradle/libs.versions.toml`
```
@@ -23,6 +23,9 @@ ksp = "2.0.20-1.0.25"
 biometric = "1.2.0-alpha05"
 playServicesAuth = "21.2.0"
 profileinstaller = "1.3.1"
+androidxBenchmark = "1.3.1"
+androidxTestExtJunit = "1.2.1"
+uiautomator = "2.3.0"
 
 [libraries]
 androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
@@ -56,10 +59,14 @@ androidx-profileinstaller = { group = "androidx.profileinstaller", name = "profi
 junit = { group = "junit", name = "junit", version.ref = "junit" }
 robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
 androidx-test-core = { group = "androidx.test", name = "core", version.ref = "androidxTestCore" }
+androidx-benchmark-macro = { group = "androidx.benchmark", name = "benchmark-macro-junit4", version.ref = "androidxBenchmark" }
+androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxTestExtJunit" }
+androidx-test-uiautomator = { group = "androidx.test.uiautomator", name = "uiautomator", version.ref = "uiautomator" }
 
 [plugins]
 android-application = { id = "com.android.application", version.ref = "agp" }
 android-library = { id = "com.android.library", version.ref = "agp" }
+android-test = { id = "com.android.test", version.ref = "agp" }
 kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
 kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
 kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

### 3. CI Results (Run 39064962565)
- **URL**: `https://github.com/soul991/Messages/actions/runs/39064962565`
- **Dependency vulnerability scan**: PASSED (35s)
- **Tests, lint, and release build**: PASSED (11m 1s)
- **Signed release artifact**: Skipped (expected behavior on non-tag PR push)

---

## Part 2: Lock the Seed Receiver & Isolate Benchmark Data

### 1. Implementation Summary
- **DUMP Permission Protection**: Added `android:permission="android.permission.DUMP"` to `SeedDataReceiver` in both `app/src/debug/AndroidManifest.xml` and `app/src/benchmark/AndroidManifest.xml`. Third-party applications cannot send broadcasts without holding `DUMP`, whereas `adb shell` holds `DUMP`.
- **Application ID Suffix**: Added `applicationIdSuffix = ".benchmark"` to the `benchmark` build type in `app/build.gradle.kts`. This ensures the benchmark APK (`com.messages.app.benchmark`) installs alongside the release APK (`com.messages.app`) without touching production data.
- **Component Addressing**: Updated `TARGET_PACKAGE = "com.messages.app.benchmark"` across macrobenchmarks. The receiver uses its fully qualified class name `com.messages.app.debug.SeedDataReceiver` so that `-n com.messages.app.benchmark/com.messages.app.debug.SeedDataReceiver` resolves without class-name mismatch.
- **Onboarding Skip**: Added `context.getSharedPreferences("onboarding", Context.MODE_PRIVATE).edit().putBoolean("done", true).apply()` in `SeedDataReceiver` so seeded builds immediately launch into `HomeScreen`.
- **Fictional Data & Thread 1 Count**:
  - Excluded thread 1 from the 500-conversation outer loop (`if (i > 1)`), ensuring thread 1 contains exactly 100 messages from the dedicated message loop.
  - Added varied fictional data, OTPs, bank/bill alerts, emojis, and links (all using 555-range numbers).
- **Synchronous Execution**: Wrapped `SeedDataReceiver` execution in `runBlocking(Dispatchers.IO)` with `setResultCode(Activity.RESULT_OK)` so that `am broadcast` synchronously waits and captures `result=-1`.

### 2. Verification on Device & Release Verification

#### Broadcast Seeding Command & Output:
```
$ adb shell am broadcast -n com.messages.app.benchmark/com.messages.app.debug.SeedDataReceiver -a com.messages.app.debug.SEED --ez wipe true
Broadcasting: Intent { act=com.messages.app.debug.SEED flg=0x400000 cmp=com.messages.app.benchmark/com.messages.app.debug.SeedDataReceiver (has extras) }
Broadcast completed: result=-1, data="Seeded 500 conversations across 5 categories and 100 messages in thread 1"
```

#### Screenshot Verification:
- Launched `com.messages.app.benchmark/com.messages.app.MainActivity` after seeding.
- Verified app opens directly to Home displaying seeded conversations (skipping onboarding):
  `docs/reviews/screens/home_seeded.png`

#### Release APK Leak Check (`./gradlew :app:assembleRelease`):
```
$ python -c "import zipfile, glob, re
apk_files = glob.glob('app/build/outputs/apk/release/*.apk')
print('Found APKs:', apk_files)
pattern = re.compile(b'SeedDataReceiver|SyntheticStress|Rostova', re.IGNORECASE)
total_matches = 0
for apk in apk_files:
    with zipfile.ZipFile(apk, 'r') as z:
        for name in z.namelist():
            if name.startswith('classes') and name.endswith('.dex'):
                data = z.read(name)
                matches = pattern.findall(data)
                total_matches += len(matches)
                print(f'{name}: {len(matches)} matches')
print('TOTAL MATCHES:', total_matches)"

Found APKs: ['app/build/outputs/apk/release\\Messages.apk']
classes.dex: 0 matches
TOTAL MATCHES: 0
```

#### Merged Release Manifest Inspection:
```
$ python -c "import glob
manifests = glob.glob('app/build/intermediates/merged*manifest*/**/release/**/AndroidManifest.xml', recursive=True)
for m in manifests:
    with open(m, 'r', encoding='utf-8', errors='ignore') as f:
        content = f.read()
        print(m, 'contains SeedDataReceiver:', 'SeedDataReceiver' in content)"

app/build/intermediates\merged_manifest\release\processReleaseMainManifest\AndroidManifest.xml contains SeedDataReceiver: False
app/build/intermediates\merged_manifests\release\processReleaseManifest\AndroidManifest.xml contains SeedDataReceiver: False
```

---

## Part 3: Cascade Animations Replay While Scrolling

### 1. Implementation Summary
- **Search Pane Cascade Entrance (`HomeScreen.kt`)**:
  - Hoisted `val alreadyAnimatedKeys = remember(queryKey) { mutableSetOf<Any>() }` and `val searchListState = rememberLazyListState()` at `SearchPane` level, with `val isScrolling = searchListState.isScrollInProgress`.
  - In `searchCascadeEntrance(key, index, alreadyAnimatedKeys, isScrolling)`:
    - Checks `Motion.animationsEnabled()`.
    - An item animates **ONLY** if its key is not in `alreadyAnimatedKeys` AND `index < 8` AND `!isScrolling`.
    - Key is added to `alreadyAnimatedKeys` on first composition.
    - Items outside these conditions render immediately at full alpha with no delay or offset.
- **Settings Cascade Entrance (`SettingsComponents.kt`, `SettingsScreen.kt`, `NotificationSettingsScreen.kt`)**:
  - Introduced `LocalSettingsPageAnimated` CompositionLocal.
  - In `SettingsScreen.kt`: hoisted `val pageEntrancePlayed = rememberSaveable(currentPage) { mutableStateOf(false) }` per page entry, setting it to `true` after the initial cascade completes via `LaunchedEffect(currentPage)`.
  - In `cascadeEntrance(index)`:
    - Checks `Motion.animationsEnabled()`.
    - Only animates cards with `index < 6` (0..5).
    - If `LocalSettingsPageAnimated.current` is true, returns immediately without replaying animation.
    - Cards never replay on scroll re-entry.

### 2. Device Verification
- **Screen Recording**: Recorded on connected emulator (`emulator-5554`) while scrolling a long search results list ("Rivera") down and back up. Rows that scroll back into view render immediately at full alpha without fading or sliding in again.
- **Artifact**: `docs/reviews/screens/search_scroll.mp4` (3,436,384 bytes).


