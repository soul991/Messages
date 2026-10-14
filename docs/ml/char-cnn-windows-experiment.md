# Windows RTX 3060 character CNN experiment

Updated: 2026-10-10

## Purpose and method

The earlier character CNN had been trained on CPU. This run established a
Windows CUDA workflow and compared a same-seed GPU baseline against one new
fit-only corpus. Both candidates used the same from-scratch Unicode CNN, 12
epochs maximum, batch size 256, patience 3, 256-character cap, and validation
loss selection. The threshold was selected from validation ham only for a 1%
false-positive target. No public test or challenge rows were used for fitting
or threshold selection.

The new local source, `scam-ham-india`, declares Apache-2.0 and contains 2,272
English ham/spam rows. Its dataset card says the merged data includes both
collected and synthetic examples. After exact normalized-text deduplication,
conflict review, and overlap checks against the existing train/validation/test
splits and frozen challenge, only 209 rows were eligible for fitting. There
were 2,052 unique overlaps with the existing splits, 10 duplicate occurrences,
one invalid row, no conflicting labels, and no challenge overlap. This source
did not provide a new independent evaluation set.

## Results

| Candidate | Fit rows | Threshold | Public test scam recall | Public test ham FPR | Frozen challenge scam recall | Frozen challenge safe FPR | Size |
|---|---:|---:|---:|---:|---:|---:|---:|
| Same-seed CUDA baseline | 74,207 | 0.8033 | 94.48% (3,373/3,570) | 0.91% (55/6,030) | 8.70% (6/69) | 21.11% (19/90) | 309,071 B |
| + Apache-2.0 corpus | 74,416 | 0.8199 | 93.95% (3,354/3,570) | 0.96% (58/6,030) | 5.80% (4/69) | 22.22% (20/90) | 309,071 B |

The augmentation did not improve either split. On the challenge diagnostic, both
models missed most scams and falsely flagged many safe examples. The separate
per-language thresholds performed worse on the challenge and are not selected.
The challenge is small and authored, and has been inspected in previous model
work, so it is not a blind estimate of production behavior; its result remains
a strong warning against automatic filtering.

## Decision

Reject the new-data augmentation and keep both checkpoints as research-only
artifacts outside the Android app. Do not replace the active n-gram resource,
enable the statistical routing gate, or use either CNN to move messages. The
new corpus substantially overlaps data already used, and its 209 unique rows
did not address the observed distribution shift. Further training on these
same public datasets is unlikely to resolve that issue. The quality blocker is
now a fresh, consented, human-labeled Indian SMS evaluation set with realistic
benign traffic, especially Hindi, Hinglish, and Punjabi.

Windows setup and reproduction commands are in
[`windows-training.md`](windows-training.md). Aggregate fit reports and
evaluations, with no message bodies, are preserved in:

- [`char-cnn-windows-baseline-report.json`](char-cnn-windows-baseline-report.json)
- [`char-cnn-windows-baseline-evaluation.json`](char-cnn-windows-baseline-evaluation.json)
- [`char-cnn-india-augmentation-report.json`](char-cnn-india-augmentation-report.json)
- [`char-cnn-india-augmentation-evaluation.json`](char-cnn-india-augmentation-evaluation.json)
