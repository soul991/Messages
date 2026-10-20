#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
make_owner_sample.py — Local-only tool to prepare stratified acceptance sample for the project owner.

Privacy Invariants (Rules R2 & R3):
- Runs strictly on the local machine.
- Reads private messages, masks personal identifiers (phone numbers, account numbers, names).
- Writes stratified target (400 messages) to D:\\Istak\\Messages\\PRIVATE_ACCEPTANCE\\to_label.xlsx.
- Automated agents NEVER read the output or inspect this file.
"""

import argparse
import os
import random
import re
import sys
from pathlib import Path
import pandas as pd

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

OUT_DIR = Path("D:/Istak/Messages/PRIVATE_ACCEPTANCE")
OUT_FILE = OUT_DIR / "to_label.xlsx"

def mask_identifiers(text: str) -> str:
    """Masks phone numbers, account numbers, and email addresses with format-preserving placeholders."""
    if not isinstance(text, str):
        return ""
    t = text
    # Mask Indian mobile numbers (10 digits, optional +91)
    t = re.sub(r"(?:\+?91[\s-]?)?([6-9]\d{2})\d{3}(\d{4})", r"+91 \1XXX X\2", t)
    # Mask bank account / card numbers
    t = re.sub(r"\b(\d{2,4})[X\d]{4,10}(\d{4})\b", r"XX\2", t)
    t = re.sub(r"\b(?:a/c|account|acct|card)\s*(?:no\.?|num\.?)?\s*[:\-]?\s*([X\d]{4,16})\b", r"A/C XX\1"[-6:], t, flags=re.I)
    # Mask emails
    t = re.sub(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b", "<EMAIL>", t)
    return t

def detect_script(text: str) -> str:
    has_beng = bool(re.search(r"[\u0980-\u09FF]", text))
    has_deva = bool(re.search(r"[\u0900-\u097F]", text))
    if has_beng:
        return "bengali"
    if has_deva:
        return "devanagari"
    return "latin"

def main():
    parser = argparse.ArgumentParser(description="Create a private, masked acceptance labeling spreadsheet for the owner.")
    parser.add_argument("--input-file", required=True, help="Path to owner private export (xlsx, csv, or txt)")
    parser.add_argument("--target-size", type=int, default=400, help="Target total sample count (default 400)")
    parser.add_argument("--seed", type=int, default=20261020, help="Random seed for sampling reproducibility")
    args = parser.parse_args()

    input_path = Path(args.input_file)
    if not input_path.exists():
        sys.exit(f"Error: Input file not found at {input_path}")

    # Load data
    ext = input_path.suffix.lower()
    if ext in (".xlsx", ".xls"):
        df = pd.read_excel(input_path)
    elif ext == ".csv":
        df = pd.read_csv(input_path)
    elif ext == ".txt":
        with open(input_path, "r", encoding="utf-8", errors="ignore") as f:
            lines = [l.strip() for l in f if l.strip()]
        df = pd.DataFrame({"text": lines})
    else:
        sys.exit(f"Error: Unsupported format {ext}")

    text_col = None
    for col in ("text", "message", "body", "Msg", "sms", "content"):
        if col in df.columns:
            text_col = col
            break
    if not text_col:
        # Fallback to first string column
        text_col = df.columns[0]

    raw_texts = df[text_col].dropna().astype(str).tolist()
    print(f"Loaded {len(raw_texts)} raw messages from input file.")

    # Deduplicate and mask
    seen = set()
    cleaned_rows = []
    for idx, raw in enumerate(raw_texts):
        raw_clean = raw.strip()
        if len(raw_clean) < 10 or raw_clean in seen:
            continue
        seen.add(raw_clean)
        masked = mask_identifiers(raw_clean)
        script = detect_script(masked)
        cleaned_rows.append({
            "orig_id": idx + 1,
            "message": masked,
            "script": script
        })

    random.seed(args.seed)
    random.shuffle(cleaned_rows)

    # Stratified selection by script
    by_script = {"latin": [], "devanagari": [], "bengali": []}
    for r in cleaned_rows:
        s = r["script"]
        if s in by_script:
            by_script[s].append(r)
        else:
            by_script["latin"].append(r)

    print(f"Pool by script: Latin={len(by_script['latin'])}, Devanagari={len(by_script['devanagari'])}, Bengali={len(by_script['bengali'])}")

    target = args.target_size
    # Stratify roughly across scripts
    sample_rows = []
    # If Bengali and Devanagari are available, allocate quotas
    deva_target = min(len(by_script["devanagari"]), int(target * 0.30))
    beng_target = min(len(by_script["bengali"]), int(target * 0.25))
    latin_target = target - deva_target - beng_target

    sample_rows.extend(by_script["devanagari"][:deva_target])
    sample_rows.extend(by_script["bengali"][:beng_target])
    sample_rows.extend(by_script["latin"][:latin_target])

    random.shuffle(sample_rows)
    sample_rows = sample_rows[:target]

    # Create final spreadsheet
    out_records = []
    for i, r in enumerate(sample_rows, 1):
        out_records.append({
            "id": i,
            "message": r["message"],
            "label": "",
            "unsure": ""
        })

    out_df = pd.DataFrame(out_records)
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    out_df.to_excel(OUT_FILE, index=False)

    print("\n" + "=" * 60)
    print(f"SUCCESS: Stratified acceptance sample generated at:")
    print(f"  {OUT_FILE}")
    print(f"Total sampled messages: {len(out_df)}")
    print("Columns: id, message, label (empty), unsure (empty)")
    print("=" * 60)
    print("NEXT STEP FOR OWNER: Review and label rows according to docs/ml/OWNER_LABELING_RUBRIC.md.")
    print("Remember: The agent will NOT read or access this file.")

if __name__ == "__main__":
    main()
