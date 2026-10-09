# SMS-Shield Mini external model baseline

Updated: 2026-10-06

## Candidate

The Apache-2.0 Hugging Face model
[`ganmoor-ai-labs/sms-shield`](https://huggingface.co/ganmoor-ai-labs/sms-shield)
publishes a 1.7B-parameter Q4_K_M Mini variant (1,282,438,944 bytes). Its card
reports a high score on a 1,390-row test set included in the same repository,
but says its training corpus is synthetic and unpublished. The model card also
states Bengali is not supported in this version. These are publisher-reported
results, not independently verified field performance.

The file was downloaded to `/private/tmp` for evaluation only and SHA-256
verified as
`2f9a65614178577a1af495f3bb81fbc8da72b64552ee4e2cd100740ebfe90da7`. It is
not part of the Android project or runtime. The local runner used llama.cpp on
CPU because Metal is unavailable in this session.

## Frozen results

The same fixed prompt and temperature-zero decoding were used on both
evaluation sets. No prompt or threshold was tuned on either set. A verdict of
`dangerous` alone is the conservative alert threshold; a verdict of either
`suspicious` or `dangerous` is the broader alert threshold.

On the frozen 159-row authored trilingual challenge, the broad threshold
caught 32/69 scams (46.4% recall) and flagged 17/90 safe messages (18.9% false
positive rate). The dangerous-only threshold caught 3/69 scams and flagged no
safe examples. This challenge is a small contrastive diagnostic, not a
representative sample of inbox prevalence.

On a deterministic stratified sample of 1,568 rows from the published test
splits, the broad threshold caught 486/607 scams (80.1% recall) and flagged
129/961 ham messages (13.4% false positive rate). The sample includes every
available Hindi and Hinglish test row and 250 ham plus 250 scam rows from each
of the English/romanized and Bengali groups, so its aggregate is intentionally
not a population-prevalence estimate. Language results:

| Group | Scam recall | Ham false-positive rate | Counts |
|---|---:|---:|---|
| English/romanized sample | 77.6% | 9.2% | TP 194 / 250; FP 23 / 250 |
| Bengali sample | 86.8% | 12.0% | TP 217 / 250; FP 30 / 250 |
| Devanagari Hindi | 81.8% | 16.5% | TP 9 / 11; FP 74 / 449 |
| Hinglish | 68.8% | 16.7% | TP 66 / 96; FP 2 / 12 |

The dangerous-only threshold on this same sample caught 214/607 scams (35.3%
recall) and flagged 14/961 hams (1.5% false-positive rate). This is a severe
recall tradeoff. Hindi and Hinglish scam/ham denominators are small and their
rates are highly uncertain. The public test splits were inspected in earlier
work, so these are not pristine blind results.

The complete aggregate output, including source breakdowns, row counts, input
audit and model hash, is in
[`sms-shield-mini-public-sample.json`](sms-shield-mini-public-sample.json).

## Decision

Do not integrate this model for automatic sorting or warnings. The broad
threshold over-flags legitimate messages, especially Devanagari Hindi; the
conservative threshold misses most scams. Its 1.28 GB footprint is also far
above this app's 2 MiB model asset budget, and its published training recipe
and test set are synthetic rather than independent real-world Indian SMS.
Keep the existing deterministic protections and the app's advanced filter
default-off while collecting fresh, consented and human-labeled evaluation
data. This model is a useful research reference, not evidence of a safe
production operating point.
