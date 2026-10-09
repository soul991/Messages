# Multilingual SMS data audit

Updated: 2026-10-02

This inventory covers the files in the workspace-level `Datasets/` folder.
Message text is intentionally not copied into this report.

## Files and recommended use

| Dataset | Local files | Scale and labels | Recommended use |
|---|---|---|---|
| Bengali SMS smishing | `bengali-sms-smishing-dataset/{train,validation,test}-00000-of-00001.parquet` | 4,903 train / 701 validation / 1,401 test. Labels: `smish`, `promo`, `normal`; `smish` is positive and the other two are non-scam for this binary experiment. Varieties: Bengali, Banglish, CodeMix, English. | Primary Bengali training and evaluation source. Preserve variety-level results; the English variety is counted under English. |
| ScamShield | `scamshield-dataset/{train,val,test}.jsonl` | Parsed by the trainer as 69,286 train / 8,192 validation / 8,202 test records. Labels: `is_scam`; language metadata includes English, Hindi, Hinglish. Training includes synthetic examples and multiple source datasets. | Main Hindi and English training/evaluation source. Devanagari Hindi and romanized Hinglish are reported separately. Remove exact cross-split duplicates before evaluation. |
| Indian Telecom SMS collection | `india-spam-sms-classification-main/dataset/spam_ham_india.csv` | 2,267 CSV rows; `spam` / `ham`; no language field. Repository reports an MIT license and crowdsourced contributions. | Supplemental training only; never treat as an independent benchmark. Add to the English pool only because the file has no reliable per-row language labels. |
| Scam/Ham India | `scam-ham-india/scam_hum_india.csv` | 2,272 CSV rows; `spam` / `ham`. README says it mixes real and synthetic examples and is English-only. | Excluded from the current fit: it is highly redundant with the Indian Telecom CSV and offers little independent evidence. |
| Bolewara Hinglish/English scam | `bolewara-hinglish-scam-text-dataset/data.json` | 3,787 source rows; 3,558 unique normalized messages. Card describes 35 handwritten Hinglish scam and 28 Hinglish benign messages; English portions derive from UCI plus curated texts. CC BY 4.0. | Training supplement only. 2,777/366/329 exact overlaps with ScamShield train/validation/test respectively; matching rows are excluded from the supplement, and it contributes only unique training rows. It has no independent split. |
| Indian Multilingual Scam Message dataset | Hugging Face bucket `bhoomee/Indian_Multilingual_Scam_Message_Dataset-bucket`, 120-row CSV | Advertises Hindi, Hinglish, and English under Apache-2.0. | Reviewed, excluded. Inspection shows repeated fixed templates with urgency-suffix substitutions and language labels that conflict with the text. It is unsuitable as independent evidence and likely to cause template memorization. |
| Punjab Fraud Intent Benchmark, trilingual v2 | `trilingual-fraud-consumer-protection-v2/data.csv`, 159 rows; 59 publisher-labeled English, 50 Hindi, and 50 Punjabi. MIT on the Hugging Face card. | Frozen source-separated challenge evaluation only, not training. It is an authored set of contrastive, edge-case, and consumer-advisory examples, not representative SMS. Punjabi is out of the current scope. Full aggregate evaluation and limitations: [`external-challenge-report.md`](external-challenge-report.md). |

The Bengali dataset card identifies an MIT license and discloses translation and
synthetic generation in the dataset's research description. ScamShield's card
identifies MIT, but the corpus combines upstream sources; check upstream terms
before redistributing source rows or publishing a model package. The India
Telecom repository includes an MIT license. The Scam/Ham India card identifies
Apache-2.0. Bolewara's card states CC BY 4.0; retain the attribution in its
local `SOURCE.md` and in any eventual app distribution that includes a model
trained on these examples. These dataset-level labels do not replace
upstream-source review.

## Duplicate and split handling

- Bengali train has 3 exact normalized-message overlaps with validation. The
  trainer excludes held-out duplicates from fitting; Bengali test has no
  exact overlap with the other Bengali splits.
- ScamShield has 12 exact train/validation, 13 train/test, and 2
  validation/test overlaps in the inventory. The trainer removes train rows
  that occur in validation or test, and removes the 2 test rows duplicated in
  validation.
- The two India CSVs share 2,052 exact normalized messages. The Telecom CSV
  also substantially overlaps ScamShield. Exact duplicates are deduplicated
  before fitting, and the second India CSV is not added.
- Labels on the exact overlaps inspected so far agreed. This does not validate
  the remaining labels or establish that near-duplicate templates are
  independent.

## Current candidate experiment

`train_multilingual_candidate.py` compares character n-gram Naive Bayes with
sparse logistic SGD from scratch, plus language-balanced and legacy-model-blend
variants. Each script-routed expert also compares both model families and
selects family, feature-frequency cutoff, minimum message length, and threshold
using validation only. It uses the
Bengali and ScamShield training splits, the unique portion of the Indian
Telecom CSV, the unique train-only portion of Bolewara's CC BY dataset, and
in-scope app training examples. Threshold and
minimum-message-length selection use validation data only. Each expert gets a
separate threshold calibrated to a 1% validation false-positive target.
Validation selected Naive Bayes for all three routes. The single-model result
is weak for Devanagari Hindi; the separate Hindi expert catches 5 of 11 Hindi
scams in the small, previously inspected held-out set. None of the candidates
replace the active model.

The published Bengali and ScamShield test records have been inspected in
earlier turns, so these measurements are held-out by split but not pristine
blind evidence. Hindi's test subset remains comparatively small. The model is
an experiment, not a production accuracy claim. Keep advanced AI filtering
disabled by default until there is a fresh independently collected and
human-labeled evaluation set.

## Additional Hindi data lead

The [Indian crowdsourced Hindi/English SMS repository](https://github.com/princebari/-SMS-Spam-Classification-on-Indian-Dataset-A-Crowdsourced-Collection-of-Hindi-and-English-Messages)
describes a 2,000-message dataset collected from 43 participants. Its GitHub
repository lists the CSV but no license file; it links to a separate dataset
request page. It has not been added to training until reuse terms are clear.

An additional promising source is the authors' [Spam SMS in Hindi Language
dataset](https://sites.google.com/cse.nits.ac.in/ramanujamge/publications/datasets-offered),
listed as 3,894 crowd-collected messages from students and peers, mostly Hindi,
with normal ham and unsolicited promotional/annoying spam. It is distributed
through [IEEE DataPort DOI 10.21227/5y8x-n678](https://dx.doi.org/10.21227/5y8x-n678).
The portal could not be fetched in this environment, and a reuse license was
not verified; keep it out of fitting until the actual file and terms are
available. Its stated labels are spam/ham, so it may help general SMS spam
coverage but is not necessarily a financial-fraud benchmark.

Other leads reviewed on 2026-10-06:

- The authors' [IMC 2025 public-report smishing dataset](https://github.com/reportsmishing/Smishing-Dataset-IMC25)
  is CC BY 4.0 and includes 33,869 positive user reports, including 175 rows
  labeled Hindi and 3,729 with India listed as the originating network country.
  It is positive-only and heavily repeated (25,329 unique message texts); it
  cannot supply benign-message examples. An exact-deduplicated, India-or-Hindi
  training-only augmentation experiment added 2,481 unique records after
  filtering against the current train/validation/test splits. The CSV remains
  in temporary storage; its hash, selection rules, and limits are recorded in
  [`imc25-smishing-augmentation-audit.md`](imc25-smishing-augmentation-audit.md).
- The [Indian Payment SMS Corpus](https://github.com/skyaara/indian-payment-sms-dataset)
  is a 172-row CC BY 4.0 set of synthetic, privacy-safe banking/payment
  messages with 67 hard negatives. It can be a small stress-test source for
  transaction alerts, OTPs, refunds, and phishing-like texts, but its authors
  explicitly say it is not a statistical sample. It has not been added to
  classifier fitting. Inspection of its 172-row JSON shows `expected.bookable`
  is a transaction-parser field, not a scam/ham label; 78 examples are
  bookable and 94 are not. It must not be mapped directly to spam labels.
- A [120-example Indian multilingual scam-message bucket](https://huggingface.co/buckets/bhoomee/Indian_Multilingual_Scam_Message_Dataset-bucket)
  claims Hindi, Hinglish, and English examples and an Apache-2.0 license. The
  card calls the examples realistic and inspired, but does not document a
  collection or human-labeling protocol. A Hugging Face dataset-card copy
  (`karanverma19/Indian_Multilingual_Scam_Message_Dataset`) was audited: its
  120 rows reduce to 49 unique texts, with changing language/domain metadata
  for 39 texts. At local validation-calibrated thresholds, the active
  character n-gram scorer achieved 23/24 scam recall and 2/25 false positives
  on those unique texts; this templated, tiny set is not a real-world benchmark
  and that score does not override its independent-challenge failure. See
  [`indian-hf-120-external-audit.md`](indian-hf-120-external-audit.md).

- [Bangalabarta](https://data.mendeley.com/datasets/jfkfbw3gzh/2) describes
  2,772 Bengali smishing/promotional/normal SMS, but its stated license is
  CC BY-NC-SA 4.0. Do not use it for a product without resolving the
  non-commercial/share-alike implications; check for overlap with the Bengali
  corpus already present before any future use.
- [Bangla phishing detection 2026](https://huggingface.co/datasets/mdsajjadullah/bangla-phishing-detection-2026)
  has about 3,020 CC BY 4.0 rows, but the card identifies simulated phishing
  messages across SMS, email, and URL types. Treat it as synthetic
  augmentation only, never as evidence of real SMS generalization.
- [SMS Spam Multilingual Collection](https://huggingface.co/datasets/bhoomee/SMS_Spam_Multilingual_Collection_Dataset)
  translates the same English UCI messages into Hindi, Bengali, and many other
  languages. It can test translation robustness, but it is not independently
  collected Hindi/Bengali SMS and should not be counted as such.

- [RevisedIndianDataset](https://github.com/shshnk158/Multilingual-SMS-spam-detection-using-RNN)
  is reported in a research paper as 4,565 messages: 165 Hindi (37 ham, 128
  spam), alongside English, Telugu, and Kannada. The repository distributes
  `Resources/revisedindiandataset.xls` and `Resources/Finaldataset.csv`; GitHub
  has no repository license file or license metadata. Its README describes
  combining an English Kaggle corpus with manually collected messages, but
  does not state collection consent or redistribution terms for those records.
  This is a promising Hindi lead, but leave it out of fitting and redistribution
  until the authors clarify permission and source-level terms. The small Hindi
  subset would be supplemental even if cleared, not an adequate Hindi benchmark.

- [Spam SMS dataset 2011-12](https://precog.iiit.ac.in/datasets/) is listed by
  IIIT-Delhi's Precog research group as an available dataset. Research papers
  describe 2,000 messages (1,000 ham, 1,000 spam) collected through a campus
  crowdsourcing process and containing Hindi-English regional wording. The
  lab page provides a ZIP and checksums but no explicit reuse license; a paper
  says access was available on request. Keep it out of training until reuse
  terms are confirmed. This source may be more useful for Hinglish than
  Devanagari Hindi.

## Reproduction

From the Android repository root, with `pyarrow` available in Python:

```sh
python3 protection-engine/tools/train_multilingual_candidate.py
```

The script reads `../../Datasets` relative to this checkout, does not print
message contents, and writes the candidate plus aggregate metrics to
`docs/ml/multilingual-model-report.json`.
