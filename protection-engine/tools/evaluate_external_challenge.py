#!/usr/bin/env python3
"""Score frozen model artifacts on a source-separated authored challenge set.

This tool does not train models, tune thresholds, or print message contents.
The input challenge data must remain excluded from all training and selection.
"""
import argparse
import csv
import hashlib
import json
import os
import sys
from collections import Counter, defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)
import train_ngram as base  # noqa: E402
import train_multilingual_candidate as multi  # noqa: E402


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_rows(path):
    rows = []
    with open(path, encoding="utf-8-sig", newline="") as stream:
        for item in csv.DictReader(stream):
            label = item.get("label", "").strip().lower()
            text = item.get("text", "").strip()
            if label not in {"scam", "safe"} or not text:
                continue
            rows.append({"text": text, "label": "scam" if label == "scam" else "ham",
                         "language": item.get("language", "unknown").strip().lower(),
                         "contextType": item.get("context_type", "unknown").strip().lower()})
    return rows


def finish(count):
    tp, fp, fn, tn = (count[k] for k in ("tp", "fp", "fn", "tn"))
    precision = tp / max(1, tp + fp)
    recall = tp / max(1, tp + fn)
    f1 = 2 * precision * recall / max(1e-12, precision + recall)
    return {"rows": sum(count[k] for k in ("tp", "fp", "fn", "tn")),
            "scamRows": tp + fn, "hamRows": fp + tn,
            "tp": tp, "fp": fp, "fn": fn, "tn": tn,
            "precision": round(precision, 4), "recall": round(recall, 4),
            "f1": round(f1, 4), "falsePositiveRate": round(fp / max(1, fp + tn), 4)}


def evaluate(rows, scorer, threshold):
    groups = defaultdict(Counter)
    dimensions = (("all", "all"),)
    for row in rows:
        pred = scorer(row["text"]) > threshold
        result = "tp" if row["label"] == "scam" and pred else \
                 "fn" if row["label"] == "scam" else \
                 "fp" if pred else "tn"
        groups["all"][result] += 1
        groups["language:" + row["language"]][result] += 1
        groups["contextType:" + row["contextType"]][result] += 1
    return {key: finish(value) for key, value in sorted(groups.items())}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", default=os.path.abspath(os.path.join(
        ROOT, "..", "..", "Datasets", "trilingual-fraud-consumer-protection-v2", "data.csv")))
    parser.add_argument("--output", default=os.path.join(ROOT, "docs", "ml",
                                                           "external-challenge-report.json"))
    args = parser.parse_args()
    rows = read_rows(args.data)
    if not rows:
        raise SystemExit("no usable rows found")

    resource = os.path.join(ROOT, "protection-engine", "src", "main", "resources", "ngram_model.json")
    active_payload = json.load(open(resource, encoding="utf-8"))
    active_weights = {g: float(v) for g, v in active_payload["weights"].items()}
    base.MIN_WORDS = int(active_payload["minWords"])
    active_result = evaluate(rows, lambda text: base.score(active_weights, text),
                             float(active_payload["threshold"]))

    candidate_path = os.path.join(ROOT, "protection-engine", "training", "ngram_multilingual_candidate.json")
    candidate = json.load(open(candidate_path, encoding="utf-8"))
    candidate_weights = {g: float(v) for g, v in candidate["weights"].items()}
    global_result = evaluate(rows, lambda text: multi.score(candidate_weights, text,
                                                              int(candidate["minWords"])),
                             float(candidate["threshold"]))

    experts_path = os.path.join(ROOT, "protection-engine", "training", "ngram_language_experts_candidate.json")
    experts = json.load(open(experts_path, encoding="utf-8"))["models"]
    def expert_score(text):
        route = multi.script_route(text)
        model = experts.get(route)
        if not model:
            return 0.0
        return multi.score({g: float(v) for g, v in model["weights"].items()},
                           text, int(model["minWords"]))
    # A routed threshold varies by script; score each message against the
    # selected route's threshold without fitting or recalibrating anything.
    routed_groups = defaultdict(Counter)
    for row in rows:
        route = multi.script_route(row["text"])
        model = experts.get(route)
        prediction = bool(model and expert_score(row["text"]) > float(model["threshold"]))
        result = "tp" if row["label"] == "scam" and prediction else \
                 "fn" if row["label"] == "scam" else \
                 "fp" if prediction else "tn"
        routed_groups["all"][result] += 1
        routed_groups["language:" + row["language"]][result] += 1
        routed_groups["scriptRoute:" + route][result] += 1
        routed_groups["contextType:" + row["contextType"]][result] += 1

    source = {"name": "karanverma19/trilingual_fraud_consumer_protection_v2",
              "sha256": sha256(args.data), "rowCount": len(rows),
              "publisherLanguageCounts": dict(sorted(Counter(r["language"] for r in rows).items())),
              "labelCounts": dict(sorted(Counter(r["label"] for r in rows).items())),
              "contextTypeCounts": dict(sorted(Counter(r["contextType"] for r in rows).items()))}
    report = {
        "purpose": "Frozen external challenge evaluation only; this data is excluded from training and tuning.",
        "source": source,
        "activeAppModel": {"modelSha256": sha256(resource), "threshold": active_payload["threshold"],
                           "metrics": active_result},
        "globalScratchCandidate": {"modelSha256": sha256(candidate_path), "threshold": candidate["threshold"],
                                   "metrics": global_result},
        "scriptRoutedCandidate": {"modelSha256": sha256(experts_path),
                                  "metrics": {key: finish(value) for key, value in sorted(routed_groups.items())}},
        "caveats": ["Small authored contrastive challenge set, not representative field traffic.",
                    "Hindi/English are in scope; Punjabi is reported only as an out-of-scope diagnostic.",
                    "Evaluation results were not used to select models, features, or thresholds."]
    }
    os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)
    with open(args.output, "w", encoding="utf-8") as stream:
        json.dump(report, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
