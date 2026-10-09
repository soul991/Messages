#!/usr/bin/env python3
"""Measure an offline multilingual embedding teacher on the local corpus.

This is a training-only experiment. It never sends message text to a service,
and it does not export or integrate the pretrained model. The classifier head
is fit from scratch on the project's train split and evaluated on the same
constructed split used by train_ngram.py.

Dependencies are intentionally isolated to this research tool:
  pip install numpy onnxruntime tokenizers
"""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

import numpy as np
import onnxruntime as ort
from tokenizers import Tokenizer


ROOT = Path(__file__).resolve().parents[2]
CORPUS = ROOT / "protection-engine/src/test/resources/corpus.json"
EXPANDED = ROOT / "protection-engine/training/synthetic_messages.json"
FRAGILE = ROOT / "protection-engine/training/held_out_fragile_indices.json"
MAX_LENGTH = 128
BATCH_SIZE = 32


def load_rows():
    corpus = json.loads(CORPUS.read_text(encoding="utf-8"))["entries"]
    expanded = json.loads(EXPANDED.read_text(encoding="utf-8"))["entries"]
    fragile = set(json.loads(FRAGILE.read_text(encoding="utf-8")))
    train, held = [], []
    for i, entry in enumerate(corpus):
        row = {"text": entry["text"],
               "label": int(entry["label"] == "scam"),
               "lang": "en",
               "source": "corpus",
               "index": i,
               "fragile": i in fragile}
        (held if i in fragile or i % 5 == 0 else train).append(row)
    for entry in expanded:
        # The teacher card explicitly lists Hindi/Gujarati but not Bengali or
        # Punjabi. Those unsupported scripts remain outside this experiment.
        lang = entry["lang"]
        row = {"text": entry["text"],
               "label": int(entry["label"] == "scam"),
               "lang": lang,
               "source": "synthetic",
               "index": -1,
               "fragile": False}
        train.append(row)
    return train, held, corpus


def make_embeddings(rows: list[dict], model_dir: Path) -> np.ndarray:
    tokenizer = Tokenizer.from_file(str(model_dir / "tokenizer.json"))
    tokenizer.enable_truncation(max_length=MAX_LENGTH)
    session = ort.InferenceSession(
        str(model_dir / "onnx/model_qint8_arm64.onnx"),
        providers=["CPUExecutionProvider"],
    )
    input_names = {x.name for x in session.get_inputs()}
    all_vectors = []
    for start in range(0, len(rows), BATCH_SIZE):
        batch = rows[start:start + BATCH_SIZE]
        encodings = tokenizer.encode_batch([r["text"] for r in batch])
        width = max(len(x.ids) for x in encodings)
        ids = np.zeros((len(batch), width), dtype=np.int64)
        masks = np.zeros_like(ids)
        types = np.zeros_like(ids)
        for i, encoding in enumerate(encodings):
            size = len(encoding.ids)
            ids[i, :size] = encoding.ids
            masks[i, :size] = encoding.attention_mask
            types[i, :size] = encoding.type_ids
        feed = {"input_ids": ids, "attention_mask": masks}
        if "token_type_ids" in input_names:
            feed["token_type_ids"] = types
        token_vectors = session.run(["last_hidden_state"], feed)[0]
        expanded_mask = masks[:, :, None].astype(np.float32)
        pooled = (token_vectors * expanded_mask).sum(axis=1) / np.maximum(
            expanded_mask.sum(axis=1), 1.0)
        norms = np.linalg.norm(pooled, axis=1, keepdims=True)
        all_vectors.append(pooled / np.maximum(norms, 1e-12))
    return np.concatenate(all_vectors, axis=0)


def fit_logistic(x: np.ndarray, y: np.ndarray) -> tuple[np.ndarray, float]:
    """Full-batch logistic regression with L2 regularization, numpy only."""
    mean = x.mean(axis=0)
    scale = x.std(axis=0)
    scale[scale < 1e-5] = 1.0
    z = (x - mean) / scale
    w = np.zeros(z.shape[1], dtype=np.float64)
    b = 0.0
    reg = 0.02
    lr = 0.08
    for _ in range(2500):
        logits = np.clip(z @ w + b, -30.0, 30.0)
        p = 1.0 / (1.0 + np.exp(-logits))
        err = p - y
        grad_w = z.T @ err / len(y) + reg * w
        grad_b = float(err.mean())
        w -= lr * grad_w
        b -= lr * grad_b
    return np.concatenate([mean, scale, w, np.array([b])]), float(reg)


def predict(x: np.ndarray, params: np.ndarray) -> np.ndarray:
    d = x.shape[1]
    mean, scale, w, b = params[:d], params[d:2*d], params[2*d:3*d], params[-1]
    z = (x - mean) / scale
    return 1.0 / (1.0 + np.exp(-np.clip(z @ w + b, -30.0, 30.0)))


def threshold_for_training_ham(scores: np.ndarray, labels: np.ndarray) -> float:
    ham = np.sort(scores[labels == 0])[::-1]
    if not len(ham):
        return 0.5
    allowed = max(0, math.floor(len(ham) * 0.01))
    if allowed >= len(ham):
        return 0.5
    return float(np.nextafter(ham[allowed], np.inf))


def report(name: str, rows: list[dict], scores: np.ndarray,
           threshold: float) -> tuple[int, int, int, int]:
    y = np.array([r["label"] for r in rows], dtype=np.int64)
    pred = scores >= threshold
    scam = y == 1
    ham = ~scam
    tp = int(np.sum(pred & scam))
    fp = int(np.sum(pred & ham))
    fn = int(np.sum(~pred & scam))
    tn = int(np.sum(~pred & ham))
    print(f"{name}: n={len(rows)} threshold={threshold:.6f} "
          f"TP={tp} FP={fp} FN={fn} TN={tn} "
          f"recall={tp/max(1,tp+fn):.3f} "
          f"ham_FPR={fp/max(1,fp+tn):.3f}")
    return tp, fp, fn, tn


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--teacher-dir", type=Path, required=True)
    args = parser.parse_args()

    train, held, corpus = load_rows()
    rows = train + held
    print(f"Embedding {len(rows)} local examples with pinned teacher")
    vectors = make_embeddings(rows, args.teacher_dir)
    x_train = vectors[:len(train)]
    x_held = vectors[len(train):]
    y_train = np.array([r["label"] for r in train], dtype=np.float64)
    y_held = np.array([r["label"] for r in held], dtype=np.int64)
    params, reg = fit_logistic(x_train, y_train)
    train_scores = predict(x_train, params)
    held_scores = predict(x_held, params)
    threshold = threshold_for_training_ham(train_scores, y_train)
    print(f"Teacher: sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2")
    print(f"Head: initialized and trained from scratch; L2={reg}; dimension={x_train.shape[1]}")
    report("train", train, train_scores, threshold)
    report("constructed held-out", held, held_scores, threshold)
    fragile_rows = [i for i, row in enumerate(held) if row["fragile"]]
    fragile_hits = sum(held_scores[i] >= threshold for i in fragile_rows)
    ham_rows = [i for i, row in enumerate(held) if row["label"] == 0]
    ham_fp = sum(held_scores[i] >= threshold for i in ham_rows)
    print(f"fragility recovery: {fragile_hits}/{len(fragile_rows)}")
    print(f"constructed held-out legitimate escalations: {ham_fp}/{len(ham_rows)}")
    print("No real messages were used. This is a transfer feasibility probe, "
          "not a real-world quality estimate.")


if __name__ == "__main__":
    main()
