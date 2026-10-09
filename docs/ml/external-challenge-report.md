# Frozen external challenge evaluation

Updated: 2026-10-02

## Purpose and source

This is a source-separated evaluation of the active app model and the two
experimental candidates. The dataset was not used for model fitting, feature
selection, threshold selection, or any other tuning. Source: [Hugging Face
dataset card](https://huggingface.co/datasets/karanverma19/trilingual_fraud_consumer_protection_v2),
which lists MIT licensing. The workspace copy and retrieval details are at
`Datasets/trilingual-fraud-consumer-protection-v2/`; its SHA-256 is
`1cc2ccb829756eb541760dc5f5dc7375894b04b1e8fd872d01616ea698a99ed9`.

The publisher describes 159 English, Hindi, and Punjabi examples, including
contrastive pairs, edge cases, and consumer-protection messages. It is an
authored challenge set, not a representative collection of SMS received by
Indian users. Some safe examples are advisories rather than ordinary personal,
transactional, or promotional SMS, so the result is a distribution-shift and
intent-boundary diagnostic rather than a deployment estimate. Punjabi is
outside the current model scope.

## Results at frozen thresholds

| Artifact | All rows: scam recall | All rows: ham false-positive rate | English: recall / FPR | Publisher-labeled Hindi: recall / FPR |
|---|---:|---:|---:|---:|
| Active app model | 82.6% (57/69) | 77.8% (70/90) | 96.3% (26/27) / 75.0% (24/32) | 71.4% (15/21) / 82.8% (24/29) |
| Global multilingual scratch candidate | 21.7% (15/69) | 36.7% (33/90) | 7.4% (2/27) / 28.1% (9/32) | 47.6% (10/21) / 41.4% (12/29) |
| Script-routed scratch candidate | 36.2% (25/69) | 52.2% (47/90) | 11.1% (3/27) / 12.5% (4/32) | 14.3% (3/21) / 51.7% (15/29) |

Neither scratch candidate is suitable for integration. The global candidate
reduces false positives on this challenge while missing most scams; the routed
candidate does not improve the Hindi or overall error tradeoff. The active
model also has an unacceptably high false-positive rate here. The data's
message style differs substantially from the app's existing holdout and the
larger public corpora, which confirms that current evidence is not sufficient
for broad automatic filtering claims.

## Reproduction

From the Android repository root, with the supplied CSV in the workspace
`Datasets/` directory:

```sh
python3 protection-engine/tools/evaluate_external_challenge.py
```

The runner prints aggregate counts only, never message contents, and writes
[`external-challenge-report.json`](external-challenge-report.json). It reads
the active model and the frozen candidate artifacts, applies their existing
thresholds, and does not modify any model.
