#!/usr/bin/env python3
"""Train a compact Unicode character CNN from scratch for SMS risk triage.

The script is intended for a GPU notebook (Kaggle/Colab), but also runs on CPU.
It reads the workspace Datasets folder, removes exact cross-split duplicates,
calibrates a high-precision threshold on validation ham only, and writes a
separate research artifact. It never prints SMS bodies or alters the app model.
"""
import argparse
import csv
import hashlib
import json
import os
import random
import sys
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np
import torch
from torch import nn
from torch.utils.data import DataLoader, Dataset

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))
import train_multilingual_candidate as data  # noqa: E402

SEED = 20261005


def digest(text):
    return hashlib.sha256(" ".join(text.casefold().split()).encode("utf-8")).hexdigest()


def script_language(text, fallback):
    chars = [c for c in text if c.isalpha()]
    bn = sum("\u0980" <= c <= "\u09ff" for c in chars)
    hi = sum("\u0900" <= c <= "\u097f" for c in chars)
    if bn > hi and bn:
        return "bengali"
    if hi:
        return "hindi"
    if fallback in ("hinglish", "hi"):
        return "hinglish" if fallback == "hinglish" else "english_or_romanized"
    return "english_or_romanized"


def load_corpora(dataset_root):
    root = Path(dataset_root)
    ss = root / "scamshield-dataset"
    bn = root / "bengali-sms-smishing-dataset"
    train, _ = data.read_jsonl(str(ss / "train.jsonl"), "train")
    val, _ = data.read_jsonl(str(ss / "val.jsonl"), "val")
    test, _ = data.read_jsonl(str(ss / "test.jsonl"), "test")
    bn_train = data.read_bengali(str(bn / "train-00000-of-00001.parquet"))
    bn_val = data.read_bengali(str(bn / "validation-00000-of-00001.parquet"))
    bn_test = data.read_bengali(str(bn / "test-00000-of-00001.parquet"))
    india_path = root / "india-spam-sms-classification-main" / "dataset" / "spam_ham_india.csv"
    india = data.read_india_csv(str(india_path))
    bolewara_path = root / "bolewara-hinglish-scam-text-dataset" / "data.json"
    bolewara = data.read_bolewara(str(bolewara_path))

    validation = data.unique(val + bn_val)
    validation_keys = {digest(r["text"]) for r in validation}
    test = data.unique(test + bn_test)
    test_validation_overlap = sum(digest(r["text"]) in validation_keys for r in test)
    test = [r for r in test if digest(r["text"]) not in validation_keys]
    held_keys = validation_keys | {digest(r["text"]) for r in test}
    raw_training = train + bn_train + india + bolewara
    # Exclude identical text with conflicting labels rather than allowing
    # source-order-dependent labels to leak into fit/evaluation.
    labels_by_text = defaultdict(set)
    rows_by_text = defaultdict(list)
    for row in raw_training + validation + test:
        labels_by_text[digest(row["text"])].add(row["label"])
        rows_by_text[digest(row["text"])].append(row)
    conflicting = {key for key, labels in labels_by_text.items() if len(labels) > 1}
    conflict_sources = Counter()
    for key in conflicting:
        sources = tuple(sorted({r["src"] for r in rows_by_text[key]}))
        conflict_sources[" | ".join(sources)] += 1
    validation = [r for r in validation if digest(r["text"]) not in conflicting]
    test = [r for r in test if digest(r["text"]) not in conflicting]
    held_keys = {digest(r["text"]) for r in validation + test}
    training = data.unique([r for r in raw_training
                            if digest(r["text"]) not in held_keys | conflicting])
    train_keys = {digest(r["text"]) for r in training}
    validation_keys = {digest(r["text"]) for r in validation}
    test_keys = {digest(r["text"]) for r in test}
    if train_keys & validation_keys or train_keys & test_keys or validation_keys & test_keys:
        raise RuntimeError("exact-text leakage remains across fit, validation, and test")
    # Report broad, auditable groups, not inferred fine-grained language claims.
    for rows in (training, validation, test):
        for row in rows:
            row["eval_lang"] = script_language(row["text"], row.get("lang", "en"))
    return training, validation, test, {
        "conflictingExactTextsExcluded": len(conflicting),
        "conflictSourceGroups": dict(sorted(conflict_sources.items())),
        "testRowsRemovedForValidationOverlap": test_validation_overlap,
        "exactTextSplitLeakageAfterCleaning": 0,
    }


def build_vocab(rows, max_vocab):
    counts = Counter(ch for row in rows for ch in row["text"])
    # PAD=0, OOV=1. Character vocabulary is built from fit data only.
    chars = [ch for ch, _ in counts.most_common(max_vocab - 2)]
    return {ch: i + 2 for i, ch in enumerate(chars)}


class SmsDataset(Dataset):
    def __init__(self, rows, vocab, max_len):
        encoded = np.zeros((len(rows), max_len), dtype=np.int32)
        self.labels = torch.tensor([row["label"] == "scam" for row in rows], dtype=torch.float32)
        for index, row in enumerate(rows):
            chars = row["text"][:max_len]
            encoded[index, :len(chars)] = [vocab.get(ch, 1) for ch in chars]
        self.ids = torch.from_numpy(encoded.astype(np.int64, copy=False))

    def __len__(self):
        return len(self.labels)

    def __getitem__(self, index):
        return self.ids[index], self.labels[index]


class CharacterCNN(nn.Module):
    def __init__(self, vocab_size, embedding_dim=32, channels=64, dropout=0.35):
        super().__init__()
        self.embedding = nn.Embedding(vocab_size, embedding_dim, padding_idx=0)
        self.convs = nn.ModuleList(nn.Conv1d(embedding_dim, channels, k, padding=k // 2)
                                   for k in (2, 3, 4, 5))
        self.head = nn.Sequential(nn.Dropout(dropout), nn.Linear(channels * 4, 128),
                                  nn.GELU(), nn.Dropout(dropout), nn.Linear(128, 1))

    def forward(self, ids):
        x = self.embedding(ids).transpose(1, 2)
        pooled = [torch.relu(conv(x)).amax(dim=-1) for conv in self.convs]
        return self.head(torch.cat(pooled, dim=-1)).squeeze(-1)


def summarize(rows, probs, threshold):
    by_language, by_source, by_context = defaultdict(Counter), defaultdict(Counter), defaultdict(Counter)
    for row, prob in zip(rows, probs):
        row_threshold = threshold.get(row["eval_lang"], 1.0) if isinstance(threshold, dict) else threshold
        gold, pred = row["label"] == "scam", prob >= row_threshold
        key = "tp" if gold and pred else "fn" if gold else "fp" if pred else "tn"
        buckets = [by_language[row["eval_lang"]], by_source[row["src"]]]
        if row.get("contextType"):
            buckets.append(by_context[row["contextType"]])
        for bucket in buckets:
            bucket["n"] += 1
            bucket[key] += 1
    def finish(grouped):
        out = {}
        for name, c in sorted(grouped.items()):
            precision = c["tp"] / max(1, c["tp"] + c["fp"])
            recall = c["tp"] / max(1, c["tp"] + c["fn"])
            out[name] = {**c, "precision": round(precision, 4), "recall": round(recall, 4),
                         "f1": round(2 * precision * recall / max(1e-12, precision + recall), 4),
                         "falsePositiveRate": round(c["fp"] / max(1, c["fp"] + c["tn"]), 4)}
        return out
    result = {"byLanguage": finish(by_language), "bySource": finish(by_source)}
    if by_context:
        result["byContextType"] = finish(by_context)
    return result


def pick_threshold(rows, probs, target_fpr):
    ham = sorted(p for row, p in zip(rows, probs) if row["label"] == "ham")
    if not ham:
        return 1.0
    # Conservative order statistic: no more than floor(target*n) validation ham
    # examples may exceed the threshold. Threshold is global and selected once.
    allowed = int(len(ham) * target_fpr)
    index = max(0, len(ham) - allowed - 1)
    return min(1.0, ham[index] + 1e-7)


def predict(model, loader, device):
    model.eval()
    output = []
    with torch.inference_mode():
        for ids, _labels in loader:
            output.extend(torch.sigmoid(model(ids.to(device))).cpu().tolist())
    return output


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data-root", default=str(ROOT.parent.parent / "Datasets"))
    parser.add_argument("--imc-smishing-csv", default="",
                        help="Optional CC BY 4.0 IMC25 public-report CSV; unique India or Hindi scam texts are added to fit only.")
    parser.add_argument("--out-dir", default=str(ROOT / "protection-engine" / "training" / "char_cnn"))
    parser.add_argument("--epochs", type=int, default=12)
    parser.add_argument("--batch-size", type=int, default=256)
    parser.add_argument("--max-len", type=int, default=256)
    parser.add_argument("--max-vocab", type=int, default=12000)
    parser.add_argument("--patience", type=int, default=3)
    parser.add_argument("--target-fpr", type=float, default=0.01)
    args = parser.parse_args()
    random.seed(SEED); torch.manual_seed(SEED)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(SEED)
    if torch.backends.mps.is_available():
        device = torch.device("mps")
    elif torch.cuda.is_available():
        device = torch.device("cuda")
    else:
        device = torch.device("cpu")
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    train_rows, val_rows, test_rows, data_audit = load_corpora(args.data_root)
    if args.imc_smishing_csv:
        path = Path(args.imc_smishing_csv)
        file_hash = hashlib.sha256(path.read_bytes()).hexdigest()
        split_keys = {digest(r["text"]) for r in train_rows + val_rows + test_rows}
        added, audit = set(), Counter()
        with path.open(encoding="utf-8", newline="") as stream:
            for row in csv.DictReader(stream):
                text = (row.get("text") or "").strip()
                if not text:
                    audit["emptyRows"] += 1
                    continue
                language = (row.get("language") or "").strip().casefold()
                country = (row.get("original_network_country") or "").strip().upper()
                if country != "IND" and language != "hindi":
                    audit["nonTargetRows"] += 1
                    continue
                key = digest(text)
                if key in split_keys:
                    audit["overlapRows"] += 1
                    continue
                if key in added:
                    audit["duplicateRows"] += 1
                    continue
                added.add(key)
                train_rows.append({"text": text, "label": "scam",
                                   "eval_lang": script_language(text, language or "en"),
                                   "src": "imc25_public_reports_india_or_hindi"})
                audit["selectedRows"] += 1
        data_audit["imc25ScamOnlyAugmentation"] = {
            "sourceSha256": file_hash, **dict(sorted(audit.items()))}
    vocab = build_vocab(train_rows, args.max_vocab)
    def loader(rows, shuffle=False):
        return DataLoader(SmsDataset(rows, vocab, args.max_len), batch_size=args.batch_size,
                          shuffle=shuffle, num_workers=0, pin_memory=device.type == "cuda")
    train_loader, val_loader, test_loader = loader(train_rows, True), loader(val_rows), loader(test_rows)
    embedding_dim, channels, dropout = 32, 64, 0.35
    model = CharacterCNN(len(vocab) + 2, embedding_dim=embedding_dim,
                         channels=channels, dropout=dropout).to(device)
    labels = torch.tensor([r["label"] == "scam" for r in train_rows], dtype=torch.float32)
    positives, negatives = int(labels.sum()), int(len(labels) - labels.sum())
    loss_fn = nn.BCEWithLogitsLoss(pos_weight=torch.tensor([negatives / max(1, positives)], device=device))
    optimizer = torch.optim.AdamW(model.parameters(), lr=0.001, weight_decay=1e-4)
    best = None
    best_val_loss = float("inf")
    stale_epochs = 0
    history = []
    for epoch in range(args.epochs):
        model.train(); total_loss = 0.0; total = 0
        for ids, targets in train_loader:
            ids, targets = ids.to(device), targets.to(device)
            optimizer.zero_grad(set_to_none=True)
            loss = loss_fn(model(ids), targets)
            loss.backward(); nn.utils.clip_grad_norm_(model.parameters(), 1.0); optimizer.step()
            total_loss += loss.item() * len(ids); total += len(ids)
        model.eval(); val_loss = 0.0; nval = 0
        with torch.inference_mode():
            for ids, targets in val_loader:
                ids, targets = ids.to(device), targets.to(device)
                loss = loss_fn(model(ids), targets)
                val_loss += loss.item() * len(ids); nval += len(ids)
        val_loss /= max(1, nval)
        history.append({"epoch": epoch + 1, "trainLoss": round(total_loss / max(1, total), 6),
                        "validationLoss": round(val_loss, 6)})
        print(json.dumps({"epoch": epoch + 1, "epochs": args.epochs,
                          "trainLoss": history[-1]["trainLoss"],
                          "validationLoss": history[-1]["validationLoss"],
                          "device": str(device)}), flush=True)
        if val_loss < best_val_loss:
            best_val_loss = val_loss
            best = {k: v.detach().cpu().clone() for k, v in model.state_dict().items()}
            stale_epochs = 0
            # Persist the best weights after every improvement so an interrupted
            # long run does not lose its current best candidate.
            torch.save(best, out_dir / "best_state_dict.pt")
        else:
            stale_epochs += 1
        if stale_epochs >= args.patience:
            break
    model.load_state_dict(best)
    val_probs, test_probs = predict(model, val_loader, device), predict(model, test_loader, device)
    threshold = pick_threshold(val_rows, val_probs, args.target_fpr)
    report = {
        "model": "Unicode character CNN trained from random initialization",
        "seed": SEED, "device": str(device), "architecture": {"embeddingDim": embedding_dim,
            "channelsPerKernel": channels, "kernels": [2, 3, 4, 5], "dropout": dropout,
            "maxLength": args.max_len, "vocabularySize": len(vocab)},
        "data": {"trainRows": len(train_rows), "validationRows": len(val_rows), "testRows": len(test_rows),
                 **data_audit,
                 "trainSha256": hashlib.sha256("\n".join(sorted(digest(r["text"]) for r in train_rows)).encode()).hexdigest(),
                 "validationSha256": hashlib.sha256("\n".join(sorted(digest(r["text"]) for r in val_rows)).encode()).hexdigest(),
                 "testSha256": hashlib.sha256("\n".join(sorted(digest(r["text"]) for r in test_rows)).encode()).hexdigest()},
        "thresholdPolicy": f"single global threshold calibrated on validation ham to target FPR <= {args.target_fpr:.4f}",
        "selectedThreshold": threshold, "epochs": history,
        "validation": summarize(val_rows, val_probs, threshold),
        "test": summarize(test_rows, test_probs, threshold),
        "caveats": ["Published test sets have been used in prior experiments and are not pristine blind evidence.",
                    "ScamShield contains multiple sources and synthetic data; source-level scores must be reviewed.",
                    "No model is safe to auto-delete or hide messages on this evidence."]}
    portable_state = {k: v.detach().cpu() for k, v in model.state_dict().items()}
    torch.save({"state_dict": portable_state, "vocabulary": vocab,
                "config": report["architecture"], "threshold": threshold}, out_dir / "char_cnn.pt")
    (out_dir / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
