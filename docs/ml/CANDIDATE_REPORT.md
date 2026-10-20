# Stage 1 Candidate Evaluation Report: Cycle 07 Multilingual Advisory Linear Expert System

- **Candidate SHA-256**: `9a4ddf6934c0bab5fb7a002d08cbef01e5661ea01efbe80d774e8cd113f9e961`
- **Candidate Prefix**: `9a4ddf6934c0bab5`
- **Evaluation Date**: 2026-10-20
- **Governing Criteria**: [Scope and Numerical Acceptance Criteria](file:///docs/ml/scope-and-acceptance-criteria.md)
- **Dataset Manifest SHA-256**: `76c8aedd77853cf606c3a4998847a4613e5c1e41f80b812033f4f65fa2d3e2f0`
- **Release Recommendation**: **`DO NOT MERGE / DO NOT ENABLE`**

---

## 1. Executive Summary & Gate Verdict

> [!CAUTION]
> **VERDICT: REJECTED FOR PRODUCTION RELEASE (FAILED STAGE 1 RELEASE GATES)**
> 
> The best candidate developed across autonomous research cycles 01 through 07 is the **Calibrated Multilingual Advisory Linear Expert System** (Cycle 07). While it dramatically reduces false positives compared to the baseline Naive Bayes model (FPR reduced from **77.80%** to **20.00%**) and satisfies overall scam recall (**80.30%**), per-language recall (English **81.76%**, Bengali **80.00%**, Devanagari Hindi **77.46%**), protected traffic immunity (**0.00%**), asset size (**971.8 KB**), and P95 latency (**0.81 ms**), it **FAILS** the primary safety release gate:
> - **Overall Ham False Positive Rate (FPR)**: **20.00%** (58 false alarms out of 290 non-scam challenge messages) vs. gate **<= 0.50%**.
> - In an inbox with 1,000 legitimate incoming messages, this model would falsely divert ~200 legitimate notifications into the Review folder. This violates the core repository principle of safe messaging protection.

---

## 2. Release Gates Scorecard (Failing Gates First)

All numbers are measured on the development challenge split `dev_challenge.jsonl` ($N = 559$: 269 scams, 290 hams) and committed in [`docs/ml/cycles/cycle_07_multilingual_advisory.json`](file:///docs/ml/cycles/cycle_07_multilingual_advisory.json). 95% confidence intervals are computed using the Wilson score interval with continuity correction.

| Gate Dimension | Gate Requirement (Stage 1) | Measured Value | Wilson 95% Confidence Interval | Status |
|---|---|---|---|---|
| **Ham False Positive Rate (FPR)** | **<= 0.50%** | **20.00%** (58 / 290) | [15.80%, 24.98%] | **FAIL** |
| **Per-Language Ham FPR (Latin / English / Hinglish)** | **<= 1.00%** | **10.14%** (15 / 148) | [6.24%, 16.05%] | **FAIL** |
| **Per-Language Ham FPR (Devanagari Hindi)** | **<= 1.00%** | **35.87%** (33 / 92) | [26.82%, 46.05%] | **FAIL** |
| **Per-Language Ham FPR (Bengali)** | **<= 1.00%** | **20.00%** (10 / 50) | [11.24%, 33.04%] | **FAIL** |
| **Probability Calibration: Brier Score** | **<= 0.10** | **0.1432** | N/A | **FAIL** |
| **Probability Calibration: ECE** | **<= 0.08** | **0.0917** | N/A | **FAIL** |
| **Overall Scam Recall** | **>= 80.0%** | **80.30%** (216 / 269) | [75.13%, 84.61%] | **PASS** |
| **Per-Language Recall (Latin / English / Hinglish)** | **>= 75.0%** | **81.76%** (121 / 148) | [74.76%, 87.15%] | **PASS** |
| **Per-Language Recall (Devanagari Hindi)** | **>= 75.0%** | **77.46%** (55 / 71) | [66.48%, 85.63%] | **PASS** |
| **Per-Language Recall (Bengali)** | **>= 75.0%** | **80.00%** (40 / 50) | [66.96%, 88.76%] | **PASS** |
| **Protected Traffic FPR (OTP / Transactions)** | **0.00%** | **0.00%** (0 / 48) | [0.00%, 7.41%] | **PASS** |
| **App Asset Size** | **<= 2.0 MiB** | **971.8 KB** (0.95 MiB) | N/A | **PASS** |
| **Inference Latency P95** | **<= 25.0 ms** | **0.81 ms** (P50: 0.52 ms) | N/A | **PASS** |
| **Transient Memory (Heap)** | **<= 8.0 MB** | **< 2.5 MB** | N/A | **PASS** |
| **Resident Footprint** | **<= 3.0 MB** | **~1.1 MB** | N/A | **PASS** |
| **Feature Default State** | `AI_LAYER_ENABLED = false` | **false** | N/A | **PASS** |

---

## 3. Failure Analysis & Root Cause

1. **Advisory / Hard Negative Vocabulary Overlap**:
   - Legitimate public service awareness messages (e.g. *"Bank never asks for OTP"*, *"Do not trust agents promising guaranteed visas without proof"*) heavily feature the exact n-grams and vocabulary that characterize scam messages (*"OTP"*, *"visa"*, *"guaranteed"*, *"payment"*, *"agent"*).
   - Although the multilingual advisory filter introduced in Cycle 07 eliminated 30 false alarms on standard advisory templates, informal and novel phrasing in Devanagari Hindi (*"पासपोर्ट कॉपी या पैसे केवल इसलिए न भेजें..."*) still triggers the linear expert when the syntax deviates from known advisory regexes.

2. **Indic Script Ham Data Scarcity**:
   - In the training set (`train.jsonl`), Devanagari had only 344 ham examples and Bengali had 402 ham examples, compared to 7,192 English/Latin ham examples.
   - Consequently, the linear classifier for Devanagari assigns positive weights to ordinary Hindi words (*"गए"*, *"को"*, *"रहा"*, *"नहीं"*), causing non-scam personal messages to exceed the detection threshold unless gated by strict vector rules.

3. **High Challenge Set Difficulty**:
   - The frozen development challenge (`dev_challenge.jsonl`) consists of adversarial and tricky samples: bank advisories, ambiguous promotional messages, transliterated Hinglish/Banglish SMS, and utility notices. On standard random validation splits, the model achieves **98.7% recall** at **0.06% FPR**, but on out-of-distribution adversarial SMS, the FPR rises to **20.00%**.

---

## 4. Candidate Architecture & Implementation Details

- **Model Type**: Multi-Script Calibrated L2-Regularized Linear Classifier with Actionable Vector Gating and Multilingual Advisory Pre-filters.
- **Pre-Filters**:
  - Deterministic Protected-Message Filter: Identifies OTPs, bank debit/credit notices, account balance alerts across English, Hindi, and Bengali (`RE_PROT`). Always forces score to `-100.0` (FPR strictly 0.00%).
  - Multilingual Transliterated Advisory Filter: Identifies consumer protection notices and warning idioms (`RE_ADVISORY`).
- **Feature Extraction**: Sublinear TF-IDF over character word-boundary n-grams ($n \in [2, 5]$), capped at 5,000 features per script.
- **Runtime Representation**: Stored as a pure JSON lookup table (`model.json`, 971.8 KB) containing sparse weights and IDF multipliers. Requires no external C++ / PyTorch runtime and evaluates via simple dot-product and sigmoid activation.
- **Parity Fixtures**: 50 verified input-output samples across Latin, Devanagari, and Bengali scripts committed in [`ml/candidates/9a4ddf6934c0bab5/score_sample.json`](file:///ml/candidates/9a4ddf6934c0bab5/score_sample.json).

---

## 5. Potential Android Changes (Documentary Only)

Because this candidate failed the Stage 1 release gates, **no app code or Android runtime files have been altered**. `AI_LAYER_ENABLED` remains strictly `false`.

If a future candidate clears all Stage 1 release gates, the following Android implementation changes in `protection-engine/` would be required:
1. **Model Loader**: Update `AiScorer.kt` to parse `model.json` schema containing `experts` (`intercept`, `weights`, `idf`) and `configs` (`th_vec`, `th_no`).
2. **Tokenizer**: Port the sublinear TF-IDF character word-boundary ($2\text{--}5$) n-gram extractor to Kotlin.
3. **Parity Tests**: Load `score_sample.json` in `AiScorerTest.kt` and verify that Android JVM output matches Python predictions within $1\times 10^{-4}$ tolerance.
