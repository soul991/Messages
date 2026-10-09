#!/usr/bin/env python3
"""Build a provenance-aware English/Hindi/Bengali n-gram candidate.

Reads the user-provided Datasets folder without modifying it. Messages are never
printed. Public validation/test splits are de-duplicated against training data;
thresholds are selected from validation only. This is an experiment and writes
to a separate model/report path, never to the active Android model.
"""
import argparse
import csv
import hashlib
import json
import math
import os
import random
import sys
from collections import Counter, defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)
import train_ngram as base  # noqa: E402


def key(text):
    return hashlib.sha256(" ".join(text.casefold().split()).encode("utf-8")).hexdigest()


def row(text, label, lang, source, group=""):
    return {"text": text.strip(), "label": label, "lang": lang,
            "src": source, "group": group, "idx": -1, "fragile": False}


def read_jsonl(path, split):
    out, bad = [], 0
    with open(path, encoding="utf-8") as f:
        for line in f:
            if not line.strip():
                continue
            try:
                item = json.loads(line)
                text = str(item.get("text", ""))
                language = str(item.get("language", "")).lower()
                label = str(item.get("is_scam", ""))
                if not text.strip() or label not in ("0", "1"):
                    bad += 1
                    continue
                lang = "hinglish" if language == "hinglish" else "hi" if language == "hindi" else "en"
                source = str(item.get("source_dataset", "unknown"))
                out.append(row(text, "scam" if label == "1" else "ham", lang,
                               "scamshield:" + source, str(item.get("split_group", ""))))
            except (json.JSONDecodeError, TypeError, ValueError):
                bad += 1
    return out, bad


def read_bengali(path):
    try:
        import pyarrow.parquet as pq
    except ImportError as e:
        raise SystemExit("Parquet input requires pyarrow (pip install pyarrow)") from e
    rows = []
    for item in pq.read_table(path).to_pylist():
        label = str(item.get("label", "")).lower()
        if label not in ("smish", "normal", "promo"):
            continue
        variety = str(item.get("source", "unknown"))
        lang = "en" if variety == "English" else "bn"
        rows.append(row(str(item["text"]), "scam" if label == "smish" else "ham",
                        lang, "bengali:" + variety, variety))
    return rows


def read_india_csv(path):
    rows = []
    with open(path, encoding="utf-8-sig", newline="") as f:
        for item in csv.DictReader(f):
            text = str(item.get("Msg", ""))
            label = str(item.get("Label", "")).lower()
            if not text.strip() or label not in ("spam", "ham"):
                continue
            # Dataset README and schema describe this as Indian SMS; no
            # language field is supplied, so this supplemental slice is only
            # included in the English/unknown pool, never Hindi evaluation.
            rows.append(row(text, "scam" if label == "spam" else "ham",
                            "en", "india_telecom_csv"))
    return rows


def read_bolewara(path):
    """CC-BY-4.0 source; no source split, so training-only."""
    if not os.path.isfile(path):
        return []
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    source_rows = data.get("rows", []) if isinstance(data, dict) else data
    rows = []
    for item in source_rows:
        text = str(item.get("text", ""))
        label = str(item.get("label", ""))
        if not text.strip() or label not in ("0", "1"):
            continue
        rows.append(row(text, "scam" if label == "1" else "ham", "en", "bolewara_hinglish_ccby"))
    return rows


def unique(rows):
    out, seen = [], set()
    for r in rows:
        h = key(r["text"])
        if h not in seen:
            out.append(r)
            seen.add(h)
    return out


def grams(text):
    return base.grams(text)


def fit(rows, equalize_languages=False):
    counts = {"scam": Counter(), "ham": Counter()}
    df = Counter()
    language_sizes = Counter(r["lang"] for r in rows)
    target = len(rows) / max(1, len(language_sizes))
    for r in rows:
        g = grams(r["text"])
        multiplier = target / language_sizes[r["lang"]] if equalize_languages else 1.0
        gram_counts = Counter(g)
        counts[r["label"]].update({gram: count * multiplier for gram, count in gram_counts.items()})
        df.update(set(g))
    vocab = {g for g, n in df.items() if n >= base.MIN_DF}
    totals = {c: sum(v for g, v in counts[c].items() if g in vocab) for c in counts}
    weights = {}
    for g in vocab:
        ps = (counts["scam"][g] + base.ALPHA) / (totals["scam"] + base.ALPHA * len(vocab))
        ph = (counts["ham"][g] + base.ALPHA) / (totals["ham"] + base.ALPHA * len(vocab))
        weights[g] = math.log(ps) - math.log(ph)
    if len(weights) > base.MAX_FEATURES:
        weights = dict(sorted(weights.items(), key=lambda it: -abs(it[1]))[:base.MAX_FEATURES])
    return weights


def fit_logistic(rows, equalize_languages=False, epochs=5):
    """Sparse SGD logistic model over the same normalized character grams."""
    document_frequency = Counter()
    row_grams = []
    for r in rows:
        counted = Counter(grams(r["text"]))
        row_grams.append(counted)
        document_frequency.update(counted.keys())
    vocab = [g for g, n in document_frequency.items() if n >= base.MIN_DF]
    if len(vocab) > base.MAX_FEATURES:
        vocab = sorted(vocab, key=lambda g: (-document_frequency[g], g))[:base.MAX_FEATURES]
    index = {g: i for i, g in enumerate(vocab)}
    vectors = []
    for r, counted in zip(rows, row_grams):
        total = max(1, sum(counted.values()))
        vectors.append(([(index[g], n / total) for g, n in counted.items() if g in index],
                         1 if r["label"] == "scam" else -1, r["lang"]))
    sizes = Counter(lang for _, _, lang in vectors)
    target = len(vectors) / max(1, len(sizes))
    weights = [0.0] * len(vocab)
    bias = 0.0
    order = list(range(len(vectors)))
    rng = random.Random(20261002)
    regularization = 0.0001
    for epoch in range(epochs):
        rng.shuffle(order)
        rate = 0.5 / math.sqrt(epoch + 1)
        for at in order:
            features, label, lang = vectors[at]
            multiplier = target / sizes[lang] if equalize_languages else 1.0
            margin = bias + sum(weights[i] * x for i, x in features)
            z = max(-30.0, min(30.0, label * margin))
            gradient = multiplier / (1.0 + math.exp(z))
            for i, x in features:
                weights[i] += rate * (label * gradient * x - regularization * weights[i])
            bias += rate * (label * gradient - regularization * bias)
    # Kotlin NGramScorer returns the mean over all grams. These weights were
    # learned against that same normalized feature representation. Intercept
    # is absorbed into the validation-selected threshold.
    return {g: weights[i] for i, g in enumerate(vocab) if weights[i] != 0.0}


def score(weights, text, min_words):
    if len(text.split()) < min_words:
        return 0.0
    gs = grams(text)
    return sum(weights.get(g, 0.0) for g in gs) / len(gs) if gs else 0.0


def script_route(text):
    """Deterministic script router: Bengali, Devanagari, then Latin fallback."""
    bn = sum(1 for ch in text if "\u0980" <= ch <= "\u09ff")
    hi = sum(1 for ch in text if "\u0900" <= ch <= "\u097f")
    if bn and bn >= hi:
        return "bengali"
    if hi:
        return "hindi"
    return "latin"


def threshold_for_language(weights, validation, min_words, target_fp=0.01):
    buckets = defaultdict(list)
    for r in validation:
        if r["label"] == "ham":
            buckets[r["lang"]].append(score(weights, r["text"], min_words))
    limits = []
    for vals in buckets.values():
        vals.sort(reverse=True)
        allowed = int(len(vals) * target_fp)
        limits.append(max(0.0, vals[min(allowed, len(vals) - 1)] + 1e-4))
    return max(limits, default=0.0)


def metrics(weights, threshold, rows, min_words):
    buckets = defaultdict(lambda: Counter(gold=0, tp=0, fp=0, fn=0, tn=0))
    sources = defaultdict(lambda: Counter(gold=0, tp=0, fp=0, fn=0, tn=0))
    for r in rows:
        pred = score(weights, r["text"], min_words) > threshold
        for bucket in (buckets[r["lang"]], sources[r["src"]]):
            bucket["gold"] += 1
            bucket["tp" if r["label"] == "scam" and pred else
                   "fn" if r["label"] == "scam" else
                   "fp" if pred else "tn"] += 1
    def finish(bucket):
        p = bucket["tp"] / max(1, bucket["tp"] + bucket["fp"])
        rec = bucket["tp"] / max(1, bucket["tp"] + bucket["fn"])
        return {**bucket, "precision": round(p, 4), "recall": round(rec, 4),
                "f1": round(2*p*rec/max(1e-12,p+rec), 4),
                "fpr": round(bucket["fp"] / max(1, bucket["fp"] + bucket["tn"]), 4)}
    return {"byLanguage": {k: finish(v) for k, v in sorted(buckets.items())},
            "bySource": {k: finish(v) for k, v in sorted(sources.items())}}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data-root", default=os.path.abspath(os.path.join(ROOT, "..", "..", "Datasets")))
    parser.add_argument("--output", default=os.path.join(ROOT, "protection-engine", "training", "ngram_multilingual_candidate.json"))
    parser.add_argument("--report", default=os.path.join(ROOT, "docs", "ml", "multilingual-model-report.json"))
    args = parser.parse_args()
    d = args.data_root
    ss = os.path.join(d, "scamshield-dataset")
    train, train_bad = read_jsonl(os.path.join(ss, "train.jsonl"), "train")
    val, val_bad = read_jsonl(os.path.join(ss, "val.jsonl"), "val")
    test, test_bad = read_jsonl(os.path.join(ss, "test.jsonl"), "test")
    bn = os.path.join(d, "bengali-sms-smishing-dataset")
    bn_train = read_bengali(os.path.join(bn, "train-00000-of-00001.parquet"))
    bn_val = read_bengali(os.path.join(bn, "validation-00000-of-00001.parquet"))
    bn_test = read_bengali(os.path.join(bn, "test-00000-of-00001.parquet"))
    csv_path = os.path.join(d, "india-spam-sms-classification-main", "dataset", "spam_ham_india.csv")
    india = read_india_csv(csv_path)
    bolewara_path = os.path.join(d, "bolewara-hinglish-scam-text-dataset", "data.json")
    bolewara = read_bolewara(bolewara_path)

    validation = unique(val + bn_val)
    validation_hashes = {key(r["text"]) for r in validation}
    test_all_before_split_clean = unique(test + bn_test)
    test_all = [r for r in test_all_before_split_clean if key(r["text"]) not in validation_hashes]
    test_rows_removed_for_validation_overlap = len(test_all_before_split_clean) - len(test_all)
    held_hashes = {key(r["text"]) for r in validation + test_all}
    training_external = unique([r for r in train + bn_train + india if key(r["text"]) not in held_hashes])
    external_hashes = {key(r["text"]) for r in training_external}
    bolewara_eligible = unique([r for r in bolewara
                                if key(r["text"]) not in held_hashes
                                and key(r["text"]) not in external_hashes])
    training_external.extend(bolewara_eligible)
    # Preserve the app's original training examples but keep its authored
    # holdout strictly for comparison only.
    app_train, app_held, _, _ = base.load()
    app_hashes = {key(r["text"]) for r in validation + test_all}
    app_train = [r for r in app_train if key(r["text"]) not in app_hashes]
    def in_scope(r):
        lang = str(r.get("lang", "en")).lower()
        if lang in ("hi", "hinglish"):
            r["lang"] = lang
            return True
        if lang in ("bn", "bengali"):
            r["lang"] = "bn"
            return True
        return lang in ("en", "english")
    app_train = [r for r in app_train if in_scope(r)]
    training = unique(app_train + training_external)

    # Keep native Hindi and romanized Hinglish as separate buckets so the
    # reported Hindi figure cannot be carried by a different script variety.

    app_model = json.load(open(os.path.join(ROOT, "protection-engine", "src", "main", "resources", "ngram_model.json"), encoding="utf-8"))
    baseline = {g: float(v) for g, v in app_model["weights"].items()}
    baseline_calibrated_threshold = threshold_for_language(
        baseline, validation, int(app_model["minWords"]))
    choices, sweep = [], []
    # Preserve a controllable contribution from the app's pre-existing model.
    # Select blend strength and minimum message length only from validation.
    selected_family = None
    for family in ("naive_bayes", "logistic_sgd"):
        for equalize_languages in (False, True):
            fitted_weights = (fit(training, equalize_languages=equalize_languages)
                              if family == "naive_bayes"
                              else fit_logistic(training, equalize_languages=equalize_languages))
            for old_weight in (0.0, 0.25, 0.5, 1.0, 2.0):
                candidate_weights = dict(fitted_weights)
                for gram, value in baseline.items():
                    candidate_weights[gram] = candidate_weights.get(gram, 0.0) + old_weight * value
                candidate_weights = {gram: value for gram, value in candidate_weights.items() if value != 0.0}
                for min_words in (1, 2, 3, 4, 6):
                    candidate_threshold = threshold_for_language(candidate_weights, validation, min_words)
                    m = metrics(candidate_weights, candidate_threshold, validation, min_words)
                    vals = list(m["byLanguage"].values())
                    macro_f1 = sum(x["f1"] for x in vals) / max(1, len(vals))
                    sweep.append({"modelFamily": family, "languageBalancedTraining": equalize_languages,
                                  "appModelWeight": old_weight, "minWords": min_words,
                                  "threshold": round(candidate_threshold, 4), "macroValidationF1": round(macro_f1, 4),
                                  "appAuthoredHoldoutDiagnostic": metrics(candidate_weights, candidate_threshold, app_held, min_words)["byLanguage"].get("en", {})})
                    choices.append((macro_f1, family == "naive_bayes", not equalize_languages,
                                    -old_weight, -min_words, candidate_weights, min_words,
                                    candidate_threshold, m, old_weight, equalize_languages, family))
    (_, _, _, _, _, weights, min_words, threshold, val_metrics,
     selected_old_weight, selected_equalize, selected_family) = max(
        choices, key=lambda x: x[:5])

    # Also train script-routed experts. Native Bengali and Devanagari Hindi
    # cannot be reliably separated by one shared likelihood ratio: their
    # character distributions are almost disjoint. Latin fallback includes
    # English, Hinglish, and Banglish rows in the source training mix.
    expert_payloads, expert_report = {}, {}
    default_min_df = base.MIN_DF
    for route in ("bengali", "hindi", "latin"):
        expert_train = [r for r in training if script_route(r["text"]) == route]
        expert_val = [{**r, "lang": route} for r in validation if script_route(r["text"]) == route]
        expert_test = [{**r, "lang": route} for r in test_all if script_route(r["text"]) == route]
        if not expert_train or not expert_val:
            continue
        expert_choices = []
        for expert_family in ("naive_bayes", "logistic_sgd"):
            for expert_min_df in (1, 2, 3, 5):
                base.MIN_DF = expert_min_df
                expert_weights = (fit(expert_train) if expert_family == "naive_bayes"
                                  else fit_logistic(expert_train))
                for expert_min_words in (2, 3, 4, 6):
                    expert_threshold = threshold_for_language(expert_weights, expert_val, expert_min_words)
                    expert_val_metrics = metrics(expert_weights, expert_threshold, expert_val, expert_min_words)
                    ev = expert_val_metrics["byLanguage"].get(route, {})
                    expert_choices.append((ev.get("f1", 0.0), expert_family == "naive_bayes",
                                           expert_min_df, expert_min_words, expert_family,
                                           expert_threshold, expert_weights, expert_val_metrics))
        (_, _, expert_min_df, expert_min_words, expert_family,
         expert_threshold, expert_weights, expert_val_metrics) = max(expert_choices, key=lambda x: x[:4])
        base.MIN_DF = default_min_df
        expert_payloads[route] = {"threshold": round(expert_threshold, 4),
                                  "minWords": expert_min_words,
                                  "minDocumentFrequency": expert_min_df,
                                  "modelFamily": expert_family,
                                  "weights": {g: round(v, 3) for g, v in sorted(expert_weights.items())}}
        expert_report[route] = {
            "trainingRows": len(expert_train), "validationRows": len(expert_val),
            "testRows": len(expert_test), "featureCount": len(expert_weights),
            "modelFamily": expert_family, "minDocumentFrequency": expert_min_df, "minWords": expert_min_words,
            "threshold": round(expert_threshold, 4),
            "validation": expert_val_metrics,
            "test": metrics(expert_weights, expert_threshold, expert_test, expert_min_words),
            "existingAppAuthoredHoldout": metrics(expert_weights, expert_threshold,
                [{**r, "lang": route} for r in app_held if script_route(r["text"]) == route], expert_min_words)
        }
    expert_path = os.path.join(ROOT, "protection-engine", "training", "ngram_language_experts_candidate.json")
    os.makedirs(os.path.dirname(expert_path), exist_ok=True)
    with open(expert_path, "w", encoding="utf-8") as f:
        json.dump({"version": 1, "routing": "Bengali Unicode majority; Devanagari; Latin fallback", "models": expert_payloads},
                  f, ensure_ascii=False, separators=(",", ":")); f.write("\n")
    payload = {"version": 1, "ngramMin": base.NGRAM_MIN, "ngramMax": base.NGRAM_MAX,
               "minWords": min_words, "threshold": round(threshold, 4),
               "weights": {g: round(v, 3) for g, v in sorted(weights.items())}}
    os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)
    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, separators=(",", ":")); f.write("\n")

    report = {
        "dataFiles": os.path.basename(d), "malformedScamShieldRowsSkipped": {"train": train_bad, "validation": val_bad, "test": test_bad},
        "rows": {"scamshield_train_valid": len(train), "scamshield_validation_valid": len(val), "scamshield_test_valid": len(test),
                 "bengali_train": len(bn_train), "bengali_validation": len(bn_val), "bengali_test": len(bn_test),
                 "india_csv_rows": len(india), "bolewara_source_rows": len(bolewara),
                 "bolewara_unique_training_rows": len(bolewara_eligible),
                 "training_after_dedup_and_holdout_exclusion": len(training),
                 "validation_after_dedup": len(validation), "test_after_dedup": len(test_all),
                 "test_rows_removed_for_validation_duplicate": test_rows_removed_for_validation_overlap},
        "thresholdPolicy": "validation only; <=1% ham false positives per language; global threshold is max language constraint",
        "languageGrouping": "Bengali dataset English variety is evaluated as English; Bengali script, Banglish, and CodeMix as Bengali; Devanagari Hindi and romanized Hinglish are reported separately",
        "modelFamily": selected_family, "selectedMinWords": min_words, "appModelWeight": selected_old_weight,
        "languageBalancedTraining": selected_equalize,
        "threshold": round(threshold, 4), "featureCount": len(weights), "candidateBytes": os.path.getsize(args.output),
        "validationSweep": sorted(sweep, key=lambda x: -x["macroValidationF1"]),
        "validationCandidate": val_metrics,
        "scriptRoutedExpertCandidate": expert_report,
        "scriptRoutedExpertBundlePath": os.path.abspath(expert_path),
        "scriptRoutedExpertBundleBytes": os.path.getsize(expert_path),
        "testCandidate": metrics(weights, threshold, test_all, min_words),
        "testCurrentAppModel": metrics(baseline, float(app_model["threshold"]), test_all, int(app_model["minWords"])),
        "currentModelThresholdCalibratedOnValidation": round(baseline_calibrated_threshold, 4),
        "testCurrentAppModelValidationCalibrated": metrics(baseline, baseline_calibrated_threshold, test_all, int(app_model["minWords"])),
        "existingAppAuthoredHoldoutCurrentModel": metrics(baseline, float(app_model["threshold"]), app_held, int(app_model["minWords"])),
        "existingAppAuthoredHoldoutCurrentModelValidationCalibrated": metrics(baseline, baseline_calibrated_threshold, app_held, int(app_model["minWords"])),
        "existingAppAuthoredHoldout": metrics(weights, threshold, app_held, min_words),
        "candidateModelPath": os.path.abspath(args.output),
        "caveats": ["ScamShield published test rows have been inspected in prior experimentation; test metrics are not pristine blind evidence.",
                    "ScamShield includes synthetic training data and source families whose collection methods differ.",
                    "India CSV is broad spam/ham and heavily overlaps ScamShield; exact overlaps are deduplicated.",
                    "Hindi evaluation is small and does not establish robust field accuracy.",
                    "This candidate is not integrated into the Android app."]
    }
    os.makedirs(os.path.dirname(os.path.abspath(args.report)), exist_ok=True)
    with open(args.report, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2); f.write("\n")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
