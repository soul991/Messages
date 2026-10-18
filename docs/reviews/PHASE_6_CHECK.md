# Phase 6 Check

## 1. On-Demand Fictional Test Data Seed
### Implementation:
- Source file: `app/src/debug/kotlin/com/messages/app/debug/SeedDataReceiver.kt`
- Intent Action: `com.messages.app.debug.SEED`
- Extras: `wipe=true` (clears existing Room database tables via `repo.db.clearAllTables()`)
- Seeding distribution:
  - 500 conversations across 5 categories (100 INBOX, 100 TRANSACTIONS, 100 PROMOTIONS, 100 REVIEW, 100 SPAM).
  - Fictional 555-range phone numbers (`+15550000001` .. `+15550000500`).
  - 100 messages in thread 1 (`+15550000001`) with alternating outgoing/incoming state and realistic timestamps.
- Manifest Registration:
  - `app/src/debug/AndroidManifest.xml`: Declared and exported.
  - `app/src/benchmark/AndroidManifest.xml`: Declared and exported.
  - Release Build Check: Completely absent from `app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml`.
  - Dex String Check: 0 occurrences of receiver in release build.

## 2. Benchmark Target & Baseline Profile Module
### Implementation:
- Added `benchmark` build type in `app/build.gradle.kts` matching `release` with `signingConfig = debug` and `proguardFiles("benchmark-rules.pro")`.
- Added `:macrobenchmark` module configured with `id("com.android.test")` and `androidx.benchmark:benchmark-macro-junit4:1.3.1`.
- Macrobenchmark test classes:
  - `StartupBenchmark.kt`: Measures `startupCold` and `startupWarm` with `StartupTimingMetric`.
  - `ScrollAndTransitionBenchmark.kt`: Measures with `FrameTimingMetric`:
    1. `homeScroll`: Fling scroll through 500 seeded conversations.
    2. `homeToChatTransition`: Click conversation row, transition into chat, back press.
    3. `chatScroll`: Rapid flings through 100 messages in thread 1.
    4. `settingsTransitionsAndScroll`: Dock navigation to settings, scroll grouped cards, sub-page navigation, and back.
  - `BaselineProfileGenerator.kt`: Captures critical user journeys for baseline profile generation using `BaselineProfileRule`.

## 3. Compilation & Build Verification
### Commands & Results:
1. Benchmark Application APK:
   `./gradlew :app:assembleBenchmark` -> `BUILD SUCCESSFUL` (Artifact: `app/build/outputs/apk/benchmark/Messages-benchmark.apk`).
2. Macrobenchmark Test APK:
   `./gradlew :macrobenchmark:assembleBenchmark` -> `BUILD SUCCESSFUL` (Artifact: `macrobenchmark/build/outputs/apk/benchmark/macrobenchmark-benchmark.apk`).
3. Project Unit Tests:
   `./gradlew test` -> `BUILD SUCCESSFUL in 4m 55s` (184 actionable tasks: 147 executed, 33 from cache, 4 up-to-date; 0 failures).
