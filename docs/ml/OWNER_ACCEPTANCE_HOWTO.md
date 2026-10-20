# Owner Acceptance Set: How-To Guide

This guide explains how the project owner creates, labels, and evaluates candidates against the blind acceptance set.

> [!IMPORTANT]
> **Owner-Only Operation (Rule R3)**: Automated AI agents are strictly forbidden from inspecting, reading, or running evaluations on the blind acceptance set. All steps below are intended to be run manually by the project owner.

---

## Step 1: Create the Stratified Acceptance Spreadsheet

Run the local sampling tool from your terminal, pointing to your private messages export (e.g. `sms_review_PRIVATE.xlsx` or raw SMS text):

```powershell
# Navigate to repo
cd "D:\Istak\Messages\Messages App\01_active_codebase\messages"

# Activate the ML environment
& "D:\Istak\Messages\.venv-ml\Scripts\Activate.ps1"

# Generate masked 400-message stratified sample
python protection-engine/tools/make_owner_sample.py `
  --input-file "D:\Istak\Messages\sms_review_PRIVATE.xlsx" `
  --target-size 400
```

This generates `D:\Istak\Messages\PRIVATE_ACCEPTANCE\to_label.xlsx` with format-preserving placeholders masking personal phone numbers and account details.

---

## Step 2: Label the Messages

1. Open `D:\Istak\Messages\PRIVATE_ACCEPTANCE\to_label.xlsx` in Excel.
2. Refer to [`docs/ml/OWNER_LABELING_RUBRIC.md`](OWNER_LABELING_RUBRIC.md) for definitions and boundary cases.
3. In the `label` column, enter:
   - `1` (or `SCAM_FRAUD`): Malicious scams, utility cutoff threats, KYC phishing, job fraud, lottery schemes.
   - `0` (or `NOT_SCAM`): Legitimate personal, transactional debit/credit alerts, OTPs, utility bills, and commercial marketing.
4. In the `unsure` column:
   - Mark `1` if the message is genuinely ambiguous or out of scope.
5. Save the file.

---

## Step 3: Run the Acceptance Evaluator

Set the environment variable `OWNER_RUN=1` and execute `evaluate_acceptance.py`:

```powershell
$env:OWNER_RUN="1"

python protection-engine/tools/evaluate_acceptance.py `
  --candidate "protection-engine/src/main/resources/ngram_model.json" `
  --labels "D:\Istak\Messages\PRIVATE_ACCEPTANCE\to_label.xlsx" `
  --json-out "D:\Istak\Messages\PRIVATE_ACCEPTANCE\acceptance_eval_result.json"
```

The evaluator prints:
- Scam recall with Wilson 95% confidence intervals.
- Ham False Positive Rate with Wilson 95% confidence intervals.
- Protected message FPR (target: 0.00%).
- Calibration metrics (Brier Score $\le 0.10$, ECE $\le 0.08$).
- Per-script breakdowns (Devanagari, Bengali, Latin).
- Gate status: `MET` or `NOT MET`.

---

## Step 4: Owner Approval (If and Only If All Gates Pass)

If the candidate meets **all Stage 1 release gates** on your acceptance set:
1. Obtain the SHA-256 of the model candidate:
   ```powershell
   Get-FileHash <path_to_candidate_model> -Algorithm SHA256
   ```
2. Create `docs/ml/OWNER_APPROVAL.md` with the exact word `APPROVED` and the model SHA-256 hash:
   ```markdown
   # Model Approval Notice
   Status: APPROVED
   Candidate SHA-256: <paste_sha256_here>
   Approved by: Owner
   ```
3. Only after this file is committed can `AI_LAYER_ENABLED` be flipped to `true` (enforced by `scripts/verify_ml.py`).
