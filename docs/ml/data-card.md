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

| Dataset Name | Source Repository | License | Declared Use | Primary Languages | Rows Used |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **scam-ham-india-14-languages** | Hugging Face (`external/scam-ham-india-14-languages`) | MIT | `fit` | Bengali, Hindi, Hinglish, English | 4,000 |
| **indian-scam-sms-synthetic-audited** | Hugging Face (`external/indian-scam-sms-synthetic-audited`) | CC-BY-4.0 | `fit` | Hinglish, English, Hindi, Banglish | 1,580 |
| **scam-ham-india-anmol** | Hugging Face (`anmolshrivastav/scam-ham-india`) | Apache-2.0 | `fit` | English | 2,272 |
| **india-spam-sms-junioralive** | GitHub (`junioralive/india-spam-sms-classification`) | MIT | `fit` | English | 2,267 |
| **cloveai-india-spam-sms** | Hugging Face (`CloveAI/india-spam-sms`) | MIT | `fit` (clean ham only) | English | 14,059 |
| **scamshield-corpus** | Hugging Face (`scamshield-dataset`) | MIT | `fit` | English, Hindi, Hinglish | 69,286 |
| **trilingual-fraud-consumer-protection-v2** | Hugging Face (`karanverma19/trilingual_fraud_consumer_protection_v2`) | MIT | `dev` | English, Hindi, Punjabi | 159 |
| **indic_test_suite_clean** | Hugging Face (`external/scam-ham-india-14-languages`) | MIT | `dev` | Bengali, Hindi, Hinglish, English | 400 |
| **bengali-sms-smishing-dataset** | Hugging Face (`shariul-islam/bengali-sms-smishing-dataset`) | MIT | `diagnostic` | Bengali, Banglish | 7,005 |

## 4. Partition Structure & Leakage Prevention
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

## 5. Synthetic Data Ratio Compliance
Per project rules, synthetic data is restricted exclusively to the training partition and strictly capped at $\le 30\%$ per language bucket:
- `hi_latn`: 653 real, 279 synthetic (29.9% synthetic) — compliant ($\le 30.0\%$)
- `en_latin`: 7,434 real, 234 synthetic (3.1% synthetic) — compliant ($\le 30.0\%$)
- `bn_beng`: 683 real, 0 synthetic (0.0% synthetic) — compliant ($\le 30.0\%$)
- `hi_deva`: 489 real, 118 synthetic (19.4% synthetic) — compliant ($\le 30.0\%$)
- `validation`: 2,305 rows (0.0% synthetic) — strictly real data.

## 6. Known Biases and Limitations
1. **Geographic focus**: Curated specifically for Indian telecom sender headers, Indian bank brands (SBI, HDFC, ICICI, PNB), UPI, and Indian statutory schemes (Aadhaar, PAN, PM Yojana).
2. **Bangladesh smishing data**: `bengali-sms-smishing-dataset` contains Bangladeshi telecom and bank mentions (+880, Janata Bank); it is marked `diagnostic` only and excluded from model fitting to prevent domestic distribution shift.
3. **Out-of-scope languages**: Regional Dravidian languages (Tamil, Telugu, Kannada, Malayalam) and Punjabi are omitted from the primary classifier and must be routed by script detection.
4. **Hard negative protection**: Bank transaction debits, credits, and OTP messages are protected by rule-based pre-filters; model training explicitly includes hard negative bank advisories ("Never share OTP") to avoid false alarms.
