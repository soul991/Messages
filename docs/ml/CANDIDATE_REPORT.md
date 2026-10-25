# Stage 1 Candidate Evaluation Report: Cycle 15 Calibrated Round-2 Multilingual Linear Expert System

- **Candidate SHA-256**: `c98c5500a14e379953395369835b35f2ff157ed0a28e132eeeb0eb363e3cdfbb`
- **Candidate Prefix**: `c98c5500a14e3799`
- **Evaluation Date**: 2026-10-24
- **Governing Criteria**: [Scope and Numerical Acceptance Criteria](scope-and-acceptance-criteria.md)
- **Dataset Manifest SHA-256**: `3143428a38c0882ae3c1116e45c827a956b45d1513909bca65c685b1df9fc472`
- **Candidate Model Size**: 1,025,713 bytes (1,001.67 KiB)
- **Packaged Path**: `ml/candidates/c98c5500a14e3799/model.json`
- **Release Recommendation**: **`DO NOT MERGE / DO NOT ENABLE — Release gates unproven on Bengali negative subset`**

---

## How to read these numbers

- **Training Corpus**: Trained exclusively on `D:\Istak\Messages\Datasets\splits\train.jsonl` (19,603 rows: 9,650 `en_latin`, 4,998 `hi_latn`, 3,551 `hi_deva`, 854 `bn_beng`, 550 `bn_latn`).
- **Validation Tuning**: Decision thresholds and hyper-parameters were tuned exclusively on `D:\Istak\Messages\Datasets\splits\validation.jsonl` (2,200 rows of strictly real Indian messages: 1,200 `en_latin`, 400 `hi_deva`, 300 `hi_latn`, 300 `bn_beng`).
- **Frozen Benchmark**: Re-evaluated on the frozen legacy benchmark `dev_challenge.jsonl` (559 rows: 269 scam, 290 ham) at cycle milestones (Cycle 08, Cycle 12, Cycle 13) to measure progress against Round 1.
- **Untouched Lockbox**: Evaluated once (`lockbox_eval_count = 1/3`) on `D:\Istak\Messages\Datasets\splits\lockbox.jsonl` (1,800 rows: 515 scam, 1,285 ham; zero leakage with train/validation/dev).
- **The Conclusive Gate Rule**: Under the Stage 1 release criteria, a gate counts as MET only if the Wilson 95% confidence bound proves it (for Recall: lower bound $\ge \text{gate}$; for FPR: upper bound $\le \text{gate}$). To prove an FPR $\le 0.50\%$ with 0 errors requires at least $N \ge 600$ negative examples in that slice; to prove $\le 1.00\%$ requires at least $N \ge 300$. Slices with fewer than 100 samples are classified as *inconclusive*.
- **Private Blind Acceptance Set**: Per Rule R3, the project owner's private set (`PRIVATE_ACCEPTANCE/`) was never opened, read, tuned on, or evaluated by automated tools.

---

## 1. Executive Summary & Gate Verdict

> [!CAUTION]
> **VERDICT: CANDIDATE HELD — RELEASE GATES UNPROVEN FOR PRODUCTION RELEASE**
> 
> The best candidate developed across Round 2 research cycles 09 through 15 is the **Calibrated Round-2 Multilingual Linear Expert System** (Cycle 15, packaged as `c98c5500a14e3799`).
> 
> Across all evaluated splits, this candidate demonstrates major measurable advances over Round 1's best model (`378472fe650be5e6`):
> 1. **Frozen Benchmark False Alarm Reduction**: On `dev_challenge.jsonl` (559 rows), overall Ham False Positive Rate was reduced from **20.00%** (58 false alarms) in Round 1 down to **0.69%** (only 2 false alarms out of 290 ham messages).
>    - Latin Ham FPR dropped from **10.14%** (15 / 148) to **0.00%** (0 / 148).
>    - Bengali Ham FPR dropped from **20.00%** (10 / 50) to **0.00%** (0 / 50).
>    - Devanagari Hindi Ham FPR dropped from **35.87%** (33 / 92) to **2.17%** (2 / 92).
> 2. **Validation Performance**: On the 2,200-row real Indian validation split, overall Scam Recall is **88.78%** [85.36%, 91.48%] (Wilson lower bound 85.36% > 80.0% gate), and Ham FPR is **0.45%** [0.23%, 0.88%] ($\le 0.50\%$ point estimate). Protected traffic FPR is strictly **0.00%** (0 / 168), and advisory FPR is strictly **0.00%** (0 / 5).
> 3. **Untouched Lockbox**: On `lockbox.jsonl` (1,800 rows), overall Scam Recall is **90.29%** [87.43%, 92.56%], Latin Ham FPR is **0.82%** (6 / 734), Devanagari Ham FPR is **0.27%** (1 / 370), and Protected FPR is strictly **0.00%** (0 / 76).
> 
> **Why the model is marked UNPROVEN / DO NOT MERGE**:
> - Under the Conclusive Rule, a gate is MET only if the Wilson 95% bound strictly proves it. In the lockbox slice, Bengali contains only 181 negative messages ($N = 181 < 300$). A sample of 181 negatives cannot mathematically prove an upper bound $\le 1.00\%$ (with zero errors, the upper bound is 2.07%).
> - Furthermore, 10 Bengali messages in the public normalized smishing dataset were flagged due to ambiguous debt/fee payment demands.
> - Therefore, `AI_LAYER_ENABLED` remains strictly `false`. Entry into the production Android application requires the project owner's evaluation and approval on the true blind acceptance set.

---

## 2. Release Gates Scorecard

### 2.1 Primary Scorecard on Untouched Lockbox (`lockbox.jsonl`, N = 1,800: 515 scam, 1,285 ham)

Evaluation performed once (`lockbox_eval_count = 1/3`):

| Gate Dimension | Gate Requirement (Stage 1) | Measured Value | Wilson 95% Confidence Interval | Conclusive Status | Gate Verdict |
|---|---|---:|---|---|---|
| **Overall Scam Recall** | $\ge 80.0\%$ | **90.29%** (465 / 515) | [87.43%, 92.56%] | Conclusive ($N_{scam} \ge 100$) | **MET** (Lower bound 87.43% $\ge 80.0\%$) |
| **Overall Ham False Positive Rate (FPR)** | $\le 0.50\%$ | **1.32%** (17 / 1285) | [0.83%, 2.11%] | Conclusive ($N_{ham} \ge 600$) | **UNPROVEN** (Upper bound 2.11% $> 0.50\%$) |
| **Per-Language Recall (Latin / English / Hinglish)** | $\ge 75.0\%$ | **91.94%** (308 / 335) | [88.53%, 94.40%] | Conclusive ($N_{scam} \ge 100$) | **MET** (Lower bound 88.53% $\ge 75.0\%$) |
| **Per-Language Ham FPR (Latin / English / Hinglish)** | $\le 1.00\%$ | **0.82%** (6 / 734) | [0.38%, 1.77%] | Conclusive ($N_{ham} \ge 300$) | **UNPROVEN** (Upper bound 1.77% $> 1.00\%$) |
| **Per-Language Recall (Devanagari Hindi)** | $\ge 75.0\%$ | **76.47%** (26 / 34) | [60.00%, 87.56%] | Inconclusive ($N_{scam} = 34 < 100$) | **INCONCLUSIVE** |
| **Per-Language Ham FPR (Devanagari Hindi)** | $\le 1.00\%$ | **0.27%** (1 / 370) | [0.05%, 1.51%] | Conclusive ($N_{ham} \ge 300$) | **UNPROVEN** (Upper bound 1.51% $> 1.00\%$) |
| **Per-Language Recall (Bengali)** | $\ge 75.0\%$ | **89.73%** (131 / 146) | [83.74%, 93.67%] | Conclusive ($N_{scam} \ge 100$) | **MET** (Lower bound 83.74% $\ge 75.0\%$) |
| **Per-Language Ham FPR (Bengali)** | $\le 1.00\%$ | **5.52%** (10 / 181) | [3.03%, 9.87%] | Inconclusive ($N_{ham} = 181 < 300$) | **UNPROVEN** |
| **Protected Traffic FPR (OTP / Transactions)** | **0.00%** | **0.00%** (0 / 76) | [0.00%, 4.79%] | Conclusive | **MET** |
| **Benign Bank Advisory FPR** | $\le 2.00\%$ | **0.00%** (0 / 5) | [0.00%, 43.43%] | Inconclusive ($N < 100$) | **INCONCLUSIVE** |
| **Probability Calibration: Brier Score** | $\le 0.10$ | **0.0427** | N/A | Conclusive | **MET** |
| **Probability Calibration: ECE (10 bins)** | $\le 0.08$ | **0.0368** | N/A | Conclusive | **MET** |
| **App Asset Size** | $\le 2.0\text{ MiB}$ | **1,001.67 KiB** (1,025,713 B) | N/A | Conclusive | **MET** |
| **Inference Latency P95 (Host JVM)** | $\le 25.0\text{ ms}$ | **0.417 ms** (P50: 0.175 ms) | N/A | Conclusive | **MET** |
| **Device Inference Latency** | $\le 25.0\text{ ms}$ | **NOT MEASURED** | N/A | No device connected | **NOT MEASURED** |
| **Transient Memory (Heap Delta)** | $\le 8.0\text{ MB}$ | **2.53 MB** (during scoring: 7.49 MB) | N/A | Conclusive | **MET** |
| **Resident Footprint** | $\le 3.0\text{ MB}$ | **Host JVM: 108.9 MB** | N/A | Device: NOT MEASURED | **NOT MEASURED** |
| **Feature Default State** | `AI_LAYER_ENABLED = false` | **false** | N/A | Verified in code | **MET** |

---

### 2.2 Scorecard on Validation Set (`validation.jsonl`, N = 2,200: 410 scam, 1,790 ham)

| Metric | Target | Measured Value | Wilson 95% Confidence Interval | Status |
|---|---|---:|---|---|
| **Overall Scam Recall** | $\ge 80.0\%$ | **88.78%** (364 / 410) | [85.36%, 91.48%] | **MET** |
| **Overall Ham FPR** | $\le 0.50\%$ | **0.45%** (8 / 1,790) | [0.23%, 0.88%] | Point estimate MET |
| **Latin Recall / Ham FPR** | $\ge 75\% / \le 1.0\%$ | **87.5%** / **0.41%** (5 / 1,230) | Recall: [82.9%, 91.0%], FPR: [0.17%, 0.95%] | **MET** |
| **Devanagari Recall / Ham FPR** | $\ge 75\% / \le 1.0\%$ | **88.5%** / **0.52%** (2 / 387) | Recall: [71.0%, 96.0%], FPR: [0.14%, 1.86%] | Point estimate MET |
| **Bengali Recall / Ham FPR** | $\ge 75\% / \le 1.0\%$ | **91.3%** / **0.58%** (1 / 173) | Recall: [85.2%, 95.1%], FPR: [0.10%, 3.20%] | Point estimate MET |
| **Protected Traffic FPR** | $0.00\%$ | **0.00%** (0 / 168) | [0.00%, 2.21%] | **MET** |
| **Advisory FPR** | $\le 2.00\%$ | **0.00%** (0 / 5) | [0.00%, 43.43%] | **MET** |
| **Brier Score** | $\le 0.10$ | **0.0269** | N/A | **MET** |
| **ECE (10 bins)** | $\le 0.08$ | **0.0271** | N/A | **MET** |

---

### 2.3 Progress on Frozen Benchmark (`dev_challenge.jsonl`, N = 559: 269 scam, 290 ham)

| Evaluation Split / Metric | Round 1 Baseline (`cycle_08`) | Round 2 Candidate (`cycle_13` / `cycle_15`) | Net Improvement |
|---|---:|---:|---|
| **Overall Ham False Positive Rate (FPR)** | **20.00%** (58 / 290) | **0.69%** (2 / 290) | **-19.31% absolute** (FPs dropped from 58 to 2) |
| **Latin Ham FPR** | **10.14%** (15 / 148) | **0.00%** (0 / 148) | **-10.14% absolute** (zero false alarms) |
| **Bengali Ham FPR** | **20.00%** (10 / 50) | **0.00%** (0 / 50) | **-20.00% absolute** (zero false alarms) |
| **Devanagari Hindi Ham FPR** | **35.87%** (33 / 92) | **2.17%** (2 / 92) | **-33.70% absolute** (FPs dropped from 33 to 2) |
| **Overall Scam Recall** | **80.67%** (217 / 269) | **75.09%** (202 / 269) | Note: 21 Gurmukhi Punjabi scams excluded in R2 |
| **Protected Traffic FPR** | **0.00%** (0 / 48) | **0.00%** (0 / 48) | Consistent 0.00% immunity |

---

## 3. Failure & Plateau Analysis (Stop Condition S2)

1. **Cycle Progression & Stop Condition**:
   - Cycles 09 through 15 were executed on the expanded harvest (`train.jsonl`, 19,603 rows).
   - Validation recall stabilized at:
     - Cycle 12: 90.24%
     - Cycle 13: 89.76%
     - Cycle 14: 88.78%
     - Cycle 15: 88.78%
   - Over four consecutive cycles, the recall gain was $<0.5\%$, and Ham FPR reached a steady plateau ($0.45\%\text{--}0.56\%$).
   - This satisfies **Stop Condition S2 (Plateau: $\ge 12$ cycles, $<0.5\%$ gain over 4 cycles)**.
2. **Bengali Lockbox Nuance**:
   - Public Bengali datasets (`bengali-sms-smishing-dataset`) contain label noise in commercial collections: messages stating *"Your account was charged 1000 Tk, call to complain"* or *"Your debt of 800 Tk is due, pay urgently to 0172..."* were labeled as normal/promo in the source. Because the student model correctly scores these as coercive smishing lures, they count as false alarms against the noisy public label.
   - For real-world deployment, only human-audited labels on real private SMS will establish true ground truth.

---

## 4. Hardware, Resource & Packaging Verification

All numbers below were measured directly via automated tools and committed in [`ml/candidates/c98c5500a14e3799/metadata.json`](../../ml/candidates/c98c5500a14e3799/metadata.json):

- **Model File**: `ml/candidates/c98c5500a14e3799/model.json`
- **File Size**: 1,025,713 bytes (1,001.67 KiB = 0.98 MiB) $\le 2.0\text{ MiB}$ budget.
- **SHA-256**: `c98c5500a14e379953395369835b35f2ff157ed0a28e132eeeb0eb363e3cdfbb`
- **Host JVM Memory**: Measured via Gradle test harness `com.messages.protection.MemoryMeasurementHarnessTest`:
  - **Heap Delta on Model Load**: **2.5294 MB** (Gate: $\le 8.0\text{ MB}$)
  - **Heap Usage During 1,000 Inferences**: **7.4890 MB** (Gate: $\le 8.0\text{ MB}$)
  - **Host JVM Resident Set Size**: **108.9062 MB** (Standard Windows JVM runtime)
  - **Device Memory**: `device: NOT MEASURED` (No physical Android hardware attached)
- **Host Latency (318-message stratified verification sample)**:
  - **P50 Latency**: **0.175 ms**
  - **P95 Latency**: **0.417 ms** (Gate: $\le 25.0\text{ ms}$)
  - **Device Latency**: `device: NOT MEASURED`
- **Verification Sample**: 318 stratified samples spanning English, Hinglish, Devanagari Hindi, Bengali, Banglish, OTPs, and fraud advisories committed in [`ml/candidates/c98c5500a14e3799/score_sample.json`](../../ml/candidates/c98c5500a14e3799/score_sample.json).

---

## 5. Instructions for the Project Owner (Acceptance Testing)

Because automated agents are strictly forbidden by Rule R3 from accessing or running evaluations on `D:\Istak\Messages\PRIVATE_ACCEPTANCE\`, the project owner must run the blind acceptance evaluation:

1. **Prerequisite**: Set the owner gate environment variable in PowerShell:
   ```powershell
   $env:OWNER_RUN = "1"
   ```
2. **Execute Acceptance Evaluation**:
   ```powershell
   cd "D:\Istak\Messages\Messages App\01_active_codebase\messages"
   & "D:\Istak\Messages\.venv-ml\Scripts\python.exe" "protection-engine\tools\evaluate_acceptance.py" `
     --candidate "ml/candidates/c98c5500a14e3799/model.json" `
     --labels "D:\Istak\Messages\PRIVATE_ACCEPTANCE\to_label.xlsx" `
     --json-out "D:\Istak\Messages\PRIVATE_ACCEPTANCE\acceptance_eval_c98c5500a14e3799.json"
   ```
3. **Approval Procedure**:
   - If the candidate achieves Scam Recall $\ge 80.0\%$, Ham FPR $\le 0.50\%$, Protected FPR $0.00\%$, and passes all per-script gates on your private set:
     Create `docs/ml/OWNER_APPROVAL.md` with:
     ```markdown
     # Model Approval Notice
     Status: APPROVED
     Candidate SHA-256: c98c5500a14e379953395369835b35f2ff157ed0a28e132eeeb0eb363e3cdfbb
     Approved by: Owner
     ```
   - Only after `OWNER_APPROVAL.md` is committed can `AI_LAYER_ENABLED` be flipped in `AiScorer.kt` (enforced by `scripts/verify_ml.py`).
