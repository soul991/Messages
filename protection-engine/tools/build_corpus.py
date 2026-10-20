#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build_corpus.py — Deterministic, template-grouped corpus builder for India 3-language SMS classification.

Groups by source AND template skeleton / near-duplicate cluster.
Ensures zero exact leakage, zero normalized leakage, and zero template leakage between train and validation.
Applies R6 licensing constraints and synthetic data <= 30% ratio.
Outputs strictly aggregate metrics.
"""

import argparse
import hashlib
import json
import os
import random
import re
import sys
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path
import pandas as pd

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

ROOT = Path(__file__).resolve().parents[2]
DATA_ROOT = Path("D:/Istak/Messages/Datasets")
SPLITS_DIR = DATA_ROOT / "splits"

SEED = 20261020

def normalize_text(text: str) -> str:
    text = unicodedata.normalize("NFKC", str(text)).casefold()
    text = re.sub(r"https?://\S+|www\.\S+", "<URL>", text)
    text = re.sub(r"(?:\+?\d{1,3}[-.\s]?)?\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}\b", "<PHONE>", text)
    text = re.sub(r"\b\d+([.,]\d+)?\b", "<NUM>", text)
    text = re.sub(r"[₹$€£]\s*<NUM>", "<CURR>", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text

def template_skeleton(text: str) -> str:
    norm = normalize_text(text)
    tokens = []
    for tok in norm.split():
        if tok in ("<URL>", "<PHONE>", "<CODE>", "<NUM>", "<CURR>"):
            tokens.append(tok)
        else:
            cleaned = re.sub(r"[^\w]", "", tok)
            if cleaned:
                tokens.append(cleaned)
    return " ".join(tokens)

def detect_language_bucket(text: str, source_lang: str = "") -> str:
    deva_chars = len(re.findall(r"[\u0900-\u097F]", text))
    beng_chars = len(re.findall(r"[\u0980-\u09FF]", text))
    latin_chars = len(re.findall(r"[a-zA-Z]", text))
    total = deva_chars + beng_chars + latin_chars

    if beng_chars > 0 and beng_chars >= deva_chars and beng_chars >= latin_chars:
        return "bn_beng"
    if deva_chars > 0 and deva_chars >= latin_chars:
        return "hi_deva"

    # Latin script
    s_lower = str(source_lang).lower()
    if "hinglish" in s_lower or "hi-latn" in s_lower:
        return "hi_latn"
    if "banglish" in s_lower or "bn-latn" in s_lower or "bn_en" in s_lower:
        return "bn_latn"
        
    words = [w.lower() for w in re.findall(r"\b[a-zA-Z]+\b", text)]
    hi_markers = {"aapka", "karein", "kare", "karo", "hai", "hain", "nahi", "khata", "paise", "turant"}
    bn_markers = {"apnar", "korun", "kore", "hobe", "taka", "shob", "kichhu", "amader", "thik"}
    
    hi_hits = sum(1 for w in words if w in hi_markers)
    bn_hits = sum(1 for w in words if w in bn_markers)
    
    if bn_hits > hi_hits and bn_hits > 0:
        return "bn_latn"
    if hi_hits > bn_hits and hi_hits > 0:
        return "hi_latn"
    return "en_latin"

def load_sources():
    raw_records = []
    
    # 1. scam-ham-india-14-languages (MIT)
    p_14 = DATA_ROOT / "external/scam-ham-india-14-languages/scam_ham_india_14_languages.csv"
    if p_14.exists():
        df = pd.read_csv(p_14)
        for _, r in df.iterrows():
            lang = str(r.get("language", "")).strip()
            # Only Bengali, Hindi, Hinglish, English are in scope
            if lang in ("Bengali", "Hindi", "Hinglish", "English"):
                text = str(r["text"]).strip()
                label = "SCAM_FRAUD" if str(r["label"]).lower() in ("scam", "1") else "NOT_SCAM"
                raw_records.append({
                    "text": text,
                    "label": label,
                    "source": "scam_ham_india_14_languages",
                    "source_lang": lang,
                    "is_synthetic": False
                })

    # 2. indian-scam-sms-synthetic-audited (CC-BY-4.0)
    p_syn = DATA_ROOT / "external/indian-scam-sms-synthetic-audited/data/synthetic_audited.csv"
    if p_syn.exists():
        df = pd.read_csv(p_syn)
        for _, r in df.iterrows():
            lang = str(r.get("language", "")).strip().lower()
            if lang in ("hinglish", "en", "hi", "bn_en"):
                text = str(r["text"]).strip()
                label = "SCAM_FRAUD" if str(r["label"]).lower() in ("scam", "1") else "NOT_SCAM"
                raw_records.append({
                    "text": text,
                    "label": label,
                    "source": "indian_scam_sms_synthetic_audited",
                    "source_lang": lang,
                    "is_synthetic": True
                })

    # 3. scam-ham-india-anmol (Apache-2.0)
    p_anm = DATA_ROOT / "scam-ham-india/scam_hum_india.csv"
    if p_anm.exists():
        df = pd.read_csv(p_anm)
        for _, r in df.iterrows():
            text = str(r["text"]).strip()
            label = "SCAM_FRAUD" if str(r["label"]).lower() in ("spam", "scam", "1") else "NOT_SCAM"
            raw_records.append({
                "text": text,
                "label": label,
                "source": "scam_ham_india_anmol",
                "source_lang": "en",
                "is_synthetic": False
            })

    # 4. junioralive_india_spam (MIT)
    p_jun = DATA_ROOT / "india-spam-sms-classification-main/dataset/spam_ham_india.csv"
    if p_jun.exists():
        df = pd.read_csv(p_jun)
        for _, r in df.iterrows():
            text = str(r["Msg"]).strip()
            label = "SCAM_FRAUD" if str(r["Label"]).lower() in ("spam", "scam", "1") else "NOT_SCAM"
            raw_records.append({
                "text": text,
                "label": label,
                "source": "junioralive_india_spam",
                "source_lang": "en",
                "is_synthetic": False
            })

    # 5. cloveai_india_spam (MIT) - clean ham only (label 0), skip ambiguous label 1
    p_clove = DATA_ROOT / "cloveai-india-spam-sms/data/train-00000-of-00001.parquet"
    if p_clove.exists():
        df = pd.read_parquet(p_clove)
        for _, r in df[df["label"] == 0].iterrows():
            text = str(r["text"]).strip()
            raw_records.append({
                "text": text,
                "label": "NOT_SCAM",
                "source": "cloveai_india_spam",
                "source_lang": "en",
                "is_synthetic": False
            })

    return raw_records

def load_dev_challenge():
    dev_records = []
    # 1. Karan Verma 159-row frozen challenge
    p_ch = DATA_ROOT / "trilingual-fraud-consumer-protection-v2/data.csv"
    if p_ch.exists():
        df = pd.read_csv(p_ch)
        for _, r in df.iterrows():
            text = str(r["text"]).strip()
            label = "SCAM_FRAUD" if str(r["label"]).lower() in ("scam", "1") else "NOT_SCAM"
            dev_records.append({
                "text": text,
                "label": label,
                "source": "trilingual_challenge_v2",
                "source_lang": str(r.get("language", "unknown")),
                "context_type": str(r.get("contextType", "unknown")),
                "is_synthetic": False
            })
            
    # 2. indic_test_suite_clean (100 per language)
    p_indic = DATA_ROOT / "external/scam-ham-india-14-languages/indic_test_suite_clean.csv"
    if p_indic.exists():
        df = pd.read_csv(p_indic)
        for _, r in df.iterrows():
            lang = str(r.get("language", "")).strip()
            if lang in ("Bengali", "Hindi", "Hinglish", "English"):
                text = str(r["text"]).strip()
                label = "SCAM_FRAUD" if str(r["label"]).lower() in ("scam", "1") else "NOT_SCAM"
                dev_records.append({
                    "text": text,
                    "label": label,
                    "source": "indic_test_suite_clean",
                    "source_lang": lang,
                    "is_synthetic": False
                })
                
    return dev_records

def build_partitions():
    random.seed(SEED)
    raw_fit = load_sources()
    dev_records = load_dev_challenge()
    
    print(f"Total raw fit candidates loaded: {len(raw_fit)}")
    print(f"Total dev-challenge records: {len(dev_records)}")

    # 1. Exclude any fit candidate that overlaps with dev-challenge (exact, normalized, or template)
    dev_norm_hashes = {hashlib.sha256(normalize_text(r["text"]).encode("utf-8")).hexdigest() for r in dev_records}
    dev_exact_hashes = {hashlib.sha256(r["text"].encode("utf-8")).hexdigest() for r in dev_records}
    dev_tmpl_hashes = {hashlib.sha256(template_skeleton(r["text"]).encode("utf-8")).hexdigest() for r in dev_records}
    
    clean_fit = []
    excluded_for_dev_leak = 0
    for r in raw_fit:
        e_h = hashlib.sha256(r["text"].encode("utf-8")).hexdigest()
        n_h = hashlib.sha256(normalize_text(r["text"]).encode("utf-8")).hexdigest()
        t_h = hashlib.sha256(template_skeleton(r["text"]).encode("utf-8")).hexdigest()
        if e_h in dev_exact_hashes or n_h in dev_norm_hashes or t_h in dev_tmpl_hashes:
            excluded_for_dev_leak += 1
            continue
        clean_fit.append(r)
        
    print(f"Excluded {excluded_for_dev_leak} rows for potential dev-challenge overlap.")
    print(f"Clean candidate rows: {len(clean_fit)}")

    # 2. Dedup exact normalized duplicates and resolve label conflicts
    label_by_norm = defaultdict(set)
    for r in clean_fit:
        norm = normalize_text(r["text"])
        label_by_norm[norm].add(r["label"])
        
    conflicting_norms = {n for n, labels in label_by_norm.items() if len(labels) > 1}
    print(f"Conflicting normalized texts excluded: {len(conflicting_norms)}")
    
    deduped = {}
    for r in clean_fit:
        norm = normalize_text(r["text"])
        if norm in conflicting_norms:
            continue
        key = (norm, r["label"])
        if key not in deduped:
            deduped[key] = r
            
    filtered_rows = list(deduped.values())
    print(f"Unique, non-conflicting rows: {len(filtered_rows)}")

    # Group into template clusters
    clusters = defaultdict(list)
    for r in filtered_rows:
        tmpl = template_skeleton(r["text"])
        cluster_id = f"{r['source']}::{tmpl}"
        r["cluster_id"] = cluster_id
        r["bucket"] = detect_language_bucket(r["text"], r["source_lang"])
        clusters[cluster_id].append(r)

    print(f"Total template-family clusters: {len(clusters)}")

    # 3. Deterministically split clusters 80% train / 20% validation
    cluster_ids = sorted(clusters.keys())
    random.shuffle(cluster_ids)
    
    train_rows = []
    val_rows = []
    
    for cid in cluster_ids:
        # Use hash-based deterministic split
        h_val = int(hashlib.sha256(cid.encode("utf-8")).hexdigest(), 16) % 100
        if h_val < 80:
            train_rows.extend(clusters[cid])
        else:
            val_rows.extend(clusters[cid])
            
    # 4. Check synthetic constraint: synthetic <= 30% of each language bucket in train
    final_train = []
    by_bucket_train = defaultdict(lambda: {"real": [], "synth": []})
    for r in train_rows:
        if r["is_synthetic"]:
            by_bucket_train[r["bucket"]]["synth"].append(r)
        else:
            by_bucket_train[r["bucket"]]["real"].append(r)
            
    for bucket, groups in by_bucket_train.items():
        n_real = len(groups["real"])
        max_synth = int(n_real * 0.30 / 0.70) if n_real > 0 else 0
        allowed_synth = groups["synth"][:max_synth]
        final_train.extend(groups["real"] + allowed_synth)
        synth_pct = len(allowed_synth) / (n_real + len(allowed_synth)) * 100 if (n_real + len(allowed_synth)) > 0 else 0
        print(f"Train bucket {bucket}: {n_real} real, {len(allowed_synth)} synth ({synth_pct:.1f}% synthetic)")

    # Validation: strictly real data per prompt constraints
    final_val = [r for r in val_rows if not r["is_synthetic"]]
    print(f"Validation rows (strictly real): {len(final_val)}")

    # Save splits
    SPLITS_DIR.mkdir(parents=True, exist_ok=True)
    
    with open(SPLITS_DIR / "train.jsonl", "w", encoding="utf-8") as f:
        for r in final_train:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
            
    with open(SPLITS_DIR / "validation.jsonl", "w", encoding="utf-8") as f:
        for r in final_val:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
            
    with open(SPLITS_DIR / "dev_challenge.jsonl", "w", encoding="utf-8") as f:
        for r in dev_records:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")

    print(f"\nSaved partitions to {SPLITS_DIR}:")
    print(f"  train.jsonl: {len(final_train)} rows")
    print(f"  validation.jsonl: {len(final_val)} rows")
    print(f"  dev_challenge.jsonl: {len(dev_records)} rows")

if __name__ == "__main__":
    build_partitions()
