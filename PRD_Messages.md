# PRD v2: "Messages" — Android Messaging App with Deterministic Word/Pattern-Based Spam, Scam & Fraud Protection

**App name:** Messages
**Date:** 2026-07-17
**Status:** Ready for implementation
**Intended builder:** Claude Code
**Platform:** Android only

---

## 0. What changed from v1 (read this first, Claude Code)

1. **All ML / on-device AI / cloud LLM classification is REMOVED.** Filtering is now 100% deterministic: a word/phrase/regex **Pattern Library** with weighted scoring. No model files, no API keys, no network calls for classification. Fully offline, fully explainable, zero cost.
2. **Nothing is ever deleted by the filter.** Every filtered message is retained and reviewable in its folder (Spam / Promotions / Blocked / Review) forever, until the user deletes it manually. This is a hard guarantee (§6).
3. **The Pattern Library in §5 is the heart of the product.** It is seeded from the analysis below, and Claude Code is REQUIRED to expand it further (§7) so no common spam/scam/fraud format slips through.
4. **UI must be best-in-class** (§9) and the feature set must match modern messaging apps (§8), including extras like "Auto-delete OTPs after 24 hours."

---

## 1. Summary

**Messages** is a full-featured Android SMS/MMS/RCS app that replaces Google Messages as the default handler. It works exactly like a normal modern messaging app, with one core difference: a **deterministic, pattern-based protection engine** that inspects every incoming message's words, phrase formats, sender type, and links, then files promotional, spam, scam, and fraud messages into silent folders — while the Inbox and notifications carry only genuine, important messages.

**How the engine decides (one paragraph):** the message text is normalized (to defeat tricks like `F.R.E.E` or hidden characters), checked against a **Protected list** (OTPs, bank alerts, deliveries — these always pass), then scored against a large library of weighted keyword and phrase patterns, adjusted by **who sent it** (contact / registered business header / random mobile number / international number). Points add up; thresholds decide the folder. A friend texting "should I buy this?" scores near zero; "You WON ₹25,00,000! Click bit.ly/xyz" scores off the charts. Every decision is explainable — the user can always see exactly which patterns matched.

---

## 2. Goals & Non-Goals

### 2.1 Goals
1. Full default-SMS-app functionality with **no regression** vs. Google Messages.
2. Deterministic classification of **every** incoming message using the Pattern Library + sender analysis + scoring.
3. Notify **only** for genuine personal/important/transactional messages; promotions, spam, scam, fraud stay silent.
4. **Never delete** a filtered message; everything is reviewable (§6).
5. Make the protection as close to unbreakable as pattern-based filtering allows: normalization against obfuscation, phrase-format regexes (not just single words), combination rules, sender context, and an easily expandable pattern file.
6. Best-possible UI (§9) and modern feature parity (§8).

### 2.2 Non-Goals
- iOS (cannot replace the default SMS handler there).
- Internet accounts / WhatsApp-style chat; servers of any kind.
- ML models or cloud AI of any kind (explicitly removed in v2).
- Building a custom RCS stack (use what Android exposes to the default app; degrade gracefully).

### 2.3 Honest engineering note (keep in README)
No filter on earth is literally impossible to break — scammers invent new wording. The design compensates with defense-in-depth: (a) phrase-format regexes that survive rewording, (b) normalization that survives obfuscation, (c) sender-context rules that survive both, (d) a **Review folder** so uncertain messages are never mis-slotted silently, (e) the never-delete guarantee so even a wrong block is always recoverable, and (f) a versioned pattern file that can be expanded in every app update. The correct goal — and the requirement — is: **every known spam/scam/fraud family in §5 must be caught, and no genuine message is ever lost.**

---

## 3. The Protection Engine (deterministic pipeline)

Every incoming message runs this pipeline in order. First decisive stage wins. Total budget: **< 50 ms** (it's just string ops and regex — far faster than the old ML plan).

### Stage 0 — Normalization (always)
Produce `normalizedText` used by all later stages:
- Unicode NFKC normalization; strip zero-width chars (U+200B–U+200D, U+FEFF).
- Homoglyph map: Cyrillic/Greek look-alikes (а е о с р х ѕ і) → Latin equivalents.
- Leet map: `0→o, 1→i, 3→e, 4→a, 5→s, 7→t, 8→b, @→a, $→s, !→i` (applied for matching only, original text untouched).
- Collapse separator-obfuscation: `F.R.E.E`, `F R E E`, `F-R-E-E`, `F_R_E_E` → `free`.
- Collapse 3+ repeated chars (`FREEEEE` → `free`), lowercase, collapse whitespace.
- Extract and separately list: URLs, phone numbers, currency amounts, digit runs.

### Stage 1 — User rules (absolute, top priority)
- **Allow-list** sender → Inbox, notify. Stop.
- **Block-list** sender → Blocked folder, silent. Stop. (Message still stored — §6.)
- **User custom rules** (keyword/sender/regex → category), evaluated in user-defined order. Stop on match.

### Stage 2 — Protected patterns (can NEVER be filtered)
If any Protected pattern (§5.7) matches → **Inbox (Important), notify. Stop.** This outranks every spam pattern. A buried OTP is the one unforgivable failure; a spam message reaching the Inbox is merely annoying.
> Exception: if the message ALSO contains a link matching the phishing-link rules (§5.6) AND the sender is not a registered/known header, it goes to Inbox **with a red fraud-warning banner** instead of being trusted blindly (fake-OTP phishing exists).

### Stage 3 — Sender analysis (sets the context multiplier)
| Sender type | How detected | Effect |
|---|---|---|
| Saved contact | Contacts provider | → Inbox (Personal) directly unless a Fraud combo (§5.8) matches; contacts get compromised too |
| Registered business header, transactional | Alphanumeric ID (e.g., `AX-BANKXX-S`), DLT suffix `-S`/`-T`/`-G` | Trust boost; content still scored |
| Registered business header, promotional | DLT suffix `-P` | → Promotions by default (India mandates this marking) |
| Alphanumeric header, unknown suffix | e.g., `VM-OFFERS` | Neutral; content decides |
| Random 10-digit mobile number, not in contacts | Numeric sender | Spam weight ×1.5 on promo/scam patterns (legit businesses must use registered headers, not personal numbers) |
| International number (unexpected country code) | `+` prefix analysis | Spam weight ×2 on scam patterns |
| Short code (4–6 digits) | Length | Neutral; content decides |
| Email-to-SMS gateway | Contains `@` | Spam weight ×1.5 |
Additionally maintain a **local sender-reputation score** per sender ID, built from the user's own actions (marked spam twice → auto-heavy weight; always opened & replied → trust boost).

### Stage 4 — Pattern scoring
Match `normalizedText` against every pattern in the library (§5). Each match adds its weight. Then apply **combination rules** (§5.8) — certain pattern *combinations* force a verdict regardless of raw score.

### Stage 5 — Verdict thresholds (tunable via the Sensitivity slider)
| Final score | Verdict | Behavior |
|---|---|---|
| Fraud-combo triggered OR score ≥ 15 | **Spam** with `Dangerous` label | Silent; red warning banner; links tap-disabled behind confirmation |
| 10 – 14 | **Spam** | Silent |
| 5 – 9 with promo-family matches | **Promotions** | Silent |
| 5 – 9 without promo-family matches | **Review** | One quiet batched low-priority notification |
| 0 – 4 | **Inbox** | Notify normally |
- Transactional patterns (§5.7 receipts/statements) → **Transactions** (notify per settings).
- Every verdict stores the matched pattern IDs → powers the "Why?" screen.

---

## 4. Categories & Folders

| Folder | Contents | Notifications |
|---|---|---|
| **Inbox** | Personal + Important | Normal / high |
| **Transactions** | Receipts, debits/credits, statements | Configurable (on by default) |
| **Promotions** | Marketing, offers, recharge offers, coupons | Silent, badge only |
| **Spam** | Junk + scam/fraud (fraud carries a red `Dangerous` label) | Silent, badge only |
| **Review** | Gray-zone messages the engine won't guess on | One quiet batched notification |
| **Blocked** | Messages from user-blocked senders | Silent |
| Archived / Starred | Standard | — |
All folders are searchable. All are one tap from the Inbox. Per-folder notification behavior is user-configurable.

---

## 5. THE PATTERN LIBRARY (seed data — ship this, then expand per §7)

> **Implementation requirement:** store as a versioned JSON asset (`patterns.json`), each entry: `{id, family, regex, weight, languages, description, examples[]}`. Every pattern must have at least one positive and one negative test case. All regexes run against `normalizedText`. `#` amounts below mean `(₹|rs\.?|inr|\$|usd)\s*[\d,]+(\.\d+)?( ?(lakh|lakhs|crore|cr|k))?`.

### 5.1 Family: PROMOTIONAL (weight 2–4 each; individually weak, cumulatively decisive)
**Sale/discount words:** offer, offers, mega offer, special offer, exclusive offer, festive/festival offer, deal, deal of the day, sale, mega sale, flash sale, clearance sale, end of season, discount, % off, upto X% off, flat X off/₹X off, price drop, lowest price, best price, starting at just, MRP, save ₹X, super saver, combo offer, buy 1 get 1 / BOGO / B1G1.
**Call-to-action:** buy now, shop now, order now, grab now, book now, avail now, hurry, hurry up, limited time, limited period, limited stock, last chance, last day, ends today/tonight/midnight, expires, don't miss, offer valid till, use code, apply code, promo code, coupon, voucher, redeem, claim offer, download app, install now, click here, visit store/link, refer & earn, invite & earn.
**Telecom/recharge (very common in India):** recharge, recharge now, recharge offer, special recharge, plan expiring, validity, unlimited calls, unlimited data, data pack, bonus data, X GB/day, talktime, top-up, best plan, upgrade plan, renew plan.
**Marketing fingerprints (weight 4 — near-certain promo):** `t&c apply`, `*t&c`, `tnc`, `to opt out`, `to unsubscribe`, `reply stop` / `sms stop to`, `dial *###*`, presence of DLT `-P` suffix header.
**Financial products marketing:** pre-approved (card/limit), credit card offer, personal loan at X%, zero annual fee, insurance plan, premium waiver, demat offer, brokerage free.
**Hinglish promo:** dhamaka, loot, loot lo, sasta, muft, free me, sirf aaj, jaldi karo, mauka, bumper offer, shandar offer, paise bachao.

### 5.2 Family: LOTTERY / PRIZE SCAM (weight 8–12)
- `(won|win|winner|winning|congratulations?).{0,60}#` — "You have WON ₹25,00,000"
- `win upto #` · `(lucky draw|lottery|jackpot|prize|reward|inaam)` + `#` or link
- `(kbc|kaun banega crorepati)` + amount/link (weight 12 — infamous scam family)
- `(selected|chosen|shortlisted) (as|for).{0,40}(winner|prize|reward|lucky)`
- `claim (your )?(prize|reward|gift|amount|winnings?)`
- `(spin|scratch) (and|&|to) win` · `free gift (card|voucher|hamper)` + link
- Hinglish: `(jeeta|jeete|jeeto|jeet gaye)` + amount, `badhai ho` + amount/link, `lakhpati bane`

### 5.3 Family: FAKE DEPOSIT / REFUND / PAYMENT SCAM (weight 8–12)
- `(deposited|credited|transferred|received).{0,40}(account|a/c|wallet).{0,80}(click|link|verify|claim|confirm|http)` — the "₹8,000 deposited in your account. Click link" classic. **Note:** without the click/link tail AND from a registered bank header, the same words are a Protected transactional alert (§5.7). Context decides.
- `(refund|cashback|reimbursement).{0,50}(pending|approved|initiated|waiting|stuck).{0,60}(claim|click|verify|link|http)`
- `(payment|amount).{0,30}(on hold|failed|reversed).{0,60}(update|verify|click)`
- `your (upi|wallet|paytm|phonepe|gpay|google pay).{0,40}(receive|credited|pending).{0,60}(accept|approve|pin|click)` (weight 12 — "enter PIN to receive money" is always fraud; UPI never needs a PIN to RECEIVE)
- `(income tax|it dept|tds).{0,40}refund.{0,60}(click|verify|link)`

### 5.4 Family: KYC / ACCOUNT-BLOCK / UTILITY THREAT SCAM (weight 8–12)
- `(kyc|e-?kyc).{0,50}(pending|expir|suspend|update|incomplete|reject)`
- `(pan|aadha?ar|pan card).{0,40}(link|update|verify).{0,50}(immediately|today|suspend|block)`
- `(account|a/c|net ?banking|debit card|credit card|upi|wallet).{0,50}(block|suspend|freez|deactivat|disabl|hold|expir).{0,80}(click|call|update|verify|link|http)`
- `(sim|mobile number|connection).{0,40}(deactivat|disconnect|block|suspend).{0,40}(24 hours|today|tonight|immediately)`
- `(electricity|power|bijli|gas|water).{0,50}(disconnect|cut|band).{0,50}(tonight|today|9\.?30|immediately|contact|call)` — the electricity-disconnection scam
- `(dear (customer|user|consumer))` from a non-registered numeric sender + any threat/link (weight +4 — banks address you via registered headers)

### 5.5 Family: LOAN / JOB / INVESTMENT / OTHER FRAUD (weight 7–12)
**Loan scams:** `(instant|urgent|quick|easy) loan`, `loan (approved|sanctioned).{0,40}#`, `(without|no) (documents?|cibil|income proof|guarantor)`, `loan in \d+ (minutes|hours)`, `aadhaar loan`.
**Job/earn-money scams:** `work from home.{0,60}(earn|#)`, `earn #.{0,30}(per day|daily|per week|weekly)`, `part.?time job.{0,60}(whatsapp|telegram|link|http)`, `(no experience|only 2-3 hours)`, `(typing|data entry|review|like videos?) (job|work).{0,40}(earn|#)`, `ghar baithe (paise|kamao|earn)`.
**Investment scams:** `(guaranteed|assured|fixed) (returns?|profit)`, `double your (money|investment)`, `(stock|share|trading) tips`, `(join|add).{0,30}(telegram|whatsapp) (group|channel).{0,40}(profit|trading|earn|tips)`, `crypto.{0,40}(profit|returns|double)`.
**Delivery/customs scams:** `(parcel|package|shipment|courier).{0,50}(on hold|held|stuck|customs|address (issue|incomplete)).{0,60}(pay|fee|click|update|link)`, `redelivery fee`, `pay # to (release|receive)`.
**Impersonation/threat:** `(i am|this is).{0,20}(from your bank|bank officer|rbi|trai|police|cbi|customs|court)`, `digital arrest`, `(fir|warrant|legal action|court notice).{0,50}(click|call|respond|pay)`, `(drugs|illegal items?).{0,30}(parcel|package)`.
**Subscription traps:** `(netflix|prime|hotstar|spotify|subscription).{0,40}(expir|renew|payment failed).{0,50}(click|update|link|http)` from non-registered senders.

### 5.6 Family: LINK & FORMAT RULES (weight shown per rule)
- **URL shorteners** (weight 6, and they enable combos): `bit.ly, tinyurl.com, cutt.ly, t.ly, rb.gy, is.gd, tiny.cc, shorturl.at, rebrand.ly, ow.ly, goo.gl, s.id, lnkd.in, t.co, surl.li, u.to, v.gd, clck.ru`
- **Suspicious TLDs** (weight 6): `.xyz .top .club .online .site .buzz .icu .vip .rest .click .link .work .loan .men .cyou .cfd .sbs .quest .monster .lol .pw .cc .tk .ml .ga .gq` (whitelist known-legit exceptions in code)
- **Brand-impersonation domains** (weight 10): `(sbi|hdfc|icici|axis|kotak|pnb|paytm|phonepe|gpay|amazon|flipkart|airtel|jio|vi|irctc|indiapost|bluedart|delhivery|dhl|fedex)` appearing inside a domain that is NOT the brand's official domain, esp. `brand[-.]?(kyc|verify|update|reward|offer|care|support|refund)\.(anything)`
- **IP-literal URL** (weight 8): `https?://\d+\.\d+\.\d+\.\d+`
- **Plain `http://`** (no TLS) with any money/urgency word (weight 5)
- **Punycode** `xn--` domains (weight 8)
- **APK direct-download** links `\.apk(\?|$)` (weight 12 — malware distribution)
- `wa\.me/|t\.me/` links from unknown senders combined with money/job words (weight 6)
- **Format signals:** >50% CAPS in a >40-char message (3), `!{2,}` (2), 3+ emoji in first 20 chars (2), currency amount + phone number + "call now" (5), message asks to "share/forward to X groups" (6)

### 5.7 Family: PROTECTED — NEVER FILTER (these force Inbox/Transactions)
- **OTP/verification** (absolute): `\b\d{4,8}\b.{0,40}(otp|one.?time (password|pin|code)|verification code|auth code|security code|login code|2fa)` or reversed order; `do not share (this )?(otp|code)`; `valid for \d+ (min|minutes)`.
- **Bank transactions** from registered/alphanumeric headers WITHOUT phishing-link matches: `(debited|credited|withdrawn|spent).{0,40}(a/c|account|card)`, `avl (bal|balance)`, `txn`, `imps|neft|rtgs|upi ref`, `emi (due|debited)`, `cheque`, `statement`.
- **Delivery status** from known courier headers: `(out for delivery|delivered|arriving|shipped|dispatched|pickup|tracking id|awb)`.
- **Travel/appointments:** `pnr`, `(flight|train|bus) (no|number)`, `boarding`, `gate`, `seat`, `appointment (confirmed|scheduled|reminder)`, `token number`, `e-ticket`.
- **Government/emergency:** sender header suffix `-G`, `epfo|uidai|cowin|digilocker|nrega|pmkisan` headers, disaster/weather alerts.
- **Bill due notices** from registered headers: `bill (of|amount|due)`, `due date`, `min(imum)? amount due`.
> Reminder: Protected + phishing-link + unregistered sender = Inbox WITH red warning banner (Stage 2 exception). Protected messages are labeled (OTP / Bank / Delivery / Travel / Bill) for search and for the OTP auto-delete feature.

### 5.8 COMBINATION RULES (force verdicts; this is what makes it hard to break)
| # | Combination | Verdict |
|---|---|---|
| C1 | money amount + any link + urgency word, sender unknown | Spam·Dangerous |
| C2 | "won/prize/lottery" + amount (link not even needed) | Spam·Dangerous |
| C3 | account-threat word (block/suspend/expire) + link or callback number, sender not a registered header | Spam·Dangerous |
| C4 | shortener or suspicious-TLD link + any of: KYC, refund, deposited, prize, loan, job | Spam·Dangerous |
| C5 | 10-digit personal number sender + any promo family match + link | Spam |
| C6 | brand name in text + non-official domain link | Spam·Dangerous |
| C7 | "enter/share PIN|OTP|CVV|password" + anything (nobody legitimate ever asks) | Spam·Dangerous |
| C8 | international sender + money/job/prize family | Spam·Dangerous |
| C9 | APK link + anything | Spam·Dangerous |
| C10 | ≥3 distinct promo matches, no protected match | Promotions minimum |

---

## 6. THE NEVER-DELETE GUARANTEE (hard requirement)

1. The filter **never deletes anything.** Spam, Promotions, Blocked, Review — all messages are stored exactly like Inbox messages, forever, until the **user** deletes them.
2. Every folder is fully browsable and included in global search (Spam/Blocked results appear under a separator).
3. So if an expected message is ever mis-filed, the user opens Spam/Blocked/Review, finds it, taps **"Not spam / Move to Inbox"** — the message moves, the sender gets a local trust boost, and (optionally, one tap) an allow-list entry is created so it never happens again.
4. **User deletions go to Trash, not oblivion.** When the user deletes a message (or thread), it is removed from the system Telephony provider (so it disappears from the phone normally) but a copy is retained in the app's own store, flagged as trash with a purge date **60 days** out. A **Trash folder** (under Settings or the folder list) lets the user browse and restore trashed messages any time within the window; after 60 days a WorkManager job purges them permanently. "Delete forever" from within Trash is available for immediate permanent deletion. Backups carry trash items *as trash* (§8.3) so a restore reproduces the same state.
5. Optional, **off by default**, clearly labeled: "Auto-clean Spam older than 90 days." Even when enabled, show a confirmation and never touch Review/Blocked (cleaned Spam goes through Trash like any user deletion).
6. The ONLY other auto-deletes in the app are: (a) the user-enabled OTP cleanup (§8), which touches only OTP-labeled messages in the user's own Inbox — never filtered folders (OTP cleanup bypasses Trash; expired OTPs have no recovery value), and (b) the 60-day Trash purge above.

---

## 7. INSTRUCTIONS TO CLAUDE CODE — expanding the library (required, not optional)

The lists in §5 are the **seed**, not the ceiling. Nothing may be left behind. Concretely:

1. **Expand every family** during implementation: brainstorm and add every additional common wording, synonym, misspelling, and Hinglish/vernacular variant you can produce for each family (target: at least double the seed patterns). Add families the seed missed (e.g., charity scams, romance/gift scams, fake FASTag/challan notices, fake app-download prompts, wrong-number "hi dear" openers from international numbers, screen-share app mentions like AnyDesk/TeamViewer in banking contexts — that last one is weight 12).
2. **Prefer phrase-format regexes over single words.** Single words are weak evidence (low weight); skeleton phrases with `.{0,N}` gaps survive rewording and deserve high weights.
3. **Every pattern ships with tests**: ≥1 real-style positive example and ≥1 near-miss negative example (e.g., positive: "You won ₹5 lakh, claim now"; negative from a friend: "we won the match!"). CI fails if any pattern lacks tests.
4. **Build the labeled corpus** (≥500 messages across all families + genuine personal/transactional messages, including Hinglish and obfuscated samples) and enforce in CI: **zero Protected-family messages filtered; ≥95% of corpus spam/scam caught.**
5. **patterns.json is versioned and updatable**: bundled with the app, replaceable via app updates, plus an "Import pattern pack" option (local file) so the library can grow without waiting on store review. User rules always take precedence over library patterns.
6. **Every verdict must be explainable**: store matched pattern IDs; the "Why filtered?" screen shows human-readable descriptions ("Matched: lottery-amount pattern, shortened link, unknown international sender").
7. **False-positive discipline:** whenever a combination could plausibly match a genuine message, the correct verdict is **Review**, not Spam. Bias: genuine-looking → Inbox/Review; only clear pattern evidence → Spam.

---

## 8. Feature Set — full modern-app parity

### 8.1 Core (identical expectations to Google Messages)
Default-SMS role; send/receive SMS/MMS/RCS (typing indicators, read receipts, reactions, high-res media where available, SMS fallback); conversation list with pinning, archiving, swipe actions; composer with camera/gallery/files/GIFs/emoji/voice notes/location/contacts; group messaging; contact integration; full-text search with filters; per-conversation mute/tones/wallpaper; rich notifications with inline reply & mark-as-read; delivery/read status; resend on failure; forward/copy/share/star/multi-select/delete; media gallery per chat; dual-SIM (per-chat SIM choice, indicators); carrier spam reporting; drafts; links preview.

### 8.2 Modern extras (from the best of today's messaging apps)
- **Auto-delete OTPs after 24 hours** (opt-in; only OTP-labeled Inbox messages; never touches filtered folders).
- **One-tap OTP copy** chip on OTP messages + autofill support.
- **Scheduled send** and **snooze/remind-me-about-this-message**.
- **Message organization:** auto-labels (OTP / Bank / Delivery / Travel / Bill) with filter chips in search.
- **Text formatting** (bold/italic/strikethrough where RCS supports), link previews.
- **Chat customization:** per-chat wallpapers, bubble colors, dark/AMOLED themes, Material You dynamic color, app icon variants.
- **Android platform features:** conversation bubbles (Android 11+), conversation shortcuts, direct share, home-screen widgets (unread + recent chats + "protection stats" widget showing spam blocked this week), notification channels per category.
- **Privacy & security:** app lock (biometric/PIN), locked/private conversations, hide previews, block & report.
- **Backup/restore:** local export/import + optional Google Drive backup (messages, settings, rules, sender reputations, pattern-pack version).
- **Quality of life:** undo for destructive actions (snackbar), mark-all-read, unread filter, starred view, archive, **Trash folder with 60-day retention and restore (§6.4)**, swipe-action customization, text size control, accessibility (TalkBack, contrast, font scaling).
- **Protection dashboard:** a stats screen — messages filtered this week/month by family, top blocked senders, with satisfying counters ("1,204 spam messages silenced").

### 8.3 Google Drive Backup & Restore (WhatsApp-style)

**Identity & storage:** Google Sign-In + Google Drive **appDataFolder** (hidden, app-private area of the user's own Drive). No app servers; the Google account IS the identity. On any device, signing in with the same Gmail lets the app discover that account's backup and offer restore. Scopes: `drive.appdata` only — never general Drive access.

**What is backed up:** all messages from Inbox, Transactions, Promotions, Review, Blocked, Archived; Trash items *flagged as trash with their purge dates*; category/label assignments, matched-pattern verdicts, user rules, allow/block lists, sender reputations, settings, and pattern-pack version. **MMS media is a separate toggle** ("Include photos/videos"), OFF by default — text always, media opt-in.

**Spam backup control (three modes, in Backup settings):**
- **Back up spam: On** — the whole Spam folder is included in snapshots (default: On, honoring the never-delete philosophy).
- **Back up spam: Off** — Spam folder excluded entirely; backups get smaller and junk doesn't follow the user to a new phone.
- **Back up spam: Custom** — the user hand-picks which spam messages/threads are worth keeping. Opens a multi-select picker over the Spam folder with **search-first UX (§8.5)**: a search bar with keyword chips at the top instead of forcing endless scrolling, checkboxes per message/thread, "select all results" for the current keyword filter, and a running count ("23 of 1,204 spam messages will be backed up"). Selections persist; newly arriving spam is NOT auto-included in Custom mode (only what the user explicitly picked).

**Deletion semantics:** backup mirrors the phone's state including Trash. A message the user deleted appears in the backup as a trash item until its 60-day purge, after which it leaves both. Each new backup **replaces** the previous snapshot (keep the last 2 snapshots in Drive for corruption safety; prune older).

**Encryption (mandatory, passkey-first):**
- The backup blob is always encrypted on-device before upload; Google only ever stores ciphertext.
- **Primary method — passkey-wrapped key:** generate a random 256-bit AES data key; wrap it using a **passkey** via the Credential Manager `prf` extension where supported (Android 14+/GMS with PRF-capable authenticator). Restore on a new device = authenticate with the same passkey (synced through the user's Google Password Manager) → unwrap → decrypt. No password to remember; phishing-resistant; survives device loss because passkeys sync with the Google account.
- **Fallback — backup password:** if the device/authenticator lacks PRF support, or as a user-selectable recovery addition, derive a wrapping key from a user-chosen password (Argon2id or PBKDF2-HMAC-SHA256, ≥600k iterations, random salt). The SAME data key may be wrapped by BOTH passkey and password — either unlocks the backup. Encourage setting the password fallback so a lost passkey ≠ lost backup.
- Honest warning in UI: losing ALL unlock methods makes the backup permanently unrecoverable — that is the security guarantee, state it plainly at setup.
- Format: AES-256-GCM, per-backup random nonce, versioned envelope header `{formatVersion, wrappedKeys[], salt, createdAt, checkpointAt, deviceModel, messageCount}` (header is the only plaintext metadata).

**Automatic backup schedule — the checkpoint model (deterministic by design):**
- Frequency menu, WhatsApp-style: **Automatic backups → Daily / Weekly / Monthly / Only when I tap "Back up" / Cancel.** Plus an always-available manual **"Back up now"** button (manual backups snapshot the current moment, ignoring the checkpoint).
- **Checkpoint rule:** automatic backups always contain messages **up to the most recent 6:00 AM (device-local) checkpoint** — never beyond — regardless of when the upload physically happens. Example: message *a* arrives 5:48 → today's 6am backup includes *a*. Internet is down until 8:36 → when connectivity returns, the upload still contains only messages till 6:00. Message *b* arrived 7:49 → *b* waits for tomorrow's checkpoint. This makes every automatic backup a clean, predictable snapshot even though Android's WorkManager cannot guarantee exact execution times.
- **Missed-checkpoint handling:** if the device stays offline across one or more checkpoints, upload ONE snapshot at the newest passed checkpoint (do not queue multiple). Weekly/Monthly use the same rule at their cadence (6am on the chosen day/date).
- Implementation: WorkManager periodic work with `NETWORK_TYPE_UNMETERED` default constraint + persisted `lastCheckpointCovered`; retry with backoff until success; a snapshot is cut by querying `date <= checkpointAt`.
- **Wi-Fi only by default**, with "Also use mobile data" toggle. Show last-backup status line (time, size, message count) exactly like WhatsApp.

**Restore flow (fresh install):**
1. First-open popup → set as default SMS app (§8.4 — restore REQUIRES the default role; without it Android forbids writing to the SMS provider).
2. "Restore from backup?" step in onboarding: Google Sign-In → look up appDataFolder → if found, show card: "Backup found — last backup: <date> 6:00 AM · <n> messages · <size>. Restore?"
3. Unlock via passkey (or password fallback) → download → decrypt → **merge** into the provider + Room index (skip messages already present by (address, timestamp, body-hash); never overwrite; restore original timestamps; restore folder/label assignments and trash flags).
4. Then normal backfill classification runs only for messages NOT covered by restored verdicts.
5. Settings/rules restore is offered as a separate checkbox ("Also restore rules & settings").
- Restore is **copy, not sync**: state plainly in UI that two devices using the same account do not stay live-synced; the backup belongs to whichever device backed up last (single-active-device model; a different device performing a backup takes over the snapshot slot with a confirmation warning).

### 8.4 First-open default-app gate (Google Messages behavior)
- On very first open: immediately show the system default-SMS-app prompt (RoleManager).
- **If denied:** the app stays usable as a viewer shell only — the conversation area shows an empty state with a single **"Set as default SMS app"** card/button (mirroring Google Messages' behavior) that re-triggers the role request. No messaging features, no backup, no protection until granted; re-prompt contextually (banner) rather than nagging with popups.
- If granted: proceed to onboarding → restore offer (§8.3) → backfill.

### 8.5 Search — everywhere, deliberate, multi-keyword, highlighted (hard requirement)

Search is the primary navigation tool of this app. Anywhere the user could face a long list, provide a search bar — global search, per-folder search (Spam, Promotions, Blocked, Review, Trash, Archived), in-conversation search, the Custom-spam-backup picker (§8.3), contact picker, and rules/allow/block list management. Unlimited scrolling is the fallback, never the primary way to find something.

**1) Search trigger — standard incremental, instant (WhatsApp/Telegram-class latency):**
- Search-as-you-type, exactly like the major messaging apps: results update live as the user types, backed by the FTS index so every query returns in tens of milliseconds even at 100k+ messages. Latency must feel identical to WhatsApp/Telegram search — this is a hard requirement.
- Junk-fragment guard: the first query fires only after **3 typed characters** (single letters like `a`/`ap` produce no query), with a short debounce (~150–250 ms) so intermediate keystrokes don't waste queries. Below the threshold, show recent searches + suggested keyword chips instead of results.
- Prefix matching applies to the word being typed (`applicat` already matches "application") so results appear before the word is even finished.

**2) Multi-keyword search with unlimited keyword chips (the disambiguation tool):**
- Problem this solves: searching `application number` may match 100 messages (driving licence, NEET, JEE, SSC, job portals…). The user must be able to narrow without scrolling.
- The user can add any number of keywords: typing more words and/or tapping suggested chips. Each keyword becomes a removable **chip** in the search bar (e.g., `[application] [neet] [jee] [upsc]`). Type-and-select or tap-and-select, both work — "infinite keywords."
- **Suggested chips are generated from the current result set:** the app extracts frequent distinctive words from the matched messages (e.g., "driving licence", "learner", "NEET", "JEE", "SSC", "registration") and offers them as one-tap chips.
- **Semantics — all keywords are equal; match-any with relevance ranking:** a message appears if it contains **any** of the keywords; results are **ranked by how many keywords they match** (more matches → higher). Example: chips `[application] [neet] [jee] [upsc]` → a NEET application message (matches 2) ranks above a generic message containing only "application" (matches 1); messages matching none are excluded. No keyword is mandatory; no boolean logic is ever shown to the user — it's simply "show messages containing these words, best matches first." Removing a chip instantly re-filters and re-ranks. Ties broken by recency. Maps to FTS `OR` queries with match-count scoring.
- Chips combine with the existing filters (folder, label like OTP/Bank/Delivery, date, sender) — e.g., `[application] [neet]` + label:OTP.
- Persist the user's frequently used keyword combos as tappable "saved searches" (e.g., a "NEET application" saved chip-set).

**3) Match highlighting (Google Messages behavior):**
- Every matched word/phrase is **highlighted** (accent-colored span, high-contrast in dark mode) in the results list — in the message-body snippet, the sender/contact name, and, when a result is opened, in the conversation view itself (auto-scroll to the matched message with the term highlighted in the bubble; next/previous match arrows for in-conversation search).
- With multiple chips, ALL chip terms are highlighted in each result (same highlight color; the snippet is windowed around the first match with ellipses).
- Snippets prefer the sentence/line containing the match rather than truncating mid-word.

**Implementation guidance:** back this with SQLite **FTS4/FTS5** (Room `@Fts4` entity mirroring the message table) — indexed full-text search stays instant at 100k+ messages, gives `snippet()`/`offsets()` for highlight spans, and multi-keyword match-any queries with match-count ranking are native FTS (`application OR neet OR jee`, ranked by hits). Index the normalized text (§3 Stage 0) so obfuscated spam is searchable by its real words too. The suggested-chip extractor runs over the FTS result set with a stop-word list (skip "the", "your", "is"…) and surfaces the top distinctive terms by frequency.

---

## 9. UI — best possible (hard requirement, not decoration)

- **Design system:** Material 3 Expressive, dynamic color (Material You), large-title collapsing app bars, tonal surfaces. Light + dark + pure-black AMOLED.
- **Motion:** spring-based animations throughout — shared-element transition from conversation list into chat, animated folder switches, smooth 120 Hz scrolling with zero jank on mid-range devices. Physics-based over-scroll.
- **Layout:** clean conversation list with avatar-first rows; folder chips (Inbox · Transactions · Promotions · Spam · Review) directly under the search bar — the calm Inbox is the hero. Badge counts, not noise.
- **Chat screen:** modern bubbles with tails, grouped consecutive messages, date pills, floating scroll-to-bottom, in-chat search, gorgeous media grid.
- **Delight details:** polished empty states with subtle illustrations ("No spam today — enjoy the silence"), satisfying "message moved" animations, haptics on key actions, category chips with distinct hues (fraud = red, promo = amber, protected = green).
- **Craft bar:** typography with clear hierarchy (one display face for headers is fine, high-legibility body face), 4dp spacing grid, WCAG AA contrast everywhere. If a screen looks like a default template, it is not done.
- **Onboarding:** 3 screens max — beautiful, animated: what it does → set as default → done. Backfill progress shown as a live counter classifying existing history.

---

## 10. Technical Architecture

- Kotlin + Jetpack Compose (Material 3), MVVM, modules: `:app`, `:core-messaging`, `:protection-engine`, `:design-system`.
- Room DB (message index, categories, labels, matched-pattern IDs, rules, sender reputation) over the system Telephony provider (source of truth for content).
- `:protection-engine` is pure Kotlin (no Android deps) → unit-testable on JVM: `Normalizer`, `SenderAnalyzer`, `PatternMatcher` (compiled-regex cache), `ComboRules`, `ScoringEngine`, `Verdict`. Loads `patterns.json` at startup; hot-reloads on pattern-pack import.
- Default-SMS role: the 4 mandatory components (SMS_DELIVER receiver, WAP_PUSH_DELIVER receiver, ACTION_SENDTO activity, ACTION_RESPOND_VIA_MESSAGE service) + RoleManager flow, graceful read-only mode if not default.
- Permissions: RECEIVE_SMS, READ_SMS, SEND_SMS, RECEIVE_MMS, RECEIVE_WAP_PUSH, READ_CONTACTS, POST_NOTIFICATIONS, media as needed — requested contextually.
- WorkManager: first-run backfill (classify existing history, newest-first, resumable), backups, optional user-enabled cleanups. Correct behavior under Doze (receiver → expedited work).
- Edge cases: flash/class-0 SMS, silent data SMS, SIM swap, default-app switch-away (clean teardown, index stays readable), RCS variability by carrier.
- Play Store: SMS permissions declaration + privacy policy required at submission (app qualifies — core function is SMS). No message content ever leaves the device (easy claim now: there is no network classification at all).

---

## 11. Success Metrics

1. **Zero Protected messages filtered** (OTP/bank/delivery) — CI-enforced on the corpus; the non-negotiable metric.
2. **≥95% of corpus spam/scam/fraud caught** at default sensitivity.
3. **Zero message loss** — every received message stored and reachable.
4. Classification median **< 50 ms**; cold start < 1.5 s; 120 Hz-smooth scrolling on mid-range hardware.
5. Review folder receives **< 10%** of traffic (the engine should be decisive).
6. Notification volume drops to essentially only genuine messages.

---

## 12. Milestones (each independently runnable)

- **M1 — Core messenger:** default-SMS role, send/receive SMS/MMS, list + chat UI (already at §9 quality bar), notifications, contacts, search.
- **M2 — Protection engine:** normalizer, sender analyzer, full seed pattern library + combos + scoring, folders, per-category channels, "Why filtered?", reclassify actions, backfill, never-delete guarantee.
- **M3 — Library expansion + hardening:** Claude Code's §7 expansion pass, labeled corpus + CI gates, sensitivity slider, rules/allow/block UI, pattern-pack import.
- **M4 — Modern extras:** OTP auto-delete + copy chip, scheduled send, labels/chips, widgets, bubbles, **Trash with 60-day retention (§6.4)**, **Drive backup & restore with checkpoint scheduling, passkey/password encryption, and spam-backup modes (§8.3)**, **first-open default-app gate (§8.4)**, **FTS-backed search everywhere: whole-word trigger, keyword chips, highlighting (§8.5)**, protection dashboard, app lock.
- **M5 — Polish & parity:** RCS features, per-chat customization, animations pass, accessibility pass, dual-SIM refinement, Play submission prep.

---

## 13. Testing

- JVM unit tests: every pattern (positive + negative), every combo rule, normalizer (obfuscation corpus: leet, zero-width, homoglyph, spaced letters), scoring thresholds, the Stage-2 fake-OTP-phishing exception.
- **Named non-negotiable test:** `OTP_and_bank_alerts_can_never_be_filtered`.
- Corpus regression suite in CI (§7.4 gates).
- Instrumented: default-SMS flows, receive→classify→notify path, backfill, Doze delivery.
- Manual matrix: dual-SIM, RCS on/off, default-app switch, real spam samples from a live SIM.

---

## 14. Guardrails Summary

1. **Correct, complete SMS app first** — filtering means nothing if messaging breaks.
2. **Never lose, never delete, never bury:** no message is ever dropped; the filter never deletes; OTP/bank/delivery can never be filtered.
3. **Deterministic and explainable:** no AI, no network, no black box — every verdict shows its matched patterns.
4. **When unsure → Review, not Spam.** The user is the final judge and every decision is reversible in two taps.
5. **The pattern library is a living asset:** versioned, tested, expandable — Claude Code must ship it broad (§7) and make growing it trivial.
6. **The Inbox stays calm and the UI stays beautiful** — that is the entire promise.
