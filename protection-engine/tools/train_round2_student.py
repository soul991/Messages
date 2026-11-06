#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
train_round2_student.py — Phase 3: Disciplined Student Training Loop.

Implements multi-script linear/logistic experts with sublinear char-wb TF-IDF,
deterministic syntactic pre-filters (protected traffic, fraud advisories),
and threshold calibration on validation.jsonl.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import re
import sys
import time
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

ROOT = Path("D:/Istak/Messages/Messages App/01_active_codebase/messages")
SPLITS_DIR = Path("D:/Istak/Messages/Datasets/splits")
CYCLES_DIR = ROOT / "docs/ml/cycles"
MANIFEST_PATH = ROOT / "docs/ml/datasets_manifest.json"

SEEDS = [20261020, 20261021, 20261022]

RE_LINK = re.compile(r"https?://\S+|www\.\S+|bit\.ly/\S+|tinyurl\.com/\S+|wa\.me/\S+|\.apk\b", re.I)
RE_PHONE = re.compile(r"(?:\+?91[\s-]?)?[6-9]\d{9}\b")
RE_PROT = re.compile(
    r"(?:"
    r"\b(?:otp|one time password|credited|debited|a/c|account no|avl bal|"
    r"paid via|monthly membership fee|bill of rs|electricity bill|delivery partner|pack kiya ja raha|"
    r"online class hai)\b|"
    r"जमा किए|निकाले गए|क्रेडिट|डेबिट|ओटीपी|खाता संख्या|জমা হয়েছে|ডেবিট|"
    r"जोड़े गए|शेष राशि|कुल शेष|"
    r"সফলভাবে|সফল হয়েছে|সম্পন্ন হয়েছে|সফলভাবে সম্পন্ন|নিশ্চিত করা হয়েছে|"
    r"ডেলিভারি করা হয়েছে|প্যাক করা হচ্ছে|"
    r"सफलतापूर्वक|सफल रहा|सफल हुआ"
    r")",
    re.I
)
RE_ADVISORY = re.compile(
    r"(?:"
    r"\bnever share\b|\bdo not share\b|\bkisi ko na (?:dein|batao)\b|\bsavdhan rahein\b|\bsatark rahein\b|"
    r"\bbeware of\b|\bfraud awareness\b|\bcaution\b|\bsafety tip\b|\bsharthoko thakun\b|\bbank never asks\b|"
    r"जन सूचना|सावधान|सतर्क|चेतावनी|सतर्कता|सचेत रहें|ध्यान रखें|झांसे में न|भरोसा न करें|सलाह दी जाती|केवल आधिकारिक|"
    r"সতর্কতা|সতর্ক থাকুন|প্রতারিত হবেন না|বিশ্বাস করবেন না|সাবধান থাকুন|"
    r"\bbe cautious\b|\bdo not trust\b|\bnever send\b|\bno consultant\b|\bno private agent\b|\bno one can\b|"
    r"\bshould be verified\b|\bverify every\b|\bred flag\b|\bdo not rely\b|\bgenuine .* requires\b|\bofficial decisions\b|"
    r"fraud (?:hota hai|pattern|signal|ka risk)|suspicious hoti|mat karein|na karein|sirf official|bina proof|"
    r"bina verification|bharosemand nahi|"
    r"public notice|जन सूचना|জন বিজ্ঞপ্তি|"
    r"(?:are not|is not) (?:released|collected|asked|demanded)|"
    r"(?:dene se pehle|karne se pehle|making any payment|before making any payment)|"
    r"(?:independently verify|verify everything yourself|verify on the official)|"
    r"(?:nahi|नहीं)\s*(?:ja sakta|जा सकता|hota|होता|dein|दें|karein|करें)|"
    r"(?:dhyan|ध्यान)\s*se\s*(?:check|padhein|verify|dekhein|देखें|पढ़ें)|"
    r"(?:bharosemand|भरोसेमंद)\s*(?:nahi|नहीं)|"
    r"(?:kabhi bhi|कभी भी)\s*[\w\s]{1,30}?\s*(?:nahi|नहीं)|"
    r"(?:keval|केवल)\s*(?:official|adhikarik|आधिकारिक|prakriya|प्रक्रिया)|"
    r"badla nahi\s*(?:ja sakta|जा सकता)|"
    r"न भेजें|दावा कर रहा|zaruri hota|ज़रूरी होता"
    r")",
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
    return (round(max(0.0, center - margin), 4), round(min(1.0, center + margin), 4))


def compute_calibration(probs: np.ndarray, labels: np.ndarray, n_bins: int = 10) -> tuple[float, float]:
    if len(probs) == 0:
        return (0.0, 0.0)
    brier = float(np.mean((probs - labels) ** 2))
    bin_boundaries = np.linspace(0, 1, n_bins + 1)
    ece = 0.0
    for i in range(n_bins):
        in_bin = (probs >= bin_boundaries[i]) & (probs < bin_boundaries[i + 1])
        bin_size = np.sum(in_bin)
        if bin_size > 0:
            bin_acc = np.mean(labels[in_bin])
            bin_conf = np.mean(probs[in_bin])
            ece += (bin_size / len(probs)) * abs(bin_acc - bin_conf)
    return (round(brier, 4), round(ece, 4))


def detect_script(text: str) -> str:
    if re.search(r"[\u0980-\u09FF]", text):
        return "bengali"
    if re.search(r"[\u0900-\u097F]", text):
        return "devanagari"
    return "latin"


def normalize_text_transliteration(text: str) -> str:
    """Cycle 11+ feature: phonetic normalization of common transliterated scam lures."""
    t = text.lower()
    # compress repeated punctuation
    t = re.sub(r"([!?.])\1+", r"\1", t)
    # common leetspeak substitutions in scam lures
    t = re.sub(r"\bk\.?y\.?c\b", "kyc", t)
    t = re.sub(r"\ba+d+h+a+r\b", "aadhaar", t)
    t = re.sub(r"\bp+a+n\b", "pan", t)
    t = re.sub(r"\bbl[0o]ck(?:ed)?\b", "blocked", t)
    t = re.sub(r"\bexp[1i]r(?:y|ed)?\b", "expired", t)
    return t


def load_jsonl(path: Path) -> list[dict]:
    records = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                records.append(json.loads(line))
    return records


class Round2StudentSystem:
    def __init__(
        self,
        c_params: dict[str, float] | float = 2.0,
        max_features_per_script: dict[str, int] | int = 5000,
        use_translit_norm: bool = False,
        use_vector_split: bool = True,
        random_state: int = 42,
    ):
        self.c_params = (
            c_params
            if isinstance(c_params, dict)
            else {"latin": c_params, "devanagari": c_params, "bengali": c_params}
        )
        self.max_features = (
            max_features_per_script
            if isinstance(max_features_per_script, dict)
            else {
                "latin": max_features_per_script,
                "devanagari": max_features_per_script,
                "bengali": max_features_per_script,
            }
        )
        self.use_translit_norm = use_translit_norm
        self.use_vector_split = use_vector_split
        self.random_state = random_state

        self.vectorizers: dict[str, TfidfVectorizer] = {}
        self.models: dict[str, LogisticRegression] = {}
        self.configs: dict[str, dict] = {
            "latin": {"th_vec": 0.35, "th_no": 0.45},
            "devanagari": {"th_vec": 0.30, "th_no": 0.40},
            "bengali": {"th_vec": 0.35, "th_no": 0.45},
        }

    def fit(self, train_rows: list[dict]):
        by_script = {"latin": [], "devanagari": [], "bengali": []}
        for r in train_rows:
            t = normalize_text_transliteration(r["text"]) if self.use_translit_norm else r["text"]
            sc = detect_script(t)
            by_script[sc].append((t, 1 if r["label"] == "SCAM_FRAUD" else 0))

        for sc in ("latin", "devanagari", "bengali"):
            sc_data = by_script[sc]
            texts = [x[0] for x in sc_data]
            labels = [x[1] for x in sc_data]

            vec = TfidfVectorizer(
                analyzer="char_wb",
                ngram_range=(2, 5),
                max_features=self.max_features[sc],
                sublinear_tf=True,
            )
            X_tr = vec.fit_transform(texts)
            clf = LogisticRegression(
                C=self.c_params[sc],
                max_iter=1000,
                class_weight="balanced",
                random_state=self.random_state,
            )
            clf.fit(X_tr, labels)

            self.vectorizers[sc] = vec
            self.models[sc] = clf

    def calibrate_thresholds(
        self, val_rows: list[dict], target_fpr_vec: float = 0.005, target_fpr_no: float = 0.005
    ):
        """Finds optimal decision thresholds on validation data targeting <= target_fpr."""
        val_by_script = defaultdict(lambda: {"vec_probs": [], "no_probs": []})

        for r in val_rows:
            raw_text = r["text"]
            if RE_PROT.search(raw_text) or RE_ADVISORY.search(raw_text):
                continue
            is_scam = 1 if r["label"] == "SCAM_FRAUD" else 0
            t = normalize_text_transliteration(raw_text) if self.use_translit_norm else raw_text
            sc = detect_script(t)
            has_vec = bool(RE_LINK.search(raw_text) or RE_PHONE.search(raw_text))

            vec = self.vectorizers[sc]
            clf = self.models[sc]
            X = vec.transform([t])
            prob = float(clf.predict_proba(X)[0, 1])

            target_list = val_by_script[sc]["vec_probs" if has_vec else "no_probs"]
            target_list.append((prob, is_scam))

        for sc in ("latin", "devanagari", "bengali"):
            t_vec = target_fpr_vec[sc] if isinstance(target_fpr_vec, dict) else target_fpr_vec
            t_no = target_fpr_no[sc] if isinstance(target_fpr_no, dict) else target_fpr_no

            vp = [x[0] for x in val_by_script[sc]["vec_probs"] if x[1] == 0]
            np_p = [x[0] for x in val_by_script[sc]["no_probs"] if x[1] == 0]

            if self.use_vector_split:
                th_vec = (
                    float(np.quantile(vp, 1.0 - t_vec))
                    if len(vp) > 10
                    else 0.35
                )
                th_no = (
                    float(np.quantile(np_p, 1.0 - t_no))
                    if len(np_p) > 10
                    else 0.45
                )
            else:
                combined_ham = vp + np_p
                single_th = (
                    float(np.quantile(combined_ham, 1.0 - t_no))
                    if len(combined_ham) > 10
                    else 0.40
                )
                th_vec = single_th
                th_no = single_th

            self.configs[sc] = {"th_vec": round(th_vec, 4), "th_no": round(th_no, 4)}

    def predict_one(self, raw_text: str) -> dict:
        if RE_PROT.search(raw_text):
            return {"is_scam": False, "prob": 0.0, "reason": "protected_filter"}
        if RE_ADVISORY.search(raw_text):
            return {"is_scam": False, "prob": 0.001, "reason": "advisory_filter"}

        t = normalize_text_transliteration(raw_text) if self.use_translit_norm else raw_text
        sc = detect_script(t)
        vec = self.vectorizers[sc]
        clf = self.models[sc]
        cfg = self.configs[sc]

        X = vec.transform([t])
        prob = float(clf.predict_proba(X)[0, 1])

        has_vec = bool(RE_LINK.search(raw_text) or RE_PHONE.search(raw_text))
        th = cfg["th_vec"] if (has_vec and self.use_vector_split) else cfg["th_no"]
        is_scam = bool(prob >= th)

        return {"is_scam": is_scam, "prob": round(prob, 4), "reason": f"{sc}_linear"}

    def export_candidate(self, out_path: Path):
        payload = {
            "modelType": "CalibratedRound2MultilingualLinear",
            "use_translit_norm": self.use_translit_norm,
            "use_vector_split": self.use_vector_split,
            "configs": self.configs,
            "experts": {},
        }
        for sc in ("latin", "devanagari", "bengali"):
            vec = self.vectorizers[sc]
            clf = self.models[sc]
            feat_names = vec.get_feature_names_out()
            coefs = clf.coef_[0]
            intercept = float(clf.intercept_[0])
            weights = {
                f: round(float(c), 4)
                for f, c in zip(feat_names, coefs)
                if abs(c) > 0.001
            }
            payload["experts"][sc] = {
                "intercept": intercept,
                "weights": weights,
                "idf": {
                    f: round(float(idf), 4)
                    for f, idf in zip(feat_names, vec.idf_)
                    if f in weights
                },
            }
        out_path.parent.mkdir(parents=True, exist_ok=True)
        with open(out_path, "w", encoding="utf-8") as f:
            json.dump(payload, f, indent=2)


def evaluate_system(system: Round2StudentSystem, dataset: list[dict]) -> dict:
    rows_eval = []
    latencies = []
    for r in dataset:
        t0 = time.perf_counter()
        res = system.predict_one(r["text"])
        lat = (time.perf_counter() - t0) * 1000
        latencies.append(lat)

        label_val = 1 if r["label"] == "SCAM_FRAUD" else 0
        sc = detect_script(r["text"])
        bucket = r.get("bucket", sc)
        is_prot = bool(RE_PROT.search(r["text"])) and label_val == 0
        is_adv = bool(RE_ADVISORY.search(r["text"])) and label_val == 0

        rows_eval.append({
            "is_scam": label_val,
            "pred": res["is_scam"],
            "prob": res["prob"],
            "script": sc,
            "bucket": bucket,
            "is_protected": is_prot,
            "is_advisory": is_adv,
        })

    def calc_metrics(subset):
        tp = sum(1 for r in subset if r["is_scam"] and r["pred"])
        fn = sum(1 for r in subset if r["is_scam"] and not r["pred"])
        fp = sum(1 for r in subset if not r["is_scam"] and r["pred"])
        tn = sum(1 for r in subset if not r["is_scam"] and not r["pred"])

        scam_n = tp + fn
        ham_n = tn + fp
        rec = tp / scam_n if scam_n > 0 else 0.0
        fpr = fp / ham_n if ham_n > 0 else 0.0
        prec = tp / (tp + fp) if (tp + fp) > 0 else 0.0
        f1 = (2 * prec * rec) / (prec + rec) if (prec + rec) > 0 else 0.0

        probs = np.array([r["prob"] for r in subset])
        labels = np.array([r["is_scam"] for r in subset])
        brier, ece = compute_calibration(probs, labels)

        return {
            "n": len(subset),
            "scam_n": scam_n,
            "ham_n": ham_n,
            "tp": tp,
            "fp": fp,
            "fn": fn,
            "tn": tn,
            "recall": round(rec, 4),
            "recall_ci95": list(wilson_interval(tp, scam_n)),
            "fpr": round(fpr, 4),
            "fpr_ci95": list(wilson_interval(fp, ham_n)),
            "precision": round(prec, 4),
            "f1": round(f1, 4),
            "brier": brier,
            "ece": ece,
            "inconclusive": scam_n < 100 or ham_n < 100,
        }

    overall = calc_metrics(rows_eval)

    per_script = {}
    for sc in ("latin", "devanagari", "bengali"):
        sub = [r for r in rows_eval if r["script"] == sc]
        per_script[sc] = calc_metrics(sub)

    prot_sub = [r for r in rows_eval if r["is_protected"]]
    prot_fp = sum(1 for r in prot_sub if r["pred"])
    prot_fpr = prot_fp / len(prot_sub) if prot_sub else 0.0

    adv_sub = [r for r in rows_eval if r["is_advisory"]]
    adv_fp = sum(1 for r in adv_sub if r["pred"])
    adv_fpr = adv_fp / len(adv_sub) if adv_sub else 0.0

    return {
        "overall": overall,
        "per_script": per_script,
        "protected_traffic": {
            "n": len(prot_sub),
            "fp": prot_fp,
            "fpr": round(prot_fpr, 4),
            "ci95": list(wilson_interval(prot_fp, len(prot_sub))),
        },
        "advisories": {
            "n": len(adv_sub),
            "fp": adv_fp,
            "fpr": round(adv_fpr, 4),
            "ci95": list(wilson_interval(adv_fp, len(adv_sub))),
        },
        "latency_ms": {
            "p50": round(float(np.percentile(latencies, 50)), 3),
            "p95": round(float(np.percentile(latencies, 95)), 3),
        },
    }


def run_cycle(
    cycle_num: int,
    hypothesis: str,
    c_params: dict | float,
    max_features: dict | int,
    use_translit_norm: bool,
    use_vector_split: bool,
    target_fpr_vec: float = 0.005,
    target_fpr_no: float = 0.005,
    eval_dev: bool = False,
    notes: str = "",
) -> dict:
    train_rows = load_jsonl(SPLITS_DIR / "train.jsonl")
    val_rows = load_jsonl(SPLITS_DIR / "validation.jsonl")

    manifest_sha = hashlib.sha256(open(MANIFEST_PATH, "rb").read()).hexdigest()

    seed_val_metrics = []
    seed_dev_metrics = []

    model_candidate_path = ROOT / f"protection-engine/training/candidate_cycle_{cycle_num:02d}.json"

    # Evaluate across 3 seeds
    for s_idx, seed in enumerate(SEEDS):
        system = Round2StudentSystem(
            c_params=c_params,
            max_features_per_script=max_features,
            use_translit_norm=use_translit_norm,
            use_vector_split=use_vector_split,
            random_state=seed,
        )
        system.fit(train_rows)
        system.calibrate_thresholds(
            val_rows, target_fpr_vec=target_fpr_vec, target_fpr_no=target_fpr_no
        )

        val_res = evaluate_system(system, val_rows)
        seed_val_metrics.append(val_res)

        if eval_dev:
            dev_rows = load_jsonl(SPLITS_DIR / "dev_challenge.jsonl")
            dev_res = evaluate_system(system, dev_rows)
            seed_dev_metrics.append(dev_res)

        # Export candidate from seed 0
        if s_idx == 0:
            system.export_candidate(model_candidate_path)

    cand_sha = hashlib.sha256(open(model_candidate_path, "rb").read()).hexdigest()

    # Aggregate validation metrics across seeds
    v0 = seed_val_metrics[0]["overall"]
    val_overall = {
        "n": v0["n"],
        "scam_n": v0["scam_n"],
        "ham_n": v0["ham_n"],
        "tp": int(np.mean([m["overall"]["tp"] for m in seed_val_metrics])),
        "fp": int(np.mean([m["overall"]["fp"] for m in seed_val_metrics])),
        "fn": int(np.mean([m["overall"]["fn"] for m in seed_val_metrics])),
        "tn": int(np.mean([m["overall"]["tn"] for m in seed_val_metrics])),
        "recall": round(float(np.mean([m["overall"]["recall"] for m in seed_val_metrics])), 4),
        "recall_ci95": v0["recall_ci95"],
        "fpr": round(float(np.mean([m["overall"]["fpr"] for m in seed_val_metrics])), 4),
        "fpr_ci95": v0["fpr_ci95"],
        "precision": round(float(np.mean([m["overall"]["precision"] for m in seed_val_metrics])), 4),
        "f1": round(float(np.mean([m["overall"]["f1"] for m in seed_val_metrics])), 4),
        "brier": round(float(np.mean([m["overall"]["brier"] for m in seed_val_metrics])), 4),
        "ece": round(float(np.mean([m["overall"]["ece"] for m in seed_val_metrics])), 4),
        "inconclusive": v0["inconclusive"],
    }

    per_script_val = {}
    for sc in ("latin", "devanagari", "bengali"):
        s0 = seed_val_metrics[0]["per_script"][sc]
        per_script_val[sc] = {
            "n": s0["n"],
            "scam_n": s0["scam_n"],
            "ham_n": s0["ham_n"],
            "tp": int(np.mean([m["per_script"][sc]["tp"] for m in seed_val_metrics])),
            "fp": int(np.mean([m["per_script"][sc]["fp"] for m in seed_val_metrics])),
            "fn": int(np.mean([m["per_script"][sc]["fn"] for m in seed_val_metrics])),
            "tn": int(np.mean([m["per_script"][sc]["tn"] for m in seed_val_metrics])),
            "recall": round(float(np.mean([m["per_script"][sc]["recall"] for m in seed_val_metrics])), 4),
            "recall_ci95": s0["recall_ci95"],
            "fpr": round(float(np.mean([m["per_script"][sc]["fpr"] for m in seed_val_metrics])), 4),
            "fpr_ci95": s0["fpr_ci95"],
            "precision": round(float(np.mean([m["per_script"][sc]["precision"] for m in seed_val_metrics])), 4),
            "f1": round(float(np.mean([m["per_script"][sc]["f1"] for m in seed_val_metrics])), 4),
            "brier": round(float(np.mean([m["per_script"][sc]["brier"] for m in seed_val_metrics])), 4),
            "ece": round(float(np.mean([m["per_script"][sc]["ece"] for m in seed_val_metrics])), 4),
            "inconclusive": s0["inconclusive"],
        }

    cycle_record = {
        "cycle": cycle_num,
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "hypothesis": hypothesis,
        "candidate_sha256": cand_sha,
        "dataset_manifest_sha256": manifest_sha,
        "seeds": SEEDS,
        "parameters": {
            "c_params": c_params,
            "max_features": max_features,
            "use_translit_norm": use_translit_norm,
            "use_vector_split": use_vector_split,
            "target_fpr_vec": target_fpr_vec,
            "target_fpr_no": target_fpr_no,
        },
        "validation_metrics": val_overall,
        "per_language_validation": per_script_val,
        "protected_traffic_fpr": round(float(np.mean([m["protected_traffic"]["fpr"] for m in seed_val_metrics])), 4),
        "advisory_fpr": round(float(np.mean([m["advisories"]["fpr"] for m in seed_val_metrics])), 4),
        "latency_ms": {
            "p50": round(float(np.mean([m["latency_ms"]["p50"] for m in seed_val_metrics])), 3),
            "p95": round(float(np.mean([m["latency_ms"]["p95"] for m in seed_val_metrics])), 3),
        },
        "lockbox_evaluated": False,
        "dev_evaluated": eval_dev,
        "notes": notes,
    }

    if eval_dev and seed_dev_metrics:
        d0 = seed_dev_metrics[0]["overall"]
        cycle_record["dev_metrics"] = {
            "n": d0["n"],
            "scam_n": d0["scam_n"],
            "ham_n": d0["ham_n"],
            "tp": int(np.mean([m["overall"]["tp"] for m in seed_dev_metrics])),
            "fp": int(np.mean([m["overall"]["fp"] for m in seed_dev_metrics])),
            "fn": int(np.mean([m["overall"]["fn"] for m in seed_dev_metrics])),
            "tn": int(np.mean([m["overall"]["tn"] for m in seed_dev_metrics])),
            "recall": round(float(np.mean([m["overall"]["recall"] for m in seed_dev_metrics])), 4),
            "recall_ci95": d0["recall_ci95"],
            "fpr": round(float(np.mean([m["overall"]["fpr"] for m in seed_dev_metrics])), 4),
            "fpr_ci95": d0["fpr_ci95"],
            "precision": round(float(np.mean([m["overall"]["precision"] for m in seed_dev_metrics])), 4),
            "f1": round(float(np.mean([m["overall"]["f1"] for m in seed_dev_metrics])), 4),
            "brier": round(float(np.mean([m["overall"]["brier"] for m in seed_dev_metrics])), 4),
            "ece": round(float(np.mean([m["overall"]["ece"] for m in seed_dev_metrics])), 4),
            "inconclusive": d0["inconclusive"],
        }
        per_script_dev = {}
        for sc in ("latin", "devanagari", "bengali"):
            sd0 = seed_dev_metrics[0]["per_script"][sc]
            per_script_dev[sc] = {
                "n": sd0["n"],
                "scam_n": sd0["scam_n"],
                "ham_n": sd0["ham_n"],
                "tp": int(np.mean([m["per_script"][sc]["tp"] for m in seed_dev_metrics])),
                "fp": int(np.mean([m["per_script"][sc]["fp"] for m in seed_dev_metrics])),
                "fn": int(np.mean([m["per_script"][sc]["fn"] for m in seed_dev_metrics])),
                "tn": int(np.mean([m["per_script"][sc]["tn"] for m in seed_dev_metrics])),
                "recall": round(float(np.mean([m["per_script"][sc]["recall"] for m in seed_dev_metrics])), 4),
                "recall_ci95": sd0["recall_ci95"],
                "fpr": round(float(np.mean([m["per_script"][sc]["fpr"] for m in seed_dev_metrics])), 4),
                "fpr_ci95": sd0["fpr_ci95"],
                "inconclusive": sd0["inconclusive"],
            }
        cycle_record["per_language_dev"] = per_script_dev

    out_file = CYCLES_DIR / f"cycle_{cycle_num:02d}_{notes.split()[0].lower() if notes else 'run'}.json"
    with open(out_file, "w", encoding="utf-8") as f:
        json.dump(cycle_record, f, indent=2)

    print(f"\n[CYCLE {cycle_num:02d} COMPLETED] Log written to {out_file}")
    print(f"Validation: Recall={val_overall['recall']*100:.2f}% {val_overall['recall_ci95']}, FPR={val_overall['fpr']*100:.2f}% {val_overall['fpr_ci95']}, Brier={val_overall['brier']}, ECE={val_overall['ece']}")
    print(f"Per-script Val Recall/FPR: Latin {per_script_val['latin']['recall']*100:.1f}%/{per_script_val['latin']['fpr']*100:.2f}%, Deva {per_script_val['devanagari']['recall']*100:.1f}%/{per_script_val['devanagari']['fpr']*100:.2f}%, Beng {per_script_val['bengali']['recall']*100:.1f}%/{per_script_val['bengali']['fpr']*100:.2f}%")
    print(f"Protected FPR: {cycle_record['protected_traffic_fpr']*100:.2f}%, Advisory FPR: {cycle_record['advisory_fpr']*100:.2f}%, P95 Latency: {cycle_record['latency_ms']['p95']} ms")
    if eval_dev:
        dm = cycle_record["dev_metrics"]
        print(f"DEV CHALLENGE: Recall={dm['recall']*100:.2f}%, FPR={dm['fpr']*100:.2f}%")

    return cycle_record


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--cycle", type=int, required=True)
    parser.add_argument("--hypothesis", type=str, required=True)
    parser.add_argument("--c-param", type=float, default=2.0)
    parser.add_argument("--c-latin", type=float, default=None)
    parser.add_argument("--c-deva", type=float, default=None)
    parser.add_argument("--c-beng", type=float, default=None)
    parser.add_argument("--max-features", type=int, default=5000)
    parser.add_argument("--feat-latin", type=int, default=None)
    parser.add_argument("--feat-deva", type=int, default=None)
    parser.add_argument("--feat-beng", type=int, default=None)
    parser.add_argument("--use-translit", action="store_true")
    parser.add_argument("--no-vector-split", action="store_true")
    parser.add_argument("--target-fpr-vec", type=float, default=0.005)
    parser.add_argument("--target-fpr-vec-latin", type=float, default=None)
    parser.add_argument("--target-fpr-vec-deva", type=float, default=None)
    parser.add_argument("--target-fpr-vec-beng", type=float, default=None)
    parser.add_argument("--target-fpr-no", type=float, default=0.005)
    parser.add_argument("--target-fpr-no-latin", type=float, default=None)
    parser.add_argument("--target-fpr-no-deva", type=float, default=None)
    parser.add_argument("--target-fpr-no-beng", type=float, default=None)
    parser.add_argument("--eval-dev", action="store_true")
    parser.add_argument("--notes", type=str, default="")
    args = parser.parse_args()

    c_params = {
        "latin": args.c_latin if args.c_latin is not None else args.c_param,
        "devanagari": args.c_deva if args.c_deva is not None else args.c_param,
        "bengali": args.c_beng if args.c_beng is not None else args.c_param,
    }
    max_features = {
        "latin": args.feat_latin if args.feat_latin is not None else args.max_features,
        "devanagari": args.feat_deva if args.feat_deva is not None else args.max_features,
        "bengali": args.feat_beng if args.feat_beng is not None else args.max_features,
    }
    target_fpr_vec = {
        "latin": args.target_fpr_vec_latin if args.target_fpr_vec_latin is not None else args.target_fpr_vec,
        "devanagari": args.target_fpr_vec_deva if args.target_fpr_vec_deva is not None else args.target_fpr_vec,
        "bengali": args.target_fpr_vec_beng if args.target_fpr_vec_beng is not None else args.target_fpr_vec,
    }
    target_fpr_no = {
        "latin": args.target_fpr_no_latin if args.target_fpr_no_latin is not None else args.target_fpr_no,
        "devanagari": args.target_fpr_no_deva if args.target_fpr_no_deva is not None else args.target_fpr_no,
        "bengali": args.target_fpr_no_beng if args.target_fpr_no_beng is not None else args.target_fpr_no,
    }

    run_cycle(
        cycle_num=args.cycle,
        hypothesis=args.hypothesis,
        c_params=c_params,
        max_features=max_features,
        use_translit_norm=args.use_translit,
        use_vector_split=not args.no_vector_split,
        target_fpr_vec=target_fpr_vec,
        target_fpr_no=target_fpr_no,
        eval_dev=args.eval_dev,
        notes=args.notes,
    )
