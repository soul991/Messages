# From-scratch multilingual character CNN

Updated: 2026-10-06

## What was trained

`protection-engine/tools/train_char_cnn.py` trains a compact Unicode character
CNN from random initialization. It uses a 32-dimensional character embedding,
four convolution widths (2–5), 64 channels per width, and a 256-character input
cap. It has no pretrained model dependency. The best epoch is selected by
validation loss; the scam threshold is selected using validation ham only. The
checkpoint is 309,071 bytes and is kept separate from the app's active scorer.

The training used the M4 Air's CPU because the installed PyTorch build reported
MPS compiled but unavailable in this session. Twelve epochs completed. The
validation loss fell from 0.1760 to 0.0858. The runner logged no message bodies.

## Data cleaning

After exact normalized-text deduplication and cross-split exclusion, the run
used 74,207 training rows, 8,892 validation rows, and 9,600 test rows. It
excluded two test rows duplicated in validation and one conflicting-label
message found in two ScamShield source subsets (one labeled ham and one scam).
No exact text overlaps remain across fit, validation, and test. The run report
records split hashes and aggregate source names, not message text.

The test rows are from public dataset test splits already inspected in earlier
experiments. They are held out from this fit and threshold selection, but are
not pristine blind evidence. ScamShield also includes synthetic and upstream
collections with mixed collection methods.

## Held-out public split

At the single global validation-calibrated threshold of 0.8234, the candidate
scored 9,600 rows:

| Group | Scam recall | Ham false-positive rate | Counts |
|---|---:|---:|---|
| Bengali script, including script-mixed text | 93.5% | 0.73% | TP 259, FN 18, FP 3, TN 408 |
| English and Latin-script messages, including Banglish | 93.3% | 1.01% | TP 2,972, FN 214, FP 52, TN 5,106 |
| Devanagari Hindi | 63.6% | 0.00% | TP 7, FN 4, FP 0, TN 449 |
| Hinglish | 100.0% | 0.00% | TP 96, FN 0, FP 0, TN 12 |
| **All** | **93.4%** | **0.91%** | **TP 3,334, FN 236, FP 55, TN 5,975** |

These are script/source buckets, not a perfect language detector: Latin-script
Banglish is grouped with English/romanized text, while the separate Hinglish
bucket uses the dataset's language metadata.

The Hindi scam denominator is only 11 and the Hinglish ham denominator only 12;
those rates are highly uncertain. Per-language thresholds selected on
validation shifted errors between language groups but did not improve the
overall result (test recall 93.3%, false-positive rate 1.01%); they remain
diagnostic only.

On these same cleaned splits, the active n-gram contribution at its installed
threshold scored 44.5% recall with a 43.7% false-positive rate. Calibrating the
active n-gram threshold to about 1% validation FPR reduced test recall to 7.8%.
This is a strong improvement for the CNN on this particular public split; it
does not establish field performance.

## Independent authored challenge

The fixed 159-row trilingual fraud challenge was not used for training,
threshold selection, or model selection. At the validation-selected global
threshold the CNN scored 3/69 scams and incorrectly flagged 17/90 safe examples:
4.3% recall, 18.9% false-positive rate, and 15.0% precision. The active
n-gram scorer caught 57/69 scams but flagged 70/90 safe examples at its
installed threshold. Both models fail this authored challenge's operating
tradeoff. Its small, contrastive examples are a diagnostic and are not a
representative SMS prevalence sample, but the poor CNN recall is enough to
reject app integration for now.
By challenge context, it caught only 3 of 56 `original` scams and none of the
9 `edge_case` or 4 `followup` scams. That points to a substantial domain shift,
not just a threshold that needs a small adjustment.

## Decision and reproduction

Keep the CNN as a research candidate only. Do not replace the installed model,
auto-delete messages, or silently move messages based on it. The next useful
step is to add independently collected, human-labeled Indian SMS with realistic
benign traffic and challenging scams, especially for Devanagari Hindi. Do not
train or tune on the fixed challenge or public test splits.

The dataset audit now includes a 3,894-message Hindi spam/ham corpus listed by
its authors on IEEE DataPort, but the portal was inaccessible from this
session and its reuse terms remain unverified. It has not been added to the
model.

Training and detailed aggregate evaluation are reproducible with:

```sh
python3 protection-engine/tools/train_char_cnn.py \
  --data-root ../../Datasets \
  --out-dir protection-engine/training/char_cnn \
  --epochs 12 --patience 3 --batch-size 512

python3 protection-engine/tools/evaluate_char_cnn_candidate.py \
  --data-root ../../Datasets \
  --candidate-dir protection-engine/training/char_cnn \
  --challenge ../../Datasets/trilingual-fraud-consumer-protection-v2/data.csv
```

See [`kaggle-free-gpu-training.md`](kaggle-free-gpu-training.md) for the
M4/local setup and optional cloud-GPU workflow.
