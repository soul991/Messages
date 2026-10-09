# Messages app product and UX review

**Date:** 2026-10-06  
**Scope:** Android source in `01_active_codebase/messages/`, plus a limited visual look at the Home screen. A device screenshot and accessibility dump unexpectedly contained SMS previews and entered this session's tool output; they cannot be described as on-device-only. The debug app's local data was cleared, its SMS permissions were revoked, it was force-stopped, and the regular `com.messages.app` SMS role was restored. The latest debug APK remains installed. No message text or identifying details are reproduced in this report.

## Ratings

| Area | Score | Assessment |
|---|---:|---|
| Overall product and UX | 8.2/10 | Thoughtful, privacy-led messaging product with useful folder navigation, search, swipe actions, recovery paths, and a clear local-first story. The visible Home screen has a coherent hierarchy and scan-friendly conversation rows. This is a provisional rating from source plus one screen, not a full-flow evaluation. |
| Visual design system | 8.3/10 | Existing violet identity, dark and light surfaces, user-selected themes, adaptive icon, and shared components form a coherent baseline. The Home screen looks clean and readable; chat flows, large font scales, and broader device sizes still need review. |
| Core messaging features | 8.4/10 | SMS/MMS intake and sending, dual-SIM paths, drafts, scheduling/outbox, archive/trash, search, contacts, notifications, widgets, and backup are represented in dedicated flows and supporting modules. RCS is explicitly unsupported. |
| Protection and AI transparency | 7.5/10 | Local rules, explanations, protected-message handling, and a local scorer are separated. The current dirty checkout says advanced scoring is opt-in while evaluation is incomplete; avoid treating model quality as established until the held-out false-positive gate is documented. |
| Accessibility and localization | 7.6/10 | Reduced-motion handling and screen-reader progress labels are improved, and visible chat labels/date/time strings now use resources and device locale preferences. Full translated resource sets and TalkBack, font-scale, switch-access, and contrast checks remain incomplete. |
| Privacy and security architecture | 8.8/10 | Strong boundaries include explicit network policy, guarded link fetching, disabled platform backup, encrypted Drive snapshots, Keystore-backed locked content, and no server-side classification path. Update downloads are now limited to HTTPS GitHub release hosts, validated redirects, public DNS and a byte cap. This is not a penetration test or release certification. |
| Release readiness | 7.4/10 | The privacy draft describes optional network features and update download behavior is bounded. The publisher, contact, and effective-date fields still require real values before publication. |

## What is working well

- The home screen gives Inbox and the other message folders a clear, one-handed navigation model; conversation rows combine sender, preview, time, mute, unread, and pin state without stacking competing indicators.
- Search, empty/loading/failure states, undo snackbars, multi-select actions, and the outbox reduce the risk that organization features feel destructive or opaque.
- Theme roles separate the app's accent identity from fraud, promotion, transaction, and review categories.
- Classification and link preview boundaries are explicit. `SafeHttp` restricts message-derived fetches to HTTPS on port 443, checks DNS results, disables cookies and automatic redirects, bounds response sizes, and applies timeouts.
- Drive backup uses app-private Drive scope and encrypted snapshots. Locked-space content is encrypted at rest and guarded by separate authentication; platform backup is disabled.

## Findings and priorities

1. **Publishability: complete the privacy policy before distribution.** `docs/ops/privacy_policy.md` still has bracketed placeholders for the operator, contact, and effective date. Its optional network disclosures now include Drive, link preview, carrier reporting, and GitHub update checks.
2. **Keep update downloads bounded and trusted.** The APK fetch now accepts only HTTPS release hosts, validates each redirect, uses public-address DNS validation and stops after 150 MiB. Android's package installer still performs final package/signature checks when the user installs.
3. **Keep AI efficacy claims bounded.** Continue to describe scoring as on-device and optional. Do not imply that it improves scam detection across languages until the current holdout evaluation and false-positive threshold are complete and reviewed.
4. **Keep message text out of diagnostics.** Classification, backfill, and telephony receiver failure paths now log the operation and exception type without the Throwable, message-row ID, or checkpoint. Continue reviewing the remaining logging paths before release; exception text and stack traces can contain message-derived data.
5. **Complete the visual/accessibility review safely.** The Home screen was viewed once, but message-bearing UI was inadvertently included in tool output. Do not capture message screens for follow-up review. Use synthetic fixtures or a message-free debug state to review chat flows, TalkBack, 1.3–2.0 font scale, dark themes, keyboard navigation, and permission-denied onboarding.
6. **Close build-tool compatibility warnings.** The debug build succeeds, but AGP 8.5.2 warns that it was tested only through compileSdk 34 while this project uses compileSdk 35. Resolve that compatibility warning in a dependency/toolchain update, and track the deprecated Android API warnings separately.

## Current source improvements

The rejected Morrow rebrand has been removed from the app source; the existing Messages identity and violet theme are restored. Current UX work makes the home toolbar compact, surfaces the existing Blocked folder from the home menu, follows Android's animation setting through shared motion, skeletons, onboarding transitions and pager movement, adds screen-reader progress semantics, localizes chat attachment/date/status/time labels, and follows the device's 12/24-hour clock preference. Existing user changes to scoring, model data, training files, update handling, and privacy text remain in place.

## Evidence limits

The latest source successfully builds `Messages-debug.apk`; it is installed on the device but force-stopped. `com.messages.app` is the SMS role holder and the debug package's SMS permissions are denied. No tests were run. The screenshot and UI dump entered this session's tool output; deleting their local copies does not undo that processing. Security observations are architectural/source findings and should not be presented as proof of Play policy compliance or penetration-test coverage. The existing working tree contains user changes; the AI scorer, model data, training files, privacy draft, and other pre-existing edits were treated as in-progress work.
