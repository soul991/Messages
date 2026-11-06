#!/usr/bin/env python3
"""router.py — Script and Language Router for Bengali, Hindi, and English SMS.
Detects:
- hi_deva (Hindi in Devanagari script)
- bn_beng (Bengali in Bengali script)
- en_latin (Indian English)
- hi_latn (Hinglish: Hindi in Latin script)
- bn_latn (Banglish: Bengali in Latin script)
"""
import re, sys
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')
from typing import Tuple, Dict

# High-frequency Romanized Indic stopwords/markers
HINGLISH_TOKENS = {
    "aapka", "aapke", "aapki", "karein", "kare", "karo", "hai", "hain", "nahi", "nahin",
    "paise", "rupaye", "khata", "sampark", "turant", "kijiye", "bheje", "dekh", "le",
    "ho", "gaya", "gayi", "raha", "rahi", "liye", "aur", "bhi", "par", "se", "ko",
    "sirf", "aaj", "kal", "abhi", "kripya", "badhai", "inam", "jeet", "jeetne", "dost"
}

BANGLISH_TOKENS = {
    "apnar", "apnake", "apni", "korun", "kore", "koro", "hobe", "hoy", "hoye", "shob",
    "kichhu", "kichu", "taka", "amader", "ei", "tar", "khub", "dekhe", "jani", "bhalo",
    "dharon", "dhairjo", "dharo", "thik", "ache", "achhe", "boro", "choto", "karon",
    "barta", "janun", "shuru", "sesh", "amar", "tomar", "tumi", "tader", "shomporko"
}

ENGLISH_COMMON = {
    "the", "is", "at", "which", "on", "and", "a", "an", "in", "to", "for", "with",
    "account", "credited", "debited", "balance", "dear", "customer", "please", "click",
    "update", "immediately", "contact", "official", "bank", "card", "service", "congratulations"
}

def detect_language_and_script(text: str) -> Tuple[str, str, float]:
    """
    Returns (language_bucket, script, confidence)
    Buckets:
    - 'hi_deva': Hindi / Devanagari script
    - 'bn_beng': Bengali script
    - 'hi_latn': Hinglish (Romanized Hindi)
    - 'bn_latn': Banglish (Romanized Bengali)
    - 'en_latin': English in Latin script
    - 'other': Unmatched / other script
    """
    if not isinstance(text, str) or not text.strip():
        return ("other", "empty", 0.0)

    # 1. Script checks via Unicode ranges
    deva_chars = len(re.findall(r"[\u0900-\u097F]", text))
    beng_chars = len(re.findall(r"[\u0980-\u09FF]", text))
    latin_chars = len(re.findall(r"[a-zA-Z]", text))
    
    total_alpha = deva_chars + beng_chars + latin_chars
    if total_alpha == 0:
        return ("other", "non_alpha", 0.0)

    if beng_chars > 0 and beng_chars >= deva_chars and beng_chars >= latin_chars:
        conf = beng_chars / (total_alpha or 1)
        return ("bn_beng", "bengali", min(1.0, conf))

    if deva_chars > 0 and deva_chars >= latin_chars:
        conf = deva_chars / (total_alpha or 1)
        return ("hi_deva", "devanagari", min(1.0, conf))

    # 2. Latin script disambiguation (Hinglish vs Banglish vs English)
    words = [w.lower() for w in re.findall(r"\b[a-zA-Z]+\b", text)]
    if not words:
        return ("en_latin", "latin", 0.5)

    hi_hits = sum(1 for w in words if w in HINGLISH_TOKENS)
    bn_hits = sum(1 for w in words if w in BANGLISH_TOKENS)
    en_hits = sum(1 for w in words if w in ENGLISH_COMMON)

    if bn_hits > hi_hits and bn_hits > 0:
        conf = min(1.0, 0.6 + 0.1 * bn_hits)
        return ("bn_latn", "latin", conf)
    elif hi_hits > bn_hits and hi_hits > 0:
        conf = min(1.0, 0.6 + 0.1 * hi_hits)
        return ("hi_latn", "latin", conf)
    else:
        conf = min(1.0, 0.7 + 0.05 * en_hits)
        return ("en_latin", "latin", conf)

def evaluate_router_accuracy(samples: Dict[str, list]) -> Dict[str, dict]:
    """Evaluates router accuracy on labeled benchmark samples."""
    results = {}
    for expected_bucket, text_list in samples.items():
        correct = 0
        total = len(text_list)
        for t in text_list:
            pred_bucket, script, conf = detect_language_and_script(t)
            if pred_bucket == expected_bucket:
                correct += 1
        acc = correct / total if total > 0 else 0.0
        results[expected_bucket] = {
            "n": total,
            "correct": correct,
            "accuracy": acc
        }
    return results

if __name__ == "__main__":
    test_cases = [
        ("আপনার অ্যাকাউন্ট ব্লক করা হয়েছে", "bn_beng"),
        ("आपका खाता ब्लॉक कर दिया गया है", "hi_deva"),
        ("Apnar bank account theke taka kete neya hoyeche", "bn_latn"),
        ("Aapka bank account block ho gaya hai turant kyc karein", "hi_latn"),
        ("Your bank account has been debited by 500 INR", "en_latin")
    ]
    for text, expected in test_cases:
        bucket, script, conf = detect_language_and_script(text)
        print(f"Text: '{text[:40]}' -> Bucket: {bucket} (Script: {script}, Conf: {conf:.2f}) [Expected: {expected}]")
