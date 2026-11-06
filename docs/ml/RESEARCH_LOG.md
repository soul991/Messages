# Research Log: India Multilingual SMS Classifier Optimization

This document provides a chronological record of all research cycles executed during the India-focused Bengali + English + Hindi SMS classifier optimization loop on branch `ml/india-3lang-loop`.

---

## 1. Research Summary Table

| Cycle | Architecture / Hypothesis | Key Changes | Split / Evaluation | Scam Recall | Ham FPR | Protected FPR | Decision | Commit |
|:---:|---|---|---|:---:|:---:|:---:|:---:|:---:|
| **00** | Character n-gram Naive Bayes Baseline | Production shipped baseline (13,499 n-grams) | Frozen 159-row challenge | 82.60% | 77.80% | 71.43% | **Reference** | `7129d92` |
| **01** | Architecture Benchmarking on Clean Splits | Compared Rules, Naive Bayes, and TF-IDF Logistic Regression | Deduplicated 559-row dev challenge | 92.57% (LR) | 35.17% (LR) | 14.29% (LR) | **Reference** | `3abb5ef` |
| **02** | Multi-Script Routed Experts | Dedicated independent n-gram tables per script (Latin, Devanagari, Bengali) | Deduplicated 559-row dev challenge | 86.99% | 41.03% | 0.00% | **Keep** | `e7726e1` |
| **03** | Feature Enrichment | Domain indicators: URL presence, phone numbers, threat words | Deduplicated 559-row dev challenge | 86.99% | 41.03% | 0.00% | **Keep** | `6e59186` |
| **04** | Actionable Vector & Advisory Discounting | Strict vector gating (URL/phone required for high score) and bank advisory downweighting | Deduplicated 559-row dev challenge | 85.50% | 38.28% | 0.00% | **Keep** | `030dc3e` |
| **05** | Strict Margin & Multi-Gram Activation | Required log-odds margin (+2.5) and >= 2 distinct scam n-gram matches | Deduplicated 559-row dev challenge | 85.50% | 37.59% | 0.00% | **Keep** | `6c6f757` |
| **06** | Calibrated Multi-Script Linear Experts | L2-regularized logistic regression over subword character n-grams with Platt calibration | Deduplicated 559-row dev challenge | 81.04% | 30.34% | 0.00% | **Keep** | `94dce4e` |
| **07** | Multilingual Advisory Syntactic Pre-filter | Expanded regex pre-filter for Hindi/Hinglish/Bengali consumer advisories + linear experts | Deduplicated 559-row dev challenge | 80.30% | 20.00% | 0.00% | **Best Candidate** | `f725a72` |

---

## 2. Detailed Cycle Trajectory

### Cycle 00: Initial Baseline Verification
- **Hypothesis**: Re-verify the shipped Naive Bayes baseline on the legacy 159-row external challenge to confirm reproducibility.
- **Data**: `docs/ml/external-challenge-report.json` (159 rows).
- **Results**: Recall: 82.60%, Ham FPR: 77.80%. Matches prior documented baseline exactly.
- **Compute**: Local CPU, < 1 second.
- **Artifact**: `docs/ml/cycles/cycle_00_baseline.json` (`7129d92`).

### Cycle 01: Baseline Exploration on Clean Splits
- **Hypothesis**: Benchmark four standard architectures on clean, deduplicated, template-clustered splits to measure initial separability.
- **Data**: `train.jsonl` (9,890 rows), `dev_challenge.jsonl` (559 rows).
- **Results**:
  - Deterministic Rules: Recall 0.37%, FPR 0.00%
  - Naive Bayes: Recall 71.75%, FPR 14.14% (Brier 0.3811)
  - TF-IDF Logistic Regression: Recall 92.57%, FPR 35.17% (Brier 0.1367, ECE 0.0782)
- **Artifact**: `docs/ml/cycles/cycle_01_baselines.json` (`3abb5ef`).

### Cycle 02: Script-Routed Expert Models
- **Hypothesis**: Splitting n-gram vocabulary into script-dedicated experts (Latin, Devanagari, Bengali) eliminates cross-script lexical interference and log-odds dilution.
- **Data**: `train.jsonl` (split by script router).
- **Results**: Recall: 86.99%, FPR: 41.03%, Protected Traffic FPR: 0.00%.
- **Artifact**: `docs/ml/cycles/cycle_02_routed_experts.json` (`e7726e1`).

### Cycle 03: Actionable Vector Feature Enrichment
- **Hypothesis**: Explicitly rewarding messages containing URLs, APK extensions, or telephone numbers while checking for urgency tokens improves separation.
- **Results**: Recall: 86.99%, FPR: 41.03%, Protected Traffic FPR: 0.00%.
- **Artifact**: `docs/ml/cycles/cycle_03_features.json` (`6e59186`).

### Cycle 04: Vector Gating & Advisory Discounting
- **Hypothesis**: Discounting scores on messages lacking an actionable contact vector and adding negative weight for bank fraud awareness notices suppresses false alarms.
- **Results**: Recall: 85.50%, FPR: 38.28% (2.75% absolute FPR reduction), Protected Traffic FPR: 0.00%.
- **Artifact**: `docs/ml/cycles/cycle_04_advisory_discounting.json` (`030dc3e`).

### Cycle 05: Strict Log-Odds Margin & Multi-Gram Activation
- **Hypothesis**: Requiring >= 2 distinct positive n-gram activations and a +2.5 log-odds margin prevents isolated subword false triggers.
- **Results**: Recall: 85.50%, FPR: 37.59% (0.69% absolute FPR reduction), Protected Traffic FPR: 0.00%.
- **Artifact**: `docs/ml/cycles/cycle_05_strict_margin.json` (`6c6f757`).

### Cycle 06: Calibrated Multi-Script Linear Expert System
- **Hypothesis**: Replacing Naive Bayes with jointly optimized L2-regularized logistic regression over subword character n-grams eliminates conditional independence distortion.
- **Results**: Recall: 81.04%, FPR: 30.34% (7.25% absolute FPR reduction), Protected Traffic FPR: 0.00%, P95 Latency: 0.785 ms.
- **Artifact**: `docs/ml/cycles/cycle_06_calibrated_linear.json` (`94dce4e`).

### Cycle 07: Multilingual Advisory Syntactic Pre-filter
- **Hypothesis**: Hard negatives in Indian SMS corpora frequently consist of public safety warnings phrased in Hindi, Hinglish, or Bengali idioms (e.g. *'fraud pattern'*, *'bina verification'*, *'sirf official'*, *'dawa bharosemand nahi'*). Adding a multilingual advisory detector eliminates false positives on legitimate educational notices.
- **Results**: Recall: 80.30%, FPR: 20.00% (10.34% absolute FPR reduction), Protected Traffic FPR: 0.00%, P95 Latency: 0.81 ms.
- **Artifact**: `docs/ml/cycles/cycle_07_multilingual_advisory.json` (`f725a72`).

---

## 3. Conclusions & Recommendation

1. **Safety First**: Although significant progress was achieved in reducing the baseline false positive rate from **77.80% down to 20.00%** while keeping recall above the 80.0% threshold, the model **remains far above the Stage 1 safety ceiling (0.50% FPR)**.
2. **Recommendation**: The model **must not be merged to production or enabled**. `AI_LAYER_ENABLED` remains `false`.
3. **Future Research Directions**:
   - Acquire substantial verified, benign domestic Indian SMS corpora in Devanagari Hindi and Bengali (e-commerce orders, delivery confirmations, personal conversations).
   - Explore on-device distilled transformer representations (e.g. quantization of IndicBERTv2 or mobile-optimized embedding projection) to capture contextual semantics beyond subword n-grams.
