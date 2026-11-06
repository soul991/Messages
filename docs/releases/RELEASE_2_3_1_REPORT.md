# Release 2.3.1 Verification Report

## Summary & Artifacts

- **PR #57**: Merged into `main` at commit `306fb1d556747c2d3dad9b1b59cc2a0c0b274671`
- **PR #58 (Version Bump)**: Merged into `main` at commit `b3b95c9db0d7fb4aea056d8f2ff2ebc737bf369c`
- **Release Tag**: `v2.3.1` (tag object: `7ccef62ac5d4101c7c0ab1a8f86fa01500f5310a`, commit: `b3b95c9db0d7fb4aea056d8f2ff2ebc737bf369c`)
- **Version**: versionCode `29`, versionName `"2.3.1"`
- **Certificate Digest Match**: yes
  - Expected: `8f06a576a05f50892b35f6b4e5983de51826d983966bd71c480d73d0d7914915`
  - Actual: `8f06a576a05f50892b35f6b4e5983de51826d983966bd71c480d73d0d7914915`
- **Release APK**:
  - SHA-256: `684223629d6a52137cd88f8dc393f0378ba8a571cfd0dacecadddd9db5c434d5`
  - Size: 4,765,729 bytes
- **Release URL**: https://github.com/soul991/Messages/releases/tag/v2.3.1

## CI Run Links

- **PR #57**:
  - Tests, lint, and release build: https://github.com/soul991/Messages/actions/runs/42212839524/job/127875450402
  - Dependency vulnerability scan: https://github.com/soul991/Messages/actions/runs/42212839524/job/127875450682
- **PR #58**:
  - Tests, lint, and release build: https://github.com/soul991/Messages/actions/runs/42214799890/job/127882022134
  - Dependency vulnerability scan: https://github.com/soul991/Messages/actions/runs/42214799890/job/127882022507

## Behaviour Checks on Device (Section 4.6)

- **4.6(a)**: Open a conversation from the Spam folder, press back: Home must show Spam in both the list and the dock.
  - Result: **PASS**
  - Screenshots: `docs/reviews/release_2_3_1/02_spam_conversation.png`, `docs/reviews/release_2_3_1/03_spam_after_back.png`
- **4.6(b)**: Open a conversation from the Offers folder, press back: Home must show Offers in both, with no avatar ghost lingering.
  - Result: **PASS**
  - Screenshots: `docs/reviews/release_2_3_1/05_offers_conversation.png`, `docs/reviews/release_2_3_1/06_offers_after_back.png`
- **4.6(c)**: Tap dock tabs in quick succession, then tap a row: the row that opens must belong to the folder that is selected.
  - Result: **PASS**
  - Screenshots: `docs/reviews/release_2_3_1/07_quick_tabs_conversation.png`, `docs/reviews/release_2_3_1/08_quick_tabs_after_back.png`

## Raw Gate Output

### Gate 1: PR #57 Verification on Merged main

`git grep onFolderConsumed -- app`:
```
app/src/main/kotlin/com/messages/app/MainActivity.kt:                            onFolderConsumed = { folderRequest = null },
app/src/main/kotlin/com/messages/app/ui/home/HomeScreen.kt:    onFolderConsumed: () -> Unit = {},
app/src/main/kotlin/com/messages/app/ui/home/HomeScreen.kt:            onFolderConsumed()
```

`bash scripts/verify_round.sh` output:
```
commit: 306fb1d  branch: main
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

### Gate 3: Version Bump PR #58 Verification on origin/main

`git log -3 --oneline`:
```
b3b95c9 Merge pull request #58 from soul991/release/2.3.1
8c028fe chore(release): bump version to 2.3.1 (versionCode 29)
306fb1d Merge pull request #57 from soul991/fix/dock-gestures-and-folder-navigation
```

`bash scripts/verify_round.sh` output:
```
commit: b3b95c9  branch: HEAD
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

### Gate 4: Signed Release APK Verification & Upgrade Check

`apksigner verify --print-certs app/build/outputs/apk/release/Messages.apk`:
```
Signer #1 certificate DN: CN=Messages, OU=Personal, O=Personal, C=IN
Signer #1 certificate SHA-256 digest: 8f06a576a05f50892b35f6b4e5983de51826d983966bd71c480d73d0d7914915
Signer #1 certificate SHA-1 digest: 79e2f95144814f30309daf569804d3cca79c2c62
Signer #1 certificate MD5 digest: a05aec5a144537f5fd5e71ba1d49c8ea
```

`aapt dump badging`:
```
package: name='com.messages.app' versionCode='29' versionName='2.3.1' platformBuildVersionName='15' platformBuildVersionCode='35' compileSdkVersion='35' compileSdkVersionCodename='15'
```

`certutil -hashfile app/build/outputs/apk/release/Messages.apk SHA256`:
```
SHA256 hash of app/build/outputs/apk/release/Messages.apk:
684223629d6a52137cd88f8dc393f0378ba8a571cfd0dacecadddd9db5c434d5
CertUtil: -hashfile command completed successfully.
```

Upgrade test:
- Installed published 2.3.0 release APK (tag `v2.3`, versionCode `28`, versionName `"2.3.0"`).
- Launched once on emulator (`emulator-5554`).
- Upgraded with `adb install -r app/build/outputs/apk/release/Messages.apk`: Success.
- Launched upgraded app: Success.
- `adb logcat -d | findstr /i "AndroidRuntime FATAL"`: zero crashes for `com.messages.app`.

Latest release API check:
`gh api repos/soul991/Messages/releases/latest --jq '.tag_name,.draft,.prerelease,.assets[0].name,.assets[0].size'`:
```
v2.3.1
false
false
Messages.apk
4765729
```

Fresh download SHA-256 match check:
`certutil -hashfile app/build/release_download_verify/Messages.apk SHA256`:
```
SHA256 hash of app/build/release_download_verify/Messages.apk:
684223629d6a52137cd88f8dc393f0378ba8a571cfd0dacecadddd9db5c434d5
CertUtil: -hashfile command completed successfully.
```

## Items Not Run

None. All steps across Phase 1 through Phase 5 were executed and verified.

## OPEN QUESTIONS

MainActivity no longer prompts existing users once for the default SMS role (the onboardingDone && !isDefaultSmsApp && !role_prompted block was removed in commit 0e39ce4 or 1a540e8). Was that intentional? I did not change it.
