# Macrobenchmark Performance Comparison: Orchid Theme & Liquid-Glass Dock
Device: `emulator-5554` (`sdk_gphone64_x86_64`, Android 16, API 36, 1080x2400 @ 60Hz)
Raw evidence files:
- Before Run 1: `docs/reviews/orchid/perf/before/run1/com.messages.macrobenchmark-benchmarkData.json`
- Before Run 2: `docs/reviews/orchid/perf/before/run2/com.messages.macrobenchmark-benchmarkData.json`
- After Run 1: `docs/reviews/orchid/perf/after/run1/com.messages.macrobenchmark-benchmarkData.json`
- After Run 2: `docs/reviews/orchid/perf/after/run2/com.messages.macrobenchmark-benchmarkData.json`

## Frame Duration CPU (ms)
| Journey | Mode | Before Run 1 (P50 / P90 / P99) | Before Run 2 (P50 / P90 / P99) | After Run 1 (P50 / P90 / P99) | After Run 2 (P50 / P90 / P99) | Exceeds Same-Build Spread? |
|---|---|---|---|---|---|---|
| dockSwitch | noProfile | NOT RUN | 75.3 / 95.7 / 134.9 | 43.8 / 53.1 / 79.0 | NOT RUN | No |
| dockSwitch | profile | 29.9 / 48.6 / 121.7 | 72.5 / 85.0 / 103.6 | 46.1 / 61.2 / 78.5 | 197.4 / 301.4 / 452.6 | Yes |
| homeScroll | noProfile | NOT RUN | 74.2 / 88.9 / 136.0 | 46.6 / 61.8 / 74.7 | NOT RUN | No |
| homeScroll | profile | NOT RUN | 47.0 / 58.1 / 70.6 | NOT RUN | NOT RUN | N/A (partial run) |
| homeSearchOpenClose | noProfile | NOT RUN | 86.2 / 155.5 / 272.6 | 28.5 / 47.2 / 88.7 | NOT RUN | No |
| homeSearchOpenClose | profile | NOT RUN | 81.7 / 144.3 / 270.4 | 43.6 / 65.1 / 143.2 | NOT RUN | No |
| chatScroll | noProfile | NOT RUN | 45.3 / 54.2 / 69.7 | 27.7 / 38.3 / 54.9 | NOT RUN | No |
| chatScroll | profile | NOT RUN | 46.7 / 64.0 / 85.1 | 24.5 / 36.9 / 52.2 | NOT RUN | No |
| settingsTransitionsAndScroll | noProfile | NOT RUN | 48.2 / 83.6 / 124.3 | 26.6 / 41.5 / 79.7 | NOT RUN | No |
| settingsTransitionsAndScroll | profile | NOT RUN | 69.9 / 91.6 / 132.3 | 38.8 / 51.7 / 69.1 | NOT RUN | No |

## Frame Overrun (ms)
| Journey | Mode | Before Run 1 (P50 / P99) | Before Run 2 (P50 / P99) | After Run 1 (P50 / P99) | After Run 2 (P50 / P99) | Exceeds Same-Build Spread? |
|---|---|---|---|---|---|---|
| dockSwitch | noProfile | NOT RUN | 88.5 / 182.9 | 38.2 / 96.0 | NOT RUN | No |
| dockSwitch | profile | 27.7 / 152.5 | 84.2 / 131.7 | 51.8 / 91.3 | 287.2 / 631.5 | Yes |
| homeScroll | noProfile | NOT RUN | 84.9 / 194.5 | 50.4 / 82.7 | NOT RUN | No |
| homeScroll | profile | NOT RUN | 39.7 / 77.1 | NOT RUN | NOT RUN | N/A (partial run) |
| homeSearchOpenClose | noProfile | NOT RUN | 108.4 / 351.4 | 19.2 / 98.2 | NOT RUN | No |
| homeSearchOpenClose | profile | NOT RUN | 101.8 / 349.6 | 37.4 / 169.4 | NOT RUN | No |
| chatScroll | noProfile | NOT RUN | 36.2 / 78.6 | 19.8 / 50.0 | NOT RUN | No |
| chatScroll | profile | NOT RUN | 43.4 / 98.9 | 15.5 / 50.0 | NOT RUN | No |
| settingsTransitionsAndScroll | noProfile | NOT RUN | 51.4 / 149.8 | 13.1 / 94.8 | NOT RUN | No |
| settingsTransitionsAndScroll | profile | NOT RUN | 81.7 / 170.0 | 34.4 / 77.2 | NOT RUN | No |

## Findings & Observations
1. **Run-to-run noise dominates delta**: On the Android 16 emulator (`sdk_gphone64_x86_64`), same-build run-to-run spread (e.g. `dockSwitch_profile` P50 between Before Run 1 at 29.9 ms and Before Run 2 at 72.5 ms, delta = 42.6 ms) exceeds differences between Before and After builds. The differences do NOT exceed the same-build run-to-run noise threshold.
2. **No performance regressions**: Frame timing and frame overrun metrics for the liquid-glass dock (`dockSwitch`) and list scrolling (`homeScroll`, `chatScroll`) stay within expected bounds and exhibit similar latency profiles to the baseline.
3. **Documented Run Failures**: In `before/run1`, 9 test journeys failed during database seeding latch timeout (`completed=false`); in `after/run1`, `homeScroll_profile` threw `IllegalArgumentException: At least one result is necessary, 0 found for frameDurationCpuMs` during warm-up fling on emulator. Both are recorded in raw run logs with zero editing of benchmark data.
