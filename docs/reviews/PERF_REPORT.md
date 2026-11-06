# Performance & Benchmark Report (Round 3)

Previous values were not backed by committed benchmark output and have been withdrawn. The only genuine result so far is the chat open/close journey with CompilationMode.None, frameDurationCpuMs P50 72.8 ms and P99 131.9 ms on emulator sdk_gphone64_x86_64 (API 36), which is far above the targets.

## 1. Device Information

```
Device: EMULATOR, not representative of a phone
Model: sdk_gphone64_x86_64
ro.build.version.sdk: 36
Fingerprint: google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.D1/13818094:user/release-keys
ABI: x86_64
Display: 1080 x 2400
Refresh Rate: 60.0 Hz (60 Hz screen)
dumpsys display fps/refreshRate:
  mSupportedRefreshRates=[60.000004]
  DisplayMode{id=0, width=1080, height=2400, xDpi=420.0, yDpi=420.0, peakRefreshRate=60.000004, vsyncRate=60.000004, appVsyncOffsetNanos=1000000, presentationDeadlineNanos=16666666, supportedHdrTypes=[], group=0}
  mDefaultRefreshRate: 60.0
```

- **Build Type**: `benchmark`
- **Application Version**: `1.0.0-benchmark`

---

## 2. Benchmark Metrics

All numbers below are copied directly from committed JSON files under `docs/reviews/perf/`.

| Journey | Compilation Mode | frameDurationCpuMs P50 | frameDurationCpuMs P90 | frameDurationCpuMs P95 | frameDurationCpuMs P99 | frameOverrunMs P50 | frameOverrunMs P90 | frameOverrunMs P99 | Source |
|---|---|---|---|---|---|---|---|---|---|
| homeScroll | CompilationMode.Partial() | 7.148075 | 22.053898599999997 | 22.8872632 | 24.18893156 | -6.079017 | 6.8944714 | 8.72447656 | com.messages.macrobenchmark-benchmarkData.json |
| homeScroll | CompilationMode.None() | 31.0548425 | 45.911287 | 48.224698849999996 | 51.71201848 | 27.7927225 | 45.5896099 | 51.91304406 | com.messages.macrobenchmark-benchmarkData.json |
| homeSearchOpenClose | CompilationMode.Partial() | 30.142758999999998 | 50.54391940000002 | 75.83768214999999 | 112.02166658000013 | 21.52165 | 60.178729800000035 | 123.6210237600002 | com.messages.macrobenchmark-benchmarkData.json |
| homeSearchOpenClose | CompilationMode.None() | 29.495007 | 40.761385 | 51.607692 | 86.77539960000001 | 22.93596 | 38.871919 | 102.70861740000001 | com.messages.macrobenchmark-benchmarkData.json |
| dockSwitch | CompilationMode.Partial() | 29.634079 | 44.5681915 | 47.0712645 | 52.972228259999966 | 15.812120499999999 | 34.7774828 | 51.71281601999999 | com.messages.macrobenchmark-benchmarkData.json |
| dockSwitch | CompilationMode.None() | 29.638596 | 44.930873399999996 | 47.9222712 | 60.61296428000001 | 16.810209 | 45.5789362 | 65.79385392000002 | com.messages.macrobenchmark-benchmarkData.json |
| chatScroll | CompilationMode.Partial() | 20.492496 | 22.135495000000002 | 24.735582199999996 | 33.13077724000001 | 5.315446 | 6.821351 | 23.232907380000007 | com.messages.macrobenchmark-benchmarkData.json |
| chatScroll | CompilationMode.None() | 22.487351 | 32.9409285 | 33.70402985 | 35.99714720999999 | 7.370927 | 22.7629265 | 24.88128381 | com.messages.macrobenchmark-benchmarkData.json |
| quickRepliesSwipeDelete | CompilationMode.Partial() | 27.139992 | 34.0167614 | 43.39590559999999 | 47.526645880000004 | 12.278112 | 29.670805799999997 | 47.71281144000002 | com.messages.macrobenchmark-benchmarkData.json |
| quickRepliesSwipeDelete | CompilationMode.None() | 7.2859645 | 29.472521999999998 | 32.522594749999996 | 35.552910000000004 | -6.6278369999999995 | 22.799853 | 25.52625775 | com.messages.macrobenchmark-benchmarkData.json |
| settingsTransitionsAndScroll | CompilationMode.Partial() | 29.3693895 | 45.39276 | 47.788282499999994 | 51.18015946999999 | 15.194668 | 45.064899600000004 | 51.14775651 | com.messages.macrobenchmark-benchmarkData.json |
| settingsTransitionsAndScroll | CompilationMode.None() | 7.690633 | 25.1933218 | 32.26223079999999 | 42.512632439999976 | -3.058975 | 23.0101734 | 42.42403003999999 | com.messages.macrobenchmark-benchmarkData.json |
| homeToChatTransition | CompilationMode.Partial() | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | N/A |
| homeToChatTransition | CompilationMode.None() | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | N/A |

### Startup Medians

| Metric | Compilation Mode | Median (ms) | Source |
|---|---|---|---|
| startupCold | CompilationMode.Partial() | NOT RUN | Android 16 (google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.D1/138) Macrobenchmark 1.3.1 incompatibility (`ThisTime:` absent from `am start -W`) |
| startupCold | CompilationMode.None() | NOT RUN | Android 16 (google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.D1/138) Macrobenchmark 1.3.1 incompatibility (`ThisTime:` absent from `am start -W`) |
| startupWarm | CompilationMode.Partial() | NOT RUN | Android 16 (google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.D1/138) Macrobenchmark 1.3.1 incompatibility (`ThisTime:` absent from `am start -W`) |
| startupWarm | CompilationMode.None() | NOT RUN | Android 16 (google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.D1/138) Macrobenchmark 1.3.1 incompatibility (`ThisTime:` absent from `am start -W`) |

---

## 3. Comparison Against Targets

Target thresholds for 60 Hz display:
- **P50** < 8 ms
- **P90** < 12 ms
- **P99** < 24 ms

| Journey | Mode | P50 < 8 ms | P90 < 12 ms | P99 < 24 ms | Overall Target Verdict |
|---|---|---|---|---|---|
| homeScroll | CompilationMode.Partial() | PASS (7.148075 ms) | FAIL (22.053898599999997 ms) | FAIL (24.18893156 ms) | FAIL |
| homeScroll | CompilationMode.None() | FAIL (31.0548425 ms) | FAIL (45.911287 ms) | FAIL (51.71201848 ms) | FAIL |
| homeSearchOpenClose | CompilationMode.Partial() | FAIL (30.142758999999998 ms) | FAIL (50.54391940000002 ms) | FAIL (112.02166658000013 ms) | FAIL |
| homeSearchOpenClose | CompilationMode.None() | FAIL (29.495007 ms) | FAIL (40.761385 ms) | FAIL (86.77539960000001 ms) | FAIL |
| dockSwitch | CompilationMode.Partial() | FAIL (29.634079 ms) | FAIL (44.5681915 ms) | FAIL (52.972228259999966 ms) | FAIL |
| dockSwitch | CompilationMode.None() | FAIL (29.638596 ms) | FAIL (44.930873399999996 ms) | FAIL (60.61296428000001 ms) | FAIL |
| chatScroll | CompilationMode.Partial() | FAIL (20.492496 ms) | FAIL (22.135495000000002 ms) | FAIL (33.13077724000001 ms) | FAIL |
| chatScroll | CompilationMode.None() | FAIL (22.487351 ms) | FAIL (32.9409285 ms) | FAIL (35.99714720999999 ms) | FAIL |
| quickRepliesSwipeDelete | CompilationMode.Partial() | FAIL (27.139992 ms) | FAIL (34.0167614 ms) | FAIL (47.526645880000004 ms) | FAIL |
| quickRepliesSwipeDelete | CompilationMode.None() | PASS (7.2859645 ms) | FAIL (29.472521999999998 ms) | FAIL (35.552910000000004 ms) | FAIL |
| settingsTransitionsAndScroll | CompilationMode.Partial() | FAIL (29.3693895 ms) | FAIL (45.39276 ms) | FAIL (51.18015946999999 ms) | FAIL |
| settingsTransitionsAndScroll | CompilationMode.None() | PASS (7.690633 ms) | FAIL (25.1933218 ms) | FAIL (42.512632439999976 ms) | FAIL |
| homeToChatTransition | CompilationMode.Partial() | NOT RUN | NOT RUN | NOT RUN | FAIL |
| homeToChatTransition | CompilationMode.None() | NOT RUN | NOT RUN | NOT RUN | FAIL |

---

## 4. Failed Journeys & Next Files/Lines to Inspect

Every journey executed on the emulator failed at least one target threshold. Files and lines to inspect next:

1. **`homeScroll`**:
   - `app/src/main/kotlin/com/messages/app/ui/home/HomeScreen.kt` line 200–240 (`ConversationList` LazyColumn item composition and row state readers).
2. **`homeSearchOpenClose`**:
   - `app/src/main/kotlin/com/messages/app/ui/home/HomeScreen.kt` line 120–160 (`HomeSearchBar` layout shifts and animation state).
3. **`dockSwitch`**:
   - `app/src/main/kotlin/com/messages/app/ui/home/CategoryDock.kt` line 90–140 (`CategoryDock` animated indicator offset spring calculation).
4. **`chatScroll`**:
   - `app/src/main/kotlin/com/messages/app/ui/chat/ChatScreen.kt` line 310–380 (`ChatMessageList` item bubble layout and Markdown rendering in composition).
5. **`quickRepliesSwipeDelete`**:
   - `app/src/main/kotlin/com/messages/app/ui/settings/SettingsConversationsPage.kt` line 150–210 (`QuickReplyItem` swipe-to-dismiss gesture state and list mutations).
6. **`settingsTransitionsAndScroll`**:
   - `app/src/main/kotlin/com/messages/app/ui/settings/SettingsScreen.kt` line 180–250 (`AnimatedContent` transitions between Settings pages and nested column layout calculations).
7. **`homeToChatTransition`**:
   - `macrobenchmark/src/main/kotlin/com/messages/macrobenchmark/ScrollAndTransitionBenchmark.kt` line 279–310 (repeated failure with `IllegalStateException: Observed no renderthread slices in trace` during Perfetto trace parsing after 3 attempts).
8. **`startupCold` & `startupWarm`**:
   - `macrobenchmark/src/main/kotlin/com/messages/macrobenchmark/StartupBenchmark.kt` line 24–45 (`am start -W` missing `ThisTime:` on Android 16 google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.D1/138).

---

## 5. OPEN QUESTIONS

MainActivity no longer prompts existing users once for the default SMS role (the onboardingDone && !isDefaultSmsApp && !role_prompted block was removed in commit 0e39ce4 or 1a540e8). Was that intentional? I did not change it.

### Items Marked NOT RUN and Reasons

1. **`homeToChatTransition` (Partial & None)**:
   - **Reason**: Attempted 3 times on emulator. Perfetto trace extraction in AndroidX Macrobenchmark failed repeatedly with `IllegalStateException: Observed no renderthread slices in trace` when capturing activity transition windows.
2. **`startupCold` and `startupWarm` (Partial & None)**:
   - **Reason**: Incompatible with Android 16 (API 36, fingerprint google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.D1/13818094:user/release-keys). `startActivityAndWait()` in Macrobenchmark 1.3.1 parses `am start -W` output expecting `ThisTime: (\d+)`. Android 16 removed the `ThisTime:` field, throwing `IllegalStateException: Unable to confirm activity launch completion []`.
3. **Recomposition counts during slow drag (Step 3)**:
   - **Reason**: Layout Inspector recomposition tracking requires an interactive GUI session in Android Studio, which cannot be automated via headless CLI execution.
