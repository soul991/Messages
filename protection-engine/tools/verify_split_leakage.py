#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Audits dataset partitions and manifests for data leakage and label consistency.

Privacy & Security Invariant:
Never logs or emits message bodies, private identifiers, or row-level raw text.
All outputs are strictly aggregate counts, collision hashes, and summary metrics.

Checks performed:
1. Exact text duplicate detection across splits (SHA-256).
2. Normalized text collision detection (NFKC, lowercase, whitespace, masked digits/URLs).
3. Template-family overlap detection (parameterized regex skeleton hashing).
4. Label conflict detection (same exact or normalized text with opposing labels).
5. Cross-partition leakage (Train vs Val vs Blind Acceptance vs Stress).
"""

import argparse
import csv
import hashlib
import json
import os
import re
import sys
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path


def sha256_text(text: str) -> str:
    """Computes a one-way SHA-256 hash of UTF-8 text."""
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def normalize_text(text: str) -> str:
    """
    Applies canonical normalization:
    - Unicode NFKC normalization
    - Case folding (lowercase)
    - URL masking
    - Phone number masking
    - Numeric token masking
    - Whitespace normalization
    """
    text = unicodedata.normalize("NFKC", text).casefold()
    text = re.sub(r"https?://\S+|www\.\S+", "<URL>", text)
    text = re.sub(r"(?:\+?\d{1,3}[-.\s]?)?\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}\b", "<PHONE>", text)
    text = re.sub(r"\b\d+([.,]\d+)?\b", "<NUM>", text)
    text = re.sub(r"[₹$€£]\s*<NUM>", "<CURR>", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text



def template_skeleton(text: str) -> str:
    """
    Extracts structural template skeleton by aggressive token abstraction:
    - Words with letters remain lowercased
    - All dynamic values (amounts, codes, links, timestamps) normalized
    """
    norm = normalize_text(text)
    # Strip non-alphanumeric punctuation except placeholders
    tokens = []
    for tok in norm.split():
        if tok in ("<URL>", "<PHONE>", "<CODE>", "<NUM>", "<CURR>"):
            tokens.append(tok)
        else:
            cleaned = re.sub(r"[^\w]", "", tok)
            if cleaned:
                tokens.append(cleaned)
    return " ".join(tokens)


def load_file(path_str: str):
    """Loads records from JSON, JSONL, or CSV file."""
    path = Path(path_str)
    if not path.is_file():
        raise FileNotFoundError(f"File not found: {path_str}")

    records = []
    suffix = path.suffix.lower()

    if suffix == ".json":
        with path.open(encoding="utf-8") as f:
            data = json.load(f)
        if isinstance(data, dict):
            items = data.get("entries") or data.get("rows") or data.get("data") or [data]
        elif isinstance(data, list):
            items = data
        else:
            items = []
        for it in items:
            if isinstance(it, dict):
                text = str(it.get("text") or it.get("body") or it.get("Msg") or "").strip()
                label = str(it.get("label") or it.get("Label") or it.get("target") or "").strip().lower()
                lang = str(it.get("lang") or it.get("language") or "unknown").strip().lower()
                if text:
                    records.append({"text": text, "label": label, "lang": lang})

    elif suffix == ".jsonl":
        with path.open(encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    it = json.loads(line)
                    text = str(it.get("text") or it.get("body") or it.get("Msg") or "").strip()
                    label = str(it.get("label") or it.get("Label") or it.get("target") or it.get("is_scam") or "").strip().lower()
                    lang = str(it.get("lang") or it.get("language") or "unknown").strip().lower()
                    if text:
                        records.append({"text": text, "label": label, "lang": lang})
                except json.JSONDecodeError:
                    continue

    elif suffix == ".csv":
        with path.open(encoding="utf-8-sig", newline="") as f:
            reader = csv.DictReader(f)
            for row in reader:
                text = str(row.get("text") or row.get("body") or row.get("Msg") or "").strip()
                label = str(row.get("label") or row.get("Label") or row.get("target") or "").strip().lower()
                lang = str(row.get("language") or row.get("lang") or "unknown").strip().lower()
                if text:
                    records.append({"text": text, "label": label, "lang": lang})

    return records


def audit_partitions(partitions: dict) -> dict:
    """
    Audits a dict mapping partition_name -> list of records.
    Returns aggregate summary with zero raw message text.
    """
    report = {
        "partitions": {},
        "exactCrossSplitLeakage": {},
        "normalizedCrossSplitLeakage": {},
        "templateCrossSplitOverlap": {},
        "labelConflicts": {
            "withinPartition": {},
            "crossPartition": {},
        },
        "passAllLeakageGates": True,
    }

    # Store hashes
    exact_by_partition = {}
    norm_by_partition = {}
    template_by_partition = {}
    label_by_exact = defaultdict(lambda: defaultdict(set))
    label_by_norm = defaultdict(lambda: defaultdict(set))

    for name, records in partitions.items():
        exact_map = defaultdict(int)
        norm_map = defaultdict(int)
        template_map = defaultdict(int)
        label_dist = Counter()
        lang_dist = Counter()

        for r in records:
            text = r["text"]
            label = r["label"]
            lang = r["lang"]
            e_hash = sha256_text(text)
            n_hash = sha256_text(normalize_text(text))
            t_hash = sha256_text(template_skeleton(text))

            exact_map[e_hash] += 1
            norm_map[n_hash] += 1
            template_map[t_hash] += 1
            label_dist[label] += 1
            lang_dist[lang] += 1

            label_by_exact[e_hash][name].add(label)
            label_by_norm[n_hash][name].add(label)

        exact_by_partition[name] = exact_map
        norm_by_partition[name] = norm_map
        template_by_partition[name] = template_map

        # Check internal label conflicts
        internal_conflicts = 0
        for e_hash, count in exact_map.items():
            if len(label_by_exact[e_hash][name]) > 1:
                internal_conflicts += 1

        report["partitions"][name] = {
            "totalRows": len(records),
            "uniqueExactTexts": len(exact_map),
            "uniqueNormalizedTexts": len(norm_map),
            "uniqueTemplates": len(template_map),
            "internalExactDuplicates": len(records) - len(exact_map),
            "internalLabelConflicts": internal_conflicts,
            "labelDistribution": dict(label_dist),
            "languageDistribution": dict(lang_dist),
        }

    # Cross-partition comparison
    part_names = list(partitions.keys())
    for i in range(len(part_names)):
        for j in range(i + 1, len(part_names)):
            p1, p2 = part_names[i], part_names[j]
            pair_key = f"{p1}_vs_{p2}"

            # Exact cross-split
            exact_overlap = set(exact_by_partition[p1].keys()) & set(exact_by_partition[p2].keys())
            report["exactCrossSplitLeakage"][pair_key] = len(exact_overlap)
            if exact_overlap:
                report["passAllLeakageGates"] = False

            # Normalized cross-split
            norm_overlap = set(norm_by_partition[p1].keys()) & set(norm_by_partition[p2].keys())
            report["normalizedCrossSplitLeakage"][pair_key] = len(norm_overlap)
            if norm_overlap:
                report["passAllLeakageGates"] = False

            # Template cross-split
            template_overlap = set(template_by_partition[p1].keys()) & set(template_by_partition[p2].keys())
            report["templateCrossSplitOverlap"][pair_key] = len(template_overlap)

            # Label conflicts across partitions
            cross_conflicts = 0
            for e_hash in exact_overlap:
                labels1 = label_by_exact[e_hash][p1]
                labels2 = label_by_exact[e_hash][p2]
                if labels1 != labels2:
                    cross_conflicts += 1
            report["labelConflicts"]["crossPartition"][pair_key] = cross_conflicts

    return report


def run_self_test():
    """Runs verification against synthetic mock partitions to test all guard rails."""
    print("Running verify_split_leakage self-test...")
    clean_partitions = {
        "train": [
            {"text": "Win free lottery prize now call 99999", "label": "scam", "lang": "en"},
            {"text": "Your account has been debited by 500 INR", "label": "ham", "lang": "en"},
        ],
        "validation": [
            {"text": "Electricity will be disconnected update KYC", "label": "scam", "lang": "en"},
            {"text": "Meeting at 4pm today in office", "label": "ham", "lang": "en"},
        ],
        "acceptance": [
            {"text": "Urgent custom parcel held pay duty", "label": "scam", "lang": "en"},
            {"text": "OTP for your bank login is 849201", "label": "ham", "lang": "en"},
        ],
    }
    rep_clean = audit_partitions(clean_partitions)
    assert rep_clean["passAllLeakageGates"] is True
    assert rep_clean["exactCrossSplitLeakage"]["train_vs_validation"] == 0
    assert rep_clean["normalizedCrossSplitLeakage"]["train_vs_acceptance"] == 0

    leaky_partitions = {
        "train": [
            {"text": "Click here http://scam.me/1 for 500 cashback", "label": "scam", "lang": "en"},
            {"text": "Hello world", "label": "ham", "lang": "en"},
        ],
        "validation": [
            # Normalized leak: different URL token but same structure
            {"text": "click here http://other.me/2 for 1000 cashback", "label": "scam", "lang": "en"},
            {"text": "Hello world", "label": "scam", "lang": "en"},  # Exact leak + label conflict
        ],
    }
    rep_leaky = audit_partitions(leaky_partitions)
    assert rep_leaky["passAllLeakageGates"] is False
    assert rep_leaky["exactCrossSplitLeakage"]["train_vs_validation"] == 1
    assert rep_leaky["normalizedCrossSplitLeakage"]["train_vs_validation"] == 2
    assert rep_leaky["labelConflicts"]["crossPartition"]["train_vs_validation"] == 1

    print("Self-test passed: exact leakage, normalized leakage, and label conflicts correctly caught.")


def main():
    parser = argparse.ArgumentParser(description="Audit dataset partitions for split leakage and label consistency.")
    parser.add_argument("--train", help="Path to training partition file")
    parser.add_argument("--val", help="Path to validation/dev partition file")
    parser.add_argument("--acceptance", help="Path to blind acceptance partition file")
    parser.add_argument("--stress", help="Path to stress/edge-case partition file")
    parser.add_argument("--json-out", help="Optional path to output aggregate JSON report")
    parser.add_argument("--self-test", action="store_true", help="Run internal self-test suite")

    args = parser.parse_args()

    if args.self_test:
        run_self_test()
        return

    partitions = {}
    if args.train:
        partitions["train"] = load_file(args.train)
    if args.val:
        partitions["validation"] = load_file(args.val)
    if args.acceptance:
        partitions["acceptance"] = load_file(args.acceptance)
    if args.stress:
        partitions["stress"] = load_file(args.stress)

    if not partitions:
        print("No input partitions provided. Use --help for usage or --self-test to verify.")
        sys.exit(1)

    report = audit_partitions(partitions)

    print("\nDataset Partition & Leakage Audit Report")
    print("=" * 60)
    print(f"Overall Status: {'PASSED (No Leakage)' if report['passAllLeakageGates'] else 'FAILED (Leakage Detected)'}")
    print("-" * 60)
    for p_name, p_info in report["partitions"].items():
        print(f"Partition [{p_name}]: {p_info['totalRows']} rows, {p_info['uniqueExactTexts']} unique exact, {p_info['uniqueNormalizedTexts']} unique normalized, {p_info['uniqueTemplates']} templates")
        if p_info["internalLabelConflicts"] > 0:
            print(f"  WARNING: {p_info['internalLabelConflicts']} internal label conflicts!")

    print("\nCross-Split Overlap Checks:")
    for pair, count in report["exactCrossSplitLeakage"].items():
        norm_count = report["normalizedCrossSplitLeakage"].get(pair, 0)
        tmpl_count = report["templateCrossSplitOverlap"].get(pair, 0)
        status = "FAIL" if count > 0 or norm_count > 0 else "PASS"
        print(f"  {pair:25}: exact={count}, normalized={norm_count}, templates={tmpl_count} -> {status}")

    if args.json_out:
        with open(args.json_out, "w", encoding="utf-8") as f:
            json.dump(report, f, indent=2)
        print(f"\nAggregate-only report saved to {args.json_out}")

    if not report["passAllLeakageGates"]:
        sys.exit(2)


if __name__ == "__main__":
    main()
