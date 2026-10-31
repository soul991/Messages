# Release 2.2.0 Execution Report

## Overview & Artifact Summary

- **PR #50 Merge Commit**: `418de026d452c79e3029d7f8d64a0e72120ef4ca`
- **Release Version Bump PR**: Pull Request #51 (`https://github.com/soul991/Messages/pull/51`)
- **Release Version Bump Merge Commit**: `4c0e875a6364e436d31c365f484c7445dea11eb5`
- **Git Release Tag**: `v2.2.0` (Tag object `a71de0b054c6cdf9f6a31f8780eb5af00b870a72` on commit `4c0e875a6364e436d31c365f484c7445dea11eb5`)
- **Version Configuration**: `versionCode = 27`, `versionName = "2.2.0"`
- **Signing Certificate SHA-256 Digest**: `8f06a576a05f50892b35f6b4e5983de51826d983966bd71c480d73d0d7914915`
- **Certificate Digest Match Required (8f06a5...4915)**: Yes
- **Built APK SHA-256**: `525d83ecd1917e4e0171b0db47259cfa4db236b25f848b6021bb545cf3748e3d`
- **Built APK File Size**: 4,749,345 bytes
- **Published GitHub Release URL**: `https://github.com/soul991/Messages/releases/tag/v2.2.0`
- **CI Run Links**:
  - PR #50 checks: `https://github.com/soul991/Messages/actions/runs/41211861742`
    - Dependency vulnerability scan: `https://github.com/soul991/Messages/actions/runs/41211861742/job/124568348144`
    - Tests, lint, and release build: `https://github.com/soul991/Messages/actions/runs/41211861742/job/124568348289`
  - PR #51 checks: `https://github.com/soul991/Messages/actions/runs/41239427712`
    - Dependency vulnerability scan: `https://github.com/soul991/Messages/actions/runs/41239427712/job/124650002162`
    - Tests, lint, and release build: `https://github.com/soul991/Messages/actions/runs/41239427712/job/124650002413`

---

## Performance Disclaimers & Known Limits

- **Benchmark Measurements**: Benchmark runs were conducted exclusively on an x86 emulator, only 10 of 40 planned journey runs exist, and same-build noise is larger than any before/after gap. The data is inconclusive and no performance claims are made.
- **Border Contrast**: `outlineVariant` borders measure between 1.25:1 and 1.51:1 contrast against surfaces (below 3:1, decorative hairlines).

---

## Gate Verifications and Raw Outputs

### Phase 1: Pre-Merge Verification (PR #50)

#### 1.1 PR #50 Head and Origin/Main
Command:
```bash
git fetch --all
gh pr view 50 --json baseRefName,baseRefOid,headRefName,headRefOid,number,state
git rev-parse origin/main
git rev-parse c681bc4
git merge-base origin/main c681bc4
```
Raw Output:
```
{"baseRefName":"main","baseRefOid":"82938f60ae1762fa0092c619756acdd2c3b03926","headRefName":"feat/orchid-theme-liquid-dock","headRefOid":"c681bc42271832659c3415040dfb45bf0a932cfd","number":50,"state":"OPEN"}
82938f60ae1762fa0092c619756acdd2c3b03926
c681bc42271832659c3415040dfb45bf0a932cfd
82938f60ae1762fa0092c619756acdd2c3b03926
```

#### 1.2 Checksum, verify_round.sh, Unit Tests, assembleRelease, and DEX Grep
Commands:
```bash
git checkout --detach c681bc42271832659c3415040dfb45bf0a932cfd
sha256sum scripts/verify_round.sh
bash scripts/verify_round.sh
./gradlew test
./gradlew :app:assembleRelease
unzip -p app/build/outputs/apk/release/*.apk 'classes*.dex' | strings | grep -ci "Rostova\|SyntheticStress\|ReactionStore"
```
Raw Output - sha256sum:
```
e86228ab1dc4d5d6e1b86012925a21bbea99fffa83a8a156e870b05f61702b8c *scripts/verify_round.sh
```
Raw Output - verify_round.sh:
```
commit: c681bc4  branch: HEAD
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
Raw Output - ./gradlew test:
```
BUILD SUCCESSFUL in 41s
184 actionable tasks: 184 up-to-date
```
Raw Output - ./gradlew :app:assembleRelease:
```
BUILD SUCCESSFUL in 17s
125 actionable tasks: 3 executed, 122 up-to-date
```
Raw Output - DEX grep:
```
0
```

#### 1.3 AI Gate Verification
Command:
```bash
shopt -s globstar; grep -n "AI_LAYER_ENABLED" protection-engine/src/main/kotlin/**/AiScorer.kt
git diff origin/main...HEAD -- protection-engine/src/main/resources/ngram_model.json
```
Raw Output:
```
17: * it. Neither `AiScorer` nor `AI_LAYER_ENABLED` exists anywhere in the shipped
227:    const val AI_LAYER_ENABLED = false
351:     * @param enabled defaults to [AI_LAYER_ENABLED]. Production call sites
355:    fun apply(verdict: Verdict, shadow: Shadow?, enabled: Boolean = AI_LAYER_ENABLED): Verdict {
434:        enabled: Boolean = AI_LAYER_ENABLED,
```
Diff on `protection-engine/src/main/resources/ngram_model.json`: empty (0 bytes).

#### 1.4 PR #50 Checks
Command:
```bash
gh pr checks 50
```
Raw Output:
```
Signed release artifact	skipping	0	https://github.com/soul991/Messages/actions/runs/41211861742/job/124570208308	
Dependency vulnerability scan	pass	57s	https://github.com/soul991/Messages/actions/runs/41211861742/job/124568348144	
Tests, lint, and release build	pass	8m12s	https://github.com/soul991/Messages/actions/runs/41211861742/job/124568348289	
```

---

### Phase 2: Merge PR #50

#### 2.1 Merge Execution
Command:
```bash
gh pr merge 50 --merge
gh pr view 50 --json state,mergedAt,mergeCommit
```
Raw Output:
```
{"mergeCommit":{"oid":"418de026d452c79e3029d7f8d64a0e72120ef4ca"},"mergedAt":"2026-10-31T20:30:08Z","state":"MERGED"}
```

#### 2.2 Verify Merged main
Command:
```bash
git fetch origin
git checkout --detach origin/main
bash scripts/verify_round.sh
./gradlew :app:assembleRelease
unzip -p app/build/outputs/apk/release/*.apk 'classes*.dex' | strings | grep -ci "Rostova\|SyntheticStress\|ReactionStore"
```
Raw Output - verify_round.sh:
```
commit: 418de02  branch: HEAD
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
Raw Output - assembleRelease:
```
BUILD SUCCESSFUL in 15s
125 actionable tasks: 3 executed, 122 up-to-date
```
Raw Output - DEX grep:
```
0
```

---

### Phase 3: Version Bump via PR #51

#### 3.1 Initial Values and Diff
Command:
```bash
Select-String -Path "app/build.gradle.kts" -Pattern "versionCode|versionName"
gh release list --limit 10
gh api repos/soul991/Messages/releases/latest --jq '.tag_name'
git diff app/build.gradle.kts
```
Raw Output:
```
app\build.gradle.kts:97:        versionCode = 26
app\build.gradle.kts:98:        versionName = "2.1.4"
app\build.gradle.kts:136:            versionNameSuffix = "-benchmark"
Messages 2.1.4	Latest	v2.1.4	2026-10-21T06:53:31Z
Messages 2.1.3		v2.1.3	2026-10-20T19:45:40Z
Messages 2.1.2		v2.1.2	2026-10-20T08:58:48Z
Messages 2.1.1		v2.1.1	2026-10-19T11:28:09Z
Messages 2.0.1		v2.0.1	2026-10-17T06:55:09Z
Messages 2.0		v2.0	2026-10-17T06:19:00Z
Messages 1.6.1		v1.6.1	2026-10-06T18:35:21Z
Messages v1.5.2 - Critical Signing Key Fix & Notification Brand Sync		v1.5.2	2026-09-06T16:11:41Z
Messages v1.5.1 - 2026 Visual Refresh Polish & Notification Branding		v1.5.1	2026-09-05T08:07:11Z
Messages v1.5.0 - 2026 Visual Refresh (Violet Identity & Warm Canvas)		v1.5.0	2026-09-05T06:50:22Z
v2.1.4
```
Diff:
```diff
diff --git a/app/build.gradle.kts b/app/build.gradle.kts
index eb3b868..4ff5ad3 100644
--- a/app/build.gradle.kts
+++ b/app/build.gradle.kts
@@ -94,8 +94,8 @@ android {
         applicationId = "com.messages.app"
         minSdk = 26
         targetSdk = 35
-        versionCode = 26
-        versionName = "2.1.4"
+        versionCode = 27
+        versionName = "2.2.0"
     }
 
     if (keystoreProps.isNotEmpty()) {
```

#### 3.2 Release Notes Verification
Rule 4 / Check 14 compliance scan on `docs/releases/2.2.0.md` (no prohibited words or self-praise):
Raw Output: 0 matches.

#### 3.3 PR #51 Creation, CI Checks, and Merge
Commands:
```bash
gh pr create --base main --head release/2.2.0 --title "release 2.2.0" --body "Bump version to 2.2.0 (versionCode 27) and add release notes."
gh pr checks 51 --watch
gh pr checks 51
gh pr merge 51 --merge
```
Raw Output - PR creation:
```
https://github.com/soul991/Messages/pull/51
```
Raw Output - gh pr checks 51:
```
Signed release artifact	skipping	0	https://github.com/soul991/Messages/actions/runs/41239427712/job/124651961081	
Dependency vulnerability scan	pass	36s	https://github.com/soul991/Messages/actions/runs/41239427712/job/124650002162	
Tests, lint, and release build	pass	8m12s	https://github.com/soul991/Messages/actions/runs/41239427712/job/124650002413	
```

#### 3.4 Verification of Merged Main
Commands:
```bash
git fetch origin
git checkout --detach origin/main
git log -n 3 --oneline
bash scripts/verify_round.sh
```
Raw Output:
```
From https://github.com/soul991/Messages
   418de02..4c0e875  main       -> origin/main
HEAD is now at 4c0e875 Merge pull request #51 from soul991/release/2.2.0
4c0e875 Merge pull request #51 from soul991/release/2.2.0
cc9258c chore(release): bump version to 2.2.0 (versionCode 27)
418de02 Merge pull request #50 from soul991/feat/orchid-theme-liquid-dock

commit: 4c0e875  branch: HEAD
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

---

### Phase 4: Signed APK Assembly and Upgrade Verification

#### 4.1 Tag Creation
Command:
```bash
git tag -a v2.2.0 -m "Messages 2.2.0"
git push origin v2.2.0
```
Raw Output:
```
4c0e875a6364e436d31c365f484c7445dea11eb5
To https://github.com/soul991/Messages.git
 * [new tag]         v2.2.0 -> v2.2.0
```

#### 4.2 Assembly
Command:
```bash
.\gradlew.bat :app:assembleRelease
```
Raw Output:
```
BUILD SUCCESSFUL in 3m 6s
125 actionable tasks: 23 executed, 102 up-to-date
```

#### 4.3 Certificate Verification
Command:
```bash
apksigner verify --print-certs app/build/outputs/apk/release/Messages.apk
```
Raw Output:
```
Signer #1 certificate DN: CN=Messages, OU=Personal, O=Personal, C=IN
Signer #1 certificate SHA-256 digest: 8f06a576a05f50892b35f6b4e5983de51826d983966bd71c480d73d0d7914915
Signer #1 certificate SHA-1 digest: 79e2f95144814f30309daf569804d3cca79c2c62
Signer #1 certificate MD5 digest: a05aec5a144537f5fd5e71ba1d49c8ea
```

#### 4.4 Badging and APK Checksum
Commands:
```bash
aapt dump badging app/build/outputs/apk/release/Messages.apk | findstr "versionCode versionName"
certutil -hashfile app/build/outputs/apk/release/Messages.apk SHA256
```
Raw Output:
```
package: name='com.messages.app' versionCode='27' versionName='2.2.0' platformBuildVersionName='15' platformBuildVersionCode='35' compileSdkVersion='35' compileSdkVersionCodename='15'
SHA256 hash of app/build/outputs/apk/release/Messages.apk:
525d83ecd1917e4e0171b0db47259cfa4db236b25f848b6021bb545cf3748e3d
CertUtil: -hashfile command completed successfully.
```

#### 4.5 Upgrade Verification on Emulator
Commands:
```bash
adb -s emulator-5554 install "docs/reviews/release_2_2_0/Messages_v2.1.1.apk"
adb -s emulator-5554 shell am start -n com.messages.app/com.messages.app.MainActivity
adb -s emulator-5554 install -r "app/build/outputs/apk/release/Messages.apk"
adb -s emulator-5554 shell am start -n com.messages.app/com.messages.app.MainActivity
adb -s emulator-5554 logcat -d | Select-String "AndroidRuntime|FATAL" | Select-String "com.messages.app"
```
Raw Output - v2.1.1 Install:
```
Performing Streamed Install
Success
Starting: Intent { cmp=com.messages.app/.MainActivity }
topResumedActivity=ActivityRecord{152525509 u0 com.messages.app/.MainActivity t516}
```
Raw Output - In-Place Upgrade to v2.2.0:
```
Performing Streamed Install
Success
Starting: Intent { cmp=com.messages.app/.MainActivity }
topResumedActivity=ActivityRecord{93695533 u0 com.messages.app/.MainActivity t517}
```
Raw Output - Logcat Crash Filter:
```
(No lines output; 0 matches for AndroidRuntime/FATAL related to com.messages.app)
```
Screenshots Captured:
- `docs/reviews/release_2_2_0/home_orchid_dock.png` (Messages Home with Orchid accent and floating dock)
- `docs/reviews/release_2_2_0/home_v2_2_0.png`

---

### Phase 5: GitHub Release Publishing

#### 5.1 Release Creation
Command:
```bash
gh release create v2.2.0 app/build/outputs/apk/release/Messages.apk --title "Messages 2.2.0" --notes-file docs/releases/2.2.0.md --latest --verify-tag
```
Raw Output:
```
https://github.com/soul991/Messages/releases/tag/v2.2.0
```

#### 5.2 Release Query
Command:
```bash
gh api repos/soul991/Messages/releases/latest --jq ".tag_name,.draft,.prerelease,.assets[0].name,.assets[0].size"
```
Raw Output:
```
v2.2.0
false
false
Messages.apk
4749345
```

#### 5.3 Download Asset Verification
Command:
```bash
cd _dl_verify_220
gh release download v2.2.0 --pattern Messages.apk --dir .
certutil -hashfile Messages.apk SHA256
```
Raw Output:
```
SHA256 hash of Messages.apk:
525d83ecd1917e4e0171b0db47259cfa4db236b25f848b6021bb545cf3748e3d
CertUtil: -hashfile command completed successfully.
```
Digest Comparison:
- Step 4.4 Build Hash: `525d83ecd1917e4e0171b0db47259cfa4db236b25f848b6021bb545cf3748e3d`
- Step 5.3 Download Hash: `525d83ecd1917e4e0171b0db47259cfa4db236b25f848b6021bb545cf3748e3d`
- Result: Matches exactly.

---

## Items Not Run

None. All steps 1.1 through 5.3 were executed.

---

## OPEN QUESTIONS

MainActivity no longer prompts existing users once for the default SMS role (the onboardingDone && !isDefaultSmsApp && !role_prompted block was removed in commit 0e39ce4 or 1a540e8). Was that intentional? I did not change it.
