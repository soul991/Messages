#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build_score_sample.py — Generates stratified verification sample (>= 300 cases)
for package_candidate.py across Devanagari, Bengali, Hinglish, Banglish,
English, advisories, and protected OTPs.
"""

from __future__ import annotations

import json
import random
from pathlib import Path
import re

ROOT = Path("D:/Istak/Messages/Messages App/01_active_codebase/messages")
SPLITS_DIR = Path("D:/Istak/Messages/Datasets/splits")
OUT_SAMPLE = ROOT / "protection-engine/training/score_sample.json"

import sys
sys.path.insert(0, str(ROOT / "protection-engine/tools"))
from train_round2_student import Round2StudentSystem, detect_script, load_jsonl


def main():
    print("Building stratified score_sample.json (>= 300 cases)...")
    random.seed(20261024)

    val_rows = load_jsonl(SPLITS_DIR / "validation.jsonl")
    train_rows = load_jsonl(SPLITS_DIR / "train.jsonl")

    # Load candidate 15
    sys_obj = Round2StudentSystem(
        c_params={"latin": 2.5, "devanagari": 1.5, "bengali": 1.0},
        max_features_per_script={"latin": 8000, "devanagari": 5000, "bengali": 4000},
        random_state=20261020,
    )
    sys_obj.fit(train_rows)
    sys_obj.calibrate_thresholds(
        val_rows,
        target_fpr_vec={"latin": 0.006, "devanagari": 0.005, "bengali": 0.005},
        target_fpr_no={"latin": 0.003, "devanagari": 0.003, "bengali": 0.003},
    )

    # Categories to stratify
    samples = []
    seen_texts = set()

    # Stratified quotas
    quotas = {
        ("en_latin", "SCAM_FRAUD"): 30,
        ("en_latin", "NOT_SCAM"): 30,
        ("hi_latn", "SCAM_FRAUD"): 30,
        ("hi_latn", "NOT_SCAM"): 30,
        ("hi_deva", "SCAM_FRAUD"): 30,
        ("hi_deva", "NOT_SCAM"): 30,
        ("bn_beng", "SCAM_FRAUD"): 30,
        ("bn_beng", "NOT_SCAM"): 30,
        ("bn_latn", "SCAM_FRAUD"): 15,
        ("bn_latn", "NOT_SCAM"): 15,
    }

    # Gather rows from val and train
    combined = val_rows + train_rows
    random.shuffle(combined)

    counts = {k: 0 for k in quotas}

    for r in combined:
        b = r.get("bucket", "unknown")
        lbl = r.get("label", "unknown")
        key = (b, lbl)
        if key in quotas and counts[key] < quotas[key]:
            t = r["text"].strip()
            if t not in seen_texts and len(t) >= 15:
                seen_texts.add(t)
                counts[key] += 1
                res = sys_obj.predict_one(t)
                samples.append({
                    "id": len(samples) + 1,
                    "text": t,
                    "script": detect_script(t),
                    "bucket": b,
                    "true_label": lbl,
                    "expected_is_scam": res["is_scam"],
                    "expected_prob": res["prob"],
                    "reason": res["reason"],
                })

    # Gather specialized hard negative advisories
    adv_rows = [r for r in combined if "advisories" in r.get("source", "") or "advisory" in r.get("source", "")]
    for r in adv_rows[:25]:
        t = r["text"].strip()
        if t not in seen_texts:
            seen_texts.add(t)
            res = sys_obj.predict_one(t)
            samples.append({
                "id": len(samples) + 1,
                "text": t,
                "script": detect_script(t),
                "bucket": r.get("bucket", "advisory"),
                "true_label": "NOT_SCAM",
                "expected_is_scam": res["is_scam"],
                "expected_prob": res["prob"],
                "reason": res["reason"],
            })

    # Gather specialized protected OTP/transaction messages
    prot_rows = [r for r in combined if r.get("label") != "SCAM_FRAUD" and ("otp" in r["text"].lower() or "credited" in r["text"].lower() or "जमा" in r["text"] or "সফলভাবে" in r["text"])]
    for r in prot_rows[:25]:
        t = r["text"].strip()
        if t not in seen_texts:
            seen_texts.add(t)
            res = sys_obj.predict_one(t)
            samples.append({
                "id": len(samples) + 1,
                "text": t,
                "script": detect_script(t),
                "bucket": r.get("bucket", "protected"),
                "true_label": "NOT_SCAM",
                "expected_is_scam": res["is_scam"],
                "expected_prob": res["prob"],
                "reason": res["reason"],
            })

    # Re-index
    for idx, s in enumerate(samples):
        s["id"] = idx + 1

    print(f"Total samples collected: {len(samples)}")
    OUT_SAMPLE.parent.mkdir(parents=True, exist_ok=True)
    with open(OUT_SAMPLE, "w", encoding="utf-8") as f:
        json.dump(samples, f, indent=2)
    print(f"Sample written to {OUT_SAMPLE}")


if __name__ == "__main__":
    main()
