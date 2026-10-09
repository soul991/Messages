# Local model training

For the separate from-scratch GPU experiment, see
[`kaggle-free-gpu-training.md`](kaggle-free-gpu-training.md) and
`protection-engine/tools/train_char_cnn.py`.
The latest local CPU experiment and its deployment decision are documented in
[`char-cnn-experiment.md`](char-cnn-experiment.md).
The multilingual MiniLM teacher baseline and scam-only IMC25 augmentation
comparison are in [`minilm-imc25-experiment.md`](minilm-imc25-experiment.md),
with aggregate-only reports alongside it. The same IMC25 augmentation was
tested on the compact from-scratch CNN; see
[`char-cnn-imc25-experiment.md`](char-cnn-imc25-experiment.md).
The Indic-focused MuRIL teacher fine-tune is documented in
[`muril-experiment.md`](muril-experiment.md); its first-epoch checkpoint and resume
notes are preserved in the workspace model archive. Training is paused pending
continuation.
The source-level data review, including the IMC25 training-only filtering, is
in [`multilingual-dataset-audit.md`](multilingual-dataset-audit.md) and
[`imc25-smishing-augmentation-audit.md`](imc25-smishing-augmentation-audit.md).
The recently audited 120-row Indian multilingual diagnostic and its
deduplicated evaluation are documented in
[`indian-hf-120-external-audit.md`](indian-hf-120-external-audit.md).
An independently frozen comparison of a recent Hugging Face Indian SMS model
is documented in [`sms-shield-mini-external-baseline.md`](sms-shield-mini-external-baseline.md);
its aggregate-only output is in
[`sms-shield-mini-public-sample.json`](sms-shield-mini-public-sample.json).

The app uses a compact character n-gram Naive Bayes scorer. It is trained from
scratch with Python's standard library and runs in the existing pure Kotlin
`protection-engine` module. There is no Hugging Face runtime dependency in the
Android app.

## Reproduce the current model

From the active repository root:

```sh
python3 protection-engine/tools/train_ngram.py
```

This writes the model directly to
`protection-engine/src/main/resources/ngram_model.json`, the scorer fixture to
`protection-engine/training/score_fixture.json`, and the training report to
`docs/ml/ngram-training-report.txt`. The app already loads that resource through
`NGramScorer.fromJson`; the normal category ceiling remains Inbox to Review.

The trainer uses two local sources:

- `protection-engine/src/test/resources/corpus.json`: 513 authored regression
  examples. A deterministic split puts 138 aside, including 43 messages
  selected because removing one matched rule makes the deterministic engine
  miss them.
- `protection-engine/training/synthetic_messages.json`: 801 generated examples
  across English, Hinglish, Hindi, Bengali, Gujarati, and Punjabi.

The 43-message slice is useful for checking that the model can recover some
messages made fragile by removing a single pattern. It is constructed from the
existing test corpus and is not an independent, real-world test set. The
synthetic language examples are not language-expert reviewed. Do not present
these results as production accuracy or use them to enable automatic filtering.

## Current training result

The latest run trained on 1,176 examples (375 regression corpus + 801 synthetic)
and emitted 13,499 weighted character n-grams. On the constructed held-out
subset, it recovered 38/43 fragile scam messages, while also escalating 2/59
held-out non-scam messages to Review (3.4%). It flagged 68/79 held-out scams.
The threshold was selected on training ham only; the held-out figures were not
used to tune it. These figures are weak evidence because the test corpus shares
authorship with the rules and the synthetic messages share one author.

For now, Advanced Message Filtering defaults off on fresh installs. Keep the
model as an optional local experiment until the owner supplies an independent,
human-labeled evaluation set with real, de-identified examples across the
languages and message types the app supports. Never train from users' SMS or
upload their messages.
