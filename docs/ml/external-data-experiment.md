# External multilingual SMS model experiment

Date: 2026-10-02

## What was trained

The existing character n-gram Naive Bayes model was retrained from scratch with
the external Bengali SMS smishing training split plus the existing project
training rows. No pretrained model weights are present in this classifier.
The validation split set the candidate's threshold; the test split was only
used for final scoring. Three exact training/validation duplicates were
excluded from fitting. No exact test overlap with the existing app training
rows was found.

The source is the MIT-licensed
[Bengali SMS Smishing Dataset](https://huggingface.co/datasets/shariul-islam/bengali-sms-smishing-dataset),
revision `6039685ef470c73915b44cba00da379ddd60a61d`. Its three splits have
4,903 training, 701 validation, and 1,401 test rows. Categories `smish` map to
scam; `normal` and `promo` map to ham. The variety field is retained for
per-variety reporting.

## Result

On the published test split, the existing model at its current threshold
flagged 478/561 scams (85.2% recall) and 313/840 ham messages (37.3% false
positive rate). The existing model recalibrated on the same validation ham
flagged 53/561 scams (9.4% recall) and 6/840 ham messages (0.7% false positive
rate). The scratch-trained candidate, with its threshold chosen on validation
ham, flagged 528/561 scams (94.1% recall) and 10/840 ham messages (1.2% false
positive rate). Candidate F1 was 96.1% on that split. The candidate is 309 KB,
within the model asset's 2 MiB limit.

| Variety | Candidate scam recall | Candidate ham false positives |
|---|---:|---:|
| Bengali script | 132/141 (93.6%) | 1/217 (0.5%) |
| Banglish | 134/141 (95.0%) | 5/219 (2.3%) |
| Code-mixed | 129/138 (93.5%) | 1/198 (0.5%) |
| English | 133/141 (94.3%) | 3/206 (1.5%) |

An additional near-template check found 64 of the 1,401 test messages had
whitespace-token Jaccard similarity of at least 0.8 to a fitted message. On the
remaining 1,337 rows, the candidate flagged 498/531 scams (93.8% recall) and
10/806 ham (1.2% false positive rate). This check reduces one kind of split
leakage; it does not make the data independent or real-world representative.

On the app's existing authored held-out set, at the validation-selected
threshold, the candidate caught 56/79 scams versus 68/79 for the existing
model; both flagged 2/59 ham. This is a regression on the app's own fragile
messages. The candidate is therefore retained as an experiment and is **not**
the active app model.

## Important data limitation

The Hugging Face card describes the split and taxonomy but does not document
how messages were sourced. The associated SmishDetect-LLM research record says
the 7,005-message corpus uses LLM-based translation and synthetic generation
because an organically collected corpus spanning these varieties and labels
was unavailable. See the
[research record](https://researchportal.murdoch.edu.au/esploro/outputs/graduate/A-Multilingual-Fine-Tuned-LLM-Framework-for/991005909771807891).
It is a Bangladesh-focused Bengali dataset, not an India-collected SMS corpus.
The test metrics are therefore a held-out synthetic/translated benchmark, not
evidence of deployment-level scam detection. The current advanced filter stays
off by default.

## India-focused dataset leads

These were reviewed as possible follow-up sources. Provenance and use terms
must be checked before merging them into the app dataset.

| Source | What its publisher reports | Use and caveat |
|---|---|---|
| [Spam SMS in Hindi Language](https://sites.google.com/cse.nits.ac.in/ramanujamge/publications/datasets-offered) | 3,894 Hindi spam/ham messages gathered from students' and peers' experiences; linked to IEEE DataPort DOI `10.21227/5y8x-n678`. | Best promising India-specific follow-up for Hindi. Labels cover broad spam, not solely fraud. The university page does not state a license; verify IEEE DataPort terms before training or redistribution. |
| [Indian Telecom SMS Spam Collection](https://github.com/junioralive/india-spam-sms-classification) | Repository reports 2,000+ Indian SMS spam/ham rows and an MIT license; it offers a contribution form. | Potential training source after auditing labels and provenance. The repository does not provide a published train/validation/test split. |
| [Spam SMS in Dravidian Languages](https://sites.google.com/cse.nits.ac.in/ramanujamge/publications/datasets-offered) | Nearly 7,700 messages across English, Tamil, Telugu, Kannada, and Malayalam, collected through a form and labeled by language experts; linked to IEEE DataPort DOI `10.21227/dcym-pd69`. | Useful if the product expands to those languages; it does not cover the current Hindi/Bengali/Gujarati/Punjabi focus. Verify DataPort terms. |
| [Trilingual Fraud & Consumer Protection Dataset](https://huggingface.co/datasets/karanverma19/trilingual_fraud_consumer_protection_v2) | Punjabi, Hindi, English/code-mixed; MIT license; the card explicitly says its samples are synthetic and highlights repetitive-pattern limitations. | Suitable only as labeled synthetic augmentation, not independent evaluation. |
| [India Multilingual Scam Message Dataset](https://huggingface.co/buckets/bhoomee/Indian_Multilingual_Scam_Message_Dataset-bucket) | 120 Hindi/Hinglish/English examples; Apache-2.0; described as “real-world inspired.” | Too small and synthetic-sounding for a held-out benchmark; possible edge-case augmentation after manual review. |

The [CloveAI India SMS dataset](https://huggingface.co/datasets/CloveAI/india-spam-sms)
advertises 20,010 rows and MIT licensing, but its README is empty and its public
examples include highly templated, implausible values. I do not recommend
using it as an independent test set until its collection and generation
process are documented.

## Reproduction

The runner is `protection-engine/tools/train_external_ngram.py`. It reads
Parquet through PyArrow and writes the experimental model to a separate output
path; it never overwrites the active resource by default. The current source
files were supplied at the workspace root and are not copied into the Android
runtime.
