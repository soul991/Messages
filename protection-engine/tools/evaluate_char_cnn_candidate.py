#!/usr/bin/env python3
"""Evaluate a saved scratch CNN at global and validation-calibrated thresholds.

No fitting or test-directed threshold selection occurs here. The external
challenge is filtered against exact text seen in any development split.
"""
import argparse
import csv
import hashlib
import json
from collections import defaultdict
from pathlib import Path

import torch
from torch.utils.data import DataLoader

import train_char_cnn as trainer


def thresholds_by_language(rows, probabilities, target_fpr):
    hams = defaultdict(list)
    for row, p in zip(rows, probabilities):
        if row["label"] == "ham":
            hams[row["eval_lang"]].append(p)
    result = {}
    for language, values in hams.items():
        values.sort()
        allowed = int(len(values) * target_fpr)
        index = max(0, len(values) - allowed - 1)
        result[language] = values[index] + 1e-7
    return result, {language: len(values) for language, values in hams.items()}


def load_challenge(path, known_hashes):
    rows = []
    valid_rows = 0
    exact_overlaps = 0
    with Path(path).open(encoding="utf-8-sig", newline="") as source:
        for item in csv.DictReader(source):
            label = str(item.get("label", "")).strip().lower()
            text = str(item.get("text", ""))
            if label not in {"safe", "scam"} or not text.strip():
                continue
            valid_rows += 1
            if trainer.digest(text) in known_hashes:
                exact_overlaps += 1
                continue
            language = str(item.get("language", "unknown")).strip().lower()
            row = {"text": text, "label": "ham" if label == "safe" else "scam",
                   "src": language, "eval_lang": trainer.script_language(text, "en"),
                   "publisherLanguage": language,
                   "contextType": str(item.get("context_type", "unknown"))}
            rows.append(row)
    return rows, valid_rows, exact_overlaps


def get_probs(rows, vocab, config, model, device):
    dataset = trainer.SmsDataset(rows, vocab, config["maxLength"])
    return trainer.predict(model, DataLoader(dataset, batch_size=256, shuffle=False), device)


def active_ngram_scores(rows, model_path):
    payload = json.loads(Path(model_path).read_text(encoding="utf-8"))
    weights = {key: float(value) for key, value in payload["weights"].items()}
    min_words = int(payload["minWords"])
    scores = []
    for row in rows:
        grams = trainer.data.base.grams(row["text"])
        if len(row["text"].split()) < min_words or not grams:
            scores.append(0.0)
        else:
            scores.append(sum(weights.get(gram, 0.0) for gram in grams) / len(grams))
    return payload, scores


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data-root", required=True)
    parser.add_argument("--candidate-dir", required=True)
    parser.add_argument("--challenge", required=True)
    parser.add_argument("--target-fpr", type=float, default=0.01)
    args = parser.parse_args()
    candidate_dir = Path(args.candidate_dir)
    fit_report = json.loads((candidate_dir / "report.json").read_text(encoding="utf-8"))
    checkpoint = torch.load(candidate_dir / "char_cnn.pt", map_location="cpu", weights_only=False)
    vocab, config = checkpoint["vocabulary"], checkpoint["config"]
    model = trainer.CharacterCNN(len(vocab) + 2, embedding_dim=config["embeddingDim"],
        channels=config["channelsPerKernel"], dropout=config["dropout"])
    model.load_state_dict(checkpoint["state_dict"])
    device = torch.device("mps" if torch.backends.mps.is_available() else
                          "cuda" if torch.cuda.is_available() else "cpu")
    model.to(device).eval()
    train, validation, test, audit = trainer.load_corpora(args.data_root)
    val_probs = get_probs(validation, vocab, config, model, device)
    test_probs = get_probs(test, vocab, config, model, device)
    global_threshold = trainer.pick_threshold(validation, val_probs, args.target_fpr)
    language_thresholds, validation_ham_counts = thresholds_by_language(
        validation, val_probs, args.target_fpr)
    challenge, challenge_valid_rows, challenge_overlaps = load_challenge(args.challenge,
        {trainer.digest(r["text"]) for r in train + validation + test})
    challenge_probs = get_probs(challenge, vocab, config, model, device)
    app_resource = Path(__file__).resolve().parents[1] / "src/main/resources/ngram_model.json"
    app_model, app_val = active_ngram_scores(validation, app_resource)
    _, app_test = active_ngram_scores(test, app_resource)
    _, app_challenge = active_ngram_scores(challenge, app_resource)
    app_validation_threshold = trainer.pick_threshold(validation, app_val, args.target_fpr)
    app_language_thresholds, app_validation_hams = thresholds_by_language(
        validation, app_val, args.target_fpr)
    calibrated = {**language_thresholds,
                  "english_or_romanized": language_thresholds.get("english_or_romanized", global_threshold)}
    app_calibrated = {**app_language_thresholds,
                      "english_or_romanized": app_language_thresholds.get("english_or_romanized", app_validation_threshold)}
    results = {
        "model": fit_report["model"], "deviceForEvaluation": str(device),
        "dataAudit": audit,
        "checkpoint": {"sha256": hashlib.sha256((candidate_dir / "char_cnn.pt").read_bytes()).hexdigest(),
                       "bytes": (candidate_dir / "char_cnn.pt").stat().st_size},
        "thresholdPolicy": "global and per-language thresholds selected on validation ham only; no test tuning",
        "globalValidationThreshold": global_threshold,
        "perLanguageValidationThresholds": language_thresholds,
        "validationHamCountsByLanguage": validation_ham_counts,
        "validationAtGlobalThreshold": trainer.summarize(validation, val_probs, global_threshold),
        "validationAtPerLanguageThresholds": trainer.summarize(validation, val_probs, calibrated),
        "testAtGlobalThreshold": trainer.summarize(test, test_probs, global_threshold),
        "testAtPerLanguageThresholds": trainer.summarize(test, test_probs, calibrated),
        "challengeSource": {"path": str(Path(args.challenge)),
            "sha256": hashlib.sha256(Path(args.challenge).read_bytes()).hexdigest(),
            "validRows": challenge_valid_rows, "exactOverlapRowsExcluded": challenge_overlaps,
            "scoredRows": len(challenge)},
        "challengeAtGlobalThreshold": trainer.summarize(challenge, challenge_probs, global_threshold),
        "challengeAtPerLanguageThresholds": trainer.summarize(challenge, challenge_probs, calibrated),
        "activeAppNgramBaseline": {
            "scope": "AiScorer n-gram contribution only; does not include the deterministic protection engine",
            "thresholdFromInstalledResource": app_model["threshold"],
            "testAtInstalledThreshold": trainer.summarize(test, app_test, float(app_model["threshold"])),
            "validationCalibratedGlobalThreshold": app_validation_threshold,
            "validationHamCountsByLanguage": app_validation_hams,
            "testAtValidationGlobalThreshold": trainer.summarize(test, app_test, app_validation_threshold),
            "testAtValidationPerLanguageThresholds": trainer.summarize(test, app_test, app_calibrated),
            "challengeAtInstalledThreshold": trainer.summarize(challenge, app_challenge, float(app_model["threshold"])),
            "challengeAtValidationGlobalThreshold": trainer.summarize(challenge, app_challenge, app_validation_threshold)
        },
        "caveats": ["Published test splits were inspected in earlier work; results are not pristine blind evidence.",
                    "The challenge set is small and authored, not representative production SMS.",
                    "Per-language thresholds with small validation samples are unstable; Hinglish has limited ham data.",
                    "The classifier is a research candidate and must not auto-delete or silently hide messages."]
    }
    destination = candidate_dir / "candidate-evaluation.json"
    destination.write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(results, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
