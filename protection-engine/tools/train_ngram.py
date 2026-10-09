#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Trains Messages' local character n-gram classifier and exports it as JSON.

Multinomial naive Bayes over character n-grams, stdlib only. sklearn is not
installed and this does not need it: NB on counted features is a dictionary of
integers and two logarithms, and writing the ~60 lines here means the training
maths and the Kotlin scoring maths sit in one repository and can be diffed
against each other. See `NGramScorer` in `src/main/kotlin/.../AiScorer.kt`.

The training corpus contains 801 synthetic messages and the app's 513 authored
regression examples. A deterministic split holds back 43 pattern-fragile
messages plus every fifth remaining regression example. This checks whether
the model can recover some messages made fragile by removing one rule, but it
is not an independent real-world evaluation. Training ham alone selects the
threshold; the held-back examples do not tune it. The generated report records
the measured results and the limitations.

Run from the repository root:  python3 protection-engine/tools/train_ngram.py
"""

import json
import math
import os
import sys
from collections import Counter, defaultdict

# Windows consoles default to cp1252; this tool prints native-script n-grams.
sys.stdout.reconfigure(encoding="utf-8")

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))

CORPUS = os.path.join(ROOT, "protection-engine", "src", "test", "resources",
                      "corpus.json")
EXPANDED = os.path.join(ROOT, "protection-engine", "training",
                        "synthetic_messages.json")
FRAGILE = os.path.join(ROOT, "protection-engine", "training",
                       "held_out_fragile_indices.json")
OUT_MODEL = os.path.join(ROOT, "protection-engine", "src", "main", "resources",
                         "ngram_model.json")
OUT_REPORT = os.path.join(ROOT, "docs", "ml", "ngram-training-report.txt")

NGRAM_MIN, NGRAM_MAX = 2, 5
MIN_DF = 3            # a gram seen in fewer documents than this is memorisation
MAX_FEATURES = 20000  # size gate; see the size section of the report
ALPHA = 1.0           # Laplace smoothing
HOLDOUT_EVERY = 5     # every 5th corpus message, plus all 43 fragile ones
MAX_TARGET_FP = 0.01  # threshold is picked to hold training ham FP at or under

# Below this many words the model abstains and returns 0.0, which makes it the
# identity function for short messages. Same principle as Layer 2's
# MIN_OBSERVATIONS: where there is not enough evidence to reason from, the
# honest output is no output, not a confident guess off two fragments. A scam
# has to make a demand and give a channel, and it cannot do both in five words.
# Measured, not assumed - without this the model flagged the corpus's genuine
# "Sorry wrong number." on the strength of three matched grams.
MIN_WORDS = 6

# The engine treats these corpus labels as "should not be filtered".
HAM_LABELS = {"genuine", "protected", "promo"}


# --------------------------------------------------------------------------
# Features. `char_wb`: n-grams are extracted per whitespace token with the
# token padded by a space at each end, so a gram never straddles two words and
# word-initial / word-final position is itself a feature. Lowercased, because
# Layer 5 must not learn that SHOUTING is scam - `format-caps` already scores
# that deterministically and doubling it would double-count one signal.
# --------------------------------------------------------------------------

def grams(text):
    out = []
    for token in text.lower().split():
        padded = " %s " % token
        for n in range(NGRAM_MIN, NGRAM_MAX + 1):
            if len(padded) < n:
                continue
            for i in range(len(padded) - n + 1):
                out.append(padded[i:i + n])
    return out


# --------------------------------------------------------------------------
# Data
# --------------------------------------------------------------------------

def load():
    corpus = json.load(open(CORPUS, encoding="utf-8"))["entries"]
    expanded = json.load(open(EXPANDED, encoding="utf-8"))["entries"]
    fragile = set(json.load(open(FRAGILE, encoding="utf-8")))

    train, held = [], []
    for i, e in enumerate(corpus):
        label = "scam" if e["label"] == "scam" else "ham"
        row = {"text": e["text"], "label": label, "lang": "en",
               "src": "corpus", "idx": i, "fragile": i in fragile}
        # The 43 go to held-out unconditionally. The rest are split on index
        # parity, which is deterministic and needs no seed.
        if i in fragile or i % HOLDOUT_EVERY == 0:
            held.append(row)
        else:
            train.append(row)

    for e in expanded:
        train.append({"text": e["text"], "label": e["label"], "lang": e["lang"],
                      "src": "synthetic", "idx": -1, "fragile": False})
    return train, held, corpus, fragile


# --------------------------------------------------------------------------
# Train. Multinomial NB reduced to one number per gram: the log-likelihood
# ratio log P(g|scam) - log P(g|ham). Scoring is then a sum of lookups, which
# is what makes the Kotlin side ~20 lines with no maths library.
# --------------------------------------------------------------------------

def train_model(rows):
    counts = {"scam": Counter(), "ham": Counter()}
    df = Counter()
    for r in rows:
        g = grams(r["text"])
        counts[r["label"]].update(g)
        df.update(set(g))

    vocab = {g for g, n in df.items() if n >= MIN_DF}
    total = {c: sum(v for g, v in counts[c].items() if g in vocab) for c in counts}

    llr = {}
    for g in vocab:
        p_scam = (counts["scam"][g] + ALPHA) / (total["scam"] + ALPHA * len(vocab))
        p_ham = (counts["ham"][g] + ALPHA) / (total["ham"] + ALPHA * len(vocab))
        llr[g] = math.log(p_scam) - math.log(p_ham)

    # Size gate. Keep the most discriminative grams; a gram whose ratio is near
    # zero contributes nothing but bytes. Ranked by |llr| among grams that
    # already cleared MIN_DF, so this prunes weak features, not rare ones.
    if len(llr) > MAX_FEATURES:
        keep = sorted(llr, key=lambda g: -abs(llr[g]))[:MAX_FEATURES]
        llr = {g: llr[g] for g in keep}
    return llr


def score(llr, text):
    """Mean log-likelihood ratio per known gram. Mirrors NGramScorer.score."""
    if len(text.split()) < MIN_WORDS:
        return 0.0
    g = grams(text)
    if not g:
        return 0.0
    hit = [llr[x] for x in g if x in llr]
    if not hit:
        return 0.0
    # Divided by the *total* gram count, not the hit count: a message made
    # mostly of grams the model has never seen should score near zero rather
    # than being decided by its two familiar fragments.
    return sum(hit) / len(g)


def pick_threshold(llr, rows):
    """Smallest threshold holding training-ham false positives at <= 1%."""
    ham = sorted((score(llr, r["text"]) for r in rows if r["label"] == "ham"),
                 reverse=True)
    if not ham:
        return 0.0
    k = int(len(ham) * MAX_TARGET_FP)
    # Floored at zero, and compared with strict `>`, for two reasons that are
    # really one. The score is a log-likelihood ratio: below zero the message
    # looks more like ham than scam, so a negative threshold would mean
    # "escalate things the model believes are innocent". And the abstain path
    # returns exactly 0.0 — with a negative threshold that abstention would
    # read as a positive, which is how "Sorry wrong number." survived the
    # MIN_WORDS floor and was still counted as a false positive.
    return max(round(ham[k] + 1e-4, 4), 0.0)


# --------------------------------------------------------------------------
# Report
# --------------------------------------------------------------------------

def main():
    train, held, corpus, fragile = load()
    llr = train_model(train)
    threshold = pick_threshold(llr, train)

    out = []
    def p(s=""):
        out.append(s)
        print(s)

    p("Layer 5 - character n-gram model")
    p("=" * 76)
    p()
    p("Training set   : %d messages (%d corpus, %d synthetic)"
      % (len(train), sum(1 for r in train if r["src"] == "corpus"),
         sum(1 for r in train if r["src"] == "synthetic")))
    p("Held out       : %d corpus messages, of which %d are the fragility set"
      % (len(held), sum(1 for r in held if r["fragile"])))
    p("Features       : char_wb %d-%d grams, min_df=%d -> %d kept (cap %d)"
      % (NGRAM_MIN, NGRAM_MAX, MIN_DF, len(llr), MAX_FEATURES))
    p("Threshold      : %.4f  (chosen on TRAINING ham only, <=%.0f%% FP)"
      % (threshold, MAX_TARGET_FP * 100))
    p()

    # ---- 1. the measurement Layer 5 exists for
    p("1. The fragility set - the only non-circular number here")
    p("-" * 76)
    p("   43 corpus messages fall from a filtered folder into the Inbox when one")
    p("   matched pattern is removed. None of them were in training. Does a model")
    p("   that has never read them still call them scam?")
    p()
    frag = [r for r in held if r["fragile"]]
    frag_hit = sum(1 for r in frag if score(llr, r["text"]) > threshold)
    p("   recovered by the model : %d / %d  (%.1f%%)"
      % (frag_hit, len(frag), frag_hit * 100.0 / len(frag)))
    p()

    # ---- 2. false positives, which decide whether it can ship at all
    p("2. False positives on held-out ham")
    p("-" * 76)
    p("   Layer 5 can only move a message INTO Review, so every false positive")
    p("   here is a real message a user has to go and dig out. This number, not")
    p("   the one above, is what decides whether the flag may ever be flipped.")
    p()
    ham = [r for r in held if r["label"] == "ham"]
    scam = [r for r in held if r["label"] == "scam"]
    fp = [r for r in ham if score(llr, r["text"]) > threshold]
    tp = sum(1 for r in scam if score(llr, r["text"]) > threshold)
    p("   held-out ham           : %d" % len(ham))
    p("   flagged as scam        : %d  (%.1f%% false positive rate)"
      % (len(fp), len(fp) * 100.0 / max(1, len(ham))))
    p("   held-out scam          : %d, of which flagged %d (%.1f%% recall)"
      % (len(scam), tp, tp * 100.0 / max(1, len(scam))))
    p()
    if fp:
        p("   Every false positive, in full - these are the ones to argue with:")
        for r in sorted(fp, key=lambda r: -score(llr, r["text"])):
            p("   %+.4f  [%s] %s" % (score(llr, r["text"]),
                                     corpus[r["idx"]]["label"],
                                     r["text"].replace("\n", " ")[:88]))
        p()

    # ---- 3. size, latency, memory - gates, not footnotes
    p("3. Size")
    p("-" * 76)
    payload = {
        "version": 1,
        "ngramMin": NGRAM_MIN,
        "ngramMax": NGRAM_MAX,
        "minWords": MIN_WORDS,
        "threshold": threshold,
        "weights": {g: round(v, 3) for g, v in sorted(llr.items())},
    }
    os.makedirs(os.path.dirname(OUT_MODEL), exist_ok=True)
    with open(OUT_MODEL, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")
    size = os.path.getsize(OUT_MODEL)

    # Cross-language agreement fixture. Scored with the *rounded* weights that
    # were just written, not the full-precision ones in memory, so the Kotlin
    # side reading the same file must reproduce these numbers exactly rather
    # than approximately. `Layer5Test.the_kotlin_scorer_agrees_with_the_python
    # _trainer` fails if the two tokenizers or the two summations ever drift.
    rounded = payload["weights"]
    sample = [r["text"] for r in held[:40]] + [
        "Sorry wrong number.",                      # the MIN_WORDS abstain path
        "घर बैठे रोज 2000 रुपये कमाइए",              # non-Latin, no ASCII grams
        "hi",                                       # shorter than one n-gram
        # Joined by non-breaking spaces. Python str.split() splits on them;
        # Java Regex("\s+") does not, which is why NGramScorer tokenizes on
        # Char.isWhitespace. If that ever changes, this row diverges first.
        "claim your refund now at this link",
    ]
    with open(os.path.join(ROOT, "protection-engine", "training",
                           "score_fixture.json"),
              "w", encoding="utf-8") as f:
        json.dump([{"text": t, "score": score(rounded, t)} for t in sample],
                  f, ensure_ascii=False, indent=1)
    p("   %s" % os.path.relpath(OUT_MODEL, ROOT).replace("\\", "/"))
    p("   %d grams, %d bytes (%.2f MiB). Budget in the brief: 2 MiB. %s"
      % (len(llr), size, size / 1048576.0,
         "PASS" if size <= 2 * 1048576 else "FAIL"))
    p("   Latency and per-classification allocation are measured on the JVM by")
    p("   `layer5-verify`, not here - Python timings say nothing about the")
    p("   phone. No Android-device latency is claimed by this trainer.")
    p()

    # ---- 4. what this cannot tell you
    p("4. What these numbers do not say")
    p("-" * 76)
    p("   * Nothing about Indic accuracy. Native-script training rows are")
    p("     synthetic and share one author; no independent script-level")
    p("     evaluation set is available. Below is a plumbing check only.")
    for lang, sample in [("hi", "घर बैठे रोज 2000 रुपये कमाइए"),
                         ("bn", "ঘরে বসে প্রতিদিন 2000 টাকা আয়")]:
        g = grams(sample)
        p("     %s: %d grams, %d known to the model, score %+.4f"
          % (lang, len(g), sum(1 for x in g if x in llr), score(llr, sample)))
    p("   * Nothing about real traffic. Both the synthetic rows and the")
    p("     paraphrase judgement behind them come from one author.")
    p("   * The 20% non-fragile held-out slice shares its authorship with the")
    p("     training corpus, so section 2's false-positive rate is a floor, not")
    p("     an estimate. Real inboxes contain message types neither set has.")
    p()
    p("   The real held-out gate remains an owner-supplied, human-labeled set.")
    p("   Until it exists,")
    p("   Fresh installs keep Advanced Message Filtering off; these results do")
    p("   not justify an always-on default.")
    p()

    os.makedirs(os.path.dirname(OUT_REPORT), exist_ok=True)
    with open(OUT_REPORT, "w", encoding="utf-8") as f:
        f.write("\n".join(out) + "\n")
    print("Report written to %s"
          % os.path.relpath(OUT_REPORT, ROOT).replace("\\", "/"))
    return 0 if size <= 2 * 1048576 else 1


if __name__ == "__main__":
    sys.exit(main())
