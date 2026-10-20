# Owner Acceptance Set Labeling Rubric

This document provides definitive guidance for labeling the blind acceptance set for the Bengali + English + Hindi SMS protection engine.

> [!IMPORTANT]
> **Privacy Rule (R2 & R3)**: This rubric contains only generic descriptions and synthetic examples. No real message text from private inboxes may ever appear in this document, in git, or in chat. The labeled acceptance set lives exclusively on your local machine at `D:\Istak\Messages\PRIVATE_ACCEPTANCE\`. Automated agents are strictly forbidden from inspecting, reading, or tuning against it.

---

## 1. Primary Classification Gate

The production model evaluates a binary distinction:
- **`SCAM_FRAUD`**: Malicious intent to deceive, steal credentials/money, or impersonate institutions.
- **`NOT_SCAM`**: Legitimate personal, transactional, service, promotional, or official messages.

If a message is genuinely ambiguous or difficult to ascertain without external context, leave `label` as your best guess and mark the `unsure` column as `1`.

---

## 2. Category Definitions & Boundaries

### A. SCAM_FRAUD (Label: `SCAM_FRAUD` or `1`)
A message is a scam if its underlying objective is financial theft, credential harvesting, extortion, or unauthorized software installation.

Key India-specific threat vectors:
1. **Bank & KYC Account Block Phishing**:
   - Claims your bank account (SBI, HDFC, ICICI, PNB, Paytm) or PAN card is blocked/suspended due to uncompleted KYC.
   - Urges clicking an unverified link or calling an unauthorized 10-digit mobile number.
2. **Electricity / Utility Disconnection Threats**:
   - Threatens power/gas cut-off tonight at 9:30 PM due to unpaid previous month bills.
   - Demands immediate contact with an "officer" mobile number or payment through a third-party APK.
3. **Traffic Police / eChallan APK Lures**:
   - Claims a pending vehicle traffic challan fine with imminent court summons, directing to an unverified `.apk` link.
4. **Digital Arrest & Law Enforcement Extortion**:
   - Claims CBI, Narcotics Bureau, or Cyber Crime Police have issued an arrest warrant against your Aadhaar. Demands immediate call.
5. **Part-time Job & Telegram Task Fraud**:
   - Offers ₹2,000–₹5,000 daily for liking YouTube videos, reviewing hotels on Google Maps, or processing e-commerce tasks.
6. **Lottery / KBC / Prize Schemes**:
   - Claims your number won ₹25 Lakh in a lucky draw; demands registration/processing fee via WhatsApp.
7. **Shortened Lures & Credential Stealing**:
   - Unsolicited bit.ly, tinyurl, or suspicious domains urging immediate action to claim cashback, credit card points, or loan disbursement.

---

### B. NOT_SCAM (Label: `NOT_SCAM` or `0`)
A message is NOT a scam if it originates from legitimate personal, institutional, or commercial senders, even if unwanted or commercial.

Sub-taxonomies (all mapped to `NOT_SCAM` for the release gate):
1. **Protected Transaction Alerts (CRITICAL: 0.00% False Positive Gate)**:
   - Genuine bank debit/credit notifications ("Account XX1234 debited by INR 450.00...").
   - UPI transaction confirmations, credit card swipe alerts, ATM withdrawals.
   - *Requirement: Must never be classified as scam.*
2. **Protected OTP & Authentication Alerts (CRITICAL: 0.00% False Positive Gate)**:
   - One-time passwords for banking, login, food delivery, e-commerce, or Aadhaar authentication.
   - *Requirement: Must never be classified as scam.*
3. **Bank Fraud Awareness Advisories (Hard Negatives)**:
   - Official alerts from banks cautioning customers: *"Never share OTP, PIN, or CVV with anyone. SBI never asks for personal details."*
   - Contains scam-related keywords ("scam", "fraud", "OTP") but benign intent.
4. **Service Alerts & Delivery Notifications**:
   - E-commerce dispatch/out-for-delivery updates (Amazon, Flipkart, Bluedart, Delhivery).
   - Utility bill arrival notices without threats (electricity, broadband, piped gas).
   - Flight/train PNR and cab booking updates.
5. **Commercial & DLT Promotions**:
   - Sale offers from registered brands (Myntra, Swiggy, Zomato, retail stores).
   - Unsolicited marketing sent via registered sender headers (e.g. `AX-MYNTRA`, `JD-SWIGGY`).
   - *Note*: Even if spammy, pure commercial promotions are NOT fraud/scams.
6. **Personal Conversations**:
   - Everyday messages from family, friends, or contacts in English, Hindi (Devanagari/Hinglish), or Bengali (Bengali/Banglish).

---

## 3. Decision Matrix for Edge Cases

| Scenario | Correct Label | Reasoning |
| :--- | :--- | :--- |
| Genuine bank advisory: *"Beware of fake calls. HDFC Bank never asks for OTP."* | `NOT_SCAM` | Benign intent; protective advisory. False positives here harm user trust. |
| Personal text: *"Can you Google Pay me ₹500 for dinner?"* | `NOT_SCAM` | Peer-to-peer financial request between contacts. |
| Urgent electricity cutoff notice from a private 10-digit number. | `SCAM_FRAUD` | Classic utility threat vector. |
| Telecom promotional offer: *"Recharge with ₹299 and get 1.5GB/day."* | `NOT_SCAM` | Standard commercial promotion; not fraudulent. |
| Message claiming loan approved, but requires paying ₹1,500 advance fee to release funds. | `SCAM_FRAUD` | Advance-fee loan fraud. |
| Shortened link from unknown sender claiming a package delivery failed. | `SCAM_FRAUD` | Delivery phishing lure. |
| Message with unknown language (e.g. Tamil or Punjabi). | `UNSURE` (or omit) | Language is out of scope for the 3-language Bengali/Hindi/English classifier. |

---

## 4. Verification Checkpoint

Once labeling is complete:
1. Verify no empty rows remain in the `label` column.
2. Confirm all labels are either `1` / `SCAM_FRAUD` or `0` / `NOT_SCAM`.
3. Keep the file securely at `D:\Istak\Messages\PRIVATE_ACCEPTANCE\to_label.xlsx` (or save as `labeled_acceptance.xlsx`).
4. Execute `evaluate_acceptance.py` with `OWNER_RUN=1` as described in `docs/ml/OWNER_ACCEPTANCE_HOWTO.md`.
