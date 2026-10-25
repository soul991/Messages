#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
evaluate_teacher_anatomy.py — Phase 2: Multilingual Teachers & Anatomy Analysis.

Performs:
1. Parameter & architectural inspection of IndicBERTv2, MuRIL, and XLM-RoBERTa.
2. Tokenizer unknown-token ([UNK]) rate benchmarking across language/script buckets
   on train.jsonl and validation.jsonl (en_latin, hi_latn, hi_deva, bn_beng, bn_latn).
3. Sequence classification inference and operating-point evaluation of fine-tuned
   teachers on validation.jsonl:
   - Calibration curves, Brier score, ECE.
   - Operating points at Ham FPR = 0.50%, 1.00%, 2.00%, 5.00%.
   - Per-script subgroup recall/FPR with Wilson 95% intervals.
   - Protected traffic and benign bank advisory FPR.
4. Error profiling (categorization of false positives and false negatives).
5. Output generation: docs/ml/teacher_evaluation_report.json.
"""

from __future__ import annotations

import json
import math
import os
import re
import sys
import time
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np
import torch
from transformers import AutoConfig, AutoModelForSequenceClassification, AutoTokenizer

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

ROOT = Path("D:/Istak/Messages/Messages App/01_active_codebase/messages")
SPLITS_DIR = Path("D:/Istak/Messages/Datasets/splits")
HF_CACHE = Path("D:/Istak/Messages/hf-cache/hub")
REPORT_OUT = ROOT / "docs/ml/teacher_evaluation_report.json"

TRAIN_SPLIT = SPLITS_DIR / "train.jsonl"
VAL_SPLIT = SPLITS_DIR / "validation.jsonl"

RE_LINK = re.compile(r"https?://\S+|www\.\S+|bit\.ly/\S+|tinyurl\.com/\S+|wa\.me/\S+|\.apk\b", re.I)
RE_PHONE = re.compile(r"(?:\+?91[\s-]?)?[6-9]\d{9}\b")
RE_PROT = re.compile(
    r"\b(?:otp|one time password|credited|debited|a/c|account no|avl bal|जमा किए|निकाले गए|क्रेडिट|डेबिट|ओटीपी|खाता संख्या|জমা হয়েছে|ডেবিট)\b",
    re.I
)
RE_ADVISORY = re.compile(
    r"(?:\bnever share\b|\bdo not share\b|\bkisi ko na (?:dein|batao)\b|\bsavdhan rahein\b|\bsatark rahein\b|"
    r"\bbeware of\b|\bfraud awareness\b|\bcaution\b|\bsafety tip\b|\bsharthoko thakun\b|\bbank never asks\b|"
    r"जन सूचना|सावधान|सतर्क|चेतावनी|सतर्कता|सचेत रहें|ध्यान रखें|झांसे में न|भरोसा न करें|सलाह दी जाती|केवल आधिकारिक|"
    r"সতর্কতা|সতর্ক থাকুন|প্রতারিত হবেন না|বিশ্বাস করবেন না|সাবধান থাকুন|"
    r"\bbe cautious\b|\bdo not trust\b|\bnever send\b|\bno consultant\b|\bno private agent\b|\bno one can\b|"
    r"\bshould be verified\b|\bverify every\b|\bred flag\b|\bdo not rely\b|\bgenuine .* requires\b|\bofficial decisions\b|"
    r"fraud (?:hota hai|pattern|signal|ka risk)|suspicious hoti|mat karein|na karein|sirf official|bina proof|"
    r"bina verification|bharosemand nahi)",
    re.I
)


def wilson_interval(k: int, n: int, confidence: float = 0.95) -> tuple[float, float]:
    if n == 0:
        return (0.0, 0.0)
    z = 1.95996
    p = k / n
    denom = 1 + (z**2) / n
    center = (p + (z**2) / (2 * n)) / denom
    margin = (z * math.sqrt((p * (1 - p) / n) + (z**2) / (4 * n**2))) / denom
    return (round(max(0.0, center - margin) * 100, 2), round(min(1.0, center + margin) * 100, 2))


def compute_calibration(probs: np.ndarray, labels: np.ndarray, n_bins: int = 10) -> tuple[float, float, list[dict]]:
    if len(probs) == 0:
        return (0.0, 0.0, [])
    brier = float(np.mean((probs - labels) ** 2))
    bin_boundaries = np.linspace(0, 1, n_bins + 1)
    ece = 0.0
    curve = []
    for i in range(n_bins):
        in_bin = (probs >= bin_boundaries[i]) & (probs < bin_boundaries[i + 1])
        bin_size = int(np.sum(in_bin))
        if bin_size > 0:
            bin_acc = float(np.mean(labels[in_bin]))
            bin_conf = float(np.mean(probs[in_bin]))
            ece += (bin_size / len(probs)) * abs(bin_acc - bin_conf)
            curve.append({
                "bin": i,
                "range": [round(float(bin_boundaries[i]), 2), round(float(bin_boundaries[i + 1]), 2)],
                "count": bin_size,
                "confidence": round(bin_conf, 4),
                "accuracy": round(bin_acc, 4),
            })
        else:
            curve.append({
                "bin": i,
                "range": [round(float(bin_boundaries[i]), 2), round(float(bin_boundaries[i + 1]), 2)],
                "count": 0,
                "confidence": 0.0,
                "accuracy": 0.0,
            })
    return (round(brier, 4), round(ece, 4), curve)


def detect_script(text: str) -> str:
    has_beng = bool(re.search(r"[\u0980-\u09FF]", text))
    has_deva = bool(re.search(r"[\u0900-\u097F]", text))
    if has_beng:
        return "bengali"
    if has_deva:
        return "devanagari"
    return "latin"


def load_dataset(path: Path) -> list[dict]:
    rows = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                rows.append(json.loads(line))
    return rows


def analyze_tokenizer_unk(tokenizer, name: str, rows: list[dict]) -> dict:
    unk_id = tokenizer.unk_token_id
    stats_by_bucket = defaultdict(lambda: {"total_tokens": 0, "unk_tokens": 0, "total_messages": 0, "messages_with_unk": 0})
    overall = {"total_tokens": 0, "unk_tokens": 0, "total_messages": 0, "messages_with_unk": 0}

    for r in rows:
        bucket = r.get("bucket", "unknown")
        text = r["text"]
        input_ids = tokenizer.encode(text, add_special_tokens=False)
        total_toks = len(input_ids)
        unk_toks = input_ids.count(unk_id) if unk_id is not None else 0
        has_unk = 1 if unk_toks > 0 else 0

        stats_by_bucket[bucket]["total_tokens"] += total_toks
        stats_by_bucket[bucket]["unk_tokens"] += unk_toks
        stats_by_bucket[bucket]["total_messages"] += 1
        stats_by_bucket[bucket]["messages_with_unk"] += has_unk

        overall["total_tokens"] += total_toks
        overall["unk_tokens"] += unk_toks
        overall["total_messages"] += 1
        overall["messages_with_unk"] += has_unk

    results = {}
    for b, s in stats_by_bucket.items():
        results[b] = {
            "total_tokens": s["total_tokens"],
            "unk_tokens": s["unk_tokens"],
            "unk_rate_pct": round(s["unk_tokens"] / max(1, s["total_tokens"]) * 100, 4),
            "total_messages": s["total_messages"],
            "messages_with_unk": s["messages_with_unk"],
            "messages_with_unk_pct": round(s["messages_with_unk"] / max(1, s["total_messages"]) * 100, 2),
        }
    results["overall"] = {
        "total_tokens": overall["total_tokens"],
        "unk_tokens": overall["unk_tokens"],
        "unk_rate_pct": round(overall["unk_tokens"] / max(1, overall["total_tokens"]) * 100, 4),
        "total_messages": overall["total_messages"],
        "messages_with_unk": overall["messages_with_unk"],
        "messages_with_unk_pct": round(overall["messages_with_unk"] / max(1, overall["total_messages"]) * 100, 2),
    }
    return results


def evaluate_model_operating_points(
    model, tokenizer, rows: list[dict], device: str, batch_size: int = 64
) -> dict:
    model.eval()
    model.to(device)

    all_probs = []
    labels = []
    subgroups = []
    scripts = []
    is_prot_list = []
    is_advisory_list = []

    print(f"Running inference on {len(rows)} validation rows...")
    t0 = time.perf_counter()

    for i in range(0, len(rows), batch_size):
        batch_rows = rows[i : i + batch_size]
        texts = [r["text"] for r in batch_rows]
        inputs = tokenizer(
            texts,
            padding=True,
            truncation=True,
            max_length=128,
            return_tensors="pt",
        ).to(device)

        with torch.no_grad():
            outputs = model(**inputs)
            logits = outputs.logits
            probs = torch.softmax(logits, dim=-1)[:, 1].cpu().numpy()

        all_probs.extend(probs.tolist())
        for r in batch_rows:
            label_val = 1 if r["label"] == "SCAM_FRAUD" else 0
            sc = detect_script(r["text"])
            bucket = r.get("bucket", "unknown")
            is_prot = bool(RE_PROT.search(r["text"])) and label_val == 0
            is_adv = bool(RE_ADVISORY.search(r["text"])) and label_val == 0

            labels.append(label_val)
            subgroups.append(bucket)
            scripts.append(sc)
            is_prot_list.append(is_prot)
            is_advisory_list.append(is_adv)

    inf_time_sec = time.perf_counter() - t0
    all_probs = np.array(all_probs)
    labels = np.array(labels)
    subgroups = np.array(subgroups)
    scripts = np.array(scripts)
    is_prot_arr = np.array(is_prot_list)
    is_adv_arr = np.array(is_advisory_list)

    brier, ece, cal_curve = compute_calibration(all_probs, labels)

    ham_mask = labels == 0
    scam_mask = labels == 1
    total_ham = int(np.sum(ham_mask))
    total_scam = int(np.sum(scam_mask))

    ham_probs_sorted = np.sort(all_probs[ham_mask])

    target_fprs = [0.005, 0.010, 0.020, 0.050]
    operating_points = {}

    for target_fpr in target_fprs:
        # threshold is the cutoff where FPR <= target_fpr
        # max allowed FP = floor(total_ham * target_fpr)
        max_fp = int(math.floor(total_ham * target_fpr))
        if max_fp == 0:
            threshold = float(ham_probs_sorted[-1]) + 1e-6
        else:
            # sorted ascending: the top max_fp items are indices from len - max_fp to len - 1
            threshold = float(ham_probs_sorted[len(ham_probs_sorted) - max_fp])

        preds = (all_probs >= threshold).astype(int)

        tp = int(np.sum((preds == 1) & scam_mask))
        fn = int(np.sum((preds == 0) & scam_mask))
        fp = int(np.sum((preds == 1) & ham_mask))
        tn = int(np.sum((preds == 0) & ham_mask))

        rec = round(tp / max(1, total_scam) * 100, 2)
        fpr = round(fp / max(1, total_ham) * 100, 2)
        rec_ci = wilson_interval(tp, total_scam)
        fpr_ci = wilson_interval(fp, total_ham)

        # Subgroup metrics
        per_bucket = {}
        for b in sorted(set(subgroups)):
            b_mask = subgroups == b
            b_scam = scam_mask & b_mask
            b_ham = ham_mask & b_mask
            b_tp = int(np.sum((preds == 1) & b_scam))
            b_fn = int(np.sum((preds == 0) & b_scam))
            b_fp = int(np.sum((preds == 1) & b_ham))
            b_tn = int(np.sum((preds == 0) & b_ham))

            b_scam_n = b_tp + b_fn
            b_ham_n = b_fp + b_tn

            b_rec = round(b_tp / max(1, b_scam_n) * 100, 2) if b_scam_n > 0 else 0.0
            b_fpr = round(b_fp / max(1, b_ham_n) * 100, 2) if b_ham_n > 0 else 0.0

            per_bucket[b] = {
                "scam_total": b_scam_n,
                "tp": b_tp,
                "fn": b_fn,
                "recall_pct": b_rec,
                "recall_ci": wilson_interval(b_tp, b_scam_n),
                "ham_total": b_ham_n,
                "fp": b_fp,
                "tn": b_tn,
                "fpr_pct": b_fpr,
                "fpr_ci": wilson_interval(b_fp, b_ham_n),
                "conclusive": b_scam_n >= 100 and b_ham_n >= 100,
            }

        # Protected & Advisory FPR
        prot_total = int(np.sum(is_prot_arr))
        prot_fp = int(np.sum((preds == 1) & is_prot_arr))
        prot_fpr = round(prot_fp / max(1, prot_total) * 100, 2)

        adv_total = int(np.sum(is_adv_arr))
        adv_fp = int(np.sum((preds == 1) & is_adv_arr))
        adv_fpr = round(adv_fp / max(1, adv_total) * 100, 2)

        operating_points[f"fpr_{target_fpr*100:.1f}pct"] = {
            "target_fpr": target_fpr,
            "threshold": round(threshold, 6),
            "tp": tp,
            "fn": fn,
            "fp": fp,
            "tn": tn,
            "recall_pct": rec,
            "recall_ci_95": rec_ci,
            "fpr_pct": fpr,
            "fpr_ci_95": fpr_ci,
            "protected_traffic": {
                "total": prot_total,
                "fp": prot_fp,
                "fpr_pct": prot_fpr,
                "ci_95": wilson_interval(prot_fp, prot_total),
            },
            "advisories": {
                "total": adv_total,
                "fp": adv_fp,
                "fpr_pct": adv_fpr,
                "ci_95": wilson_interval(adv_fp, adv_total),
            },
            "buckets": per_bucket,
        }

    # Error analysis at 1.0% FPR operating point
    ref_op = operating_points["fpr_1.0pct"]
    ref_th = ref_op["threshold"]
    ref_preds = (all_probs >= ref_th).astype(int)

    fps = []
    fns = []
    for idx, (r, p, prob) in enumerate(zip(rows, ref_preds, all_probs)):
        label_val = 1 if r["label"] == "SCAM_FRAUD" else 0
        if label_val == 0 and p == 1:
            fps.append({
                "idx": idx,
                "source": r.get("source", "unknown"),
                "bucket": r.get("bucket", "unknown"),
                "prob": round(float(prob), 4),
                "is_protected": bool(RE_PROT.search(r["text"])),
                "is_advisory": bool(RE_ADVISORY.search(r["text"])),
                "text_snippet": r["text"][:100],
            })
        elif label_val == 1 and p == 0:
            fns.append({
                "idx": idx,
                "source": r.get("source", "unknown"),
                "bucket": r.get("bucket", "unknown"),
                "prob": round(float(prob), 4),
                "text_snippet": r["text"][:100],
            })

    return {
        "inference_time_sec": round(inf_time_sec, 2),
        "total_evaluated": len(rows),
        "scam_count": total_scam,
        "ham_count": total_ham,
        "brier_score": brier,
        "ece": ece,
        "calibration_curve": cal_curve,
        "operating_points": operating_points,
        "error_analysis_1pct_fpr": {
            "total_fp": len(fps),
            "total_fn": len(fns),
            "fp_sources": dict(Counter(f["source"] for f in fps)),
            "fp_buckets": dict(Counter(f["bucket"] for f in fps)),
            "fp_advisories": sum(1 for f in fps if f["is_advisory"]),
            "fp_protected": sum(1 for f in fps if f["is_protected"]),
            "fn_sources": dict(Counter(f["source"] for f in fns)),
            "fn_buckets": dict(Counter(f["bucket"] for f in fns)),
            "sample_fps": fps[:10],
            "sample_fns": fns[:10],
        },
    }


def main():
    print("=" * 70)
    print("PHASE 2: Multilingual Teachers & Anatomy Analysis")
    print("=" * 70)

    device = "cuda" if torch.cuda.is_available() else "cpu"
    print(f"Device: {device}")
    if device == "cuda":
        print(f"GPU: {torch.cuda.get_device_name(0)}")

    print(f"Loading train split: {TRAIN_SPLIT}")
    train_rows = load_dataset(TRAIN_SPLIT)
    print(f"Loaded {len(train_rows)} train rows.")

    print(f"Loading validation split: {VAL_SPLIT}")
    val_rows = load_dataset(VAL_SPLIT)
    print(f"Loaded {len(val_rows)} validation rows.")

    # Model specifications
    model_configs = {
        "ai4bharat/IndicBERTv2-MLM-only": {
            "cache_path": str(HF_CACHE / "models--ai4bharat--IndicBERTv2-MLM-only/snapshots/8598f13fe52443bc3fc054fcd665944560145b5c"),
            "finetuned_path": "D:/Istak/Messages/indicbertv2-sms-candidate/best",
            "license": "mit",
            "license_url": "https://huggingface.co/ai4bharat/IndicBERTv2-MLM-only",
        },
        "google/muril-base-cased": {
            "cache_path": str(HF_CACHE / "models--google--muril-base-cased/snapshots/afd9f36c7923d54e97903922ff1b260d091d202f"),
            "finetuned_path": "D:/Istak/Messages/muril-sms-experiment-2026-10-10-resume/best",
            "license": "apache-2.0",
            "license_url": "https://huggingface.co/google/muril-base-cased",
        },
        "xlm-roberta-base": {
            "cache_path": str(HF_CACHE / "models--xlm-roberta-base/snapshots/e73636d4f797dec63c3081bb6ed5c7b0bb3f2089"),
            "finetuned_path": None,
            "license": "mit",
            "license_url": "https://huggingface.co/xlm-roberta-base",
        },
    }

    report = {
        "generated_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "hardware": {
            "device": device,
            "gpu_name": torch.cuda.get_device_name(0) if device == "cuda" else "cpu",
            "cuda_version": torch.version.cuda if device == "cuda" else None,
        },
        "splits": {
            "train_rows": len(train_rows),
            "validation_rows": len(val_rows),
            "train_buckets": dict(Counter(r.get("bucket", "unknown") for r in train_rows)),
            "val_buckets": dict(Counter(r.get("bucket", "unknown") for r in val_rows)),
        },
        "teachers": {},
    }

    for model_name, info in model_configs.items():
        print(f"\n--- Analyzing Architecture & Tokenizer: {model_name} ---")
        tok_path = info["cache_path"]
        tokenizer = AutoTokenizer.from_pretrained(tok_path)
        config = AutoConfig.from_pretrained(tok_path)

        # Parameter counts from config
        hidden = config.hidden_size
        layers = config.num_hidden_layers
        heads = config.num_attention_heads
        vocab = config.vocab_size
        model_type = config.model_type

        # Calculate exact parameter counts
        # Embeddings: vocab * hidden + max_pos * hidden + type_vocab * hidden + LayerNorm
        # Encoder: layers * (4 * hidden * hidden (attn) + 8 * hidden * hidden (ffn) + biases/norms)
        # Total approx
        total_p = None
        ft_path = info.get("finetuned_path")
        if ft_path and os.path.exists(ft_path):
            m = AutoModelForSequenceClassification.from_pretrained(ft_path)
            total_p = sum(p.numel() for p in m.parameters())
            del m
        elif model_name == "xlm-roberta-base":
            total_p = 278043648

        print(f"Vocab size: {vocab}, Hidden size: {hidden}, Layers: {layers}, Heads: {heads}")
        print("Benchmarking unknown-token rates...")
        val_unk = analyze_tokenizer_unk(tokenizer, model_name, val_rows)
        train_unk = analyze_tokenizer_unk(tokenizer, model_name, train_rows)

        report["teachers"][model_name] = {
            "model_type": model_type,
            "vocab_size": vocab,
            "hidden_size": hidden,
            "num_hidden_layers": layers,
            "num_attention_heads": heads,
            "total_parameters": total_p,
            "license": info["license"],
            "license_url": info["license_url"],
            "tokenizer_unk_stats": {
                "validation": val_unk,
                "train": train_unk,
            },
        }

        # Sequence classification operating points if fine-tuned model exists
        if ft_path and os.path.exists(ft_path):
            print(f"Loading sequence classification model from {ft_path}...")
            model = AutoModelForSequenceClassification.from_pretrained(ft_path)
            eval_res = evaluate_model_operating_points(model, tokenizer, val_rows, device)
            report["teachers"][model_name]["validation_evaluation"] = eval_res
            del model
            if device == "cuda":
                torch.cuda.empty_cache()

    REPORT_OUT.parent.mkdir(parents=True, exist_ok=True)
    with open(REPORT_OUT, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)

    print(f"\n[DONE] Teacher evaluation report written to {REPORT_OUT}")


if __name__ == "__main__":
    main()
