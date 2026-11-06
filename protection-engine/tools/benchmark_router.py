#!/usr/bin/env python3
"""benchmark_router.py — Benchmarks router accuracy on 50 verified samples per bucket.
Buckets:
1. hi_deva: Hindi in Devanagari script
2. bn_beng: Bengali in Bengali script
3. en_latin: English in Latin script
4. hi_latn: Hinglish (Hindi in Latin script)
5. bn_latn: Banglish (Bengali in Latin script)
"""
import json, os, sys
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')
import pandas as pd
from router import detect_language_and_script, evaluate_router_accuracy

def load_verified_samples():
    samples = {
        "hi_deva": [],
        "bn_beng": [],
        "en_latin": [],
        "hi_latn": [],
        "bn_latn": []
    }
    
    # 1. scam-ham-india-14-languages
    p_14 = "D:/Istak/Messages/Datasets/external/scam-ham-india-14-languages/scam_ham_india_14_languages.csv"
    if os.path.exists(p_14):
        df = pd.read_csv(p_14)
        hi = df[df["language"] == "Hindi"]["text"].dropna().tolist()
        bn = df[df["language"] == "Bengali"]["text"].dropna().tolist()
        en = df[df["language"] == "English"]["text"].dropna().tolist()
        hing = df[df["language"] == "Hinglish"]["text"].dropna().tolist()
        
        samples["hi_deva"].extend(hi[:50])
        samples["bn_beng"].extend(bn[:50])
        samples["en_latin"].extend(en[:50])
        samples["hi_latn"].extend(hing[:50])

    # 2. Banglish samples from synthetic_audited and bengali-sms-smishing
    p_syn = "D:/Istak/Messages/Datasets/external/indian-scam-sms-synthetic-audited/data/synthetic_audited.csv"
    if os.path.exists(p_syn):
        df_syn = pd.read_csv(p_syn)
        bn_en = df_syn[df_syn["language"] == "bn_en"]["text"].dropna().tolist()
        samples["bn_latn"].extend(bn_en[:50])
        
    p_bg = "D:/Istak/Messages/Datasets/bengali-sms-smishing-dataset/data/validation-00000-of-00001.parquet"
    if len(samples["bn_latn"]) < 50 and os.path.exists(p_bg):
        df_bg = pd.read_parquet(p_bg)
        banglish = df_bg[df_bg["source"] == "Banglish"]["text"].dropna().tolist()
        needed = 50 - len(samples["bn_latn"])
        samples["bn_latn"].extend(banglish[:needed])

    # Truncate each to exactly 50
    for k in samples:
        samples[k] = samples[k][:50]
        
    return samples

def main():
    samples = load_verified_samples()
    print("Evaluating router across 5 buckets (50 samples each)...")
    for k, v in samples.items():
        print(f"Bucket {k}: {len(v)} samples")
        
    metrics = evaluate_router_accuracy(samples)
    
    total_samples = sum(m["n"] for m in metrics.values())
    total_correct = sum(m["correct"] for m in metrics.values())
    overall_accuracy = total_correct / total_samples if total_samples > 0 else 0.0
    
    report = {
        "total_samples": total_samples,
        "total_correct": total_correct,
        "overall_accuracy": overall_accuracy,
        "per_bucket": metrics
    }
    
    print("\nRouter Benchmark Results:")
    print("=" * 50)
    for bucket, m in metrics.items():
        print(f"  {bucket:10}: {m['correct']}/{m['n']} ({m['accuracy']*100:.1f}%)")
    print("-" * 50)
    print(f"Overall Accuracy: {overall_accuracy*100:.1f}% ({total_correct}/{total_samples})")
    
    os.makedirs("docs/ml", exist_ok=True)
    with open("docs/ml/router_benchmark.json", "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)
    print("\nWrote docs/ml/router_benchmark.json")

if __name__ == "__main__":
    main()
