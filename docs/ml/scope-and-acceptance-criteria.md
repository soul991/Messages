# Scope and Numerical Acceptance Criteria

Updated: 2026-10-15  
Status: Proposal for Repository Owner Review  
Governing Document: [`model-capability-roadmap.md`](model-capability-roadmap.md) (Phase 1)

---

## 1. Operating Envelope and Intended Routing Invariant

### Permitted Routing Effect
- **Inbox to Review Only**: The sole permitted automated routing modification by any AI model (local or cloud) is moving an unflagged message from `Category.INBOX` to `Category.REVIEW`.
- **Strictly Prohibited**:
  - An AI model must **never** output `Category.SPAM`, `Category.BLOCKED`, `Category.DANGEROUS`, or trigger an immediate fraud warning banner.
  - An AI model must **never** auto-delete, suppress delivery, or silently discard any message.
  - An AI model must **never** modify or downgrade an existing deterministic classification (e.g., cannot move a message flagged as Spam back to Inbox).
  - Deterministic rules, user allow/block lists, and contact whitelist context remain authoritative and outrank all statistical/AI inferences.

### Protected-Message Immunity (Hard Invariant)
Messages meeting any of the following criteria are strictly immune from AI evaluation and routing modification:
1. **One-Time Passwords (OTPs)**: 2FA tokens, login verification codes, banking authorization SMS.
2. **Financial Transactions**: Legitimate account debits, credits, UPI auto-pay confirmations, balance updates.
3. **Emergency and Civic Alerts**: Disaster management warnings, government alerts, carrier broadcast emergency alerts.
4. **Known Contacts & Allowed Senders**: Senders on the user's contact list or explicit user allowlist.

Any AI score on a protected message is discarded. The protected-message false-positive rate must be exactly **0.00%**.

---

## 2. In-Scope Languages, Scripts, and Varietals

| Language / Variety | Script | Scope Status | Rationale & Current Evidence |
|---|---|---|---|
| **English** | Latin (`en`) | **In-Scope** | Primary SMS lingua franca in India and global traffic; strong baseline representation across public corpora. |
| **Devanagari Hindi** | Devanagari (`hi`) | **In-Scope** | High-priority domestic script. Current evidence shows severe vulnerability (45–63% recall, high false positives on advisories); critical improvement target. |
| **Romanized Hinglish** | Latin (`hi-Latn`) | **In-Scope** | Dominant conversational and fraudulent SMS format in urban India. High separability in prior research baselines but requires robust benign lookalike coverage. |
| **Bengali Script** | Bengali (`bn`) | **In-Scope** | High-prevalence regional script with strong existing training split (Bengali SMS dataset), but needs verified real-world adversarial holdouts. |
| **Romanized Banglish** | Latin (`bn-Latn`) | **In-Scope** | Frequent in West Bengal and Bangladesh regional mobile communication. |
| **Punjabi** | Gurmukhi (`pa`) | *Deferred / Diagnostic Only* | Present in the 159-row challenge; excluded from initial model target due to lack of representative training/validation corpora and script-router collisions. |
| **Gujarati, Marathi, Tamil, Telugu, Kannada, Malayalam** | Indic scripts | *Out of Scope (Phase 1/2)* | Data is currently insufficient or unverified. Model must abstain (zero routing impact) on unrepresented scripts. |

---

## 3. Threat Taxonomy vs. Benign Lookalike Taxonomy

### In-Scope Threat Categories
1. **Financial Fraud & Payment Lures**:
   - Unauthorized UPI collect requests (`upi://pay`, fake payment approvals).
   - Fake cashback, lottery, reward points, and sweepstakes winnings.
   - Predatory instant loan offers, fake loan approvals, processing fee demands.
   - Fake utility disconnection threats (electricity, water, gas cut off tonight).
   - Part-time job / task-based investment scams (Telegram/WhatsApp task scams).
2. **Credential & Identity Theft**:
   - Fake bank KYC suspension, PAN card deactivation alerts.
   - SIM swap / eSIM conversion / 5G upgrade lures.
   - Phishing links impersonating bank netbanking portals or government services.
3. **Malicious Payloads & Extortion**:
   - Links prompting installation of malicious APKs (e.g. fake "SBI YONO APK").
   - Traffic challan payment lures with fake `.apk` or spoofed portals.
   - Courier parcel detention / customs duty demands.

### Benign Lookalike Categories (Critical False-Positive Budget)
1. **Legitimate Transaction Alerts**: Senders with official headers (e.g., `VM-HDFCBK`) reporting debits, credits, or EMIs.
2. **Authentication & Delivery OTPs**: Numeric codes accompanied by time-sensitive instructions.
3. **Official Security & Consumer Advisories**: Genuine bank warnings educating customers (e.g., *"Never share your OTP or click unknown links - Bank of India"*). Current models frequently misclassify these as scams due to lexical overlap.
4. **Telecom & Utility Service Updates**: Bill generated, low balance warnings, data pack expiry.
5. **Legitimate Commercial Promotions**: Marketing SMS from known retail brands with shortened URLs.
6. **Code-Mixed Informal Personal Texts**: Casual messages between family and friends discussing money, loans, or purchases.

---

## 4. Hardware Tiers and Performance Envelope

Target deployment hardware spans budget Android devices sold in the Indian market over the past 5 years.

| Metric | Budget Tier (Android Go / 2–3 GB RAM) | Mainstream Tier (4–8 GB RAM) | Premium Tier (8+ GB RAM) |
|---|---|---|---|
| **Reference Hardware** | Cortex-A53, Helio G25/G35, Snapdragon 429 | Snapdragon 695, Dimensity 700/810 | Snapdragon 8 Gen series, Tensor |
| **Max Model Asset Size** | <= 2.0 MB uncompressed in APK | <= 2.0 MB uncompressed in APK | <= 2.0 MB uncompressed in APK |
| **P50 Inference Latency** | <= 10.0 ms | <= 5.0 ms | <= 2.0 ms |
| **P95 Latency Ceiling** | <= 25.0 ms (Hard guardrail; trips fallback) | <= 15.0 ms | <= 8.0 ms |
| **Transient Memory (Heap)** | <= 8.0 MB allocation delta | <= 8.0 MB allocation delta | <= 8.0 MB allocation delta |
| **Persistent Memory** | <= 3.0 MB resident footprint | <= 3.0 MB resident footprint | <= 3.0 MB resident footprint |

---

## 5. Numerical Acceptance Criteria Table

All proposed targets are subject to formal signoff by the repository owner.

| Dimension | Target Metric | Evaluation Split & Condition | Status / Owner Decision |
|---|---|---|---|
| **Overall Scam Recall** | **>= 85.0%** | Independent Blind Acceptance Set (at operating threshold) | **[PROPOSED]** *Owner choice: 80% vs 85% vs 90%* |
| **Per-Language Scam Recall** | **>= 80.0%** each for English, Devanagari Hindi, Hinglish, Bengali, Banglish | Independent Blind Acceptance Set (subgroups with N >= 100) | **[PROPOSED]** *Owner choice: 75% vs 80%* |
| **Ham False Positive Rate (FPR)** | **<= 0.50%** overall (<= 1 per 200 legitimate SMS) | Representative Ham Acceptance Set | **[PROPOSED]** *Owner choice: 0.5% vs 1.0%* |
| **Per-Language Ham FPR** | **<= 1.00%** per supported language group | Representative Ham Acceptance Set (subgroups with N >= 100) | **[PROPOSED]** *Standard industry safety ceiling* |
| **Protected Message FPR** | **0.00%** (Zero false positives allowed) | Transaction / OTP / Emergency holdout suites | **[MANDATORY HARD GATE]** *Non-negotiable* |
| **Benign Advisory FPR** | **<= 2.00%** | Bank fraud awareness & security advisory holdout | **[PROPOSED]** *Prevents overflagging legitimate safety warnings* |
| **Probability Calibration** | Brier score <= 0.10; ECE <= 0.08 | Development Validation Set | **[PROPOSED]** *Ensures score reflects true confidence* |
| **Statistical Uncertainty** | 95% Wilson score confidence interval reported for all metrics | Any subgroup with N < 100 marked **Inconclusive** | **[MANDATORY]** *Standard reporting requirement* |
| **Latency Budget** | P95 <= 25.0 ms on reference hardware | Kotlin runtime on JVM / ART | **[MANDATORY HARD GATE]** *Existing `LATENCY_BUDGET_MS`* |
| **App Asset Footprint** | <= 2.0 MiB uncompressed | In-APK asset footprint | **[MANDATORY HARD GATE]** *Existing budget requirement* |
| **Feature Default** | `advanced_message_filtering = false` | Fresh installs | **[MANDATORY HARD GATE]** *Must remain off until acceptance pass* |

---

## 6. Open Decisions for Repository Owner

1. **Operating Threshold Policy**:
   - *Option A (High Safety - Recommended)*: Prioritize low false-positive rate (ham FPR <= 0.50%), accepting lower scam recall (>= 80.0%).
   - *Option B (Aggressive Catch)*: Prioritize scam recall (>= 90.0%), accepting ham FPR up to 1.00%.
2. **Scope of Regional Languages in V1**:
   - Confirm whether Punjabi (`pa`) should remain formally out-of-scope until dedicated Gurmukhi training corpora and clean script routing are established.
3. **Acceptance Threshold for Subgroups**:
   - Confirm the minimum subgroup size (proposed: N >= 100) below which an evaluation result must be certified as inconclusive rather than a release decision.
