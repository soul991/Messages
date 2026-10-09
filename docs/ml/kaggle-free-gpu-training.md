# Character CNN training: local and free GPU options

This experiment adds a small model trained from random initialization. It is
separate from the installed Android scorer. It uses character IDs directly, so
the same model can learn Latin, Devanagari, Bengali, and mixed-script SMS
without downloading a pretrained tokenizer or model.

## Run locally on Apple Silicon

The same experiment can run on the M4 Air without uploading the corpora. Use a
temporary virtual environment so the app's Gradle/Python setup stays untouched:

```sh
python3 -m venv /tmp/messages-ml
/tmp/messages-ml/bin/python -m pip install torch pyarrow numpy
cd 01_active_codebase/messages
/tmp/messages-ml/bin/python protection-engine/tools/train_char_cnn.py \
  --data-root ../../Datasets \
  --out-dir protection-engine/training/char_cnn \
  --epochs 12 --patience 3 --batch-size 512
```

The runner selects Apple's MPS backend when PyTorch can access it, then CUDA,
then CPU. Check the start-of-epoch progress output for the selected device.
Keep the Mac connected to power and avoid running multiple training jobs at
once. The saved candidate is still a research checkpoint, not an Android
integration artifact.

## Run on Kaggle

Kaggle's notebook service documents GPU sessions (hardware availability varies)
and a maximum 12-hour execution session. Create a **private Kaggle Dataset**
containing the project code and a second private Dataset containing the
workspace `Datasets/` directory. Keep the workspace hierarchy intact. Do not
upload credentials, signing files, transcripts, or any personal SMS. Public
corpora may have their own redistribution terms; keep both Kaggle datasets
private unless each source's terms have been checked.

Attach both datasets to a new Kaggle Notebook and enable a GPU accelerator.
Run the following cell after replacing the two input paths with the names shown
in the notebook's `/kaggle/input/` directory:

```python
%pip install pyarrow
%run /kaggle/input/messages-app-code/protection-engine/tools/train_char_cnn.py \
  --data-root /kaggle/input/messages-app-datasets/Datasets \
  --out-dir /kaggle/working/char-cnn-run \
  --epochs 12 --batch-size 256 --target-fpr 0.01
```

Download or save `/kaggle/working/char-cnn-run/` as a notebook output when the
run finishes. The folder contains the PyTorch state, character vocabulary,
configuration, validation threshold, and aggregate-only evaluation report.
The script uses GPU when available and falls back to CPU otherwise. It pins a
random seed, but different GPU kernels can still introduce small numerical
differences.

## Data handling and evaluation

The runner reads the existing ScamShield, Bengali SMS, India Telecom, and
Bolewara training sources. It removes exact normalized-text duplicates across
the held-out sets before fitting and excludes Bolewara rows that collide with
held-out data. Validation sets the one global decision threshold using ham
messages only at a target false-positive rate of 1%. The test splits are scored
after training and threshold selection; they do not select epochs or settings.

The report groups results by Bengali script, Devanagari Hindi, and
English/romanized text, and records hashes of the three split inventories. It
does not print SMS bodies. It also explicitly records that the published test
sets have been inspected in earlier work, so the scores are not a fresh blind
benchmark. Review false-positive counts and the source-specific corpus
limitations before considering any integration.

After a completed fit, score the separate authored challenge set without
changing the selected threshold:

```sh
python3 protection-engine/tools/evaluate_char_cnn_candidate.py \
  --data-root ../../Datasets \
  --candidate-dir protection-engine/training/char_cnn \
  --challenge ../../Datasets/trilingual-fraud-consumer-protection-v2/data.csv
```

## Deployment boundary

The model checkpoint is not an Android-ready artifact. Before integration it
would need a supported mobile export/runtime, size and latency measurements on
representative devices, and a clean independent evaluation. The app's current
on-device model remains unchanged. Do not use this classifier to auto-delete or
silently hide messages.
