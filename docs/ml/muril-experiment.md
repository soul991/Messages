# MuRIL Indic-language SMS classifier experiment

Updated: 2026-10-06

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

Training is paused after epoch 1. Its validation loss was 0.082157. The run was
interrupted eight steps into epoch 2; the selected epoch 1 weights were saved,
but optimizer state was not. Resume by fine-tuning from those weights, then
evaluate by language and on the frozen challenge. No test or challenge results
are available for this checkpoint yet. No deployment decision should be
inferred; even strong public-split results remain research-only if the
independent false-positive/recall tradeoff is poor or the model's
approximately 953 MB footprint is impractical for on-device inference.
