# SMS Model Capability Implementation Backlog

Updated: 2026-10-15  
Governing Document: [`model-capability-roadmap.md`](model-capability-roadmap.md)  
Status: Active Work Tracker

---

## Roadmap Phase Status Summary

| Phase | Description | Current State | Immediate Next Steps |
|---|---|---|---|
| **Phase 1** | Scope & Acceptance Criteria | **Complete** | Staged threshold policy (Stage 1 FPR <=0.50%, Stage 2 recall >=90.0%) and V1 language scope (bn, hi, en) approved by owner. |
| **Phase 2** | Data & Split Design | **Complete (Specification)** | Recruit native annotators and configure access-controlled storage. |
| **Phase 3** | Reproducible Baselines | **Complete** | Standard n-gram reproduced; split leakage verification operational; Kotlin/Python cross-language parity verified. |
| **Phase 4** | Evidence-Led Improvements | **Proposed / Queued** | Analyze error distributions on development set; address Devanagari Hindi and advisory lookalike gaps. |
| **Phase 5** | Candidate Evaluation | **Blocked** | Blocked until independent blind human-labeled acceptance set is collected, adjudicated, and frozen. |
| **Phase 6** | Android Suitability & Safe Integration | **Blocked** | Blocked until candidate clears Phase 5 acceptance gates; runtime safeguards currently default-off. |

---

## Detailed Implementation Items

### Phase 1: Scope and Acceptance Criteria

#### [P1-01] Scope, Threat Model, and Lookalike Taxonomy Definition
- **Priority**: P0
- **Status**: **Complete**
- **Artifact**: [`docs/ml/scope-and-acceptance-criteria.md`](scope-and-acceptance-criteria.md)
- **Description**: Formulate explicit specifications for in-scope languages (`en`, `hi`, `hi-Latn`, `bn`, `bn-Latn`), threat taxonomy, benign lookalike taxonomy, and protected message categories.
- **Acceptance Evidence**: Detailed document committed and reviewed.

#### [P1-02] Numerical Acceptance Targets and Operating Envelope Specification
- **Priority**: P0
- **Status**: **Complete**
- **Prerequisites**: [P1-01]
- **Artifact**: [`docs/ml/scope-and-acceptance-criteria.md`](scope-and-acceptance-criteria.md)
- **Description**: Establish target recall, false-positive budget, 0.00% protected message FP, model size (<=2.0 MB), latency (p95 <=25 ms), and calibration (Brier <=0.10).
- **Acceptance Evidence**: Owner approved staged threshold policy (Stage 1: FPR <=0.50%, recall >=80%; Stage 2: FPR <=1.00%, recall >=90%) and V1 focus on Bengali, Hindi, and English.


---

### Phase 2: Data and Split Design

#### [P2-01] Consented Human-Labeling & De-identification Protocol
- **Priority**: P0
- **Status**: **Complete**
- **Artifact**: [`docs/ml/data-and-split-design.md`](data-and-split-design.md)
- **Description**: Design privacy-preserving data protocol specifying client-side PII/OTP scrubbing, salted one-way hash mapping, local encrypted storage (`PRIVATE_SMS_REVIEW`), and retention bounds.
- **Acceptance Evidence**: Protocol committed with strict prohibition of raw SMS in Git or hosted model APIs.

#### [P2-02] Multi-Class Label Rubric & Provenance Schema
- **Priority**: P0
- **Status**: **Complete**
- **Artifact**: [`docs/ml/data-and-split-design.md`](data-and-split-design.md)
- **Description**: Define 6-category rubric (`SCAM_FRAUD`, `COMMERCIAL_SPAM`, `BENIGN_TRANSACTION`, `BENIGN_PERSONAL`, `BENIGN_ADVISORY`, `UNCERTAIN_CONTEXT`) and formal JSON provenance schema.
- **Acceptance Evidence**: Schema defined with validator compatibility.

#### [P2-03] Automated Split Leakage & Partition Verification Tooling
- **Priority**: P0
- **Status**: **Complete**
- **Artifact**: [`protection-engine/tools/verify_split_leakage.py`](../../protection-engine/tools/verify_split_leakage.py)
- **Description**: Implement self-testing tool enforcing exact, normalized, template-family, and cross-split leakage checks without exposing raw message contents.
- **Acceptance Evidence**: `verify_split_leakage.py --self-test` passes cleanly on synthetic mock splits.

#### [P2-04] Native-Language Annotator Recruitment & Onboarding
- **Priority**: P1
- **Status**: **Proposed / Blocked on Owner**
- **Prerequisites**: [P2-01], [P2-02]
- **Description**: Onboard native speakers for Hindi, Hinglish, Bengali, Banglish to execute dual independent blinded annotation.
- **Acceptance Evidence**: Documented inter-annotator agreement (Fleiss' Kappa >= 0.85).

---

### Phase 3: Reproducible Baselines

#### [P3-01] Local N-Gram Model Baseline Reproduction
- **Priority**: P0
- **Status**: **Complete**
- **Artifact**: [`docs/ml/ngram-training-report.txt`](ngram-training-report.txt), [`protection-engine/src/main/resources/ngram_model.json`](../../protection-engine/src/main/resources/ngram_model.json)
- **Description**: Run `python protection-engine/tools/train_ngram.py` and verify clean generation of 13,499 n-grams and 198 KB asset.
- **Acceptance Evidence**: Execution completes; unit tests in `protection-engine` pass (`BUILD SUCCESSFUL`).

#### [P3-02] Android Runtime Parity & Kotlin Scorer Regression Check
- **Priority**: P0
- **Status**: **Complete**
- **Prerequisites**: [P3-01]
- **Artifact**: `protection-engine/src/test/resources/score_fixture.json`, `protection-engine/src/test/kotlin/com/messages/protection/IndicClassificationAndAiTest.kt`
- **Description**: Verify that preprocessing, tokenization, whitespace padding, and scoring calculations in Kotlin `NGramScorer` produce identical results to Python `train_ngram.py` on fixture messages.
- **Acceptance Evidence**: Kotlin test `kotlin_scorer_agrees_with_python_trainer_on_fixture` passes across all 44 test messages (English, Hindi, non-breaking space, sub-length, and abstain paths) with score equality within 1e-4.

#### [P3-03] Transformer Teacher Checkpoint Containment Audit
- **Priority**: P1
- **Status**: **Complete**
- **Description**: Verify that large research checkpoints (e.g. HinSpam 476 MB, SMS-Shield 1.28 GB, IndicBERTv2 1.1 GB) remain outside the Android project repository and APK build path.
- **Acceptance Evidence**: No binary model checkpoints in Git history or app assets.

---

### Phase 4: Evidence-Led Improvements

#### [P4-01] Development Error Analysis on Consumer Advisories & Benign Overflagging
- **Priority**: P1
- **Status**: **Proposed**
- **Prerequisites**: [P3-01]
- **Description**: Analyze why safe bank security advisories ("never share OTP") trigger scam weights due to lexical overlap. Formulate negative ngram weighting or structural context rules.
- **Acceptance Evidence**: False-positive reduction on advisory diagnostic set without degrading scam recall.

#### [P4-02] Devanagari Hindi Vocabulary & Tokenization Enhancement
- **Priority**: P1
- **Status**: **Proposed**
- **Prerequisites**: [P2-04]
- **Description**: Expand Devanagari Hindi coverage using reviewed, consented non-synthetic corpora to address current 14–45% scam recall deficit.
- **Acceptance Evidence**: Demonstrable recall increase on held-out development Hindi split.

---

### Phase 5: Candidate Evaluation

#### [P5-01] Threshold Calibration on Development Split Only
- **Priority**: P0
- **Status**: **Queued**
- **Prerequisites**: [P4-01], [P4-02]
- **Description**: Calibrate candidate operating threshold strictly on development ham to meet <= 0.5% / <= 1.0% target FPR. Calculate Wilson 95% confidence intervals and calibration curves (Brier/ECE).
- **Acceptance Evidence**: Frozen threshold recorded with validation report; zero evaluation on blind acceptance set.

#### [P5-02] Blind Acceptance Gate Evaluation
- **Priority**: P0
- **Status**: **Blocked**
- **Prerequisites**: [P2-04], [P5-01]
- **Description**: Execute a single, un-tuned evaluation of the frozen candidate against the locked blind acceptance set upon explicit owner approval.
- **Acceptance Evidence**: Candidate meets all Phase 1 numerical criteria or is formally rejected without re-tuning.

---

### Phase 6: Android Suitability and Safe Integration

#### [P6-01] Reference Hardware Latency and Memory Profiling
- **Priority**: P0
- **Status**: **Queued**
- **Prerequisites**: [P5-02]
- **Description**: Measure P50/P95 latency and peak heap allocation of candidate model on physical Android reference devices (e.g. Android Go / budget tier).
- **Acceptance Evidence**: P95 <= 25 ms, heap delta <= 8 MB, zero JNI/native crashes.

#### [P6-02] Deterministic Protection & AI Safety Gate Audit
- **Priority**: P0
- **Status**: **Complete (Audit Verified)**
- **Artifact**: `protection-engine/src/main/kotlin/com/messages/protection/AiScorer.kt`
- **Description**: Verify that `AI_LAYER_ENABLED = false` by default, `readsAsProtected` prevents any OTP/transaction modification, and routing effect is strictly `INBOX -> REVIEW`.
- **Acceptance Evidence**: Verified in codebase; passes all unit and integration tests.
