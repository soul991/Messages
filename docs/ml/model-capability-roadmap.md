# SMS model capability roadmap

Updated: 2026-10-14

## Objective

Build the strongest SMS protection model that can be supported by representative, consented evidence, while preserving the app's deterministic protections and keeping AI filtering disabled until it meets release gates. Prioritize the scam/fraud classifier. The separate SMS-to-remark experiment is a data-review aid and is not classifier training data.

“Highest capability” means useful scam detection at a measured, acceptable false-positive rate across supported languages and message types, with protected messages preserved and latency, model size, and failure behavior suitable for the Android devices we support. A larger checkpoint or a high score on a familiar public split is not by itself progress toward this objective.

## Current evidence and decision

- N-gram, CNN, MiniLM, MuRIL, and IndicBERTv2 experiments exist, along with India-focused data variants. The current reports show severe drops on the frozen challenge; none is acceptable for automatic filtering.
- Public benchmark splits have been inspected during experimentation. Treat their scores as development diagnostics, not fresh release evidence.
- The available authored challenge has only 159 rows and is not a representative independent population. It is useful for detecting obvious distribution failures, but not for certifying safety or estimating production rates precisely.
- The private 5,358-message remark corpus has message-type/redaction remarks, not adjudicated scam/ham labels. Do not convert its remarks into classifier labels. The mT5 remark model remains a private research checkpoint because it invents sender/brand details.
- Keep deterministic protection authoritative and the AI gate off. No candidate currently clears the evidence gate for app integration.

## Ordered work plan

### 1. Freeze scope, threat model, and release criteria

Write down supported languages and scripts, scam types, benign transaction and OTP messages, protected categories, expected routing behavior, and supported device tiers. Establish the operating target before model comparisons. At minimum, track per-language scam recall at fixed ham false-positive budgets, precision at realistic prevalence, false-positive counts by benign subtype, protected-message misses, calibration, model bytes, cold-start and steady-state latency, and memory use. See detailed specification in [`scope-and-acceptance-criteria.md`](scope-and-acceptance-criteria.md).

Define a hard invariant: AI may contribute only to review or a clearly specified reversible signal until the independent acceptance gate passes. AI must never suppress or misroute OTP, account-security, emergency, or other protected messages. Deterministic rules and the user's existing settings remain authoritative.

### 2. Build trustworthy labeled data

Create a consented, de-identified collection plan with a documented retention policy and a private raw-to-review crosswalk. Keep raw SMS, identities, exports, and signing material outside Git and external training services unless a separate explicit data-sharing decision authorizes it. Commit schemas, scripts, aggregate reports, and provenance; keep message bodies and row-level predictions in access-controlled local storage. Detailed protocol and schema: [`data-and-split-design.md`](data-and-split-design.md). Automated partition audit: `protection-engine/tools/verify_split_leakage.py`.

Use a label rubric that separates scam/fraud, promotion/spam, genuine personal, account/transaction/OTP, protected, and uncertain/insufficient-context cases. Preserve the distinction between message intent and message type. Have trained human reviewers label independently, adjudicate disagreements, and record evidence and uncertainty. Use native-language reviewers for Hindi, Bengali, Hinglish, Banglish, Punjabi, and any additional supported language; do not infer language quality from script alone.

Deduplicate exact and normalized variants, group template families and campaigns, and split by source, time, sender/campaign where safe, and template family before fitting. Keep near-duplicates and translations in one partition. Maintain a prevalence-aware training/development set, a representative human-labeled acceptance set, and a separate stress set for rare attacks and safe counterexamples. Work progress is tracked in [`implementation-backlog.md`](implementation-backlog.md).

### 3. Protect evaluation from leakage

Create a versioned, access-controlled acceptance set that is never used for fitting, prompt selection, threshold selection, or repeated error-driven tuning. Publish only its schema, provenance, label counts, and aggregate metrics. Before each evaluation, verify data hashes, split boundaries, duplicate/template overlap, label completeness, and candidate hashes. Rotate or replenish the acceptance set when it has been exposed to model developers.

Build a development challenge from realistic, independently sourced examples covering links, impersonation, payment requests, credential theft, threats, lures, forwarded alerts, code-mixed language, benign lookalikes, transactional alerts, promotions, and protected messages. Keep synthetic data explicitly tagged and report results on it separately; never let synthetic-only gains stand in for real-world evidence.

### 4. Establish simple, reproducible baselines

Reproduce the current deterministic and n-gram baselines from pinned data manifests and scripts. Add a character CNN and a compact multilingual transformer teacher only where they answer a defined question. Pin model revisions, library versions, seeds, preprocessing, thresholds, and hardware. Keep teacher checkpoints outside the Android bundle unless later compression and device evaluation justify them.

Use source-aware and language-aware ablations. Compare fit-only versus carefully audited supplemental data, class weighting, calibration, and language routing without touching the locked acceptance set. For each run, save candidate hash, data hashes, configuration, per-language and per-subtype metrics, errors in a private store, and an aggregate-only report in Git.

### 5. Improve data before expanding model complexity

Review errors from development data with human adjudication. Prioritize underrepresented benign message types and realistic local-language scams that explain current failures. Add reviewed examples only to training partitions, run duplicate and contamination checks, then repeat development evaluation. Avoid repeatedly tuning against the existing frozen challenge; preserve it as a diagnostic and create a fresh blind set for release decisions.

Use active learning only to prioritize human review. Do not treat model confidence, external AI flags, synthetic labels, or the private remark workbook as ground truth. Maintain provenance and label versions for every accepted example.

### 6. Select a candidate against explicit gates

Choose thresholds on development validation data only, using the agreed false-positive budget and reporting uncertainty intervals. Compare global and language-specific calibration only when enough independent data exists for each language. Report both pooled and per-language results, including counts and confidence intervals; small subgroups must be marked inconclusive.

A candidate advances only if it meets all predeclared acceptance criteria on the untouched human-labeled set, including:

- per-language recall and precision at the required false-positive budget;
- no forbidden protected-message suppression or unsafe routing;
- acceptable performance on benign lookalikes and message subtypes;
- stable calibration and robustness to spelling, script, transliteration, and template changes;
- Android implementation parity, resource-size budget, latency, and memory limits on supported hardware.

If a gate fails, do not change the threshold against the acceptance set. Record the failure, make the data or model change on development evidence, and evaluate a new candidate against a fresh blind set.

### 7. Integrate safely and monitor

Only after all gates pass, implement the model behind a default-off feature flag with deterministic fallback, explicit user control, auditable reason codes, and a safe rollback. Verify that protected-message paths remain unchanged and that model errors cannot cause automatic deletion or irreversible suppression. Run staged internal testing first; monitor opt-in aggregate outcomes without collecting message bodies. Define rollback triggers and an owner for model/data versioning.

## Completion checklist for each candidate

- [ ] Data card, consent basis, source/license audit, and label rubric are complete.
- [ ] Raw/private data are outside Git; committed artifacts are aggregate-only or explicitly approved.
- [ ] Exact, normalized, campaign, template, and split-leakage audits pass.
- [ ] Training and validation are reproducible from pinned inputs and configuration.
- [ ] Development metrics include language, script, benign subtype, protected category, calibration, and uncertainty.
- [ ] Acceptance set was locked and untouched before the candidate hash was submitted.
- [ ] Protected-message, zero unsafe-routing, Android parity, size, latency, and memory checks pass.
- [ ] Human review, rollback path, feature-flag default, and monitoring plan are documented.

## Immediate next actions

1. Keep all current classifier candidates offline and leave AI filtering disabled.
2. Agree on supported language coverage, threat categories, protected-message policy, and numerical release thresholds.
3. Design the consented human-labeling protocol and recruit native-language reviewers before collecting or labeling more examples.
4. Create the private split manifest and blind acceptance set; keep it separate from training and development tooling.
5. Reproduce the current baselines from pinned inputs, then use the new human review to target the largest evidenced gaps.
6. Reassess compact model options only after the data and evaluation gates are in place.

## Data and publication boundary

This repository may contain source code, licenses, data schemas, reproducibility instructions, model cards, model hashes, aggregate metrics, and approved compact app assets. Do not commit raw or de-identified SMS bodies, private review workbooks, row-level predictions, private crosswalks, local checkpoints, API credentials, signing keys, or session exports. Large or access-restricted artifacts should be referenced by source and hash, with local retrieval instructions where permitted.
