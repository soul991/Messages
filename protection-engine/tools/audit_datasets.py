#!/usr/bin/env python3
"""audit_datasets.py — Quality audit of candidate datasets for SMS classification.
Aggregate-only output, deterministic, no raw private messages.
"""
import os, sys, json, re, hashlib
from collections import Counter
import pandas as pd

def normalize_text(text):
    if not isinstance(text, str):
        return ""
    t = text.lower()
    t = re.sub(r"\d+", "<NUM>", t)
    t = re.sub(r"https?://\S+", "<URL>", t)
    t = re.sub(r"\s+", " ", t).strip()
    return t

def digest(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()

def detect_script(text):
    if not isinstance(text, str):
        return "unknown"
    has_deva = bool(re.search(r"[\u0900-\u097F]", text))
    has_beng = bool(re.search(r"[\u0980-\u09FF]", text))
    has_latin = bool(re.search(r"[a-zA-Z]", text))
    if has_beng:
        return "bengali"
    if has_deva:
        return "devanagari"
    if has_latin:
        return "latin"
    return "other"

def main():
    print("=== DATASET QUALITY AUDIT (Aggregate Only) ===")
    
    datasets = {
        "scam_ham_india_14_languages": {
            "path": "D:/Istak/Messages/Datasets/external/scam-ham-india-14-languages/scam_ham_india_14_languages.csv",
            "type": "csv",
            "text_col": "text",
            "label_col": "label"
        },
        "synthetic_audited": {
            "path": "D:/Istak/Messages/Datasets/external/indian-scam-sms-synthetic-audited/data/synthetic_audited.csv",
            "type": "csv",
            "text_col": "text",
            "label_col": "label"
        },
        "scam_ham_india_anmol": {
            "path": "D:/Istak/Messages/Datasets/scam-ham-india/scam_hum_india.csv",
            "type": "csv",
            "text_col": "text",
            "label_col": "label"
        },
        "junioralive_india_spam": {
            "path": "D:/Istak/Messages/Datasets/india-spam-sms-classification-main/dataset/spam_ham_india.csv",
            "type": "csv",
            "text_col": "Msg",
            "label_col": "Label"
        },
        "cloveai_india_spam": {
            "path": "D:/Istak/Messages/Datasets/cloveai-india-spam-sms/data/train-00000-of-00001.parquet",
            "type": "parquet",
            "text_col": "text",
            "label_col": "label"
        },
        "bengali_smishing_train": {
            "path": "D:/Istak/Messages/Datasets/bengali-sms-smishing-dataset/data/train-00000-of-00001.parquet",
            "type": "parquet",
            "text_col": "text",
            "label_col": "label"
        },
        "frozen_challenge": {
            "path": "D:/Istak/Messages/Datasets/trilingual-fraud-consumer-protection-v2/data.csv",
            "type": "csv",
            "text_col": "text",
            "label_col": "label"
        }
    }
    
    all_texts = {}
    normalized_texts = {}
    audit_summary = {}
    
    for name, cfg in datasets.items():
        p = cfg["path"]
        if not os.path.exists(p):
            print(f"Skipping {name}: file not found")
            continue
            
        if cfg["type"] == "csv":
            df = pd.read_csv(p)
        elif cfg["type"] == "parquet":
            df = pd.read_parquet(p)
        else:
            continue
            
        tcol = cfg["text_col"]
        lcol = cfg["label_col"]
        
        texts = [str(x) for x in df[tcol].dropna()]
        labels = [str(x) for x in df[lcol].dropna()]
        norm = [normalize_text(x) for x in texts]
        scripts = [detect_script(x) for x in texts]
        
        n_rows = len(texts)
        exact_unique = len(set(texts))
        norm_unique = len(set(norm))
        exact_dups = n_rows - exact_unique
        norm_dups = n_rows - norm_unique
        
        script_dist = dict(Counter(scripts))
        class_dist = dict(Counter(labels))
        
        all_texts[name] = set(texts)
        normalized_texts[name] = set(norm)
        
        audit_summary[name] = {
            "total_rows": n_rows,
            "exact_duplicates": exact_dups,
            "normalized_duplicates": norm_dups,
            "script_distribution": script_dist,
            "class_distribution": class_dist
        }
        
        print(f"\n--- {name} ---")
        print(f"Rows: {n_rows} | Exact Dups: {exact_dups} | Normalized Dups: {norm_dups}")
        print(f"Scripts: {script_dist}")
        print(f"Classes: {class_dist}")
        
    print("\n=== CROSS-SOURCE OVERLAP AUDIT ===")
    challenge_set = all_texts.get("frozen_challenge", set())
    challenge_norm = normalized_texts.get("frozen_challenge", set())
    
    for name, text_set in all_texts.items():
        if name == "frozen_challenge":
            continue
        exact_leak = len(text_set.intersection(challenge_set))
        norm_leak = len(normalized_texts[name].intersection(challenge_norm))
        print(f"{name} vs frozen_challenge: Exact overlap={exact_leak}, Normalized overlap={norm_leak}")
        
    os.makedirs("docs/ml", exist_ok=True)
    with open("docs/ml/dataset_quality_audit.json", "w", encoding="utf-8") as f:
        json.dump(audit_summary, f, indent=2)
    print("\nWrote docs/ml/dataset_quality_audit.json")

if __name__ == "__main__":
    main()
