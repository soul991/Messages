#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
evaluate_acceptance.py — Evaluates candidate SMS protection models against the blind acceptance set.

MANDATORY REFUSAL GATE (Rule R3):
This script can ONLY be executed by the project owner. It will immediately refuse
execution and exit unless the environment variable OWNER_RUN=1 is explicitly set.
Automated agents must NEVER run this script or access acceptance labels.
"""

import argparse
import json
import math
import os
import re
import sys
from pathlib import Path
import numpy as np
import pandas as pd

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

# Refusal gate check
if os.environ.get("OWNER_RUN") != "1":
    sys.exit(
        "\n[GATE REFUSAL] Error: evaluate_acceptance.py is reserved exclusively for the PROJECT OWNER.\n"
        "Automated coding assistants must NEVER run this tool or inspect the blind acceptance set.\n"
        "To run as owner, set environment variable OWNER_RUN=1 in your shell:\n"
        "  $env:OWNER_RUN='1'; python evaluate_acceptance.py --candidate <model.json> --labels <to_label.xlsx>\n"
    )

def wilson_interval(k: int, n: int, confidence: float = 0.95) -> tuple:
    """Calculates Wilson score interval for binomial proportions."""
    if n == 0:
        return (0.0, 0.0)
    z = 1.95996  # 95% confidence z-score
    p = k / n
    denom = 1 + (z**2) / n
    center = (p + (z**2) / (2 * n)) / denom
    margin = (z * math.sqrt((p * (1 - p) / n) + (z**2) / (4 * n**2))) / denom
    lower = max(0.0, center - margin)
    upper = min(1.0, center + margin)
    return (round(lower, 4), round(upper, 4))

def detect_script(text: str) -> str:
    has_beng = bool(re.search(r"[\u0980-\u09FF]", text))
    has_deva = bool(re.search(r"[\u0900-\u097F]", text))
    if has_beng:
        return "bengali"
    if has_deva:
        return "devanagari"
    return "latin"

def compute_calibration(probs, labels, n_bins=10):
    """Calculates Brier Score and Expected Calibration Error (ECE)."""
    if len(probs) == 0:
        return (0.0, 0.0)
    probs = np.array(probs, dtype=float)
    labels = np.array(labels, dtype=float)
    
    # Brier score
    brier = float(np.mean((probs - labels)**2))
    
    # ECE
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

def load_candidate_model(model_path: str):
    """Loads n-gram Naive Bayes candidate or rule-based model."""
    path = Path(model_path)
    if not path.is_file():
        sys.exit(f"Candidate file not found: {model_path}")
        
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
        
    weights = data.get("weights", {})
    threshold = float(data.get("threshold", 0.0))
    ngram_min = int(data.get("ngramMin", 2))
    ngram_max = int(data.get("ngramMax", 5))
    
    def score_text(text: str) -> float:
        score = 0.0
        n_chars = len(text)
        for n in range(ngram_min, min(ngram_max + 1, n_chars + 1)):
            for i in range(n_chars - n + 1):
                gram = text[i:i + n]
                if gram in weights:
                    score += weights[gram]
        return score
        
    return score_text, threshold

def main():
    parser = argparse.ArgumentParser(description="Owner Acceptance Set Evaluator.")
    parser.add_argument("--candidate", required=True, help="Path to candidate model (JSON)")
    parser.add_argument("--labels", required=True, help="Path to owner-labeled spreadsheet (xlsx or csv)")
    parser.add_argument("--json-out", help="Optional path to output aggregate JSON report")
    args = parser.parse_args()

    scorer, threshold = load_candidate_model(args.candidate)

    # Load labels
    lbl_path = Path(args.labels)
    if lbl_path.suffix.lower() in (".xlsx", ".xls"):
        df = pd.read_excel(lbl_path)
    else:
        df = pd.read_csv(lbl_path)

    text_col = "message" if "message" in df.columns else df.columns[1]
    label_col = "label" if "label" in df.columns else "Label"

    # Filter out empty or unsure labels
    df = df.dropna(subset=[label_col])
    if "unsure" in df.columns:
        df = df[df["unsure"].isna() | (df["unsure"] == "") | (df["unsure"] == 0)]

    total_rows = len(df)
    print(f"\nEvaluating candidate across {total_rows} labeled acceptance messages...")

    # Scoring and metrics computation
    results_by_group = {}
    
    def process_subset(sub_df, name):
        if len(sub_df) == 0:
            return None
        n = len(sub_df)
        tp = fp = fn = tn = 0
        probs = []
        labels = []
        
        for _, row in sub_df.iterrows():
            text = str(row[text_col])
            raw_lbl = str(row[label_col]).strip().upper()
            is_scam = 1 if raw_lbl in ("1", "SCAM", "SCAM_FRAUD") else 0
            
            raw_score = scorer(text)
            prob = 1.0 / (1.0 + math.exp(-raw_score)) if -50 < raw_score < 50 else (1.0 if raw_score >= 50 else 0.0)
            pred = raw_score > threshold
            
            probs.append(prob)
            labels.append(is_scam)
            
            if is_scam and pred:
                tp += 1
            elif is_scam and not pred:
                fn += 1
            elif not is_scam and pred:
                fp += 1
            else:
                tn += 1

        scam_total = tp + fn
        ham_total = tn + fp
        
        recall = tp / scam_total if scam_total > 0 else 0.0
        fpr = fp / ham_total if ham_total > 0 else 0.0
        precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
        f1 = (2 * precision * recall) / (precision + recall) if (precision + recall) > 0 else 0.0
        
        rec_ci = wilson_interval(tp, scam_total)
        fpr_ci = wilson_interval(fp, ham_total)
        brier, ece = compute_calibration(probs, labels)
        
        return {
            "n": n,
            "scam_n": scam_total,
            "ham_n": ham_total,
            "tp": tp,
            "fp": fp,
            "fn": fn,
            "tn": tn,
            "scam_recall": round(recall, 4),
            "scam_recall_ci95": rec_ci,
            "ham_fpr": round(fpr, 4),
            "ham_fpr_ci95": fpr_ci,
            "precision": round(precision, 4),
            "f1": round(f1, 4),
            "brier_score": brier,
            "ece": ece,
            "inconclusive": n < 100
        }

    # All messages
    results_by_group["all"] = process_subset(df, "all")
    
    # By script
    df["script"] = df[text_col].apply(detect_script)
    for sc in ("latin", "devanagari", "bengali"):
        sub = df[df["script"] == sc]
        if len(sub) > 0:
            results_by_group[f"script_{sc}"] = process_subset(sub, sc)

    # Protected message check (OTP / bank transactions)
    prot_mask = df[text_col].str.contains(r"\b(?:otp|one time password|credited|debited|a/c|account no|avl bal)\b", case=False, regex=True)
    protected_df = df[prot_mask & (df[label_col].astype(str).str.upper().isin(["0", "NOT_SCAM", "HAM"]))]
    if len(protected_df) > 0:
        prot_res = process_subset(protected_df, "protected_traffic")
        prot_fpr = prot_res["ham_fpr"] if prot_res else 0.0
    else:
        prot_fpr = 0.0
        prot_res = {"n": 0, "ham_fpr": 0.0}

    # Print summary report
    print("\n" + "=" * 70)
    print("OWNER ACCEPTANCE EVALUATION REPORT (Stage 1 Gates)")
    print("=" * 70)
    
    all_res = results_by_group["all"]
    print(f"Overall Metrics (N = {all_res['n']}):")
    print(f"  Scam Recall      : {all_res['scam_recall']*100:.2f}% (Wilson 95%: [{all_res['scam_recall_ci95'][0]*100:.2f}%, {all_res['scam_recall_ci95'][1]*100:.2f}%])")
    print(f"  Ham False Pos Rate: {all_res['ham_fpr']*100:.2f}% (Wilson 95%: [{all_res['ham_fpr_ci95'][0]*100:.2f}%, {all_res['ham_fpr_ci95'][1]*100:.2f}%])")
    print(f"  Precision / F1   : {all_res['precision']*100:.2f}% / {all_res['f1']*100:.2f}%")
    print(f"  Calibration (Brier / ECE): {all_res['brier_score']} / {all_res['ece']}")
    print(f"  Protected Message FPR (Target 0.00%): {prot_fpr*100:.2f}% (N = {prot_res['n']})")

    print("\nPer-Script Subgroups:")
    print("-" * 70)
    for k, v in results_by_group.items():
        if k == "all" or not v:
            continue
        inc_tag = " [INCONCLUSIVE N<100]" if v["inconclusive"] else ""
        print(f"  {k:15}: N={v['n']:3} | Recall={v['scam_recall']*100:.1f}% (CI: {v['scam_recall_ci95']}) | FPR={v['ham_fpr']*100:.2f}% (CI: {v['ham_fpr_ci95']}){inc_tag}")

    print("=" * 70)

    # Check release gates
    gate_recall = all_res["scam_recall"] >= 0.80 and all_res["scam_recall_ci95"][0] >= 0.70
    gate_fpr = all_res["ham_fpr"] <= 0.005 and all_res["ham_fpr_ci95"][1] <= 0.010
    gate_prot = prot_fpr == 0.0
    gate_calib = all_res["brier_score"] <= 0.10 and all_res["ece"] <= 0.08

    print("Stage 1 Release Gates Summary:")
    print(f"  [1] Overall Scam Recall >= 80% (Wilson lower >= 70%): {'MET' if gate_recall else 'NOT MET'}")
    print(f"  [2] Overall Ham FPR <= 0.50% (Wilson upper <= 1.00%): {'MET' if gate_fpr else 'NOT MET'}")
    print(f"  [3] Protected Messages FPR exactly 0.00%:             {'MET' if gate_prot else 'NOT MET'}")
    print(f"  [4] Calibration Brier <= 0.10, ECE <= 0.08:          {'MET' if gate_calib else 'NOT MET'}")

    all_gates_passed = gate_recall and gate_fpr and gate_prot and gate_calib
    print("\nVERDICT:")
    if all_gates_passed:
        print("  ALL GATES MET! The owner may proceed to generate docs/ml/OWNER_APPROVAL.md.")
    else:
        print("  GATES NOT MET. The candidate cannot be approved for app deployment.")
    print("=" * 70)

    if args.json_out:
        out_payload = {
            "candidate": args.candidate,
            "metrics": results_by_group,
            "protected_message_fpr": prot_fpr,
            "gates_passed": all_gates_passed
        }
        with open(args.json_out, "w", encoding="utf-8") as f:
            json.dump(out_payload, f, indent=2)
        print(f"Report JSON saved to {args.json_out}")

if __name__ == "__main__":
    main()
