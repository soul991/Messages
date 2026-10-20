# Release Verification and Audit Report: v2.1.2

- **Main Commit SHA**: `4f46cee51d333bb27d924765814c9c82544ed8bf`
- **Release Tag**: `v2.1.2`
- **Release URL**: https://github.com/soul991/Messages/releases/tag/v2.1.2
- **Release APK Artifact**: `Messages-2.1.2.apk`
- **Release APK SHA-256**: `0114658b6f436eaf0ff36d4cb40772a21bc5545f353e8adfb67fb223a01e06b7`
- **Release APK File Size**: 4,217,133 bytes (4.02 MiB)
- **Signing Key SHA-256**: `8f06a576a05f50892b35f6b4e5983de51826d983966bd71c480d73d0d7914915` (Matches v2.0.1 baseline)

---

## 1. Summary of Verification Results

| Step | Description | Status | Evidence / Note |
|---|---|---|---|
| **A0** | Fetch, checkout `ml/india-3lang-loop`, merge `origin/main`, diff check, `ngram_model.json` hash check | **PASS** | 44 files changed, 0 files in app/src, blob sha256 `1ba8aa43...` |
| **A1** | Replace `file:///` links with relative links across `docs/` and `ml/` | **PASS** | `grep -rn "file:///" docs/ ml/` returned exit code 1 (no matches) |
| **A2** | Honest numbers in `docs/ml/CANDIDATE_REPORT.md` | **PASS** | Added guidance section, added qualification note, updated fixture count to 235 |
| **A3** | Remove intermediate candidate models and retain required inputs | **PASS** | 5 intermediate models removed (219,936 lines deleted), required inputs kept |
| **A4** | Pre-merge verification checks | **PASS** | `verify_round.sh` (FAIL: 0, WARN: 0), `./gradlew test` (1,441 tests passed) |
| **A5** | Push `ml/india-3lang-loop`, post PR comment, CI checks, merge PR #39 | **PASS** | CI passed, merged cleanly with merge commit `4f46cee` |
| **A6** | Post-merge main verification | **PASS** | HEAD `4f46cee`, blob hash `1ba8aa43...`, `verify_round.sh` passed, tests passed |
| **B0** | Pre-release checks | **PASS** | No pre-existing tag, no pre-existing release, versionCode 24, versionName 2.1.2 |
| **B1** | Build signed release APK | **PASS** | `./gradlew clean :app:assembleRelease` -> `Messages.apk` |
| **B2a** | Signature verification (`apksigner`) | **PASS** | Signer #1 SHA-256 matches `8f06a576a05f50892b35f6b4e5983de51826d983966bd71c480d73d0d7914915` |
| **B2b** | Manifest verification (`aapt2`) | **PASS** | `packageName='com.messages.app'`, `versionCode='24'`, `versionName='2.1.2'` |
| **B2c** | DEX leftover strings scan | **PASS** | Count: 0 |
| **B2d** | Compute APK SHA-256 | **PASS** | `0114658b6f436eaf0ff36d4cb40772a21bc5545f353e8adfb67fb223a01e06b7` |
| **B2e** | Compute APK file size | **PASS** | 4,217,133 bytes |
| **B3** | Device physical verification | **NOT RUN** | Reason: No device or emulator connected (`adb devices` returned empty list) |
| **B4** | Tag and publish GitHub release | **PASS** | Tag `v2.1.2` pushed, release created with notes and SHA-256 |
| **B5** | Download and verify published release asset | **PASS** | Downloaded `Messages-2.1.2.apk` SHA-256 equals `0114658b6f436eaf0ff36d4cb40772a21bc5545f353e8adfb67fb223a01e06b7` |

---

## 2. Part A: Raw Command Outputs

### A0. Fetch, Merge and Pre-Check
```
Already on 'ml/india-3lang-loop'
Your branch is up to date with 'origin/ml/india-3lang-loop'.
Already up to date.
```

`git diff --stat origin/main...HEAD`:
```
 .gitignore                                         |     3 +
 docs/ml/CANDIDATE_REPORT.md                        |    82 +
 docs/ml/OWNER_ACCEPTANCE_HOWTO.md                  |    81 +
 docs/ml/OWNER_LABELING_RUBRIC.md                   |    92 +
 docs/ml/RESEARCH_LOG.md                            |    79 +
 docs/ml/cycles/cycle_00_baseline.json              |    52 +
 docs/ml/cycles/cycle_01_baselines.json             |   312 +
 docs/ml/cycles/cycle_02_routed_experts.json        |   112 +
 docs/ml/cycles/cycle_03_features.json              |   112 +
 docs/ml/cycles/cycle_04_advisory_discounting.json  |   112 +
 docs/ml/cycles/cycle_05_strict_margin.json         |   112 +
 docs/ml/cycles/cycle_06_calibrated_linear.json     |   112 +
 docs/ml/cycles/cycle_07_multilingual_advisory.json |   112 +
 docs/ml/data-card.md                               |    61 +
 docs/ml/dataset_quality_audit.json                 |    97 +
 docs/ml/datasets_manifest.json                     |   152 +
 docs/ml/external-challenge-report.json             |     6 +-
 docs/ml/requirements-lock.txt                      |    48 +
 docs/ml/router_benchmark.json                      |    32 +
 docs/ml/split_leakage_report.json                  |    73 +
 ml/candidates/9a4ddf6934c0bab5/metadata.json       |    19 +
 ml/candidates/9a4ddf6934c0bab5/model.json          | 29946 +++++++++
 ml/candidates/9a4ddf6934c0bab5/score_sample.json   |  2117 +
 protection-engine/tools/audit_datasets.py          |   150 +
 protection-engine/tools/benchmark_router.py        |    91 +
 protection-engine/tools/build_corpus.py            |   322 +
 protection-engine/tools/evaluate_acceptance.py     |   270 +
 .../tools/evaluate_char_cnn_candidate.py           |     2 +-
 protection-engine/tools/make_owner_sample.py       |   155 +
 protection-engine/tools/router.py                  |   114 +
 .../tools/train_and_eval_baselines.py              |   361 +
 .../tools/train_cycle_02_routed_experts.py         |   359 +
 protection-engine/tools/train_cycle_03_features.py |   379 +
 .../tools/train_cycle_04_advisory_discounting.py   |   279 +
 .../tools/train_cycle_05_strict_margin.py          |   278 +
 .../tools/train_cycle_06_calibrated_linear.py      |   307 +
 .../tools/train_cycle_07_multilingual_advisory.py  |   317 +
 .../training/calibrated_advisory_candidate.json    | 29946 +++++++++
 .../training/calibrated_linear_candidate.json      | 29946 +++++++++
 .../training/ngram_advisory_candidate.json         | 63325 ++++++++++++++++++
 .../training/ngram_features_candidate.json         | 63328 +++++++++++++++++++
 .../training/ngram_routed_candidate.json           | 63322 ++++++++++++++++++
 .../training/ngram_strict_candidate.json           | 63324 ++++++++++++++++++
 scripts/verify_ml.py                               |   147 +
 44 files changed, 350642 insertions(+), 4 deletions(-)
```

Model blob SHA-256 check:
```
1ba8aa43bcb46cc1844583ca6e64f96f319602d1fcc7849c53a10b2923eb2b8f
```

### A1. Broken Links Check
`grep -rn "file:///" docs/ ml/`:
```
(No output, exited with code 1)
```

### A2. Parity Fixture Count Command
```
$env:PYTHONUTF8=1; python -c "import json;d=json.load(open('ml/candidates/9a4ddf6934c0bab5/score_sample.json'));print(len(d) if isinstance(d,list) else len(d.get('samples',d)))"
235
```

### A3. Candidate Model Grep Audit and Cleanup
Grep hits for candidates:
```
protection-engine/tools/train_cycle_06_calibrated_linear.py:37:MODEL_OUT = ROOT / "protection-engine/training/calibrated_linear_candidate.json"
protection-engine/tools/train_cycle_04_advisory_discounting.py:34:MODEL_OUT = ROOT / "protection-engine/training/ngram_advisory_candidate.json"
protection-engine/tools/train_cycle_03_features.py:32:MODEL_OUT = ROOT / "protection-engine/training/ngram_features_candidate.json"
protection-engine/tools/evaluate_external_challenge.py:79:        ROOT, "protection-engine", "training", "ngram_language_experts_candidate.json"))
protection-engine/tools/train_multilingual_candidate.py:413:    expert_path = os.path.join(ROOT, "protection-engine", "training", "ngram_language_experts_candidate.json")
protection-engine/tools/evaluate_external_challenge.py:77:        ROOT, "protection-engine", "training", "ngram_multilingual_candidate.json"))
protection-engine/tools/train_multilingual_candidate.py:287:    parser.add_argument("--output", default=os.path.join(ROOT, "protection-engine", "training", "ngram_multilingual_candidate.json"))
protection-engine/tools/train_cycle_02_routed_experts.py:33:MODEL_OUT = ROOT / "protection-engine/training/ngram_routed_candidate.json"
protection-engine/tools/train_cycle_04_advisory_discounting.py:219:    candidate_source = ROOT / "protection-engine/training/ngram_routed_candidate.json"
protection-engine/tools/train_cycle_05_strict_margin.py:218:    candidate_source = ROOT / "protection-engine/training/ngram_routed_candidate.json"
protection-engine/tools/train_cycle_05_strict_margin.py:32:MODEL_OUT = ROOT / "protection-engine/training/ngram_strict_candidate.json"
```

Deleted intermediate candidate files:
- `protection-engine/training/calibrated_linear_candidate.json`
- `protection-engine/training/ngram_advisory_candidate.json`
- `protection-engine/training/ngram_bengali_candidate.json`
- `protection-engine/training/ngram_features_candidate.json`
- `protection-engine/training/ngram_strict_candidate.json`

Preserved required candidate files:
- `protection-engine/training/calibrated_advisory_candidate.json` (written by Cycle 07 trainer)
- `protection-engine/training/ngram_routed_candidate.json` (required input for `train_cycle_04` and `train_cycle_05`)
- `protection-engine/training/ngram_language_experts_candidate.json` (referenced in `evaluate_external_challenge.py`)
- `protection-engine/training/ngram_multilingual_candidate.json` (referenced in `evaluate_external_challenge.py`)

`python scripts/verify_ml.py`:
```
commit: 8f592af
------------------------------------------------------------
PASS  1  AI_LAYER_ENABLED is false (research-only gate closed)
PASS  2  shipped model assets <= 2 MiB
PASS  3  no raw/private/archive/acceptance data tracked
PASS  4  dataset manifest valid (10 entries)
FAIL  5  unsafe loading at: scripts/verify_ml.py:101
PASS  6  training/tuning code never references the acceptance set
PASS  7  8 cycle log(s) valid
PASS  8  no promotional wording in docs/ml
FAIL  9  uncommitted changes
------------------------------------------------------------
FAIL: 2   WARN: 0
```
Note: Check 5 is a regex self-match inside `verify_ml.py` line 101 (`\bpickle\.load`), and Check 9 was uncommitted working tree changes prior to commit.

### A4. Pre-Commit Verifications
`bash scripts/verify_round.sh`:
```
commit: eb90a39  branch: ml/india-3lang-loop
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

`./gradlew test`:
```
BUILD SUCCESSFUL in 38s
184 actionable tasks: 184 up-to-date
```
Test results summary:
```
Total tests: 1441, Failures: 0, Errors: 0, Skipped: 0
```

### A5. PR #39 Merge Evidence
- Pushed commit `eb90a39` to `origin/ml/india-3lang-loop`.
- CI Checks:
```
Signed release artifact         skipping    0       https://github.com/soul991/Messages/actions/runs/39396426740/job/118540937855
Dependency vulnerability scan   pass        56s     https://github.com/soul991/Messages/actions/runs/39396426740/job/118540193398
Tests, lint, and release build  pass        1m48s   https://github.com/soul991/Messages/actions/runs/39396426740/job/118540193690
```
- PR Merge Result:
```
{"mergeCommit":{"oid":"4f46cee51d333bb27d924765814c9c82544ed8bf"},"mergedAt":"2026-10-20T08:53:43Z","state":"MERGED"}
```

### A6. Post-Merge Origin/Main Checks
`git rev-parse HEAD`:
```
4f46cee51d333bb27d924765814c9c82544ed8bf
```

Model blob SHA-256 on main:
```
1ba8aa43bcb46cc1844583ca6e64f96f319602d1fcc7849c53a10b2923eb2b8f
```

`bash scripts/verify_round.sh` on main:
```
commit: 4f46cee  branch: HEAD
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

`./gradlew test` on main:
```
BUILD SUCCESSFUL in 15s
184 actionable tasks: 184 up-to-date
```
Test results summary:
```
Total tests: 1441, Failures: 0, Errors: 0, Skipped: 0
```

---

## 3. Part B: Raw Command Outputs

### B0. Pre-Release Preconditions
- `git status`:
```
HEAD detached at origin/main
nothing to commit, working tree clean
```
- `git tag --list` output: Did not contain `v2.1.2`.
- `gh release list`: Latest was `v2.1.1`.
- `grep -E "versionCode|versionName" app/build.gradle.kts`:
```
        versionCode = 24
        versionName = "2.1.2"
            versionNameSuffix = "-benchmark"
```
- `docs/releases/2.1.2.md`:
```
# Messages 2.1.2

- The research AI model can no longer be switched on from Settings.
- A previously saved opt-in is cleared.
- The model size limit now counts bytes.
```

### B1. Build Signed Release APK
Command: `./gradlew clean :app:assembleRelease`
```
BUILD SUCCESSFUL in 1m
130 actionable tasks: 67 executed, 51 from cache, 12 up-to-date
```
Output artifact: `app/build/outputs/apk/release/Messages.apk` (and `app-release.apk`).

### B2. APK Independent Verification
#### (a) Signature Verification
Command: `apksigner.bat verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk`
```
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): false
Verified using v3.1 scheme (APK Signature Scheme v3.1): false
Verified using v4 scheme (APK Signature Scheme v4): false
Verified for SourceStamp: false
Number of signers: 1
Signer #1 certificate DN: CN=Messages, OU=Personal, O=Personal, C=IN
Signer #1 certificate SHA-256 digest: 8f06a576a05f50892b35f6b4e5983de51826d983966bd71c480d73d0d7914915
Signer #1 certificate SHA-1 digest: 79e2f95144814f30309daf569804d3cca79c2c62
Signer #1 certificate MD5 digest: a05aec5a144537f5fd5e71ba1d49c8ea
Signer #1 key algorithm: RSA
Signer #1 key size (bits): 4096
Signer #1 public key SHA-256 digest: 7920c72e6e96c7bb0ee69653a6ceb2ab932dca56ea5039b52fe7918941522e7e
Signer #1 public key SHA-1 digest: 898b976cf7fd46484b47602f955770f30015ee28
Signer #1 public key MD5 digest: 9a34530ae467699f999d97f63efc3055
```
Comparison with v2.0.1 baseline:
- Baseline Digest: `8f06a576a05f50892b35f6b4e5983de51826d983966bd71c480d73d0d7914915`
- Release Digest:  `8f06a576a05f50892b35f6b4e5983de51826d983966bd71c480d73d0d7914915`
- Match: **EXACT MATCH**

#### (b) Manifest Badging
Command: `aapt2.exe dump badging app/build/outputs/apk/release/app-release.apk | grep -E "package: name|versionCode|versionName"`
```
package: name='com.messages.app' versionCode='24' versionName='2.1.2' platformBuildVersionName='15' platformBuildVersionCode='35' compileSdkVersion='35' compileSdkVersionCodename='15'
```

#### (c) Leftover Test Strings
Command: `python -c "import zipfile,re; z=zipfile.ZipFile('app/build/outputs/apk/release/app-release.apk'); dex=b''.join(z.read(n) for n in z.namelist() if n.endswith('.dex')); print(len(re.findall(rb'Rostova|SyntheticStress|ReactionStore', dex, re.I)))"`
```
0
```

#### (d) & (e) SHA-256 and File Size
```
0114658b6f436eaf0ff36d4cb40772a21bc5545f353e8adfb67fb223a01e06b7 *app/build/outputs/apk/release/app-release.apk
-rw-r--r-- 1 hyper 197609 4217133 Oct 20 14:26 app/build/outputs/apk/release/app-release.apk
```

### B3. Device Verification
Command: `adb.exe devices -l`
```
List of devices attached
```
Status: **NOT RUN: no device connected**

### B4. Tagging and Release Publishing
- Tag creation: `git tag -a v2.1.2 4f46cee51d333bb27d924765814c9c82544ed8bf -m "Release v2.1.2"`
- Tag push: `git push origin v2.1.2`
- Release publish:
```
https://github.com/soul991/Messages/releases/tag/v2.1.2
```

### B5. Release Verification
Command: `gh release view v2.1.2 --json tagName,isDraft,isPrerelease,assets,url`
```json
{
  "assets": [
    {
      "apiUrl": "https://api.github.com/repos/soul991/Messages/releases/assets/658796537",
      "contentType": "application/vnd.android.package-archive",
      "createdAt": "2026-10-20T08:58:46Z",
      "digest": "sha256:0114658b6f436eaf0ff36d4cb40772a21bc5545f353e8adfb67fb223a01e06b7",
      "downloadCount": 0,
      "id": "RA_kwDOTuBQp84nRG_5",
      "label": "",
      "name": "Messages-2.1.2.apk",
      "size": 4217133,
      "state": "uploaded",
      "updatedAt": "2026-10-20T08:58:47Z",
      "url": "https://github.com/soul991/Messages/releases/download/v2.1.2/Messages-2.1.2.apk"
    }
  ],
  "isDraft": false,
  "isPrerelease": false,
  "tagName": "v2.1.2",
  "url": "https://github.com/soul991/Messages/releases/tag/v2.1.2"
}
```

Asset download and SHA-256 verification:
```
gh release download v2.1.2 -p "Messages-2.1.2.apk" -D .tmp_verify/
0114658b6f436eaf0ff36d4cb40772a21bc5545f353e8adfb67fb223a01e06b7 *.tmp_verify/Messages-2.1.2.apk
```
Digest matches step B2(d) bit for bit.
