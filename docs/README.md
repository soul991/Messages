# Documentation index

Four documents live at the repo root because tooling, CI, and session prompts
reference those paths directly — do not move them:

| File | What it is |
|---|---|
| [`../README.md`](../README.md) | Trust promises, honest limitations, build commands, module map |
| [`../PROGRESS.md`](../PROGRESS.md) | **Authoritative** phase-by-phase state and next steps. Single source of truth when resuming after a context clear |
| [`../PRD_Messages.md`](../PRD_Messages.md) | The spec (v2) — scope and non-negotiable guardrails |
| [`../PROJECT_HANDOFF.md`](../PROJECT_HANDOFF.md) | Orientation layer above PROGRESS.md — read this first in a new session |

Everything else is filed below.

## `ops/` — operational runbooks

| File | What it is |
|---|---|
| [`ops/RELEASE_SIGNING.md`](ops/RELEASE_SIGNING.md) | Keystore location, password handling, certificate fingerprint, data-preserving reinstall path |
| [`ops/DRIVE_BACKUP_SETUP.md`](ops/DRIVE_BACKUP_SETUP.md) | One-time Google Cloud OAuth registration for `drive.appdata` backup |
| [`ops/DISTRIBUTION_CHECKLIST.md`](ops/DISTRIBUTION_CHECKLIST.md) | Pre-distribution checklist: clean checkout, gitignore verification, artifact verification (R-32 compliance) |
| [`ops/SUPPLY_CHAIN.md`](ops/SUPPLY_CHAIN.md) | What the build pins — wrapper checksum, dependency verification metadata, action SHAs — and how to change it safely (R-28) |
| [`ops/DESIGN_REFERENCE_PROVENANCE.md`](ops/DESIGN_REFERENCE_PROVENANCE.md) | Provenance, licensing position and attribution for the gitignored `design-refs/` material (R-32) |
| [`ops/privacy_policy.md`](ops/privacy_policy.md) | Published privacy policy (required for any Play Store distribution) |

## `design/` — UI design plans

| File | What it is |
|---|---|
| [`design/PHASE5_DESIGN_PLAN.md`](design/PHASE5_DESIGN_PLAN.md) | Approved Phase 5 UI overhaul plan, screen by screen |

## `research/` — external research feeding design decisions

| File | What it is |
|---|---|
| [`research/DESIGN_REFS_NOTES.md`](research/DESIGN_REFS_NOTES.md) | Measured WhatsApp/Telegram geometry and colour values, plus where the source archives live |
| [`research/TRUECALLER_ANALYSIS.md`](research/TRUECALLER_ANALYSIS.md) | Truecaller taxonomy/UX teardown and the three recommendation lists (A/B/C) |

## `perf/` — performance evidence

Raw `gfxinfo` output kept deliberately as the evidence behind the performance
claims in PROGRESS.md and README.md. Not build artifacts — do not clean.

`results-baseline.txt` · `results-final.txt` · `results-release-run1..3.txt`

## `reviews/` — historical review and fix records

Point-in-time records. Useful as history; **not** live TODO lists — several
items in them have since been fixed in code.

| File | What it is |
|---|---|
| [`reviews/REVIEW_FINDINGS.md`](reviews/REVIEW_FINDINGS.md) | 2026-07-18 review (branch `codex/s`): findings F-01… with severities |
| [`reviews/FIXES_APPLIED.md`](reviews/FIXES_APPLIED.md) | Companion to the above — what was implemented and how it was verified |
| [`reviews/issues_found.md`](reviews/issues_found.md) | Separate `main`-branch review. Items 1 and 3 are fixed; 2 and 4 remain advisory |
| [`reviews/fix_conversation_summary_bug.md`](reviews/fix_conversation_summary_bug.md) | Single-bug writeup, superseded by the code |

## Not in git

`design-refs/` (~180MB of WhatsApp/Telegram reference material, including the
original community zips under `design-refs/archives/`) is gitignored and local
only. `research/DESIGN_REFS_NOTES.md` is the committed distillation of it.
