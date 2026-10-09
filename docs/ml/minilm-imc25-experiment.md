# Multilingual MiniLM with IMC25 scam-only augmentation

Updated: 2026-10-06

## Experiment

The teacher model is Microsoft's MIT-licensed
[`Multilingual-MiniLM-L12-H384`](https://huggingface.co/microsoft/Multilingual-MiniLM-L12-H384),
fine-tuned on the existing cleaned SMS splits. The baseline and augmentation
runs used the same pinned starting weights, seed, three epochs, batch size 24,
160-token cap, 2e-5 learning rate, loss weighting, validation-based checkpoint
selection, and validation-ham-only 1% FPR threshold policy. Only the augmented
run added 2,481 deduplicated India-origin or Hindi scam examples from the
CC BY 4.0 IMC25 public-report dataset, to the training set. Exact overlap with
the existing fit/validation/test splits was zero. See
[`imc25-smishing-augmentation-audit.md`](imc25-smishing-augmentation-audit.md)
for source details and filtering.

## Results

Both checkpoints were selected at epoch 3. Both calibrated to exactly 1.00%
validation ham FPR. On the already-inspected public test split, the baseline
caught 3,469/3,570 scams (97.17% recall) and flagged 59/6,030 ham (0.98% FPR).
The augmented run caught 3,468/3,570 scams (97.14% recall) and also flagged
59/6,030 ham (0.98% FPR). It changed Bengali test recall from 98.19% (272/277)
to 98.56% (273/277), while Hindi recall moved from 72.73% (8/11) to 81.82%
(9/11) with one Hindi ham false positive. English/romanized recall fell from
97.08% to 96.99% (one fewer caught scam, one fewer false positive). The Hindi
denominator is too small to support a reliable improvement claim.

On the untouched 159-row authored challenge, the baseline caught 28/69 scams
(40.58% recall) and flagged 35/90 safe messages (38.89% FPR). The augmented
model caught 25/69 scams (36.23% recall) and flagged 41/90 safe messages
(45.56% FPR). Its challenge results worsened in English and Punjabi; both runs
missed all 2 Hindi scams and flagged 7 of the 13 Hindi safe examples. These
results do not support the hypothesis that the extra positive-only data
improves useful generalization.

The small authored challenge is a diagnostic rather than a representative
estimate of inbox prevalence. Still, a roughly 46% false-positive rate on
those safe examples is unacceptable for automatic filtering. Strong scores on
the public splits do not cancel that failure, especially because those public
splits were inspected during earlier work.

## Decision

Reject the IMC25-augmented checkpoint and keep both MiniLM checkpoints as
research-only teacher experiments. Do not integrate them into Android: each
checkpoint is about 470.6 MB and neither resolves the independent false
positive problem. Keep the from-scratch 309 KB character CNN as a separate
research candidate only; its challenge result was also not integration-ready.

The next highest-value work is to broaden the independent evaluation with
consented, human-labeled benign and scam SMS from Indian users, especially
Hindi and Punjabi/code-mixed everyday traffic. The current data lacks enough
realistic negatives that resemble scam vocabulary, and adding scam-only rows
does not fix that gap.

## Reproduction and reports

The trainer accepts the temporary source CSV through `--imc-smishing-csv`.
The experiment-specific aggregate reports, containing no message bodies, are
[`minilm-baseline-report.json`](minilm-baseline-report.json) and
[`minilm-imc25-augmentation-report.json`](minilm-imc25-augmentation-report.json).
The model weights and downloaded corpus stayed in `/private/tmp` and are not
part of the Android app or repository.
