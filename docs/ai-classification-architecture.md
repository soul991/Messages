# Message classification and optional AI architecture

**Status:** analysis and architecture proposal. No third-party AI provider is
currently integrated. The optional cloud-provider flow described below is not
implemented by this document.

## Current behavior

Incoming SMS is handed from `SmsDeliverReceiver` to `MessageRepository`. The
repository deduplicates redeliveries, writes the message to Android's Telephony
provider, classifies it, and saves the app's indexed message. Classification
has two distinct layers:

1. `ProtectionEngine` applies local user rules, protected-message handling,
   sender context, pattern scoring, and evidence combinations. Its result has
   reason identifiers used by the explanation UI.
2. When Advanced Message Filtering is enabled, `MessageRepository` loads the
   bundled `NGramScorer` and calls `AiLayer.apply(..., enabled = true)`. The
   scorer uses character n-gram log-likelihood weights. Its permitted effect is
   Inbox to Review for eligible messages; it cannot assign Spam, Dangerous, or
   a fraud warning. The setting now defaults to off on a fresh install and
   remains an explicit user choice.

This means the current app has local statistical AI, despite older README and
onboarding copy saying “No AI.” It makes no classification network request.
Notifications are selected from the saved category by the app's notification
path; cloud classification is not involved.

A separate from-scratch character CNN has now been trained and evaluated on
the workspace SMS corpora. It improves sharply on the published test splits,
but performs poorly on the independent authored challenge set, so it remains
an unshipped research candidate. See
[`docs/ml/char-cnn-experiment.md`](ml/char-cnn-experiment.md) for metrics and
the deployment decision.

A recent Apache-2.0 Indian SMS-Shield Mini was also benchmarked locally as a
pretrained reference. Its broad alert threshold still over-flags held-out ham,
especially in Devanagari Hindi, while its conservative dangerous-only boundary
misses most scams. Its quantized weights are 1.28 GB and its authors describe
synthetic training data. It is not an app candidate; see
[`docs/ml/sms-shield-mini-external-baseline.md`](ml/sms-shield-mini-external-baseline.md).

## Recommended decision pipeline

Keep the deterministic rule engine as the always-available, offline decision
path and the bundled local model as an optional, offline second layer. Treat
cloud AI as a second opinion that can only add uncertainty, never remove
deterministic protections or independently block, delete, or declare a message
fraudulent.

```text
Incoming SMS
  -> deduplicate and persist in Telephony + local database
  -> deterministic rules and protected-message handling
  -> optional local n-gram score
  -> persist initial category and reason
  -> immediately apply category notification policy
  -> if cloud opt-in and eligible: enqueue a bounded background review
       -> provider returns strict structured result or abstains
       -> persist provider/model/provenance and review reason
       -> permit only Inbox -> Review; never lower protection
```

Cloud review must not run inside the SMS broadcast receiver or hold initial
message storage/notification on a network response. Messages remain available
and locally classified during offline periods, provider errors, rate limits,
invalid credentials, and timeouts. Since the initial alert may already have
been shown, cloud escalation is a later review; it cannot be described as a
pre-delivery scam shield. The UI must say this plainly. A user who needs a
pre-alert decision should use the local-only mode until a reliable, consented
on-device model can meet that need.

## Scope and safety rules for a cloud result

- Before creating a provider request, reload the persisted message and verify
  it is in the normal space. The current intake path resolves locked-space
  routing after classification, so a pre-routing boolean is not sufficient.
  Recheck immediately before transmission. Never send locked-space messages,
  OTPs, contacts, attachments, or full conversation history.
- Send only a single message body after a clear per-feature disclosure. Numeric
  identifiers and phone numbers may appear inside that body; redaction needs a
  defined policy and cannot be promised perfect. Any redaction must be described
  as best-effort, and residual disclosure risk must be explicit before consent.
- Exclude known protected transaction/OTP messages and user-allowed senders
  from cloud review. User block rules remain authoritative.
- Accept only a versioned JSON response with a closed category enum, bounded
  finite confidence, and bounded reason code. Ignore prose instructions,
  tool calls, URLs, and model-generated actions. Provider text is untrusted
  data, not executable policy.
- Use a conservative, calibrated threshold and an abstain band. The only
  automated category change allowed is Inbox to Review. A cloud score must
  never create Spam, Dangerous, a fraud banner, sender block, or deletion.
- Keep local reasons and cloud reasons separate in the database and “Why?” UI.
  Show provider and model, when the result was produced, and whether it was a
  local or cloud assessment. Do not imply provider confidence is a probability
  until that model has been calibrated on representative data.
- Make notifications category-driven and configurable. The current initial
  notification can display message text, so cloud consent must separately
  disclose that cloud review happens after that alert may already be shown.
  Honor the user's notification-preview/privacy setting in both the initial
  and any updated notification. Review alerts should be quiet/optional; later
  escalation should update or group an existing notification, not emit
  repeated alerts. If there is no preview control independent of app lock,
  introduce one before claiming message text is hidden by default.

## Provider and credential design

Offer a small, reviewed catalog of providers with fixed HTTPS API hosts and
documented request/retention terms. Do not accept arbitrary base URLs, proxy
URLs, custom headers, or model-supplied endpoints. This avoids credential
exfiltration and private-network/SSRF paths. Keep provider adapters separate
behind a narrow interface such as `MessageRiskProvider.analyze(MessageInput)`;
the provider adapter must not depend on UI or the deterministic engine.

The user first selects a provider and model, reviews that provider's data
handling and the exact data sent, then accepts a versioned, provider-specific
consent screen. Consent is required before enabling automatic cloud review and
must be revocable in settings. The disclosure must say that message contents
leave the phone and are processed under the selected provider's terms, privacy
policy, retention, and billing; the app publisher cannot promise the provider's
handling. Do not make users waive statutory rights or suggest a disclaimer
transfers legal responsibility by itself. Have the publisher complete the
privacy policy and obtain appropriate legal review before release.

Collect API keys in a masked field and store them only encrypted with an
Android Keystore-backed key. Exclude ciphertext and consent secrets from backup,
logs, analytics, crash reports, screenshots where practical, and exported
diagnostics. Never put a key in a URL, app resource, manifest, or source
control. Use TLS with normal certificate validation, strict timeouts, bounded
request/response sizes, no redirects, per-request authorization headers, and
redacted errors. Clear key and provider data on disable/removal. A direct
device-to-provider key is still exposed to the provider and to a compromised
device; explain that trade-off in settings.

## Model training and release gates

The bundled n-gram model is a lightweight local fallback, not a foundation
model. Do not train on users' live SMS or upload message samples. Train only on
consented, lawfully sourced and de-identified data, split by sender/campaign
and time to prevent near-duplicate leakage. Include legitimate financial,
delivery, government, personal, multilingual, code-mixed and short-message
examples. Measure false positives separately for OTPs and protected
transactions, per-language recall, calibration, abstention, and robustness to
obfuscation. Keep a frozen held-out set and a documented model hash, corpus
provenance, training script/version, threshold, and evaluation report for each
model release.

Before enabling a new model for automatic routing, require independent review
of the held-out set, an acceptably low false-positive rate, no OTP regressions,
and a staged shadow period. Release cloud review only after consent, encrypted
key storage, provider host pinning, deletion/disable semantics, timeout/offline
fallback, migration behavior, and privacy-policy updates are implemented and
reviewed. The current repository has no provider integration or API-key
storage; those are prerequisites, not implied by the proposal above.

## Current issues to resolve

- `AiScorer.kt` retains a default-off constant. `MessageRepository` can enable
  the layer when the user preference is true; the preference now defaults off
  on fresh installs because the source documents a 3.4% held-out
  false-positive rate and says the gate set is absent. Existing explicit user
  choices are preserved.
- The privacy policy has publisher/contact/effective-date placeholders. It
  cannot serve as a publishable policy until the owner fills them with accurate
  information. Adding cloud review requires a substantive policy update before
  release.
- Product copy previously claimed “No AI”; that did not describe the shipped
  local scorer. It has been corrected to distinguish local statistical scoring
  from cloud processing.
