#!/usr/bin/env python3
"""Fine-tune a pinned multilingual MiniLM checkpoint on audited SMS splits.

This experiment stays separate from the Android runtime. It trains on fit rows
only, selects the checkpoint and scam threshold on validation only, then scores
test and the frozen external challenge once. Message bodies are never logged.
"""
import argparse
import csv
import hashlib
import json
import math
import random
import sys
from collections import Counter, defaultdict
from pathlib import Path

import torch
from torch.utils.data import DataLoader, Dataset
from transformers import (AutoModelForSequenceClassification, AutoTokenizer,
                          DataCollatorWithPadding, XLMRobertaTokenizer,
                          get_linear_schedule_with_warmup)

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))
import train_char_cnn as data  # noqa: E402
from evaluate_char_cnn_candidate import load_challenge  # noqa: E402

MODEL_REPO = "microsoft/Multilingual-MiniLM-L12-H384"
MODEL_REVISION = "6e8c1ec6b4ec4e3fc6eb7d2cd834fcd582b61daf"
MURIL_REPO = "google/muril-base-cased"
MURIL_REVISION = "afd9f36c7923d54e97903922ff1b260d091d202f"
SEED = 20261006


class EncodedSms(Dataset):
    def __init__(self, rows, tokenizer, max_length):
        self.labels = [int(row["label"] == "scam") for row in rows]
        self.features = []
        texts = [row["text"] for row in rows]
        for start in range(0, len(texts), 2048):
            batch = tokenizer(texts[start:start + 2048], truncation=True,
                              max_length=max_length, padding=False)
            self.features.extend({key: values[index] for key, values in batch.items()}
                                 for index in range(len(batch["input_ids"])))

    def __len__(self):
        return len(self.labels)

    def __getitem__(self, index):
        return {**self.features[index], "labels": self.labels[index]}


def digest(path):
    h = hashlib.sha256()
    with open(path, "rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def summarize(rows, probs, threshold):
    groups = defaultdict(Counter)
    for row, prob in zip(rows, probs):
        pred = prob >= threshold
        scam = row["label"] == "scam"
        bucket = "tp" if scam and pred else "fn" if scam else "fp" if pred else "tn"
        for key in ("all", "language:" + row["eval_lang"], "source:" + row["src"]):
            groups[key][bucket] += 1

    result = {}
    for name, c in sorted(groups.items()):
        tp, fp, fn, tn = (c[k] for k in ("tp", "fp", "fn", "tn"))
        precision = tp / max(1, tp + fp)
        recall = tp / max(1, tp + fn)
        result[name] = {"n": tp + fp + fn + tn, "tp": tp, "fp": fp,
                        "fn": fn, "tn": tn,
                        "precision": round(precision, 4),
                        "recall": round(recall, 4),
                        "f1": round(2 * precision * recall /
                                    max(1e-12, precision + recall), 4),
                        "falsePositiveRate": round(fp / max(1, fp + tn), 4)}
    return result


def pick_threshold(rows, probs, target_fpr):
    ham = sorted(prob for row, prob in zip(rows, probs)
                 if row["label"] == "ham")
    allowed = int(len(ham) * target_fpr)
    index = max(0, len(ham) - allowed - 1)
    return min(1.0, ham[index] + 1e-7) if ham else 1.0


def predict(model, rows, dataset, collator, device, batch_size):
    loader = DataLoader(dataset, batch_size=batch_size, shuffle=False,
                        collate_fn=collator, num_workers=0)
    model.eval()
    probs = []
    with torch.inference_mode():
        for batch in loader:
            batch = {key: value.to(device) for key, value in batch.items()
                     if key != "labels"}
            logits = model(**batch).logits
            probs.extend(torch.softmax(logits.float(), dim=-1)[:, 1].cpu().tolist())
    return probs


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data-root", default=str(ROOT.parent.parent / "Datasets"))
    parser.add_argument("--model-dir", default="/private/tmp/messages-minilm-model")
    parser.add_argument("--model-repo", default=MODEL_REPO)
    parser.add_argument("--model-revision", default=MODEL_REVISION)
    parser.add_argument("--tokenizer", choices=("xlm-roberta", "auto"), default="xlm-roberta")
    parser.add_argument("--out-dir", default="/private/tmp/messages-minilm-candidate")
    parser.add_argument("--challenge", default=str(ROOT.parent.parent / "Datasets" /
                        "trilingual-fraud-consumer-protection-v2" / "data.csv"))
    parser.add_argument("--imc-smishing-csv", default="",
                        help="Optional CC BY 4.0 IMC25 public-report CSV; unique India or Hindi scam texts are added to fit only.")
    parser.add_argument("--epochs", type=int, default=3)
    parser.add_argument("--freeze-encoder-layers", type=int, default=0,
                        help="Freeze embeddings and this many lowest encoder layers before fine-tuning.")
    parser.add_argument("--batch-size", type=int, default=24)
    parser.add_argument("--max-length", type=int, default=160)
    parser.add_argument("--learning-rate", type=float, default=2e-5)
    parser.add_argument("--target-fpr", type=float, default=0.01)
    parser.add_argument("--max-steps", type=int, default=0,
                        help="Optional bounded pilot; zero trains full epochs.")
    args = parser.parse_args()
    random.seed(SEED)
    torch.manual_seed(SEED)
    torch.set_num_threads(min(8, torch.get_num_threads()))
    device = torch.device("mps" if torch.backends.mps.is_available() else
                          "cuda" if torch.cuda.is_available() else "cpu")
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    train_rows, val_rows, test_rows, audit = data.load_corpora(args.data_root)
    imc_audit = {"provided": bool(args.imc_smishing_csv), "selectedRows": 0,
                 "duplicateRows": 0, "emptyRows": 0, "nonTargetRows": 0,
                 "overlapRows": 0, "sourceSha256": None}
    if args.imc_smishing_csv:
        imc_path = Path(args.imc_smishing_csv)
        imc_audit["sourceSha256"] = digest(imc_path)
        fit_keys = {data.digest(row["text"]) for row in train_rows + val_rows + test_rows}
        added_keys = set()
        with imc_path.open(encoding="utf-8", newline="") as stream:
            for record in csv.DictReader(stream):
                text = (record.get("text") or "").strip()
                if not text:
                    imc_audit["emptyRows"] += 1
                    continue
                language = (record.get("language") or "").strip().casefold()
                country = (record.get("original_network_country") or "").strip().upper()
                if country != "IND" and language != "hindi":
                    imc_audit["nonTargetRows"] += 1
                    continue
                key = data.digest(text)
                if key in fit_keys:
                    imc_audit["overlapRows"] += 1
                    continue
                if key in added_keys:
                    imc_audit["duplicateRows"] += 1
                    continue
                added_keys.add(key)
                train_rows.append({"text": text, "label": "scam",
                                   "eval_lang": data.script_language(text, language or "en"),
                                   "src": "imc25_public_reports_india_or_hindi"})
                imc_audit["selectedRows"] += 1
    audit["imc25ScamOnlyAugmentation"] = imc_audit
    print(json.dumps({"stage": "data_loaded", "train": len(train_rows),
                      "validation": len(val_rows), "test": len(test_rows),
                      "audit": audit, "device": str(device)}), flush=True)
    tokenizer_class = XLMRobertaTokenizer if args.tokenizer == "xlm-roberta" else AutoTokenizer
    tokenizer = tokenizer_class.from_pretrained(args.model_dir)
    train_data = EncodedSms(train_rows, tokenizer, args.max_length)
    val_data = EncodedSms(val_rows, tokenizer, args.max_length)
    test_data = EncodedSms(test_rows, tokenizer, args.max_length)
    collator = DataCollatorWithPadding(tokenizer=tokenizer, return_tensors="pt")
    train_loader = DataLoader(train_data, batch_size=args.batch_size, shuffle=True,
                              collate_fn=collator, num_workers=0)

    id2label, label2id = {0: "ham", 1: "scam"}, {"ham": 0, "scam": 1}
    model = AutoModelForSequenceClassification.from_pretrained(
        args.model_dir, num_labels=2, id2label=id2label, label2id=label2id,
        ignore_mismatched_sizes=True).to(device)
    if args.freeze_encoder_layers:
        base = model.base_model
        layers = getattr(getattr(base, "encoder", None), "layer", None)
        if layers is None or not 0 <= args.freeze_encoder_layers < len(layers):
            raise ValueError("freeze-encoder-layers must be less than the model encoder depth")
        for parameter in base.embeddings.parameters():
            parameter.requires_grad = False
        for layer in layers[:args.freeze_encoder_layers]:
            for parameter in layer.parameters():
                parameter.requires_grad = False
    trainable_parameters = [parameter for parameter in model.parameters()
                            if parameter.requires_grad]
    trainable_parameter_count = sum(parameter.numel() for parameter in trainable_parameters)
    positive = sum(train_data.labels)
    negative = len(train_data.labels) - positive
    loss_fn = torch.nn.CrossEntropyLoss(weight=torch.tensor(
        [1.0, negative / max(1, positive)], dtype=torch.float32, device=device))
    optimizer = torch.optim.AdamW(trainable_parameters, lr=args.learning_rate,
                                  weight_decay=0.01)
    total_steps = (math.ceil(len(train_loader)) * args.epochs
                   if not args.max_steps else args.max_steps)
    warmup = max(1, int(total_steps * 0.06))
    scheduler = get_linear_schedule_with_warmup(optimizer, warmup, total_steps)
    best_val_loss, best_epoch, stale = float("inf"), 0, 0
    history, steps = [], 0
    best_dir = out_dir / "best"

    for epoch in range(args.epochs):
        model.train()
        running, seen = 0.0, 0
        for batch in train_loader:
            labels = batch["labels"].to(device)
            batch = {key: value.to(device) for key, value in batch.items()
                     if key != "labels"}
            optimizer.zero_grad(set_to_none=True)
            logits = model(**batch).logits.float()
            loss = loss_fn(logits, labels)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optimizer.step()
            scheduler.step()
            running += loss.item() * len(labels)
            seen += len(labels)
            steps += 1
            if steps % 100 == 0:
                print(json.dumps({"stage": "training", "epoch": epoch + 1,
                                  "steps": steps,
                                  "meanLoss": round(running / max(1, seen), 5)}),
                      flush=True)
            if args.max_steps and steps >= args.max_steps:
                break

        val_loader = DataLoader(val_data, batch_size=args.batch_size, shuffle=False,
                                collate_fn=collator, num_workers=0)
        model.eval()
        val_loss_sum, val_seen, val_probs = 0.0, 0, []
        with torch.inference_mode():
            for batch in val_loader:
                labels = batch["labels"].to(device)
                batch = {key: value.to(device) for key, value in batch.items()
                         if key != "labels"}
                logits = model(**batch).logits.float()
                val_loss_sum += loss_fn(logits, labels).item() * len(labels)
                val_seen += len(labels)
                val_probs.extend(torch.softmax(logits, dim=-1)[:, 1].cpu().tolist())
        val_loss = val_loss_sum / max(1, val_seen)
        history.append({"epoch": epoch + 1, "steps": steps,
                        "trainLoss": round(running / max(1, seen), 6),
                        "validationLoss": round(val_loss, 6)})
        print(json.dumps({"stage": "epoch_end", **history[-1]}), flush=True)
        if val_loss < best_val_loss:
            best_val_loss, best_epoch, stale = val_loss, epoch + 1, 0
            if best_dir.exists():
                import shutil
                shutil.rmtree(best_dir)
            model.save_pretrained(best_dir, safe_serialization=True)
            tokenizer.save_pretrained(best_dir)
        else:
            stale += 1
        if (stale >= 2 or args.max_steps and steps >= args.max_steps):
            break

    # Reload the validation-selected checkpoint before score calibration/evaluation.
    model = AutoModelForSequenceClassification.from_pretrained(best_dir).to(device)
    val_probs = predict(model, val_rows, val_data, collator, device, args.batch_size)
    threshold = pick_threshold(val_rows, val_probs, args.target_fpr)
    test_probs = predict(model, test_rows, test_data, collator, device, args.batch_size)
    known = {data.digest(row["text"]) for row in train_rows + val_rows + test_rows}
    challenge_rows, valid_challenge, overlaps = load_challenge(args.challenge, known)
    challenge_data = EncodedSms(challenge_rows, tokenizer, args.max_length)
    challenge_probs = predict(model, challenge_rows, challenge_data, collator,
                              device, args.batch_size)
    report = {
        "model": args.model_repo, "revision": args.model_revision,
        "modelFileSha256": digest(best_dir / "model.safetensors"),
        "checkpointBytes": (best_dir / "model.safetensors").stat().st_size,
        "device": str(device), "seed": SEED, "epochs": history,
        "bestEpoch": best_epoch, "thresholdPolicy":
            f"validation ham only, target FPR <= {args.target_fpr}",
        "threshold": threshold, "batchSize": args.batch_size,
        "maxLength": args.max_length, "learningRate": args.learning_rate,
        "freezeEncoderLayers": args.freeze_encoder_layers,
        "trainableParameters": trainable_parameter_count,
        "classCounts": {"train": dict(Counter(r["label"] for r in train_rows)),
                        "validation": dict(Counter(r["label"] for r in val_rows)),
                        "test": dict(Counter(r["label"] for r in test_rows))},
        "dataAudit": audit,
        "validationMetrics": summarize(val_rows, val_probs, threshold),
        "testMetrics": summarize(test_rows, test_probs, threshold),
        "challenge": {"validRows": valid_challenge, "exactOverlapsExcluded": overlaps,
                      "scoredRows": len(challenge_rows),
                      "metrics": summarize(challenge_rows, challenge_probs, threshold)},
        "caveats": ["Public test splits have been inspected in earlier work.",
                    "The challenge set is small and authored, not representative traffic.",
                    "This transformer checkpoint is an experimental teacher, not an Android asset."]
    }
    (out_dir / "report.json").write_text(json.dumps(report, ensure_ascii=False,
                                                       indent=2) + "\n")
    print(json.dumps({"stage": "complete", "bestEpoch": best_epoch,
                      "threshold": threshold,
                      "validation": report["validationMetrics"].get("all"),
                      "test": report["testMetrics"].get("all"),
                      "challenge": report["challenge"]["metrics"].get("all"),
                      "report": str(out_dir / "report.json")}), flush=True)


if __name__ == "__main__":
    main()
