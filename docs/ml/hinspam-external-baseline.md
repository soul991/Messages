# External HinSpam model baseline

Updated: 2026-10-05

## Artifact and license metadata

The Hugging Face model card declares Apache-2.0 and reports a 0.1B-parameter
RoBERTa classifier for romanized Hinglish. Its model page claims 95.3% accuracy
and 94.9% F1, but does not identify its training corpus or give enough test-set
details to independently verify those figures. The model weighs 498,612,824
bytes (about 476 MiB), far above this app's 2 MiB n-gram asset limit.

The downloaded config omits `id2label`; this evaluation uses class id 1 as
spam, following the model card's label description. Weight SHA-256:
`85aa4eabc780b47979e61b50fc4bbd39f1e39c45e25ac922b75533e3f3d63954`.
Hugging Face files page short revision: `3c0e106`. Keep that label-map caveat
with all reported metrics.

## Frozen threshold results

The model was evaluated without fitting on 9,600 deduplicated public test rows
from ScamShield and the Bengali SMS corpus. Exact overlaps against both
validation files were excluded; one additional exact duplicate across test
sources was removed. The corpora have previously been inspected, so these are
source-separated test diagnostics rather than pristine blind evidence.

At the default argmax boundary:

| Group | Scam recall | Ham false-positive rate | F1 |
|---|---:|---:|---:|
| All included test rows | 97.8% (3,491/3,570) | 15.9% (960/6,030) | 0.871 |
| English | 98.1% (2,984/3,043) | 9.5% (467/4,935) | 0.919 |
| Devanagari Hindi | 90.9% (10/11) | 42.1% (189/449) | 0.095 |
| Romanized Hinglish | 99.0% (95/96) | 33.3% (4/12) | 0.974 |
| Bengali script variety | 91.5% (129/141) | 26.7% (58/217) | 0.787 |
| Banglish | 97.2% (137/141) | 53.4% (117/219) | 0.694 |
| Bengali code-mix | 98.6% (136/138) | 63.1% (125/198) | 0.682 |

The headline recall comes with an unacceptable false-positive burden. The
Hindi and Hinglish ham subsets are also small.

## Validation-only calibration

As a diagnostic, per-source-language score thresholds were selected to target
at most 1% false positives on the 8,892 exact-deduplicated validation rows.
The thresholds were then applied once to the public test rows:

| Group | Scam recall | Ham false-positive rate |
|---|---:|---:|
| All included test rows | 46.3% (1,654/3,570) | 1.5% (89/6,030) |
| English | 49.4% (1,503/3,043) | 1.5% (74/4,935) |
| Devanagari Hindi | 63.6% (7/11) | 2.0% (9/449) |
| Romanized Hinglish | 94.8% (91/96) | 25.0% (3/12) |
| Bengali script variety | 31.9% (45/141) | 0.9% (2/217) |
| Banglish | 1.4% (2/141) | 0.5% (1/219) |
| Bengali code-mix | 4.3% (6/138) | 0.0% (0/198) |

The validation target did not transfer uniformly to the test set: Hindi and
Hinglish test false-positive rates exceed 1%, and the high-recall raw boundary
collapses for English and Bengali when calibrated this way. Only ten Hinglish
ham examples were available for validation, so its estimated cutoff is weak.

## Separate challenge set

On the 159-row authored contrastive challenge set, class id 1 as spam gives
85.5% recall but flags 86.7% of safe messages (78/90). The challenge is not
representative of ordinary SMS and is kept separate from all tuning. Details
are in [`hinspam-external-challenge.json`](hinspam-external-challenge.json).

## Decision

Do not integrate this model or use it as the sole teacher for the multilingual
student. It is far too large for the current on-device model budget; its
default boundary creates too many false positives; and its validation-calibrated
boundary misses most English and Bengali scams. It shows that the current data
contains useful Hinglish separability, but the app's scratch n-gram candidates
remain the practical on-device direction. Neither the Hinglish subset nor the
public corpora justify an automatic-filtering accuracy claim.

## Reproduction

With PyTorch, Transformers, PyArrow and the pinned model files available:

```sh
python3 protection-engine/tools/evaluate_hf_external_model.py \
  --model-dir /path/to/pinned-hinspam-model \
  --input /path/to/scamshield-dataset/test.jsonl \
         /path/to/bengali-sms-smishing-dataset/test-00000-of-00001.parquet \
  --validation /path/to/scamshield-dataset/val.jsonl \
               /path/to/bengali-sms-smishing-dataset/validation-00000-of-00001.parquet \
  --calibrate-validation \
  --output docs/ml/hinspam-public-tests.json
```

The runner writes aggregate metrics, exact-source hashes, duplicate counts,
and validation-selected cutoffs. It never prints message contents. The
challenge result can be reproduced by passing its CSV as the sole `--input`
and omitting `--calibrate-validation`.
