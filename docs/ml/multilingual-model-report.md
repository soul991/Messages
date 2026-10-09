# Bengali, Hindi, and English model experiment

Updated: 2026-10-02

## Models built

The training script compares scratch-trained character n-gram Naive Bayes and
sparse logistic SGD, checks language-balanced training, and evaluates a single
global model against a script-routed set of experts. Exact duplicate rows are
removed across splits before fitting or scoring. Thresholds are calibrated on
validation ham only, with a 1% false-positive target per route.

The single global model is not suitable: it has to reconcile incompatible
script distributions with one score and one threshold. The more promising
candidate is an experimental three-expert bundle routed by Unicode script:
Bengali-script majority → Bengali expert; Devanagari → Hindi expert; otherwise
→ Latin expert. The bundle is 918,320 bytes, below the existing 2 MiB asset
limit. The Android loader currently accepts a single model only, so this bundle
is not yet loadable by the app. The trainer now compares Naive Bayes and sparse
logistic SGD for every expert, alongside the feature-frequency and minimum
message-length sweeps; validation selected Naive Bayes for all three routes.

## Script-routed held-out results

| Expert route | Test rows | Scam recall | Ham false-positive rate | F1 |
|---|---:|---:|---:|---:|
| Bengali script | 689 | 95.7% (265/277) | 1.46% (6/412) | 0.967 |
| Devanagari Hindi | 461 | 45.5% (5/11) | 0.67% (3/450) | 0.526 |
| Latin fallback: English, Hinglish, Banglish | 8,450 | 81.5% (2,674/3,282) | 1.37% (71/5,168) | 0.887 |

Hindi is the main blocker. The Hindi test subset contains only 11 scam examples,
and this candidate catches five. Its training split has 585 scam versus 4,851
ham Devanagari examples; the validation split has only nine scams. On the
`Kaggle_Hindi_Merged` source, it catches 5 of 11 test scams. More independently
collected, reusable Hindi fraud examples are needed before making a strong
Hindi claim. The Bengali result is based on a
Bangladesh-focused dataset that includes generated/translated examples; it is
not evidence of the same performance on Indian Bengali traffic.

The one-model baseline illustrates why script-specific routing matters: after
separating Hindi and Hinglish, it reached 88.3% recall on the Bengali group,
51.0% on English, 0% on the small Devanagari Hindi scam subset, and 88.5% on
Hinglish with an 8.3% false-positive rate. The better expert results come with
an integration cost and do not solve Hindi quality.

## Integration decision

**Do not integrate or enable these candidates yet.** On the app-authored
holdout, the selected Latin expert caught 49/79 scams and flagged 6/59 ham
(10.2% false-positive rate), compared with the active model's 68/79 scams and
2/59 ham at its current threshold. That holdout is small and authored for
project regressions, but the difference still signals a real compatibility
problem. Keep advanced AI filtering default-off.

Logistic SGD was included in the new route-by-route validation sweep but was
not selected for any route. The global language-balanced model also sacrificed
too much English performance. The script-routed Naive Bayes bundle is the best
current experiment, not a production-ready model.

A newly added, source-separated intent-boundary challenge set shows a large
distribution shift: the active model flags 70/90 safe examples, while the
global scratch candidate flags 33/90 and the routed candidate 47/90, with
substantial scam misses in both candidates. None is suitable for automatic
filtering on this evidence. See the [frozen external challenge report](external-challenge-report.md)
for language and context breakdowns. The challenge data was not used for
training or model selection.

I also evaluated the Apache-2.0 [HinSpam Hinglish model](https://huggingface.co/Keshav0av/HinSpam-spam-detection)
as an unmodified reference. Its raw predictions have 99.0% recall but 33.3%
false positives on the small Hinglish slice, and its 476 MiB size is far over
the app's 2 MiB asset budget. A validation-calibrated boundary cuts its overall
test false-positive rate to 1.5%, but recall falls to 46.3%; Bengali code-mix
recall falls to 4.3%. It is not suitable to integrate or use as the sole
teacher. See the [full external baseline report](hinspam-external-baseline.md)
and aggregate results in [`hinspam-public-tests.json`](hinspam-public-tests.json).

## Limits and next work

- Published Bengali and ScamShield test records have been inspected in earlier
  work. Their splits are held out, but they are not pristine blind evidence.
- ScamShield combines datasets and synthetic training material. The Bengali
  research description also reports generated or translated examples.
- Two exact ScamShield validation/test duplicates were removed from test
  scoring; exact train/held-out overlaps were excluded from fitting.
- The Indian Telecom CSV lacks per-row language metadata and has broad spam/ham
  labels. It is used as supplemental training data only.
- A 120-row Apache-2.0 corpus advertised as multilingual Indian scam SMS was
  reviewed but excluded: its rows repeat a handful of fixed templates with
  urgency suffix substitutions and inconsistent language metadata. It would
  add synthetic template leakage rather than independent Hindi evidence.
- The trilingual Punjabi/Hindi/English challenge corpus is retained as
  source-separated evaluation data only; its authored advisory examples are
  not representative of ordinary SMS.
- I found another India-focused Hindi/English collection described as 1,000 ham
  plus 1,000 spam messages from 43 participants, but its GitHub repository does
  not state a reuse license and links to a separate data request page. It is
  not included until its data terms can be verified.
- Metrics do not guarantee real-world performance or 100% accuracy. Scam
  wording and promotions change over time.

Full counts, source-specific results, thresholds, and candidate paths are in
[`multilingual-model-report.json`](multilingual-model-report.json). Dataset
provenance and duplicate handling are in
[`multilingual-dataset-audit.md`](multilingual-dataset-audit.md).

## Reproduce

From the Android repository root, with `pyarrow` available:

```sh
python3 protection-engine/tools/train_multilingual_candidate.py
```

This reads the workspace `Datasets/` folder, does not print message contents,
and writes separate experimental artifacts:

- `protection-engine/training/ngram_multilingual_candidate.json`
- `protection-engine/training/ngram_language_experts_candidate.json`

The active app model and default-off setting are not changed by this trainer.
