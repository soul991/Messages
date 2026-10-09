#!/usr/bin/env python3
"""Use a pretrained semantic model as a training-only data teacher.

The exported classifier is still trained from scratch by train_ngram.py. The
Hugging Face model is used only to accept/reject label-preserving synonym
variants by embedding similarity. It is not compressed, included in the APK,
or called while classifying messages.

Install the isolated research dependencies with:
  pip install numpy onnxruntime tokenizers
Download the pinned Apache-2.0 teacher revision separately, then pass its
directory with --teacher-dir. Only project-local synthetic messages are read.
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

import numpy as np

import teacher_transfer_experiment as teacher
import train_ngram as student


TEACHER_ID = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
TEACHER_REVISION = "e8f8c211226b894fcb81acc59f3b34ba3efd5f42"
MIN_SEMANTIC_SIMILARITY = 0.86
MAX_VARIANTS_PER_MESSAGE = 4

# Conservative, one-token substitutions. The embedding teacher filters out
# mutations whose sentence meaning moves too far from their labeled source.
SYNONYMS = {
    "verify": ("confirm", "validate"),
    "verification": ("confirmation", "validation"),
    "account": ("profile", "banking account"),
    "claim": ("collect", "receive"),
    "prize": ("reward", "winnings"),
    "won": ("selected", "earned"),
    "blocked": ("suspended", "locked"),
    "suspended": ("blocked", "frozen"),
    "expire": ("deactivate", "close"),
    "urgent": ("immediate", "time-sensitive"),
    "fee": ("charge", "tax"),
    "click": ("visit", "open"),
    "refund": ("reimbursement", "rebate"),
    "loan": ("credit", "financing"),
    "cashback": ("cash back", "cash reward"),
    "password": ("passcode", "login code"),
    "pin": ("passcode", "security code"),
}


def candidates(rows: list[dict]) -> list[dict]:
    out = []
    for row_index, row in enumerate(rows):
        # The teacher card identifies these languages. Bengali and Punjabi are
        # deliberately left to the script-agnostic base student, because the
        # teacher does not list them.
        if row["lang"] not in {"en", "hinglish", "hi", "gu"}:
            continue
        found = 0
        low = row["text"].lower()
        for word, replacements in SYNONYMS.items():
            if found >= MAX_VARIANTS_PER_MESSAGE:
                break
            if not re.search(r"(?<!\w)" + re.escape(word) + r"(?!\w)", low):
                continue
            for replacement in replacements[:1]:
                variant = re.sub(
                    r"(?<!\w)" + re.escape(word) + r"(?!\w)",
                    replacement,
                    row["text"],
                    count=1,
                    flags=re.IGNORECASE,
                )
                if variant != row["text"]:
                    out.append({"text": variant, "label": row["label"],
                                "lang": row["lang"], "source_index": row_index})
                    found += 1
                    break
    return out


def write_model(path: Path, weights: dict[str, float], threshold: float) -> None:
    payload = {
        "version": 1,
        "ngramMin": student.NGRAM_MIN,
        "ngramMax": student.NGRAM_MAX,
        "minWords": student.MIN_WORDS,
        "threshold": threshold,
        "weights": {g: round(v, 3) for g, v in sorted(weights.items())},
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False,
                               separators=(",", ":")) + "\n", encoding="utf-8")


def measure(rows: list[dict], weights: dict[str, float], threshold: float):
    scores = [student.score(weights, row["text"]) for row in rows]
    scam = [i for i, row in enumerate(rows) if row["label"] == "scam"]
    ham = [i for i, row in enumerate(rows) if row["label"] != "scam"]
    tp = sum(scores[i] > threshold for i in scam)
    fp = sum(scores[i] > threshold for i in ham)
    fragile = [i for i, row in enumerate(rows) if row["fragile"]]
    recovered = sum(scores[i] > threshold for i in fragile)
    return {"tp": tp, "scam": len(scam), "fp": fp, "ham": len(ham),
            "fragileRecovered": recovered, "fragile": len(fragile)}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--teacher-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True,
                        help="candidate weights path; does not replace the app model")
    args = parser.parse_args()

    train, held, _corpus, _fragile = student.load()
    generated = candidates(train)
    print(f"Generated {len(generated)} label-preserving candidate variants")

    rows_for_embedding = train + [
        {"text": row["text"], "lang": row["lang"]} for row in generated
    ]
    vectors = teacher.make_embeddings(rows_for_embedding, args.teacher_dir)
    original_vectors = vectors[:len(train)]
    variant_vectors = vectors[len(train):]
    accepted = []
    similarities = []
    for row, vector in zip(generated, variant_vectors):
        source = original_vectors[row["source_index"]]
        similarity = float(np.dot(source, vector))
        if similarity >= MIN_SEMANTIC_SIMILARITY:
            accepted.append({"text": row["text"], "label": row["label"],
                             "lang": row["lang"], "source": "teacher-filtered"})
            similarities.append(similarity)

    print(f"Teacher-accepted {len(accepted)}/{len(generated)} at cosine >= "
          f"{MIN_SEMANTIC_SIMILARITY:.2f}")
    if similarities:
        print(f"accepted cosine mean={np.mean(similarities):.3f} "
              f"min={np.min(similarities):.3f}")

    base_weights = student.train_model(train)
    base_threshold = student.pick_threshold(base_weights, train)
    augmented_train = train + accepted
    candidate_weights = student.train_model(augmented_train)
    candidate_threshold = student.pick_threshold(candidate_weights, augmented_train)
    baseline = measure(held, base_weights, base_threshold)
    candidate = measure(held, candidate_weights, candidate_threshold)
    write_model(args.output, candidate_weights, candidate_threshold)

    print(f"Student trained from scratch: char n-gram multinomial Naive Bayes")
    print(f"Teacher used only as semantic augmentation filter: {TEACHER_ID}@{TEACHER_REVISION}")
    print(f"Training examples: {len(train)} base + {len(accepted)} teacher-filtered variants")
    print(f"Baseline held-out: {baseline}")
    print(f"Distilled held-out: {candidate}")
    print(f"Candidate model: {args.output} ({args.output.stat().st_size} bytes)")
    print("These constructed held-out metrics are for comparison only; they are "
          "not an independent real-world evaluation.")


if __name__ == "__main__":
    main()
