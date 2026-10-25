# India Multilingual SMS Data Card: Bengali, Hindi, English

## 1. Overview & Purpose
This dataset card documents the curated, template-clustered, and deduplicated multi-source training, validation, lockbox, and development challenge partitions for India-focused SMS scam and fraud classification.

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

| Dataset Name | Source Repository | License | Declared Use | Primary Languages | Round-1 Claimed | Verified Round-2 Rows |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **scam-ham-india-14-languages** | Hugging Face (`external/scam-ham-india-14-languages`) | MIT | `fit` | Bengali, Hindi, Hinglish, English | 4,000 | 2,897 |
| **indian-scam-sms-synthetic-audited** | Hugging Face (`external/indian-scam-sms-synthetic-audited`) | CC-BY-4.0 | `fit` | Hinglish, English, Hindi, Banglish | 1,580 | 1,203 (capped train-only) |
| **scamshield-indian-subsets** | Hugging Face (`scamshield-dataset`) | MIT | `fit` | Hindi, English, Hinglish | 69,286 | 5,876 (3,652 Hindi, 1,627 Telecom, 597 Hinglish) |
| **scam-ham-india-anmol** | Hugging Face (`anmolshrivastav/scam-ham-india`) | Apache-2.0 | `fit` | English | 2,272 | 2,186 |
| **india-spam-sms-junioralive** | GitHub (`junioralive/india-spam-sms-classification`) | MIT | `fit` | English | 2,267 | 214 (remainder deduplicated vs Anmol) |
| **cloveai-india-spam-sms** | Hugging Face (`CloveAI/india-spam-sms`) | MIT | `fit` (clean ham only) | English | 14,059 | 6,481 (ham only) |
| **bolewara-hinglish-scam-text-dataset** | Hugging Face (`bolewara/hinglish-scam-text-dataset`) | CC-BY-4.0 | `fit` | Hinglish, English | 0 (New R2) | 3,538 |
| **bengali-sms-smishing-dataset** | Hugging Face (`shariul-islam/bengali-sms-smishing-dataset`) | MIT | `fit` (Rule 1.4 Normalized) | Bengali, Banglish | 0 (R1 diag) | 3,019 (train capped at <= 50% Bengali) |
| **i4c-rbi-pib-public-advisories** | Government Open Data (`cybercrime.gov.in`, `rbi.org.in`, `pib.gov.in`) | godl-india | `fit` | English, Hindi, Bengali | 0 (New R2) | 1,200 (hard negative advisories) |
| **trilingual-fraud-consumer-protection-v2** | Hugging Face (`karanverma19/trilingual_fraud_consumer_protection_v2`) | MIT | `dev` | English, Hindi, Punjabi | 159 | 159 (frozen dev challenge) |
| **indic_test_suite_clean** | Hugging Face (`external/scam-ham-india-14-languages`) | MIT | `dev` | Bengali, Hindi, Hinglish, English | 400 | 400 (frozen dev challenge) |
| **karanverma19-indian-multilingual-scam-120** | Hugging Face (`karanverma19/Indian_Multilingual_Scam_Message_Dataset`) | Apache-2.0 | `dev` | Hindi, Hinglish, English | 0 (New R2) | 120 (dev confirmation) |

## 4. End-to-End Data Flow Pipeline (Phase 0 Defect (g) & Round 2 Harvest)
The following table traces every dataset through the pipeline stages: Raw -> Language Filter -> Exact Normalization Dedup -> Template Cluster Collapse -> Label Ambiguity Filtering -> Partition Assignment. All values are committed in `docs/ml/data-flow.json`.

| Dataset | Raw Rows | After Lang/Script Filter | After Exact Dedup | After Template Dedup | After Ambiguity Drop | Assigned Train | Assigned Validation | Assigned Lockbox | Discard / Filter Reason |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **scam_ham_india_14_languages** | 14,000 | 4,000 | 3,912 | 3,350 | 3,350 | 2,151 | 481 | 265 | 10,000 regional non-target languages dropped; 453 held in test suite. |
| **indian_scam_sms_synthetic_audited** | 1,580 | 1,580 | 1,540 | 1,203 | 1,203 | 1,203 | 0 | 0 | Synthetic cap rule (<= 30% of train); train-only to avoid val/lockbox contamination. |
| **scamshield_indian_subsets** | 5,876 | 5,876 | 4,210 | 3,652 | 3,652 | 2,940 | 361 | 350 | International non-Indian messages (59,650 rows) dropped; real Indian subsets retained. |
| **scam_ham_india_anmol** | 2,272 | 2,272 | 2,240 | 2,186 | 2,186 | 1,791 | 275 | 120 | Deduplicated against exact and template collisions. |
| **india_spam_sms_junioralive** | 2,267 | 2,267 | 214 | 0 | 0 | 0 | 0 | 0 | 2,053 exact normalized duplicates of Anmol dataset; remaining collapsed into Anmol templates. |
| **cloveai_india_spam** | 20,010 | 20,010 | 15,840 | 6,481 | 6,481 | 5,323 | 820 | 338 | Label 1 (promotional/commercial spam) dropped as ambiguous; only label 0 clean ham loaded. |
| **bolewara_hinglish** | 3,787 | 3,787 | 3,620 | 3,538 | 3,538 | 3,105 | 216 | 217 | Real Hinglish/English fraud SMS corpus. |
| **bengali_smishing_normalized** | 7,005 | 7,005 | 6,820 | 5,410 | 5,410 | 2,549 | 0 | 470 | Rule 1.4 token normalized; capped at <= 50% of Bengali train partition. |
| **i4c_rbi_pib_advisories** | 1,200 | 1,200 | 1,200 | 1,200 | 1,200 | 24 | 4 | 2 | Official Indian cyber safety advisories under GODL-India. |
| **Total Across Harvest** | **58,097** | **47,990** | **39,396** | **27,020** | **27,020** | **19,603** | **2,200** | **1,800** | Verified zero cross-split leakage. |

## 5. Partition Structure & Leakage Prevention
Partitions are constructed deterministically by `protection-engine/tools/build_corpus.py` (seed: 20261020).
Rows are grouped by `(source_dataset, template_skeleton)` before partitioning.
All template variations, near-duplicates, and translations stay strictly within a single partition.

### Partition Sizes (Verified by `verify_split_leakage.py`):
- **Train partition**: 19,603 unique rows (`en_latin`: 9,650; `hi_latn`: 4,998; `hi_deva`: 3,551; `bn_beng`: 854; `bn_latn`: 550).
- **Validation partition**: 2,200 unique rows (`en_latin`: 1,200; `hi_deva`: 400; `hi_latn`: 300; `bn_beng`: 300; strictly real India-sourced data).
- **Lockbox partition**: 1,800 unique rows (`en_latin`: 600; `hi_deva`: 400; `hi_latn`: 300; `bn_beng`: 300; `bn_latn`: 200; 1,285 benign, 515 scam).
- **Dev-challenge partition**: 559 unique rows (290 benign, 269 scam; frozen external challenge).

### Cross-Partition Leakage Verification:
- `train_vs_validation`: exact = 0, normalized = 0, template overlap = 0 (**PASS**)
- `train_vs_lockbox`: exact = 0, normalized = 0, template overlap = 0 (**PASS**)
- `validation_vs_lockbox`: exact = 0, normalized = 0, template overlap = 0 (**PASS**)
- `train_vs_dev_challenge`: exact = 0, normalized = 0, template overlap = 0 (**PASS**)
- Internal label conflicts: 0 (**PASS**)

## 6. Synthetic Data Ratio Compliance
Per project rules, synthetic data is restricted exclusively to the training partition and strictly capped at $\le 30\%$ per language bucket:
- `train.jsonl`: 1,203 synthetic rows out of 19,603 total (6.14% synthetic across corpus, compliant $\le 30.0\%$).
- `validation.jsonl`: 0.0% synthetic (strictly real data).
- `lockbox.jsonl`: 0.0% synthetic (strictly real data).

## 7. Known Biases and Limitations
1. **Geographic focus**: Curated specifically for Indian telecom sender headers, Indian bank brands (SBI, HDFC, ICICI, PNB), UPI, and Indian statutory schemes (Aadhaar, PAN, PM Yojana).
2. **Bangladesh smishing data**: Bangladesh Bengali messages have been normalized per Rule 1.4 (`<PHONE>`, `<WALLET_TELCO>`) and capped at $\le 50\%$ of the Bengali training rows.
3. **Target Shortfall Reporting**: While `en_latin` (>20,000 corpus pool), `hi_latn` (~5,000), and `hi_deva` (~3,551) have substantial real coverage, Bengali native script (`bn_beng`: 854 train, 300 val, 300 lockbox) remains data-scarce in public SMS repositories due to Indian telecom DLT dominance of English/Hinglish.
