#!/usr/bin/env python3
"""Evaluate a pinned Hugging Face classifier without tuning it.

No message bodies are printed. Requires torch and transformers in the research
runtime and local model files. Evaluation files are never used for training.
"""
import argparse, csv, hashlib, json
from collections import Counter, defaultdict
from pathlib import Path
import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer


def metrics(c):
    tp, fp, fn, tn = (c[k] for k in ("tp", "fp", "fn", "tn"))
    precision = tp / max(1, tp + fp)
    recall = tp / max(1, tp + fn)
    return {"n": tp + fp + fn + tn, "tp": tp, "fp": fp, "fn": fn, "tn": tn,
            "precision": round(precision, 4), "recall": round(recall, 4),
            "f1": round(2 * precision * recall / max(1e-12, precision + recall), 4),
            "falsePositiveRate": round(fp / max(1, fp + tn), 4)}


def read_rows(path):
    path = Path(path)
    if path.suffix == ".csv":
        with path.open(encoding="utf-8-sig", newline="") as f:
            raw = list(csv.DictReader(f))
    elif path.suffix == ".jsonl":
        # Do not use str.splitlines(): SMS bodies may contain Unicode line
        # separators such as U+2028, which are legal inside JSON strings.
        raw = [json.loads(line) for line in path.read_text(encoding="utf-8").split("\n") if line.strip()]
    elif path.suffix == ".parquet":
        import pyarrow.parquet as pq
        raw = pq.read_table(path).to_pylist()
    else:
        raise ValueError(f"unsupported input format: {path.suffix}")
    rows = []
    for row in raw:
        label = str(row.get("label", row.get("is_scam", ""))).strip().lower()
        if label in {"1", "smish", "scam", "spam"}:
            label = "scam"
        elif label in {"0", "normal", "promo", "ham", "safe", "legit"}:
            label = "ham"
        else:
            continue
        rows.append({"text": str(row.get("text", row.get("message", ""))),
                     "label": label,
                     "language": str(row.get("language", row.get("lang", row.get("source", "unknown")))),
                     "source": str(row.get("source_dataset", row.get("source", path.stem)))})
    return rows


def predict_rows(rows, model, tokenizer, positive_id, max_length, batch_size):
    scored = []
    for start in range(0, len(rows), batch_size):
        batch = rows[start:start + batch_size]
        texts = [r["text"] for r in batch]
        inputs = tokenizer(texts, padding=True, truncation=True,
                           max_length=max_length, return_tensors="pt")
        with torch.inference_mode():
            logits = model(**inputs).logits
            predictions = logits.argmax(-1).tolist()
            scores = logits.softmax(-1)[:, positive_id].tolist()
        scored.extend((row, prediction, score)
                      for row, prediction, score in zip(batch, predictions, scores))
    return scored


def evaluate(model_dir, input_paths, output, positive_id, validation_paths=(),
             calibrate_validation=False, max_length=256, batch_size=24):
    rows = [row for path in input_paths for row in read_rows(path)]
    validation_keys = set()
    for path in validation_paths:
        validation_keys.update(" ".join(row["text"].casefold().split()) for row in read_rows(path))
    if validation_keys:
        rows = [r for r in rows if " ".join(r["text"].casefold().split()) not in validation_keys]
    unique_rows, seen = [], {}
    duplicate_rows = label_conflicts = 0
    for row in rows:
        key = " ".join(row["text"].casefold().split())
        if key in seen:
            duplicate_rows += 1
            label_conflicts += int(seen[key] != row["label"])
            continue
        seen[key] = row["label"]
        unique_rows.append(row)
    rows = unique_rows
    model_dir = Path(model_dir)
    weights = model_dir / "model.safetensors"
    digest = hashlib.sha256(weights.read_bytes()).hexdigest()
    tokenizer = AutoTokenizer.from_pretrained(model_dir, local_files_only=True)
    model = AutoModelForSequenceClassification.from_pretrained(model_dir, local_files_only=True).eval()
    result = defaultdict(Counter)
    average_prob = defaultdict(list)
    scored_rows = predict_rows(rows, model, tokenizer, positive_id, max_length, batch_size)
    for row, prediction, score in scored_rows:
        gold = row["label"] == "scam"
        groups = ["all", "language:" + row.get("language", "unknown").strip().lower(),
                  "source:" + row.get("source", "unknown").strip().lower()]
        for group in groups:
            result[group]["tp" if gold and prediction == positive_id else
                          "fn" if gold else "fp" if prediction == positive_id else "tn"] += 1
        average_prob[(row.get("language", "unknown").lower(), "scam" if gold else "ham")].append(score)
    calibrated = None
    if calibrate_validation:
        validation_rows = [r for path in validation_paths for r in read_rows(path)]
        # De-duplicate calibration rows and exclude labels whose exact text is
        # present in the evaluated test inputs.
        test_keys = {" ".join(r["text"].casefold().split()) for r in rows}
        seen_val, clean_val = set(), []
        for row in validation_rows:
            key = " ".join(row["text"].casefold().split())
            if key in test_keys or key in seen_val:
                continue
            seen_val.add(key); clean_val.append(row)
        val_scored = predict_rows(clean_val, model, tokenizer, positive_id, max_length, batch_size)
        ham_scores = defaultdict(list)
        for row, _pred, score in val_scored:
            if row["label"] == "ham":
                ham_scores[row["language"].strip().lower()].append(score)
        thresholds = {}
        for language, values in ham_scores.items():
            values.sort(reverse=True)
            allowed = int(len(values) * 0.01)
            thresholds[language] = values[min(allowed, len(values) - 1)] if values else 1.0
        calibrated_counts = defaultdict(Counter)
        for row, _prediction, score in scored_rows:
            lang = row["language"].strip().lower()
            prediction = score > thresholds.get(lang, 1.0)
            gold = row["label"] == "scam"
            result_key = "tp" if gold and prediction else "fn" if gold else "fp" if prediction else "tn"
            calibrated_counts["all"][result_key] += 1
            calibrated_counts["language:" + lang][result_key] += 1
        calibrated = {"policy": "per-language threshold selected using validation ham only; target <=1% FPR",
                      "validationRowsAfterExactDedupAndTestExclusion": len(clean_val),
                      "validationHamCounts": {k: len(v) for k, v in sorted(ham_scores.items())},
                      "thresholds": {k: round(v, 8) for k, v in sorted(thresholds.items())},
                      "testMetrics": {k: metrics(v) for k, v in sorted(calibrated_counts.items())}}
    report = {"model": "Keshav0av/HinSpam-spam-detection",
              "revision": "3c0e106bf979c4295b8c31f26553c603651c892c",
              "weightsSha256": digest, "sizeBytes": weights.stat().st_size,
              "labelMapping": {"positiveClassId": positive_id,
                               "positiveLabelInterpretation": "publisher card says label 1 is spam; downloaded config omits id2label"},
              "inputs": [{"path": str(path), "sha256": hashlib.sha256(Path(path).read_bytes()).hexdigest(),
                          "usableRows": len(read_rows(path))} for path in input_paths],
              "validationFilesUsedOnlyForExactDuplicateExclusion": [str(x) for x in validation_paths],
              "scoredRowsAfterDuplicateExclusion": len(rows),
              "exactDuplicateRowsRemovedFromScoredInputs": duplicate_rows,
              "duplicateLabelConflicts": label_conflicts,
              "metrics": {k: metrics(v) for k, v in sorted(result.items())},
              "validationCalibratedMetrics": calibrated,
              "meanPositiveClassProbabilityByLanguageAndGold": {
                  k[0] + ":" + k[1]: round(sum(v) / len(v), 4)
                  for k, v in sorted(average_prob.items())},
              "caveats": ["No fitting, threshold tuning, or test-directed selection.",
                          "Model config omits explicit class names; results depend on label-1-as-spam convention.",
                          "Publisher model card claims Hinglish; English, Hindi, and Punjabi scores are transfer diagnostics."]}
    Path(output).parent.mkdir(parents=True, exist_ok=True)
    Path(output).write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))

if __name__ == "__main__":
    p=argparse.ArgumentParser()
    p.add_argument("--model-dir", required=True); p.add_argument("--input", required=True, nargs="+")
    p.add_argument("--validation", nargs="*", default=[])
    p.add_argument("--calibrate-validation", action="store_true")
    p.add_argument("--output", required=True); p.add_argument("--positive-id", type=int, default=1)
    args=p.parse_args(); evaluate(args.model_dir,args.input,args.output,args.positive_id,args.validation,args.calibrate_validation)
