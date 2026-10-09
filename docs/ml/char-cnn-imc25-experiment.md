# Compact character CNN with IMC25 scam-only augmentation

Updated: 2026-10-06

The from-scratch Unicode character CNN was trained with the same architecture,
seed, batch size, and 12-epoch cap as the existing CNN experiment. It added
2,481 deduplicated India-origin or Hindi positive examples from IMC25 to the
training split only. The selected checkpoint was epoch 12. Its size was
311,631 bytes, close to the original 309,071-byte checkpoint.

At the global threshold selected from validation ham for a 1% FPR target, the
augmented model scored the already-inspected public test at 3,323/3,570 scam
recall (93.08%) and 61/6,030 ham false positives (1.01%). This is slightly
below the
original CNN's public test recall of 93.4%. Language breakdown:

| Group | Scam recall | Ham false-positive rate | Counts |
|---|---:|---:|---|
| Bengali script | 91.34% | 0.73% | TP 253, FN 24, FP 3, TN 408 |
| English/romanized | 93.13% | 1.12% | TP 2,967, FN 219, FP 58, TN 5,100 |
| Devanagari Hindi | 63.64% | 0.00% | TP 7, FN 4, FP 0, TN 449 |
| Hinglish | 100.0% | 0.00% | TP 96, FN 0, FP 0, TN 12 |

On the untouched 159-row authored challenge, the augmented CNN detected 5/69
scams and falsely flagged 14/90 safe examples (7.25% recall, 15.56% FPR,
26.3% precision). The original CNN caught 3/69 and flagged 17/90 at its own
validation-calibrated threshold. This is a small numerical improvement, but
the resulting scam recall is still unusably low and the safe-message false
positive rate remains too high for automatic sorting. The augmented CNN caught
no Hindi or Punjabi challenge scams; its small 311 KB size does not offset
those errors.

The public test and challenge are not population-representative, and the
challenge has only 69 scams and 90 safe messages. Both are diagnostic evidence;
more fresh, consented Indian benign and scam examples are needed. The result
also shows that a positive-only augmentation can lower public-split recall
without solving the independent challenge gap.

**Decision:** do not integrate this checkpoint. Keep the original and
augmented CNNs as research-only artifacts. The aggregate fit and evaluation
reports are [`char-cnn-imc25-fit-report.json`](char-cnn-imc25-fit-report.json)
and [`char-cnn-imc25-evaluation.json`](char-cnn-imc25-evaluation.json); no
message text is stored in these reports. Reproduce training with
`train_char_cnn.py --imc-smishing-csv /path/to/final_dataset_output.csv`, then
run `evaluate_char_cnn_candidate.py` on the frozen challenge path. The source
license, hash, and filtering details are in
[`imc25-smishing-augmentation-audit.md`](imc25-smishing-augmentation-audit.md).
