# IndicBERTv2 Indian-language SMS classification experiment

Updated: 2026-10-10

## Candidate anatomy

[`ai4bharat/IndicBERTv2-MLM-only`](https://huggingface.co/ai4bharat/IndicBERTv2-MLM-only)
is an encoder-only BERT language model, not a ready-made scam classifier. It
was pretrained with masked-language modeling on IndicCorp v2. AI4Bharat reports
278 million parameters and coverage of 23 Indic languages plus English; the
Hugging Face card tags it for 26 languages and identifies an MIT license. Its
pretraining makes it a plausible transfer-learning base for Hindi, Bengali,
Punjabi, and English SMS, but does not establish fraud-detection ability.

The selected MLM-only variant preserves the source scripts. Its classification
head was newly initialized for this task. The model weights are about 1.11 GB,
so this experiment is a teacher candidate and cannot fit the app's 2 MiB model
asset budget.

## Fine-tuning setup

The model was downloaded and pinned to revision
`8598f13fe52443bc3fc054fcd665944560145b5c`. On Windows, it was fine-tuned on
the RTX 3060 using the same cleaned corpus pipeline as the previous transformer
experiments. Training used 74,207 unique fit rows (47,199 ham, 27,008 scam),
with 8,892 validation rows and 9,600 public test rows. Exact-text conflicts
were excluded, and there was no exact-text overlap between fit, validation,
and test after cleaning. The frozen 159-row authored challenge had zero exact
overlap with the fit or evaluation rows.

The embeddings and bottom eight of twelve encoder layers were frozen; the top
four layers and classification head were trainable (28,943,618 parameters).
Training ran for three epochs with batch size 24, maximum length 160, learning
rate 2e-5, and seed `20261006`. The checkpoint was selected by validation loss
(best at epoch 3). The alert threshold was calibrated using validation ham
only to target a 1% false-positive rate. Neither the public test nor the
authored challenge was used for training, checkpoint selection, or threshold
calibration.

## Results

At the validation-selected threshold of `0.649946`:

| Evaluation | Scam recall | Safe-message false-positive rate | Counts |
|---|---:|---:|---|
| Public test split | 97.76% | 1.33% | TP 3,490 / 3,570; FP 80 / 6,030 |
| Frozen authored challenge | 33.33% | 37.78% | TP 23 / 69; FP 34 / 90 |

On the challenge source groups, English recall/FPR were 44.4%/43.8%, Hindi
47.6%/65.5%, and Punjabi 4.8%/3.4%. The challenge is small and authored, so
these rates are diagnostic rather than population estimates. The severe false
positive rate still shows the model does not safely generalize to the tested
message distinctions. The public split was inspected in earlier work and its
high score does not override the challenge failures.

The held-out challenge comparison across pretrained candidates is:

| Candidate | Scam recall | Safe-message FPR | Checkpoint size |
|---|---:|---:|---:|
| Multilingual MiniLM baseline | 40.6% | 38.9% | 470.6 MB |
| MiniLM + India/Hindi scam-only augmentation | 36.2% | 45.6% | 470.6 MB |
| MuRIL | 31.9% | 22.2% | 950.3 MB |
| IndicBERTv2 | 33.3% | 37.8% | 1,112.2 MB |

No pretrained candidate has shown an acceptable automatic-filtering operating
point on this challenge. IndicBERTv2's better public-test result is not
evidence that another epoch or a different threshold will fix the mismatch;
the available training material does not adequately represent the safe
messages in the challenge.

## Decision and reproduction

**Do not integrate this checkpoint or enable automatic message filtering.**
The active Android n-gram resource and its default-off behavior are unchanged.
The trained checkpoint and log remain outside the Android project. The full
aggregate-only report, with model and data hashes and language/source metrics,
is in [`indicbertv2-experiment-report.json`](indicbertv2-experiment-report.json).

With the downloaded checkpoint cached in `HF_HOME`, the run can be reproduced
from PowerShell with:

```powershell
$repo = 'D:\Istak\Messages\Messages App\01_active_codebase\messages'
$python = 'D:\Istak\Messages\.venv-ml\Scripts\python.exe'
$model = 'D:\Istak\Messages\hf-cache\hub\models--ai4bharat--IndicBERTv2-MLM-only\snapshots\8598f13fe52443bc3fc054fcd665944560145b5c'

& $python -u (Join-Path $repo 'protection-engine\tools\train_minilm_candidate.py') `
  --data-root 'D:\Istak\Messages\Datasets' `
  --model-dir $model `
  --model-repo 'ai4bharat/IndicBERTv2-MLM-only' `
  --model-revision '8598f13fe52443bc3fc054fcd665944560145b5c' `
  --tokenizer auto `
  --out-dir 'D:\Istak\Messages\indicbertv2-sms-candidate' `
  --epochs 3 --freeze-encoder-layers 8 --batch-size 24 `
  --max-length 160 --learning-rate 2e-5 --target-fpr 0.01
```
