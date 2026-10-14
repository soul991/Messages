# India focused n-gram candidate retraining

Updated: 2026-10-12

## Training run

I retrained an offline India focused scam/ham candidate using the workspace's
existing public and synthetic corpora. The 5,358 private-device SMS chart was
not used because its remarks do not provide scam/ham labels. The active app
model and routing gate were not replaced or enabled by this run.

- Deduplicated fit data: 75,336 rows.
- Validation: 8,892 rows; test: 9,600 rows. Threshold and candidate selection
  use validation only.
- Sources include ScamShield, the Bengali SMS dataset, the Indian telecom
  spam/ham CSV, and 87 eligible Hinglish examples after overlap exclusion.
- The selected global model is a language-balanced character n-gram Naive
  Bayes model with a six-word minimum and 20,000 features; size: 326,397 bytes.
- A three-expert file was also regenerated at
  `protection-engine/training/ngram_language_experts_candidate.json` (918,348
  bytes). Its script router selects Bengali, Devanagari Hindi, or Latin.

## Held-out results

The script-routed candidate produced these split results:

| Route | Scam recall | Safe-message false-positive rate | Counts |
|---|---:|---:|---|
| Bengali script | 95.7% | 1.21% | TP 265/277; FP 5/412 |
| Devanagari Hindi | 45.5% | 0.67% | TP 5/11; FP 3/450 |
| Latin fallback | 81.5% | 1.39% | TP 2,674/3,282; FP 72/5,168 |

On the app-authored holdout, its Latin expert recalled 49/79 scams and falsely
flagged 6/59 safe messages (10.2%). Hindi has only 11 test scams. These
published test splits were inspected in prior experiments and include
synthetic or mixed-source examples; they are not a fresh independent release
holdout. The Hindi recall and authored-holdout false-positive rate rule out
shipping this candidate as an automatic filter.

I then scored the newly written global weights and script-routed experts on the
frozen 159-row source-separated challenge without changing any thresholds.
The routed model caught 25/69 scams (36.2%) and falsely flagged 47/90 safe
messages (52.2%). On the in-scope English and Hindi rows alone, it caught 6/48
scams (12.5%) and flagged 19/61 safe messages (31.1%). The global model caught
15/69 scams (21.7%) and flagged 33/90 safe rows (36.7%). Punjabi is out of
scope for this app diagnostic but is reported separately; the script router
misroutes Punjabi into the Hindi expert, so its Punjabi result is not evidence
of Hindi performance. These challenge failures reject both new candidates for
integration.

The active n-gram model's separate retraining report is
[`ngram-training-report.txt`](ngram-training-report.txt). Its held-out ham
false-positive rate is 3.4%, also above the 1% target. Keep the app's
AI-filtering gate disabled until there is independent human-labeled evidence.

## Artifacts

- Global candidate weights: `D:\Istak\Messages\PRIVATE_SMS_REVIEW\india_variant_candidate_PRIVATE.json`
- Script-routed expert weights: `protection-engine/training/ngram_language_experts_candidate.json`
- Aggregate training/evaluation report: [`india-variant-candidate-2026-10-12.json`](india-variant-candidate-2026-10-12.json)
- Frozen challenge count report: `D:\Istak\Messages\PRIVATE_SMS_REVIEW\india_variant_frozen_challenge_PRIVATE.json`

The report contains counts and metrics only, with no SMS bodies. This is a
research candidate, not a public dataset approval or production release.
