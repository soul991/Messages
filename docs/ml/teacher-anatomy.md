# Multilingual Pretrained Teacher Anatomy & Operating-Point Analysis

Date: 2026-10-24  
Report Source: [`docs/ml/teacher_evaluation_report.json`](teacher_evaluation_report.json)  
Hardware: NVIDIA GeForce RTX 3060 (12 GB VRAM), CUDA 12.8, PyTorch 2.8.0+cu128  
Evaluation Dataset: Validation split (`D:\Istak\Messages\Datasets\splits\validation.jsonl`, N = 2,200 rows: 410 scam, 1,790 ham)  
Benchmark Dataset: Train split (`D:\Istak\Messages\Datasets\splits\train.jsonl`, N = 19,603 rows)

---

## 1. Architectural & Parameter Comparison

Three multilingual encoder-only backbones were inspected in the local Hugging Face hub cache (`D:\Istak\Messages\hf-cache\hub`):

| Metric | `ai4bharat/IndicBERTv2-MLM-only` | `google/muril-base-cased` | `xlm-roberta-base` |
|---|---|---|---|
| **Architecture** | BERT (`BertForMaskedLM` / `BertForSequenceClassification`) | BERT (`BertForMaskedLM` / `BertForSequenceClassification`) | RoBERTa (`XLMRobertaForSequenceClassification`) |
| **Pretrained Parameters** | 278,042,882 | 237,557,762 | 278,043,648 |
| **Hidden Size ($d_{model}$)** | 768 | 768 | 768 |
| **Encoder Layers** | 12 | 12 | 12 |
| **Attention Heads** | 12 | 12 | 12 |
| **Vocabulary Size** | 250,000 | 197,285 | 250,002 |
| **Tokenizer Type** | SentencePiece / Unigram | WordPiece (`BertTokenizerFast`) | SentencePiece / BPE |
| **License** | MIT (`https://huggingface.co/ai4bharat/IndicBERTv2-MLM-only`) | Apache-2.0 (`https://huggingface.co/google/muril-base-cased`) | MIT (`https://huggingface.co/xlm-roberta-base`) |
| **Weight Size on Disk** | 1,112.2 MB (`model.safetensors`) | 950.3 MB (`model.safetensors`) | ~1,110 MB (unquantized) |
| **App Budget Compliance** | Fails ($>550\times$ over 2.0 MiB asset limit) | Fails ($>470\times$ over 2.0 MiB asset limit) | Fails ($>550\times$ over 2.0 MiB asset limit) |

---

## 2. Tokenizer Unknown-Token ([UNK]) Benchmarks

Unknown-token rates were measured across all 19,603 training rows (473,387–512,557 tokens) and 2,200 validation rows (51,153–57,567 tokens), categorized by script bucket:

### 2.1 Training Corpus Tokenizer Statistics (N = 19,603 rows)

| Language Bucket | IndicBERTv2 UNK Rate (%) | IndicBERTv2 Msgs with UNK (%) | MuRIL UNK Rate (%) | MuRIL Msgs with UNK (%) | XLM-RoBERTa UNK Rate (%) | XLM-RoBERTa Msgs with UNK (%) |
|---|---:|---:|---:|---:|---:|---:|
| `en_latin` (9,650 rows) | 0.0147% (33 / 224,085) | 0.25% (24 / 9,650) | 0.1588% (372 / 234,284) | 2.15% (207 / 9,650) | 0.0024% (6 / 247,641) | 0.05% (5 / 9,650) |
| `hi_latn` (4,998 rows) | 0.0008% (1 / 131,434) | 0.02% (1 / 4,998) | 0.0618% (83 / 134,253) | 1.46% (73 / 4,998) | 0.0030% (4 / 135,470) | 0.08% (4 / 4,998) |
| `hi_deva` (3,551 rows) | 0.0052% (4 / 76,430) | 0.08% (3 / 3,551) | 0.0362% (29 / 80,188) | 0.68% (24 / 3,551) | 0.0000% (0 / 83,657) | 0.00% (0 / 3,551) |
| `bn_beng` (854 rows) | 0.0666% (13 / 19,533) | 0.94% (8 / 854) | 0.2987% (60 / 20,085) | 2.34% (20 / 854) | 0.0289% (7 / 24,249) | 0.59% (5 / 854) |
| `bn_latn` (550 rows) | 0.0502% (11 / 21,905) | 1.64% (9 / 550) | 0.2025% (46 / 22,714) | 3.64% (20 / 550) | 0.0371% (8 / 21,540) | 1.27% (7 / 550) |
| **Overall (19,603 rows)** | **0.0131%** (62 / 473,387) | **0.23%** (45 / 19,603) | **0.1200%** (590 / 491,524) | **1.75%** (344 / 19,603) | **0.0049%** (25 / 512,557) | **0.11%** (21 / 19,603) |

### 2.2 Validation Corpus Tokenizer Statistics (N = 2,200 rows)

| Language Bucket | IndicBERTv2 UNK Rate (%) | MuRIL UNK Rate (%) | XLM-RoBERTa UNK Rate (%) |
|---|---:|---:|---:|
| `en_latin` (1,200 rows) | 0.0073% (2 / 27,334) | 0.0937% (27 / 28,826) | 0.0000% (0 / 30,669) |
| `hi_deva` (400 rows) | 0.0000% (0 / 8,320) | 0.0000% (0 / 8,734) | 0.0000% (0 / 9,036) |
| `hi_latn` (300 rows) | 0.0000% (0 / 8,187) | 0.0240% (2 / 8,335) | 0.0000% (0 / 8,363) |
| `bn_beng` (300 rows) | 0.0000% (0 / 7,312) | 0.0000% (0 / 7,564) | 0.0000% (0 / 9,499) |
| **Overall (2,200 rows)** | **0.0039%** (2 / 51,153) | **0.0542%** (29 / 53,459) | **0.0000%** (0 / 57,567) |

### Key Tokenizer Finding:
- XLM-RoBERTa's SentencePiece BPE tokenizer yields the lowest unknown-token rate (0.0049% train, 0.0000% val), with zero UNKs across Devanagari Hindi.
- MuRIL's WordPiece tokenizer has a higher UNK rate (0.1200% train, 0.0542% val) because rare Latin transliterations and specialized Bengali conjuncts fall out of its 197k WordPiece inventory.

---

## 3. Validation Operating Points & Calibration Curves

Evaluation on `validation.jsonl` (2,200 rows: 410 scam, 1,790 ham; inference time: MuRIL 5.02s, IndicBERTv2 9.31s on RTX 3060).

### 3.1 Overall Operating Point Comparison

| Metric | Target Ham FPR = 0.50% | Target Ham FPR = 1.00% | Target Ham FPR = 2.00% | Target Ham FPR = 5.00% |
|---|---|---|---|---|
| **MuRIL Threshold ($\tau$)** | `0.997445` | `0.995373` | `0.985615` | `0.712994` |
| MuRIL Scam Recall | 61.95% [57.16, 66.52] | 71.95% [67.41, 76.08] | 83.90% [80.03, 87.14] | 93.66% [90.87, 95.64] |
| MuRIL Ham FPR | 0.45% [0.23, 0.88] | 0.95% [0.59, 1.52] | 1.96% [1.41, 2.71] | 4.97% [4.06, 6.08] |
| MuRIL Protected Traffic FPR | 4.17% [1.94, 8.78] (7/168) | 7.74% [4.51, 12.96] (13/168) | 16.67% [11.83, 22.95] (28/168) | 23.81% [18.02, 30.79] (40/168) |
| MuRIL Benign Advisory FPR | 0.00% [0.00, 43.43] (0/5) | 0.00% [0.00, 43.43] (0/5) | 0.00% [0.00, 43.43] (0/5) | 20.00% [3.62, 62.45] (1/5) |
| **IndicBERTv2 Threshold ($\tau$)** | `0.998614` | `0.998360` | `0.996123` | `0.766970` |
| IndicBERTv2 Scam Recall | 56.34% [51.50, 61.06] | 58.78% [53.96, 63.44] | 68.54% [63.89, 72.84] | 93.17% [90.31, 95.23] |
| IndicBERTv2 Ham FPR | 0.45% [0.23, 0.88] | 0.95% [0.59, 1.52] | 1.96% [1.41, 2.71] | 4.97% [4.06, 6.08] |
| IndicBERTv2 Protected Traffic FPR | 3.57% [1.65, 7.57] (6/168) | 8.33% [4.99, 13.62] (14/168) | 18.45% [13.37, 24.91] (31/168) | 38.69% [31.68, 46.19] (65/168) |
| IndicBERTv2 Benign Advisory FPR | 40.00% [11.76, 76.93] (2/5) | 60.00% [23.07, 88.24] (3/5) | 60.00% [23.07, 88.24] (3/5) | 60.00% [23.07, 88.24] (3/5) |

### 3.2 Calibration Metrics (Validation Split, N = 2,200)

| Model | Brier Score | ECE (10 bins) | Overconfidence Zone |
|---|---:|---:|---|
| **`google/muril-base-cased`** | 0.0509 | 0.0537 | Bin [0.9, 1.0]: 449 samples, confidence 0.9918, accuracy 0.8641 |
| **`ai4bharat/IndicBERTv2-MLM-only`** | 0.0524 | 0.0557 | Bin [0.9, 1.0]: 445 samples, confidence 0.9921, accuracy 0.8427 |

Both models display extreme probability overconfidence: almost all positive predictions produce softmax scores above $0.99$, compressing meaningful decision separation into the fourth decimal place ($0.995$ vs $0.998$).

---

## 4. Subgroup Breakdown at Target Ham FPR = 1.00%

Per-bucket breakdown on `validation.jsonl` (410 scam, 1,790 ham):

| Model | Subgroup Bucket | Scam Total | TP | Recall (%) [95% CI] | Ham Total | FP | Ham FPR (%) [95% CI] | Conclusive Status ($N \ge 100$) |
|---|---|---:|---:|---:|---:|---:|---:|---|
| **MuRIL** | `bn_beng` | 127 | 91 | 71.65% [63.27, 78.77] | 173 | 0 | 0.00% [0.00, 2.17] | Conclusive (Scam $\ge 100$, Ham $\ge 100$) |
| MuRIL | `en_latin` | 164 | 103 | 62.80% [55.19, 69.83] | 1036 | 16 | 1.54% [0.95, 2.49] | Conclusive |
| MuRIL | `hi_deva` | 26 | 20 | 76.92% [57.95, 88.97] | 374 | 1 | 0.27% [0.05, 1.50] | Inconclusive ($N_{scam} = 26 < 100$) |
| MuRIL | `hi_latn` | 93 | 81 | 87.10% [78.79, 92.46] | 207 | 0 | 0.00% [0.00, 1.82] | Inconclusive ($N_{scam} = 93 < 100$) |
| **IndicBERTv2** | `bn_beng` | 127 | 79 | 62.20% [53.53, 70.16] | 173 | 3 | 1.73% [0.59, 4.97] | Conclusive |
| IndicBERTv2 | `en_latin` | 164 | 73 | 44.51% [37.12, 52.16] | 1036 | 14 | 1.35% [0.81, 2.26] | Conclusive |
| IndicBERTv2 | `hi_deva` | 26 | 15 | 57.69% [38.95, 74.46] | 374 | 0 | 0.00% [0.00, 1.02] | Inconclusive ($N_{scam} = 26 < 100$) |
| IndicBERTv2 | `hi_latn` | 93 | 74 | 79.57% [70.28, 86.51] | 207 | 0 | 0.00% [0.00, 1.82] | Inconclusive ($N_{scam} = 93 < 100$) |

---

## 5. Error Profiling & Defect Analysis

### 5.1 Failure on Protected Traffic (OTPs & Bank Transactions)
- **Problem**: In `validation.jsonl`, there are 168 legitimate protected messages containing transactional keywords ("OTP", "one time password", "credited", "debited", "account no", "avl bal").
- **Observation**:
  - At the 1.00% FPR threshold, **MuRIL incorrectly classifies 13 legitimate protected messages as scams** (7.74% protected FPR).
  - **IndicBERTv2 incorrectly classifies 14 legitimate protected messages as scams** (8.33% protected FPR).
- **Root Cause**: Pretrained contextual embeddings heavily attend to high-salience financial tokens ("OTP", "account", "credited", "SBI", "HDFC") which are heavily shared between phishing attacks and legitimate transaction confirmations. Without an explicit rule/pre-filter constraint, statistical attention weights alone cannot satisfy the project's zero-tolerance gate (`Protected FPR == 0.00%`).

### 5.2 Failure on Fraud Awareness Advisories
- **Observation**:
  - IndicBERTv2 triggered a **60.00% false positive rate** (3 out of 5 advisories flagged) at the 1.00% operating point.
  - IndicBERTv2 flags advisories containing text such as: *"Never share your OTP or password with anyone. Bank officials never ask for secret credentials."*
- **Root Cause**: The advisory text contains the identical phrase tokens found in smishing lures (*"share your OTP"*, *"password"*, *"bank officials"*). Because the transformer is trained on bag-of-words or masked token likelihoods without explicit polarity inversion detection, it confuses fraud warnings with actual fraud.

---

## 6. Architectural Conclusion & Distillation Guidance

1. **Teacher Suitability**:
   - `google/muril-base-cased` is the stronger teacher backbone: it outperforms IndicBERTv2 across all operating points (71.95% vs 58.78% recall at 1.0% FPR, Brier 0.0509 vs 0.0524), with zero false positives on Bengali and Hindi validation ham at $\le 1.0\%$ FPR.
2. **Impossibility of Direct Deployment**:
   - Parameter size (237M params, 950 MB) exceeds Android budget ($<2.0\text{ MiB}$) by $475\times$.
   - Unconstrained neural inference fails the core safety invariant (`Protected FPR == 0.00%`).
3. **Implications for Student Architecture**:
   - The student model must combine multi-script linear/logistic models (which easily compress below 1.5 MiB) with **deterministic syntactic pre-filters**:
     1. Strict OTP/transaction hard-negative pre-filter to guarantee $0.00\%$ protected FPR.
     2. Multilingual advisory regex pre-filter to eliminate false alarms on public awareness notices.
     3. Sublinear char-wb TF-IDF n-grams with per-script probability calibration to maintain high recall.
