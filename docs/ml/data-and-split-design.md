# Data and Leakage-Resistant Split Design

Updated: 2026-10-15  
Status: Phase 2 Specification  
Governing Document: [`model-capability-roadmap.md`](model-capability-roadmap.md) (Phase 2)

---

## 1. Consented Collection and Privacy Controls

### Privacy Boundary & Local Storage
- **Zero Raw Data in Git**: No raw SMS message bodies, phone numbers, recipient identities, session exports, credentials, or private review workbooks may ever be committed to Git or pushed to any remote repository.
- **Access-Controlled Local Storage**: Raw collection items, de-identification maps, and row-level annotations reside solely in secure, access-controlled local storage (e.g., `D:\Istak\Messages\PRIVATE_SMS_REVIEW\`).
- **No External Cloud Transmission**: Private SMS data must **never** be transmitted to hosted model APIs (e.g., OpenAI, Anthropic, Google Gemini), hosted fine-tuning platforms, or external annotation services.

### Consented Donation Protocol
1. **Explicit, Revocable Consent**: Data collection requires unambiguous, opt-in consent detailing the exact purpose (SMS fraud classification research), retention duration, and right to withdraw.
2. **Client-Side Redaction & Scrubbing**: Before storage or human review, automated regex sanitizers mask:
   - Full names and personal salutations.
   - 10-digit Indian phone numbers and international phone numbers (`<PHONE>`).
   - Bank account numbers, card numbers, and 4-to-8 digit OTP tokens (`<NUM>`, `<CODE>`).
   - Specific URLs, shortened links, and tokenized query strings (`<URL>`).
   - Virtual Payment Addresses / UPI handles (e.g., `*@okhdfcbank` -> `<VPA>`).
3. **One-Way Salted Pseudonymization**: A per-collection cryptographic salt produces a one-way SHA-256 identifier (`sha256(salt + raw_body)`) enabling deduplication and right-to-forget purging without exposing message content.
4. **Data Retention & Expiry**: Donated data retention is bounded to 180 days for development corpora, with immediate purging upon consent revocation.

---

## 2. Multi-Class Label Rubric and Provenance Schema

To prevent confusing intent (scam vs spam) with message type (transactional vs promo), the label rubric separates threat level from carrier function:

| Label Code | Category | Operational Definition | Examples (Generic Archetype) |
|---|---|---|---|
| `SCAM_FRAUD` | Scam / Fraud | Active deceit intended to steal money, credentials, personal data, or compromise device integrity. | Fake lottery, unauthorized UPI collect, electricity cut-off threat, fake bank KYC link, predatory loan app. |
| `COMMERCIAL_SPAM` | Commercial Spam | Unsolicited marketing, advertising, betting, rummy, or promotional pitches without direct theft/phishing intent. | Online casino promo, unrequested real-estate offer, unsolicited product discount without malicious URL. |
| `BENIGN_TRANSACTION` | Benign Transaction / OTP | Legitimate financial debits, credits, account status updates, or time-sensitive authentication codes. | Bank debit alert from registered header, OTP for login or delivery. |
| `BENIGN_PERSONAL` | Benign Personal | Interpersonal communication, family/friend chat, casual conversational text (including code-mixed Hinglish/Banglish). | "Reached station, call me", "Split the bill for dinner". |
| `BENIGN_ADVISORY` | Benign Advisory | Legitimate consumer protection warnings, fraud awareness alerts from banks/telecoms, or civic safety messages. | Official warning: "Do not click unverified links or share OTPs". |
| `UNCERTAIN_CONTEXT` | Inconclusive | Ambiguous, truncated, unresolvable dialect, or lacking sufficient context to classify safely. | Single-word texts, unformatted random character runs. |

### Provenance JSON Schema
Every admitted record must conform to the following metadata schema:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "SMSDatasetRecordProvenance",
  "type": "object",
  "required": [
    "record_hash",
    "source_id",
    "license",
    "synthetic",
    "declared_language",
    "detected_script",
    "adjudicated_label",
    "inter_annotator_agreement",
    "template_family_hash",
    "partition"
  ],
  "properties": {
    "record_hash": { "type": "string", "pattern": "^[a-f0-9]{64}$" },
    "source_id": { "type": "string" },
    "license": { "type": "string" },
    "synthetic": { "type": "boolean" },
    "declared_language": { "type": "string", "enum": ["en", "hi", "hi-Latn", "bn", "bn-Latn", "pa", "other"] },
    "detected_script": { "type": "string", "enum": ["Latin", "Devanagari", "Bengali", "Gurmukhi", "Mixed", "Unknown"] },
    "adjudicated_label": { "type": "string", "enum": ["SCAM_FRAUD", "COMMERCIAL_SPAM", "BENIGN_TRANSACTION", "BENIGN_PERSONAL", "BENIGN_ADVISORY", "UNCERTAIN_CONTEXT"] },
    "annotator_ids": { "type": "array", "items": { "type": "string" } },
    "inter_annotator_agreement": { "type": "number", "minimum": 0.0, "maximum": 1.0 },
    "template_family_hash": { "type": "string", "pattern": "^[a-f0-9]{64}$" },
    "campaign_id": { "type": "string" },
    "partition": { "type": "string", "enum": ["train", "dev", "blind_acceptance", "stress"] }
  }
}
```

---

## 3. Human Annotation and Adjudication Protocol

1. **Dual Independent Review**: Each sample is reviewed blindly by two independent annotators who assign a primary label, secondary attributes (e.g. presence of link, urgency signal), and a subjective confidence score (1 to 5).
2. **Native Language Qualification**: Reviewers for Hindi, Hinglish, Bengali, Banglish, and Punjabi must be verified native speakers. Script appearance alone cannot substitute for linguistic comprehension.
3. **Disagreement Adjudication**: If the two annotators disagree on `SCAM_FRAUD` vs any non-scam label, the row is flagged for adjudication by a senior domain adjudicator.
4. **Agreement Threshold**: Only records with resolved consensus or an inter-annotator Fleiss' Kappa >= 0.85 are admitted into validation or acceptance splits. Rows failing consensus are assigned `UNCERTAIN_CONTEXT` and excluded from model fitting and evaluation.

---

## 4. 5-Tier Leakage-Resistant Isolation Protocol

To ensure evaluation splits measure true generalization rather than memorized templates, data must clear five progressive leakage barriers:

```
[ Raw Candidate Records ]
           │
           ▼
  [ 1. Exact Duplicate Filter (SHA-256) ]
           │
           ▼
  [ 2. Normalized Text Filter (NFKC, lowercase, masked digits/URLs) ]
           │
           ▼
  [ 3. Template-Family Cluster Filter (Parameterized token skeletons) ]
           │
           ▼
  [ 4. Campaign & Sender Cluster Filter (Same sender/campaign -> single split) ]
           │
           ▼
  [ 5. Translation & Cross-Script Variant Grouping ]
           │
           ▼
[ Partition Assignment: Train / Dev / Blind Acceptance / Stress ]
```

1. **Exact Text Check**: `sha256(text.encode('utf-8'))` must not appear in more than one partition.
2. **Normalized Text Check**: NFKC-normalized, lowercase, whitespace-collapsed, digit-masked, and URL-masked text must have zero intersection between partitions.
3. **Template-Family Check**: Dynamic slot abstraction transforms variable amounts, OTP codes, reference IDs, and URLs into structural tokens. Identical structural skeletons are clustered; all members of a cluster are confined to a single partition.
4. **Campaign and Sender Check**: Messages originating from the same sender handle (e.g., `BZ-LOANPK`) or identified as part of a coordinated campaign are assigned to one split only.
5. **Translation & Synthetic Overlap**: Translated variants of identical source messages (such as multilingual translations of English corpora) must remain in the same partition.

---

## 5. Partition Architecture

| Partition | Share | Role & Usage Rules | Access Policy |
|---|---|---|---|
| **Train** | ~60% | Feature extraction, vocabulary building, model fitting, and training-only data augmentation. | Developer access. |
| **Development (Dev / Val)** | ~20% | Hyperparameter tuning, feature selection, probability calibration, and threshold selection at target FPR. | Developer access; aggregate reports committed. |
| **Blind Acceptance Set** | ~15% | Formal release gate certification. Never used for fitting, prompt selection, or threshold tuning. | **Locked & Access-Controlled**. Never inspected row-by-row during active development. Evaluated once per frozen candidate. |
| **Stress Test Set** | ~5% | Rare attack vectors, adversarial typos, homoglyphs, zero-width space obfuscations, and safe consumer advisories. | Access-controlled diagnostic holdout. |

---

## 6. Verification Tooling

The protocol is automated and enforced by:
`protection-engine/tools/verify_split_leakage.py`

Usage:
```bash
python protection-engine/tools/verify_split_leakage.py \
  --train path/to/train.jsonl \
  --val path/to/dev.jsonl \
  --acceptance path/to/acceptance.jsonl \
  --stress path/to/stress.jsonl \
  --json-out path/to/audit_report.json
```

The tool reports exact collisions, normalized collisions, template family overlaps, and internal/cross-partition label conflicts. It returns non-zero exit code on any cross-split leakage, blocking candidate progression.
