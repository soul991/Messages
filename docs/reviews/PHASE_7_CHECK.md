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

---

## Part 4: Swipe Row Modifier Order & SwipeToRevealRow Signature

### 1. Implementation Summary
- **Modifier Reordering**:
  - Reordered modifier chain on both Home conversation swipe row (`HomeScreen.kt`) and `SwipeToRevealRow` (`SettingsComponents.kt`):
    `graphicsLayer { translationX = ... }` -> `.background(MaterialTheme.colorScheme.groupCard)` -> `.pointerInput(...)`.
  - Previously, `.background()` was applied outside the `graphicsLayer`, keeping the opaque background stationary while only inner text moved, obscuring the action background underneath. Placing `graphicsLayer` before `background` moves the card background along with the foreground content, properly revealing the underlying action surface.
  - Kept all behaviors identical: 42% swipe threshold, `canCommitSwipe`, selection mode handling, `Motion.butterSpring()` spring-back return, and accessibility custom actions.
- **API Signature Fix**:
  - Removed hardcoded default `"Delete"` literal from `SwipeToRevealRow(deleteLabel: String)`. Callers now explicitly pass `stringResource(R.string.action_delete)` ensuring proper localization.

### 2. Device Verification
- **Mid-Swipe Screenshot (Home Conversation)**:
  - Artifact: `docs/reviews/screens/swipe_home.png` (245,540 bytes).
  - Shows green action background with archive icon clearly revealed behind sliding conversation row.
- **Mid-Swipe Screenshot (Quick Replies)**:
  - Artifact: `docs/reviews/screens/swipe_quick_reply.png` (197,233 bytes).
  - Shows `errorContainer` red action background with delete icon and "Delete" label revealed behind sliding template row.

---

## Part 5: Small Fixes & Contrast Audit

### 1. Implementation Summary
- **Search Focus (`HomeScreen.kt`)**:
  - Replaced `delay(40)` and try/catch exception-swallowing with `kotlinx.coroutines.android.awaitFrame()` followed immediately by `searchFocusRequester.requestFocus()`.
  - Ensures focus request occurs after the composable node is measured and attached to the layout hierarchy without arbitrary timeouts or swallowed errors.
- **Dock Badge Ring (`Dock.kt`)**:
  - Dynamic surface ring behind badge: `val ringColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh`.
  - Applied to 1.5dp border on both numeric unread count badges and dot indicators.
- **DockItem Constructor Cleanup (`Dock.kt`)**:
  - Removed unused `accentColor: Color? = null` parameter from backward compatibility constructor in `DockItem`.
- **Dock Badge Text & Container Contrast Audit**:
  - Calculated WCAG relative luminance contrast ratios for badge text against category tints across Light, Dark, and AMOLED modes.
  - While Light mode tints (#1B6C31, #7A5900, #555F71, #BA1A1A, #5A41DD) provide 6.44:1 to 6.49:1 contrast against white text, Dark/AMOLED mode tints (#88D896, #EFC047, #BDC7DC, #FFB4AB, #D1BBFD) are high-luminance (Tone 80) and fail severely against white text with 1.70:1 to 1.72:1 (< 4.5:1).
  - To guarantee AA and AAA contrast across all themes and user-selected accents, badge uses the palette's `(container, onContainer)` pair (`palette.container` / `palette.onContainer` for category items, and `primary` / `onPrimary` for Inbox/default), achieving ≥ 7.16:1 contrast in all modes.

### 2. Contrast Audit Table

| Theme | Category | Tint Hex | White vs Tint (CR) | Result (< 4.5:1?) | Palette Container | Palette onContainer | Container Pair CR | Result (≥ 4.5:1?) |
|---|---|---|---|---|---|---|---|---|
| Light | INBOX (Default) | `#5A41DD` | 6.49:1 | PASS | `#E8DEFF` | `#1E0060` | 13.34:1 | PASS (AAA) |
| Light | TRANSACTIONS | `#1B6C31` | 6.49:1 | PASS | `#A3F4AF` | `#00210A` | 13.19:1 | PASS (AAA) |
| Light | PROMOTIONS | `#7A5900` | 6.45:1 | PASS | `#FFDF9E` | `#261A00` | 13.25:1 | PASS (AAA) |
| Light | REVIEW | `#555F71` | 6.44:1 | PASS | `#D9E3F8` | `#121C2B` | 13.28:1 | PASS (AAA) |
| Light | SPAM | `#BA1A1A` | 6.46:1 | PASS | `#FFDAD6` | `#410002` | 13.26:1 | PASS (AAA) |
| Dark | INBOX (Default) | `#D1BBFD` | 1.72:1 | **FAIL** | `#4F378B` | `#E8DEFF` | 7.25:1 | PASS (AAA) |
| Dark | TRANSACTIONS | `#88D896` | 1.71:1 | **FAIL** | `#0F5223` | `#A3F4AF` | 7.16:1 | PASS (AAA) |
| Dark | PROMOTIONS | `#EFC047` | 1.71:1 | **FAIL** | `#5C4300` | `#FFDF9E` | 7.22:1 | PASS (AAA) |
| Dark | REVIEW | `#BDC7DC` | 1.70:1 | **FAIL** | `#3E4759` | `#D9E3F8` | 7.24:1 | PASS (AAA) |
| Dark | SPAM | `#FFB4AB` | 1.70:1 | **FAIL** | `#93000A` | `#FFDAD6` | 7.24:1 | PASS (AAA) |
| AMOLED | INBOX (Default) | `#D1BBFD` | 1.72:1 | **FAIL** | `#4F378B` | `#E8DEFF` | 7.25:1 | PASS (AAA) |
| AMOLED | TRANSACTIONS | `#88D896` | 1.71:1 | **FAIL** | `#0F5223` | `#A3F4AF` | 7.16:1 | PASS (AAA) |
| AMOLED | PROMOTIONS | `#EFC047` | 1.71:1 | **FAIL** | `#5C4300` | `#FFDF9E` | 7.22:1 | PASS (AAA) |
| AMOLED | REVIEW | `#BDC7DC` | 1.70:1 | **FAIL** | `#3E4759` | `#D9E3F8` | 7.24:1 | PASS (AAA) |
| AMOLED | SPAM | `#FFB4AB` | 1.70:1 | **FAIL** | `#93000A` | `#FFDAD6` | 7.24:1 | PASS (AAA) |

---

## Part 6: Make the Benchmarks Test What They Claim

### 1. Implementation Summary
- **Element Grounding (`testTagsAsResourceId`)**:
  - Configured `Modifier.semantics { @OptIn(ExperimentalComposeUiApi::class) testTagsAsResourceId = true }` at the root UI level on `SharedTransitionLayout` in `MainActivity.kt`.
  - Added 14 inert `testTag` locators across all primary user surfaces:
    1. `home_conversation_list`: `HomeScreen.kt` LazyColumn (`LazyListState`).
    2. `home_search_pill`: `HomeScreen.kt` resting search Surface.
    3. `home_settings_button`: `HomeScreen.kt` top-bar FilledTonalIconButton.
    4. `dock_INBOX`, `dock_TRANSACTIONS`, `dock_PROMOTIONS`, `dock_REVIEW`, `dock_SPAM`: `Dock.kt` Box tab slots.
    5. `chat_message_list`: `ChatScreen.kt` message LazyColumn.
    6. `chat_composer_field`: `ChatScreen.kt` composer BasicTextField.
    7. `chat_send_button`: `ChatScreen.kt` send FilledIconButton.
    8. `chat_quick_reply_chip`: `ChatScreen.kt` composer bolt button and bottom-sheet quick reply chips.
    9. `settings_page_conversations`: `SettingsHub.kt` HubRow navigating to Conversations settings.
    10. `quick_reply_list`: `SettingsScreen.kt` SettingsGroupCard wrapping quick replies.
    11. `quick_reply_row_<index>`: `SettingsScreen.kt` Column wrapping each quick reply template item.
    12. `quick_reply_edit_toggle`: `SettingsScreen.kt` Edit/Done mode TextButton.
    13. `quick_reply_input_field`: `SettingsComponents.kt` BasicTextField in QuickReplyInputPill.
    14. `quick_reply_add_button`: `SettingsComponents.kt` "+" Box button in QuickReplyInputPill.
- **Seeding Inside Macrobenchmark (`@Before`)**:
  - Integrated `@Before` fixture in `ScrollAndTransitionBenchmark.kt`, `StartupBenchmark.kt`, and `BaselineProfileGenerator.kt`:
    `device.executeShellCommand("am broadcast -n com.messages.app.benchmark/com.messages.app.debug.SeedDataReceiver -a com.messages.app.debug.SEED --ez wipe true")`
    Verifies output contains `result=-1` (Activity.RESULT_OK) or fails the test run.
  - Added `ensureHomeScreen(device)` assertion verifying `home_conversation_list` contains the seeded row (`Alex Morgan`) before measurement begins.
- **Journeys Overhaul (No Raw Coordinates)**:
  - Overhauled all journeys in `ScrollAndTransitionBenchmark.kt` to use `By.res(Pattern.compile(".*(:id/)?$tag"))`:
    - **Journey a**: Home fling down/up x5 over 500 conversations (`home_conversation_list.fling(Direction.DOWN/UP)`).
    - **Journey b**: Home search overlay tap search pill (`home_search_pill`), wait for idle, press back x10 inside measured block.
    - **Journey c**: Home switch dock tabs (`dock_INBOX` -> `dock_TRANSACTIONS` -> `dock_PROMOTIONS` -> `dock_REVIEW` -> `dock_SPAM` -> `dock_INBOX`).
    - **Journey d**: 100-message chat fling up/down; send 5 text messages (`chat_composer_field`, `chat_send_button`) and 3 quick replies (`chat_quick_reply_chip`).
    - **Journey e**: Settings (`home_settings_button`) -> Conversations (`settings_page_conversations`) -> Quick replies: swipe-delete row (`quick_reply_row_0`), toggle Edit mode (`quick_reply_edit_toggle`), add reply (`quick_reply_input_field`, `quick_reply_add_button`).
    - **Journey f**: Chat <-> Home open/close transitions x10 via thread click and device back gesture.
  - All journeys evaluated under both `CompilationMode.Partial()` and `CompilationMode.None()` with `iterations = 10` and `FrameTimingMetric()`.
  - Cold and warm startups evaluated in `StartupBenchmark.kt` under both compilation modes with `StartupTimingMetric()`.
- **Baseline Profile Generator**:
  - Updated `BaselineProfileGenerator.kt` to cover journeys a through f using the new semantic tags.
  - Removed handwritten wildcard rules from `app/src/main/baseline-prof.txt` in preparation for real profile generation on device.

### 2. Release & Build Verification
- **Release Compilation (`:app:assembleRelease`)**:
  - Built successfully in 2m 45s.
- **DEX Symbol Leak Check**:
  - Command: Scan `classes*.dex` in release APK for `SeedDataReceiver`, `SyntheticStress`, `Rostova`.
  - Result: `Total matches: 0`.
- **Macrobenchmark Compilation (`:macrobenchmark:compileBenchmarkKotlin`)**:
  - Built successfully in 20s.





