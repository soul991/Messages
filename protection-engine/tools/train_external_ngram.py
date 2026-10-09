#!/usr/bin/env python3
"""Train and evaluate an experimental n-gram model on external SMS splits.

Inputs are JSONL with ``text`` and ``label`` fields (optional ``lang``), or
Parquet files with the same columns. Label aliases are intentionally narrow:
scam/smish/spam map to scam; ham/normal/genuine/protected/promo map to ham.
The training split alone is used for fitting; validation ham calibrates the
threshold; the test split is evaluated once and never used to tune it.

Example:
  python3 protection-engine/tools/train_external_ngram.py \
    --train /path/train.parquet --validation /path/validation.parquet \
    --test /path/test.parquet --output /private/tmp/ngram_candidate.json

Parquet support requires pandas and pyarrow. This script never prints message
text and fails on cross-split exact duplicates to avoid accidental leakage.
"""

import argparse
import hashlib
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)
import train_ngram as base  # noqa: E402


LABELS = {
    "scam": "scam", "smish": "scam", "spam": "scam",
    "ham": "ham", "normal": "ham", "genuine": "ham",
    "protected": "ham", "promo": "ham",
}


def read_rows(path):
    if path.lower().endswith((".parquet", ".pq")):
        try:
            import pyarrow.parquet as pq
        except ImportError as exc:
            raise SystemExit("Parquet input requires pyarrow in the active Python environment") from exc
        rows = pq.read_table(path).to_pylist()
    else:
        with open(path, encoding="utf-8") as handle:
            rows = [json.loads(line) for line in handle if line.strip()]
    normalized = []
    for i, row in enumerate(rows):
        text = str(row.get("text", "")).strip()
        raw_label = str(row.get("label", "")).strip().lower()
        if not text or raw_label not in LABELS:
            raise ValueError("row %d has empty text or unsupported label %r" % (i + 1, raw_label))
        normalized.append({"text": text, "label": LABELS[raw_label],
                           "lang": str(row.get("lang", row.get("language", row.get("source", "und")))),
                           "src": "external", "idx": -1, "fragile": False})
    if not normalized:
        raise ValueError("input split is empty: " + path)
    return normalized


def key(row):
    text = " ".join(row["text"].casefold().split())
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def evaluate(model, threshold, rows):
    counts = {"scam": 0, "ham": 0, "tp": 0, "fp": 0, "fn": 0, "tn": 0}
    by_lang = {}
    for row in rows:
        actual = row["label"]
        predicted = base.score(model, row["text"]) > threshold
        bucket = by_lang.setdefault(row["lang"], {"scam": 0, "ham": 0, "tp": 0, "fp": 0, "fn": 0, "tn": 0})
        counts[actual] += 1
        bucket[actual] += 1
        outcome = "tp" if actual == "scam" and predicted else \
                  "fn" if actual == "scam" else \
                  "fp" if predicted else "tn"
        counts[outcome] += 1
        bucket[outcome] += 1
    return {"overall": counts, "byLanguage": by_lang}


def token_set(text):
    return set(text.casefold().split())


def template_novelty(rows, fitted_rows, threshold=0.8):
    """Flag test messages sharing most whitespace tokens with any fit row."""
    fitted = [token_set(row["text"]) for row in fitted_rows]
    novel, near_template = [], []
    max_similarities = []
    for row in rows:
        tokens = token_set(row["text"])
        best = max((len(tokens & other) / max(1, len(tokens | other))
                    for other in fitted), default=0.0)
        max_similarities.append(best)
        (near_template if best >= threshold else novel).append(row)
    return novel, near_template, sorted(max_similarities)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--train", required=True)
    parser.add_argument("--validation", required=True)
    parser.add_argument("--test", required=True)
    parser.add_argument("--output", required=True, help="candidate model JSON path; never overwrites the app model by default")
    args = parser.parse_args()

    train, _, _, _ = base.load()
    ext_train = read_rows(args.train)
    validation = read_rows(args.validation)
    test = read_rows(args.test)
    splits = {"train": ext_train, "validation": validation, "test": test}
    hashes = {name: {key(row) for row in rows} for name, rows in splits.items()}
    validation_test_overlap = hashes["validation"] & hashes["test"]
    if validation_test_overlap:
        raise SystemExit("exact text leakage: %d duplicate messages across validation and test" % len(validation_test_overlap))
    # Some public corpora have duplicate lines even when their split files are
    # otherwise disjoint. Keep validation/test as authoritative and remove any
    # matching training rows so held-out evidence cannot affect fitting.
    heldout_hashes = hashes["validation"] | hashes["test"]
    ext_train_before_dedup = len(ext_train)
    ext_train = [row for row in ext_train if key(row) not in heldout_hashes]
    hashes["train"] = {key(row) for row in ext_train}

    fitted_rows = train + ext_train
    weights = base.train_model(fitted_rows)
    # The threshold is calibrated only on validation ham, never on test rows.
    threshold = base.pick_threshold(weights, validation)
    payload = {"version": 1, "ngramMin": base.NGRAM_MIN, "ngramMax": base.NGRAM_MAX,
               "minWords": base.MIN_WORDS, "threshold": threshold,
               "weights": {g: round(v, 3) for g, v in sorted(weights.items())}}
    output = os.path.abspath(args.output)
    os.makedirs(os.path.dirname(output), exist_ok=True)
    with open(output, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, separators=(",", ":"))
        handle.write("\n")

    # Also report whether any external test item already occurs in app training.
    # Such rows are leakage and must not be represented as independent evidence.
    base_hashes = {key(row) for row in train}
    test_overlap = hashes["test"] & base_hashes
    app_model_path = os.path.join(ROOT, "protection-engine", "src", "main",
                                  "resources", "ngram_model.json")
    app_model = json.load(open(app_model_path, encoding="utf-8"))
    app_weights = app_model["weights"]
    app_threshold = app_model["threshold"]
    app_validation_threshold = base.pick_threshold(app_weights, validation)
    novel_test, near_template_test, similarities = template_novelty(test, fitted_rows)
    result = {
        "trainingRows": len(fitted_rows),
        "externalTrainRows": len(ext_train),
        "externalTrainRowsRemovedForSplitOverlap": ext_train_before_dedup - len(ext_train),
        "validationRows": len(validation),
        "testRows": len(test),
        "thresholdFromValidationHamOnly": threshold,
        "testExactOverlapWithExistingTraining": len(test_overlap),
        "currentModelThreshold": app_threshold,
        "currentModelTestMetrics": evaluate(app_weights, app_threshold, test),
        "currentModelThresholdCalibratedOnValidation": app_validation_threshold,
        "currentModelValidationCalibratedTestMetrics": evaluate(app_weights, app_validation_threshold, test),
        "testMetrics": evaluate(weights, threshold, test),
        "testRowsWithHighTokenOverlapToTrainingAtJaccard0_8": len(near_template_test),
        "templateFilteredTestRows": len(novel_test),
        "templateFilteredCurrentModelMetrics": evaluate(app_weights, app_threshold, novel_test),
        "templateFilteredCurrentModelValidationCalibratedMetrics": evaluate(app_weights, app_validation_threshold, novel_test),
        "templateFilteredCandidateMetrics": evaluate(weights, threshold, novel_test),
        "nearestTrainTokenJaccardMedian": similarities[len(similarities) // 2],
        "candidateModel": output,
        "candidateBytes": os.path.getsize(output),
    }
    for metrics_key in ("currentModelTestMetrics", "currentModelValidationCalibratedTestMetrics",
                        "testMetrics", "templateFilteredCurrentModelMetrics",
                        "templateFilteredCurrentModelValidationCalibratedMetrics",
                        "templateFilteredCandidateMetrics"):
        m = result[metrics_key]["overall"]
        m["precision"] = m["tp"] / max(1, m["tp"] + m["fp"])
        m["recall"] = m["tp"] / max(1, m["tp"] + m["fn"])
        m["f1"] = 2 * m["precision"] * m["recall"] / max(1e-12, m["precision"] + m["recall"])
        m["falsePositiveRate"] = m["fp"] / max(1, m["fp"] + m["tn"])
    print(json.dumps(result, ensure_ascii=False, indent=2))
    if test_overlap:
        raise SystemExit("external test overlaps existing training data; metrics are reported but are not independent")


if __name__ == "__main__":
    main()
