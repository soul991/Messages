# MuRIL Indic-language SMS classifier experiment

Updated: 2026-10-10

## Candidate and rationale

Google's Apache-2.0
[`google/muril-base-cased`](https://huggingface.co/google/muril-base-cased)
was selected as a stronger Indic-language teacher candidate. Its model card
describes pretraining on 17 Indian languages and transliterated counterparts,
including Bengali, Hindi, English, and Punjabi. That is a closer language fit
for this app than the earlier general multilingual MiniLM encoder. The card's
published benchmark results are for general NLP tasks, not SMS scam detection;
they are not evidence of expected app accuracy.

Pinned revision: `afd9f36c7923d54e97903922ff1b260d091d202f`. The downloaded
PyTorch checkpoint is 953,477,430 bytes with SHA-256
`e65e3b7aaa3ce16d23fc4218783efd0417880655f8f398e6cb1a111a0de0c95f`.
The 237,557,762-parameter encoder loaded successfully with its official
`BertTokenizerFast`/WordPiece tokenizer. The original download remains in
`/private/tmp`. The epoch 1 fine-tuned checkpoint is preserved separately in
the workspace model archive, outside the Android app and Git repository.

## Training setup

This experiment uses the same exact-deduplicated train/validation/test splits
and class-weighted loss as the MiniLM experiments. It runs on CPU because
PyTorch reports no available MPS device in this environment. To lower CPU and
memory cost while adapting the Indic encoder, token embeddings and the lowest
eight of twelve encoder layers are frozen; the upper four layers, pooler, and
classification head are trainable. The model uses batch size 24, sequence cap
160, learning rate 2e-5, three epochs, and validation-loss checkpoint
selection. The scam alert threshold is chosen on validation ham only to target
1% FPR. Test and authored challenge rows are never used for fitting or
threshold selection.

The October 6 run was paused after epoch 1. Its validation loss was 0.082157.
It was interrupted eight steps into epoch 2; the selected epoch 1 weights were
saved, but optimizer state was not. A follow-up run on October 10 resumed from
those weights with a fresh optimizer and scheduler for two additional epochs.
It used the same audited splits, class weighting, batch size 24, sequence cap
160, learning rate 2e-5, frozen embeddings and bottom eight encoder layers,
validation-loss checkpoint selection, and validation-ham-only 1% FPR
calibration. The resulting candidate and its aggregate-only report are outside
the Android app; the report is preserved at
[`muril-resume-report.json`](muril-resume-report.json).

## October 10 resume result

The validation-selected candidate calibrated to threshold 0.8650. On the
previously inspected public test split it caught 3,429/3,570 scams (96.05%
recall) and flagged 62/6,030 ham (1.03% FPR). Bengali recall was 97.11% (269/277),
Hindi recall 81.82% (9/11), and English/romanized recall 95.89% (3,055/3,186).
The Hindi result remains based on only 11 test scams.

On the frozen 159-row authored challenge, it caught 22/69 scams (31.88% recall)
and flagged 20/90 safe messages (22.22% FPR). It caught only 2/21 Punjabi scams
and 0/2 Hindi scams. This independent diagnostic is small and authored, but its
large error rates are sufficient to reject this checkpoint for app filtering.
Strong public-split results do not override that failure, especially because
those splits have been inspected in prior experiments.

The resumed checkpoint's `model.safetensors` is 950,254,592 bytes. It is a
large research teacher and is not an Android-ready asset. Keep the shipped
character n-gram model and its default-off setting unchanged. Do not integrate
this MuRIL candidate or use it to automatically filter messages. The JSON
report preserves aggregate counts and language/source metrics without SMS
bodies. It does not include epoch loss history or runtime/latency measurements;
those details must be captured in a subsequent run log if training continues.
