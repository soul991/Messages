# Indian multilingual 120-row external-set audit

Updated: 2026-10-06

## Source and quality checks

The [Hugging Face dataset card](https://huggingface.co/datasets/karanverma19/Indian_Multilingual_Scam_Message_Dataset)
publishes 120 English, Hindi, and Hinglish rows under Apache-2.0. It claims
real-world-inspired examples but does not describe collection, annotation,
annotator agreement, or de-identification methods. Its rows were downloaded
to temporary storage only; the CSV SHA-256 is
`3f55ed6f161d0b5f5dafde8b41b5134c8b64b7f854dc34cc265b946927e21bb6`.

The 120 balanced rows contain only 49 unique message texts: 24 unique scams
and 25 unique legitimate examples. There are 71 repeated rows, no repeated
text with conflicting scam/legitimate labels, and 39 of the 49 unique texts
are assigned different language or domain metadata across their repetitions.
All scam rows use the same `reason` string, and all legitimate rows use a
different shared `reason` string. This suggests templated or programmatically
assembled examples; it is not evidence of independent consumer SMS.

Exact-text overlap with the available training, validation, test, and frozen
authored challenge sets was zero. For meaningful counts, metrics below use
only the 49 deduplicated texts; raw-row results in the accompanying JSON are
included to make the duplicate inflation visible. Metadata inconsistencies
make language and domain subgroup estimates unreliable, so do not interpret
these results as language-level accuracy.

## Frozen-threshold diagnostic

All operating thresholds were set on the existing training pipeline's
validation ham only, not on this external set. On the 49 unique examples:

| Model | Scam recall | Legitimate false-positive rate | Counts |
|---|---:|---:|---|
| Original from-scratch character CNN | 8.3% | 36.0% | TP 2, FN 22, FP 9, TN 16 |
| IMC25-augmented character CNN | 41.7% | 20.0% | TP 10, FN 14, FP 5, TN 20 |
| Active app n-gram scorer | 95.8% | 8.0% | TP 23, FN 1, FP 2, TN 23 |

The strong n-gram result is likely tied to the small, templated sample and
does not outweigh its poor performance on the independent authored challenge.
The augmented CNN looks better than its original baseline on this set, but
its absolute false-positive rate is too high and the sample is too small and
repetitive to establish that the improvement transfers to real SMS. None of
these scores justifies changing app behavior.

The dataset is a useful smoke test for this particular authored style, not a
credible production benchmark or replacement for fresh consented Indian
messages. The 120-row source file itself was not copied into the repository;
only aggregate results are preserved in
[`indian-hf-120-external-evaluation.json`](indian-hf-120-external-evaluation.json).
