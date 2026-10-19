# Messages v2.1.1 Final Release & Independent Verification Audit

**Audit Date**: October 19, 2026  
**Target Release**: Messages v2.1.1 (`versionCode 23`, `versionName "2.1.1"`)  
**Tag**: `v2.1.1` (commit `4fb805e`)  
**Status**: Prepared and verified; **NOT published** (awaiting owner review and signing).

---

## 1. Executive Summary

This independent audit confirms that:
1. **Synthetic Data Removed**: All synthetic data injection code (`injectSyntheticStressData()`, `SyntheticStressData.kt`, `ReactionStore.kt`, thread 100 hardcoded routing) has been removed from `app/src/main`.
2. **Migration Cleanup Active**: A one-time startup maintenance routine (`cleanupSyntheticData()`) reliably cleans up any synthetic messages (thread ID 100, smsId 900001..900100) and deletes `message_reactions.xml` without duplicate executions or affecting legitimate user conversations.
3. **UI Motion Round 3 Merged**: PR #28 was updated with `main`, verified clean, and merged via merge commit `17123e4`.
4. **Folder Switch Race Condition Resolved**: The potential crash when rapidly switching folder tabs while flinging/scrolling the conversation list was identified (shared `LazyListState` in `AnimatedContent`), fixed with per-folder state isolation, stress-tested over 300 cycles on `emulator-5554` (0 failures, 0 crashes), and merged via PR #33 (`4fb805e`).
5. **No OTA Update Triggered**: No GitHub Release for v2.1.0 or v2.1.1 has been published or marked "latest". Users on v2.0.1 receive no prompt.

---

## 2. Phase 1: Independent Verification (Pre-Merge Audit)

### 2.1 Branch Verifications

| Branch / Ref | Commit | `scripts/verify_round.sh` | Unit Tests | Release APK DEX Grep | Result |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `origin/hotfix/2.1.1` | `7b9cdbe` | `FAIL: 2, WARN: 1` (Checks 9 & 10 fail as expected on hotfix; check 14 self-praise warning) | 1,438 passed (0 fail, 0 error) | `0` matches | PASS |
| `origin/feat/ui-motion-round3` | `fbd90ce` | `FAIL: 0, WARN: 0` (All 15 checks PASS) | 1,438 passed (0 fail, 0 error) | `0` matches | PASS |
| `origin/main` | `f858e95` | NOT RUN (`scripts/verify_round.sh` did not exist on main `f858e95`) | 1,438 passed (0 fail, 0 error) | `5` matches (proves check detects bad strings) | EXPECTED |

- **SHA-256 of `scripts/verify_round.sh`**:
  `e86228ab1dc4d5d6e1b86012925a21bbea99fffa83a8a156e870b05f61702b8c` (Verified identical across branches).

### 2.2 GitHub State Audit
- `gh release list --limit 10`:
```
Messages 2.0.1  Latest  v2.0.1  2026-10-17T06:55:09Z
Messages 2.0            v2.0    2026-10-17T06:19:00Z
Messages 1.6.1          v1.6.1  2026-10-06T18:35:21Z
```
- `gh api repos/soul991/Messages/releases/latest --jq '.tag_name,.published_at,.prerelease,.draft'`:
```
v2.0.1
2026-10-17T06:55:09Z
false
false
```
- Branch protection on `main`: `gh api repos/soul991/Messages/branches/main/protection` returned HTTP 404 (none).
- Release status: No GitHub Release exists for `v2.1.0` or `v2.1.1`. Tag `v2.1.0` exists as a git tag pointing to `8cf1e53b1a3ac07c8b6ed82961c9dbf13c60e8fe`.

### 2.3 Emulator In-Place Upgrade Test
1. Installed debug build of `8cf1e53` (`v2.1.0`) on `emulator-5554`:
   - Launched and observed synthetic thread generation ("Elena Rostova", thread ID 100).
   - Screenshot: [01_v2_1_0_with_fake_thread.png](file:///D:/Istak/Messages/Messages%20App/01_active_codebase/messages/docs/reviews/upgrade_test/01_v2_1_0_with_fake_thread.png)
2. In-place upgrade with debug build of `origin/hotfix/2.1.1`:
   - Launched and captured cleanup logcat:
```
10-19 02:49:08.725  5004  5042 I MessagesApp: cleaned up 96 synthetic message(s)
```
   - Screenshot after hotfix: [02_after_hotfix.png](file:///D:/Istak/Messages/Messages%20App/01_active_codebase/messages/docs/reviews/upgrade_test/02_after_hotfix.png)
3. Relaunch test:
   - Force-stopped app and relaunched.
   - Logcat inspection confirmed 0 duplicate cleanup executions.

---

## 3. Phase 2: Hotfix Merge

- PR #29 (`hotfix/2.1.1` -> `main`) verified with green CI.
- Merged via merge commit `3768bcf`.
- Checks on merged main: Checks 1 and 2 of `verify_round.sh` PASS; release APK dex grep printed `0`.

---

## 4. Phase 3: PR #28 (UI Motion Round 3) Integration

- Checked out `feat/ui-motion-round3` and merged `origin/main` (`336c132`).
- Merge conflict in `MessagesApp.kt` resolved in favor of `main` to preserve `cleanupSyntheticData()`.
- Wording error in `docs/reviews/PERF_REPORT.md` corrected: replaced `API 36 preview` with exact device fingerprint `google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.D1/138`.
- Verification on merged branch:
  - `bash scripts/verify_round.sh`: `FAIL: 0, WARN: 0`
  - `./gradlew test`: PASS
  - `SyntheticCleanupTest`: PASS (1 test passed)
  - Release APK dex grep: `0`
- GitHub Actions CI run `39168418580` passed all required checks.
- Merged PR #28 into `main` via merge commit `17123e4`.

---

## 5. Phase 4: Folder Switch Crash Resolution

- **Problem**: When flinging/scrolling conversations in `HomeScreen` and tapping another dock tab, `AnimatedContent` composed outgoing and incoming `LazyColumn` instances with the same shared `listState`.
- **Fix**: Isolated `LazyListState` per folder tab (`INBOX`, `TRANSACTIONS`, `PROMOTIONS`, `SPAM`, `REVIEW`, `BLOCKED`) and passed `listStateFor: (String) -> LazyListState` to `FolderPane`.
- **Stress Test Verification**:
  - `FolderSwitchStressTest` executed 300 cycles on `emulator-5554`:
```
com.messages.macrobenchmark.FolderSwitchStressTest:.

Time: 3,406.682

OK (1 test)
```
  - `adb logcat -b crash -d`: Empty.
  - `adb logcat -d | grep -i fatal`: 0 hits for `com.messages.app`.
  - Defensive check: `git grep -n "state = listState" app/src/main/kotlin/com/messages/app/ui/home/HomeScreen.kt` returned 0 matches.
- Merged via PR #33 into `main` (`4fb805e`).

---

## 6. Final State on `main` (`4fb805e`)

- `git status`: Clean working tree.
- `bash scripts/verify_round.sh`:
```
commit: 4fb805e  branch: main
------------------------------------------------------------
PASS  1  no synthetic/test-data/reaction code in app/src/main
PASS  2  no seeding hooks in app/src/main
PASS  3  every exported debug/benchmark receiver has android:permission
PASS  4  SeedDataReceiver requires confirm=WIPE_ALL_DATA
PASS  5a benchmark build has applicationIdSuffix
PASS  5b benchmark does not compile src/debug/kotlin
PASS  6  no hardcoded Color(0x...) in screens
PASS  7  no gradients/blur outside Vault + chat wallpaper
PASS  8  no literal strings in Text()
PASS  9  corner radii within {4,8,12,20,24,28,32}
PASS  10 baseline-prof.txt has no wildcard rules (10158 lines)
PASS  11 all perf JSON files are genuine androidx benchmark output (1)
PASS  12 docs/reviews/perf/device.txt exists
PASS  13 every ms value in PERF_REPORT.md is present in a genuine JSON file
PASS  14 no self-praise wording in docs/reviews or docs/releases
PASS  15 working tree clean
------------------------------------------------------------
FAIL: 0   WARN: 0
```
- Release APK dex scan:
```
0
```
- Tag `v2.1.1` created and pushed to origin.
- CI Workflow `39215188139` completed successfully.
- No release published on GitHub.

---

## 7. Open Architectural Question for the Owner

> *MainActivity no longer prompts existing users once for the default SMS role (the `onboardingDone && !isDefaultSmsApp && !role_prompted` block was removed in commit `0e39ce4` or `1a540e8`). Was that intentional? I did not change it.*
