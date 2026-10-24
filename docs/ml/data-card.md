# India Multilingual SMS Data Card: Bengali, Hindi, English

## 1. Overview & Purpose
This dataset card documents the curated, template-clustered, and deduplicated multi-source training, validation, and development challenge partitions for India-focused SMS scam and fraud classification.

The primary target is binary classification:
- **SCAM_FRAUD**: smishing, KYC expiration lures, bank impersonation, electricity disconnection threats, lottery/prize frauds, part-time job/Telegram tasks, digital arrest threats, loan app harassment.
- **NOT_SCAM**: legitimate bank transactions (debit/credit), OTP alerts, utility bills, delivery updates, DLT promotional messages, and personal messages.

## 2. In-Scope Languages and Scripts
The project covers three India-focused linguistic categories across native and transliterated scripts:
1. **Bengali**: Native Bengali script (`bn_beng`) and Romanized Banglish (`bn_latn`).
2. **Hindi**: Native Devanagari script (`hi_deva`) and Romanized Hinglish (`hi_latn`).
3. **English**: Indian English in Latin script (`en_latin`).

All other languages (Punjabi, Tamil, Telugu, Kannada, Malayalam, Gujarati, Marathi, Odia, Assamese) are explicitly **out of scope** and held only as diagnostic stress suites.

## 3. Data Sources and Licensing
Every dataset was audited against official repository cards and license statements per Rule R6:

| Dataset Name | Source Repository | License | Declared Use | Primary Languages | Round-1 Claimed | Verified Round-1 Used |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **scam-ham-india-14-languages** | Hugging Face (`external/scam-ham-india-14-languages`) | MIT | `fit` | Bengali, Hindi, Hinglish, English | 4,000 | 2,897 (2,312 tr / 585 val) |
| **indian-scam-sms-synthetic-audited** | Hugging Face (`external/indian-scam-sms-synthetic-audited`) | CC-BY-4.0 | `fit` | Hinglish, English, Hindi, Banglish | 1,580 | 631 (631 tr / 0 val) |
| **scam-ham-india-anmol** | Hugging Face (`anmolshrivastav/scam-ham-india`) | Apache-2.0 | `fit` | English | 2,272 | 2,186 (1,734 tr / 452 val) |
| **india-spam-sms-junioralive** | GitHub (`junioralive/india-spam-sms-classification`) | MIT | `fit` | English | 2,267 | 0 (collapsed by anmol dedup) |
| **cloveai-india-spam-sms** | Hugging Face (`CloveAI/india-spam-sms`) | MIT | `fit` (clean ham only) | English | 14,059 | 6,481 (5,213 tr / 1,268 val) |
| **scamshield-corpus** | Hugging Face (`scamshield-dataset`) | MIT | `fit` | English, Hindi, Hinglish | 69,286 | 0 (omitted from round-1 builder) |
| **bengali-sms-smishing-dataset** | Hugging Face (`shariul-islam/bengali-sms-smishing-dataset`) | MIT | `diagnostic` | Bengali, Banglish | 7,005 | 0 (held diagnostic only) |
| **trilingual-fraud-consumer-protection-v2** | Hugging Face (`karanverma19/trilingual_fraud_consumer_protection_v2`) | MIT | `dev` | English, Hindi, Punjabi | 159 | 159 (dev challenge only) |
| **indic_test_suite_clean** | Hugging Face (`external/scam-ham-india-14-languages`) | MIT | `dev` | Bengali, Hindi, Hinglish, English | 400 | 400 (dev challenge only) |

## 4. End-to-End Data Flow Pipeline (Phase 0 Defect (g) Resolution)
The following table traces every dataset through the pipeline stages: Raw -> Language Filter -> Exact Normalization Dedup -> Template Cluster Collapse -> Label Ambiguity Filtering -> Partition Assignment. All values are committed in `docs/ml/data-flow.json`.

| Dataset | Raw Rows | After Lang/Script Filter | After Exact Dedup | After Template Dedup | After Ambiguity Drop | Assigned Train | Assigned Validation | Assigned Dev | Discard / Filter Reason |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **scam_ham_india_14_languages** | 14,000 | 4,000 | 3,912 | 3,350 | 3,350 | 2,312 | 585 | 0 | 10,000 rows dropped (non-target regional languages); 453 held in test suite. |
| **indian_scam_sms_synthetic_audited** | 1,580 | 1,580 | 1,540 | 902 | 902 | 631 | 0 | 0 | Synthetic cap rule (<= 30% of train); train-only to avoid val contamination; remainder capped. |
| **scam_ham_india_anmol** | 2,272 | 2,272 | 2,240 | 2,186 | 2,186 | 1,734 | 452 | 0 | Deduplicated against exact and template collisions. |
| **india_spam_sms_junioralive** | 2,267 | 2,267 | 214 | 0 | 0 | 0 | 0 | 0 | 2,053 exact normalized duplicates of Anmol dataset; remaining 214 collapsed into Anmol template clusters. |
| **cloveai_india_spam** | 20,010 | 20,010 | 15,840 | 6,481 | 6,481 | 5,213 | 1,268 | 0 | Label 1 (promotional/commercial spam) dropped as ambiguous; only label 0 clean ham loaded; template collapsed. |
| **scamshield_corpus** | 69,286 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **Defect (g) Root Cause**: Manifest declared 69,286 rows as `fit`, but round-1 `build_corpus.py` omitted ingestion code. |
| **bengali_sms_smishing_dataset** | 7,005 | 7,005 | 6,820 | 5,410 | 5,410 | 0 | 0 | 0 | Declared diagnostic only in round 1 due to Bangladesh (+880) context. |
| **trilingual_challenge_v2** | 159 | 159 | 159 | 159 | 159 | 0 | 0 | 159 | Frozen development challenge set. |
| **indic_test_suite_clean** | 400 | 400 | 400 | 400 | 400 | 0 | 0 | 400 | Frozen multi-language development challenge set (100 per language). |
| **Total Across Corpus** | **116,979** | **37,693** | **31,125** | **18,888** | **18,888** | **9,890** | **2,305** | **559** | Verified zero unexplained gaps. |

### Analysis of ScamShield Corpus Discard (Phase 0 Defect (g))
The 69,286 rows in `scamshield-dataset/train.jsonl` (and 85,680 total across splits) consist of:
- `Smishing_Dataset`: 55,475 rows (64.7%) — international/Singapore/UK/US SMS containing Singapore MRT references, UK postcodes (`yodel.co.uk`), US dollar lotteries.
- `UCI_SMS_Spam`: 4,175 rows (4.9%) — classic international SMS spam collection.
- `Synthetic_Tier_C`: 3,720 rows (4.3%) — synthetic templates.
- `Kaggle_Hindi_Merged`: 3,652 rows (4.3%) — real Hindi Devanagari messages.
- `Indian_Telecom_SMS`: 1,627 rows (1.9%) — real Indian English telecom messages.
- `ysangam/Indian_Cyber_Scam_Hinglish`: 597 rows (0.7%) — real Hinglish cyber scam messages.
- `Benchmark_Ground_Truth`: 40 rows (0.05%).

**Conclusion**: The complete omission in round 1 was unintended due to an incomplete `load_sources()` function in `build_corpus.py`. However, importing all 69,286 rows indiscriminately would have contaminated the Indian SMS classifier with 59,650 global non-Indian messages. The proper engineering solution for Round 2 is to ingest only the verified India-relevant partitions (3,652 Hindi, 1,627 Indian Telecom English, 597 Hinglish = 5,876 unique rows), which provides genuine India-relevant volume without foreign distribution drift.

## 5. Partition Structure & Leakage Prevention
Partitions are constructed deterministically by `protection-engine/tools/build_corpus.py` (seed: 20261020).
Rows are grouped by `(source_dataset, template_skeleton)` before partitioning.
All template variations, near-duplicates, and translations stay strictly within a single partition.

### Partition Sizes (Measured by `verify_split_leakage.py`):
- **Train partition**: 9,890 unique rows (9,870 unique template families).
- **Validation partition**: 2,305 unique rows (2,292 unique template families, strictly real data).
- **Dev-challenge partition**: 559 unique rows (559 unique template families).

### Cross-Partition Leakage Verification:
- `train_vs_validation`: exact = 0, normalized = 0, template overlap = 0 (**PASS**)
- `train_vs_stress`: exact = 0, normalized = 0, template overlap = 0 (**PASS**)
- `validation_vs_stress`: exact = 0, normalized = 0, template overlap = 0 (**PASS**)
- Internal label conflicts: 0 (**PASS**)

## 6. Synthetic Data Ratio Compliance
Per project rules, synthetic data is restricted exclusively to the training partition and strictly capped at $\le 30\%$ per language bucket:
- `hi_latn`: 653 real, 279 synthetic (29.9% synthetic) — compliant ($\le 30.0\%$)
- `en_latin`: 7,434 real, 234 synthetic (3.1% synthetic) — compliant ($\le 30.0\%$)
- `bn_beng`: 683 real, 0 synthetic (0.0% synthetic) — compliant ($\le 30.0\%$)
- `hi_deva`: 489 real, 118 synthetic (19.4% synthetic) — compliant ($\le 30.0\%$)
- `validation`: 2,305 rows (0.0% synthetic) — strictly real data.

## 7. Known Biases and Limitations
1. **Geographic focus**: Curated specifically for Indian telecom sender headers, Indian bank brands (SBI, HDFC, ICICI, PNB), UPI, and Indian statutory schemes (Aadhaar, PAN, PM Yojana).
2. **Bangladesh smishing data**: `bengali-sms-smishing-dataset` contains Bangladeshi telecom and bank mentions (+880, Janata Bank); it is marked `diagnostic` only and excluded from model fitting to prevent domestic distribution shift.
3. **Out-of-scope languages**: Regional Dravidian languages (Tamil, Telugu, Kannada, Malayalam) and Punjabi are omitted from the primary classifier and must be routed by script detection.
4. **Hard negative protection**: Bank transaction debits, credits, and OTP messages are protected by rule-based pre-filters; model training explicitly includes hard negative bank advisories ("Never share OTP") to avoid false alarms.
