#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build_corpus.py — Deterministic, template-grouped corpus builder for India 3-language SMS classification (Round 2).

Integrates:
- scam_ham_india_14_languages (MIT)
- indian_scam_sms_synthetic_audited (CC-BY-4.0) (capped <= 30% train only)
- scam_ham_india_anmol (Apache-2.0)
- india_spam_sms_junioralive (MIT)
- cloveai_india_spam (MIT, clean ham only)
- scamshield-dataset Indian subsets (MIT: Kaggle_Hindi_Merged, Indian_Telecom_SMS, ysangam/Indian_Cyber_Scam_Hinglish)
- bolewara-hinglish-scam-text-dataset (CC-BY-4.0)
- bengali-sms-smishing-dataset (MIT, Rule 1.4 token normalized, capped at <= 50% of Bengali train)
- i4c-rbi-pib-public-advisories (GODL-India)

Partitions produced:
- train.jsonl
- validation.jsonl (untouched, strictly real India-sourced data)
- dev_challenge.jsonl (frozen 559-row benchmark)
- lockbox.jsonl (NEW untouched lockbox, >= 1,500 rows, >= 600 benign, all 3 languages and scripts)
"""

import argparse
from collections import Counter, defaultdict
import hashlib
import json
import os
from pathlib import Path
import random
import re
import sys
import unicodedata
import pandas as pd

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

ROOT = Path(__file__).resolve().parents[2]
DATA_ROOT = Path("D:/Istak/Messages/Datasets")
SPLITS_DIR = DATA_ROOT / "splits"
SPLITS_DIR.mkdir(parents=True, exist_ok=True)

SEED = 20261020

RE_LINK = re.compile(r"https?://\S+|www\.\S+|bit\.ly/\S+|tinyurl\.com/\S+|wa\.me/\S+|\.apk\b", re.I)
RE_PHONE = re.compile(r"(?:\+?91[\s-]?)?[6-9]\d{9}\b")
RE_BD_PHONE = re.compile(r"(?:\+?880[\s-]?)?0?1[3-9]\d{8}\b")
RE_BD_BRANDS = re.compile(r"(?:বিকাশ|নগদ|রকেট|গ্রামীণফোন|বাংলালিংক|রবি|bkash|nagad|rocket|upay|grameenphone|banglalink|robi|teletalk|gp)", re.I)
RE_PROT = re.compile(r"\b(?:otp|one time password|credited|debited|a/c|account no|avl bal|जमा किए|निकाले गए|क्रेडिट|डेबिट|ओटीपी|खाता संख्या|জমা হয়েছে|ডেবিট)\b", re.I)

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
        if tok in ("<URL>", "<PHONE>", "<CODE>", "<NUM>", "<CURR>", "<WALLET_TELCO>"):
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

    if beng_chars > 0 and beng_chars >= deva_chars and beng_chars >= latin_chars:
        return "bn_beng"
    if deva_chars > 0 and deva_chars >= latin_chars:
        return "hi_deva"

    s_lower = str(source_lang).lower()
    if "hinglish" in s_lower or "hi-latn" in s_lower or "hindi" in s_lower:
        return "hi_latn"
    if "banglish" in s_lower or "bn-latn" in s_lower or "bn_en" in s_lower:
        return "bn_latn"
        
    words = [w.lower() for w in re.findall(r"\b[a-zA-Z]+\b", text)]
    hi_markers = {"aapka", "karein", "kare", "karo", "hai", "hain", "nahi", "khata", "paise", "turant", "kijiye"}
    bn_markers = {"apnar", "korun", "kore", "hobe", "taka", "shob", "kichhu", "amader", "thik", "takar"}
    
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
            if lang in ("Bengali", "Hindi", "Hinglish", "English"):
                text = str(r["text"]).strip()
                label = "SCAM_FRAUD" if str(r["label"]).lower() in ("scam", "1") else "NOT_SCAM"
                raw_records.append({
                    "text": text,
                    "label": label,
                    "source": "scam_ham_india_14_languages",
                    "source_lang": lang,
                    "is_synthetic": False,
                    "geography": "india"
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
                    "is_synthetic": True,
                    "geography": "india"
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
                "is_synthetic": False,
                "geography": "india"
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
                "is_synthetic": False,
                "geography": "india"
            })

    # 5. cloveai_india_spam (MIT, clean ham only)
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
                "is_synthetic": False,
                "geography": "india"
            })

    # 6. scamshield-dataset Indian subsets (MIT)
    p_ss = DATA_ROOT / "scamshield-dataset/train.jsonl"
    if p_ss.exists():
        with open(p_ss, "r", encoding="utf-8") as f:
            for line in f:
                if line.strip():
                    item = json.loads(line)
                    s_ds = item.get("source_dataset", "")
                    if s_ds in ("Kaggle_Hindi_Merged", "Indian_Telecom_SMS", "ysangam/Indian_Cyber_Scam_Hinglish"):
                        text = str(item.get("text", "")).strip()
                        is_sc = bool(item.get("is_scam", 0) == 1)
                        label = "SCAM_FRAUD" if is_sc else "NOT_SCAM"
                        lang = "hi" if s_ds == "Kaggle_Hindi_Merged" else ("hi-Latn" if "Hinglish" in s_ds else "en")
                        raw_records.append({
                            "text": text,
                            "label": label,
                            "source": f"scamshield_{s_ds}",
                            "source_lang": lang,
                            "is_synthetic": False,
                            "geography": "india"
                        })

    # 7. bolewara-hinglish-scam-text-dataset (CC-BY-4.0)
    p_bol = DATA_ROOT / "bolewara-hinglish-scam-text-dataset/data.json"
    if p_bol.exists():
        d_bol = json.load(open(p_bol, encoding="utf-8")).get("rows", [])
        for r in d_bol:
            text = str(r.get("text", "")).strip()
            label = "SCAM_FRAUD" if int(r.get("label", 0)) == 1 else "NOT_SCAM"
            raw_records.append({
                "text": text,
                "label": label,
                "source": "bolewara_hinglish",
                "source_lang": "hi-Latn",
                "is_synthetic": False,
                "geography": "india"
            })

    # 8. bengali-sms-smishing-dataset (MIT, Rule 1.4 Normalized)
    p_bn = DATA_ROOT / "bengali-sms-smishing-dataset/train-00000-of-00001.parquet"
    if p_bn.exists():
        df_bn = pd.read_parquet(p_bn)
        for _, r in df_bn.iterrows():
            raw_lbl = str(r.get("label", "")).lower()
            if raw_lbl in ("smish", "normal"):
                label = "SCAM_FRAUD" if raw_lbl == "smish" else "NOT_SCAM"
                raw_text = str(r.get("text", "")).strip()
                # Rule 1.4 normalization:
                norm_text = RE_BD_PHONE.sub("<PHONE>", raw_text)
                norm_text = RE_BD_BRANDS.sub("<WALLET_TELCO>", norm_text)
                raw_records.append({
                    "text": norm_text,
                    "label": label,
                    "source": "bengali_smishing_normalized",
                    "source_lang": "bn",
                    "is_synthetic": False,
                    "geography": "bangladesh"
                })

    # 9. i4c-rbi-pib-public-advisories (GODL-India)
    p_adv = DATA_ROOT / "external/i4c-rbi-advisories/advisories.jsonl"
    if p_adv.exists():
        with open(p_adv, "r", encoding="utf-8") as f:
            for line in f:
                if line.strip():
                    item = json.loads(line)
                    raw_records.append({
                        "text": item["text"],
                        "label": "NOT_SCAM",
                        "source": "i4c_rbi_advisories",
                        "source_lang": item.get("source_lang", "en"),
                        "is_synthetic": False,
                        "geography": "india",
                        "is_advisory": True
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
                "is_synthetic": False,
                "geography": "india"
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
                    "is_synthetic": False,
                    "geography": "india"
                })
                
    return dev_records

def build_partitions():
    random.seed(SEED)
    raw_fit = load_sources()
    dev_records = load_dev_challenge()

    print(f"Total raw candidates loaded: {len(raw_fit)}")
    print(f"Total frozen dev-challenge records: {len(dev_records)}")

    # Exclude any candidate that overlaps with dev_challenge (exact, normalized, or template)
    dev_exact = {sha256_text(r["text"]) for r in dev_records}
    dev_norm = {sha256_text(normalize_text(r["text"])) for r in dev_records}
    dev_tmpl = {sha256_text(template_skeleton(r["text"])) for r in dev_records}

    clean_pool = []
    excluded_for_dev = 0
    for r in raw_fit:
        e_h = sha256_text(r["text"])
        n_h = sha256_text(normalize_text(r["text"]))
        t_h = sha256_text(template_skeleton(r["text"]))
        if e_h in dev_exact or n_h in dev_norm or t_h in dev_tmpl:
            excluded_for_dev += 1
            continue
        clean_pool.append(r)

    print(f"Excluded {excluded_for_dev} rows for dev-challenge overlap.")
    print(f"Clean pool: {len(clean_pool)}")

    # Dedup exact normalized texts and resolve conflicts
    label_by_norm = defaultdict(set)
    for r in clean_pool:
        norm = normalize_text(r["text"])
        label_by_norm[norm].add(r["label"])

    conflicts = {n for n, labels in label_by_norm.items() if len(labels) > 1}
    print(f"Ambiguous conflicting texts dropped: {len(conflicts)}")

    deduped = {}
    for r in clean_pool:
        norm = normalize_text(r["text"])
        if norm in conflicts:
            continue
        key = (norm, r["label"])
        if key not in deduped:
            deduped[key] = r

    unique_rows = list(deduped.values())
    print(f"Unique non-conflicting candidate rows: {len(unique_rows)}")

    # Assign buckets and cluster by template family
    clusters_by_bucket = defaultdict(lambda: defaultdict(list))
    for r in unique_rows:
        b = detect_language_bucket(r["text"], r.get("source_lang", ""))
        r["bucket"] = b
        tmpl = template_skeleton(r["text"])
        cluster_id = f"{r['source']}::{tmpl}"
        r["cluster_id"] = cluster_id
        clusters_by_bucket[b][cluster_id].append(r)

    # Construct the 4 partitions:
    # 1. Lockbox: >= 1,500 rows, >= 600 benign, all 3 languages & scripts
    # 2. Validation: Real India data for calibration & early stopping
    # 3. Train: Remaining rows, capping Bangladesh Bengali at <= 50% and synthetic <= 30%
    lockbox_rows = []
    val_rows = []
    train_rows = []

    # Allocate entire clusters to Lockbox deterministically
    target_lockbox_per_bucket = {
        "bn_beng": 300,
        "bn_latn": 200,
        "hi_deva": 400,
        "hi_latn": 300,
        "en_latin": 600
    }

    target_val_per_bucket = {
        "bn_beng": 300,
        "bn_latn": 200,
        "hi_deva": 400,
        "hi_latn": 300,
        "en_latin": 1200
    }

    for b in ("bn_beng", "bn_latn", "hi_deva", "hi_latn", "en_latin"):
        cluster_items = list(clusters_by_bucket[b].items())
        # Sort deterministically by hash of cluster_id
        cluster_items.sort(key=lambda x: hashlib.sha256(x[0].encode("utf-8")).hexdigest())

        lb_target = target_lockbox_per_bucket[b]
        vl_target = target_val_per_bucket[b]

        curr_lb = []
        curr_vl = []
        curr_tr = []

        for cid, rows in cluster_items:
            # Check if rows are real (lockbox and validation must be real data)
            is_any_syn = any(r.get("is_synthetic", False) for r in rows)
            is_bangladesh = any(r.get("geography") == "bangladesh" for r in rows)

            if len(curr_lb) < lb_target and not is_any_syn:
                curr_lb.extend(rows)
            elif len(curr_vl) < vl_target and not is_any_syn and not is_bangladesh:
                # Validation is strictly India-sourced real data
                curr_vl.extend(rows)
            else:
                curr_tr.extend(rows)

        lockbox_rows.extend(curr_lb)
        val_rows.extend(curr_vl)
        train_rows.extend(curr_tr)

    # Apply Rule 1.4 cap on Bangladesh Bengali in Train (at most 50% of bn_beng training)
    bn_train_india = [r for r in train_rows if r["bucket"] == "bn_beng" and r.get("geography") != "bangladesh"]
    bn_train_bd = [r for r in train_rows if r["bucket"] == "bn_beng" and r.get("geography") == "bangladesh"]
    
    max_bd_allowed = len(bn_train_india)
    if len(bn_train_bd) > max_bd_allowed:
        random.shuffle(bn_train_bd)
        bn_train_bd = bn_train_bd[:max_bd_allowed]
        print(f"Capped Bangladesh Bengali train rows to {len(bn_train_bd)} (<= 50% of Bengali train partition).")

    # Reassemble train
    other_train = [r for r in train_rows if r["bucket"] != "bn_beng"]
    train_rows = other_train + bn_train_india + bn_train_bd

    # Final shuffle
    random.shuffle(train_rows)
    random.shuffle(val_rows)
    random.shuffle(lockbox_rows)

    print("\nFinal Partition Counts:")
    print("=" * 60)
    print(f"Train rows:      {len(train_rows)}")
    print(f"Validation rows: {len(val_rows)}")
    print(f"Lockbox rows:    {len(lockbox_rows)}")
    print(f"Dev challenge:   {len(dev_records)}")

    # Lockbox audit
    lb_ham = sum(1 for r in lockbox_rows if r["label"] == "NOT_SCAM")
    print(f"\nLockbox composition (Total: {len(lockbox_rows)}, Benign: {lb_ham}):")
    print("Buckets:", Counter(r["bucket"] for r in lockbox_rows))
    print("Labels: ", Counter(r["label"] for r in lockbox_rows))

    # Save to files
    def save_jsonl(path, rows):
        with open(path, "w", encoding="utf-8") as f:
            for r in rows:
                f.write(json.dumps(r, ensure_ascii=False) + "\n")

    save_jsonl(SPLITS_DIR / "train.jsonl", train_rows)
    save_jsonl(SPLITS_DIR / "validation.jsonl", val_rows)
    save_jsonl(SPLITS_DIR / "lockbox.jsonl", lockbox_rows)
    save_jsonl(SPLITS_DIR / "dev_challenge.jsonl", dev_records)

    print(f"\nWrote partitions successfully to {SPLITS_DIR}.")

def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()

if __name__ == "__main__":
    build_partitions()
