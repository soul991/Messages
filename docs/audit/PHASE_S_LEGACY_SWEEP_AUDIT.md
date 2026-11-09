# Phase S: Complete Legacy Identity & Asset Sweep Audit

**App Version**: Messages 2.5.1  
**Target Branch**: `fix/2.5.1`  
**Date**: November 9, 2026  
**Auditor**: Antigravity Assistant & Independent QA Verifier  
**Verdict**: Complete & Verified (0 legacy identity remnants in active source)

---

## 1. Executive Summary

In accordance with Phase S requirements, an exhaustive repository-wide sweep was conducted across Kotlin source files, XML layouts, drawables, mipmaps, values, fonts, widgets, shortcuts, onboarding flows, documentation, and tests.

All remnants of the legacy multi-accent system, dynamic color overrides, violet/lavender/teal branding, and outdated documentation were cataloged, eliminated, or marked as superseded.

---

## 2. Sweep Findings & Actions Taken

| Domain / Category | File Path | Legacy Item Found | Action Taken |
| :--- | :--- | :--- | :--- |
| **Test Suite** | `app/src/test/kotlin/.../AccentSchemesContrastTest.kt` | Legacy class name `AccentSchemesContrastTest` reflecting eliminated multi-accent scheme | Renamed file to `FixedColorSchemesContrastTest.kt` and class to `FixedColorSchemesContrastTest`. Verified all 802 unit tests pass. |
| **Onboarding Illustration** | `app/src/main/kotlin/.../OnboardingScreen.kt` | Comment referencing legacy "violet shield" hero illustration | Updated docstring to "green brand badge flanked by three tilted message chips". Confirmed `IntroHero` uses `cs.primary` (`#30D158`) and `R.drawable.ic_stat_message`. |
| **Unused Drawables** | `app/src/main/res/drawable/ic_notif_review.xml` | Legacy review notification icon from pre-2.5.0 iterations | Deleted unused file. Confirmed zero references in active source. |
| **ChatStyle Presets** | `app/src/main/kotlin/.../ChatStyle.kt` | Per-chat bubble colors (`ocean`, `forest`, `sunset`, `berry`, etc.) and gradient wallpaper presets (`mist`, `dawn`, `meadow`, `sand`) | Confirmed eliminated. Outgoing bubbles strictly resolve to `LocalSentBubble` (`#30D158` / `#34C759`). **Photo-wallpaper feature (`photoFile()`) is preserved separately without removal.** |
| **Notification Icons & Colors** | `MessageNotifier.kt`, `AppUpdateManager.kt`, `Scheduling.kt` | Raw hex colors (`0xFFD32F2F`, `0xFFEF4444`) | Replaced with design tokens: `NOTIF_ACCENT_COLOR` (`#30D158`) and `NOTIF_FRAUD_COLOR` (`#FF453A`). |
| **Launcher & Splash Mipmaps** | `res/mipmap-*/ic_launcher*.png`, `res/drawable/ic_splash_logo.xml` | Legacy purple bubble launcher icon | Verified all densities render the redesigned green 'M' on black (`#000000`) or off-white (`#F7F7F4`). |
| **Font Assets** | `res/font/` | Check for legacy font files | Confirmed only `inter_variable.ttf` exists in `design-system/src/main/res/font/`. No outdated fonts present. |
| **Historical Documentation** | `docs/design/PHASE5_DESIGN_PLAN.md` | Legacy Phase 5 design exploration with 8-seed accent palette | Retained history and prepended `> [!NOTE] SUPERSEDED` notice. |
| **Historical Documentation** | `ANIMATION_AND_SMOOTHNESS_REPORT.md` | Mentions violet surface / lavender indicators | Retained history and prepended `> [!NOTE] SUPERSEDED` notice. |
| **Historical Documentation** | `docs/reviews/1.6.0-product-audit.md` | Mentions violet visual system | Retained history and prepended `> [!NOTE] SUPERSEDED` notice. |
| **Historical Documentation** | `docs/reviews/2026-10-06-product-review.md` | Mentions violet identity and user theme picker | Retained history and prepended `> [!NOTE] SUPERSEDED` notice. |
| **Historical Documentation** | `docs/reviews/google-messages-vs-our-messages-audit.md` | Mentions purple bubble | Retained history and prepended `> [!NOTE] SUPERSEDED` notice. |
| **Historical Documentation** | `docs/reviews/orchid/ORCHID_REVIEW_REPORT.md` | Orchid accent review | Retained history and prepended `> [!NOTE] SUPERSEDED` notice. |
| **Historical Documentation** | `docs/reviews/orchid/PR_BODY.md` | Orchid PR description | Retained history and prepended `> [!NOTE] SUPERSEDED` notice. |

---

## 3. Photo-Wallpaper Feature Status

Per explicit user instruction:
> *"ChatStyle per-chat bubble colors and gradient wallpaper presets (they contradict the fixed identity: remove them; list the photo-wallpaper feature separately and do NOT remove it without my decision);"*

- **Status**: **Preserved**.
- **Implementation**:
  - `ChatStyle.WALLPAPER_PHOTO = "photo"`
  - `ChatStyle.photoFile(ctx, threadId, space)` resolves to `filesDir/wallpapers/wp_${space}_${threadId}.jpg`.
  - In `ChatScreen.kt`, when `wallpaperId == ChatStyle.WALLPAPER_PHOTO`, `AsyncImage` renders the photo under a legibility surface scrim.
  - No code was removed or broken for this feature.

---

## 4. Final Source Grep Verification (Zero Matches)

A full case-insensitive regex scan across all production Kotlin (`.kt`) and XML (`.xml`) files in `app/`, `core-messaging/`, `protection-engine/`, and `design-system/` for `\bviolet\b`, `\bpurple\b`, `\blavender\b`, `\bteal\b`, `AccentScheme`, and `dynamicColor`:

```
Active Source Scan Scope:
- app/src/main/
- core-messaging/src/main/
- protection-engine/src/main/
- design-system/src/main/

Result: 0 matches found.
```
*(Note: "IndiGo" appears strictly as an airline name in `SenderAnalyzer.kt` and `FriendlySenderNames.kt` for airline ticket classification, not as a color).*

---

## 5. Test Suite Verification

- **Command**: `./gradlew.bat testDebugUnitTest`
- **Result**: `BUILD SUCCESSFUL`
  - `:protection-engine`: **179 passed**, 0 failed
  - `:core-messaging`: **293 passed**, 0 failed
  - `:app`: **330 passed**, 0 failed
  - **Total**: **802 passed**, 0 failed
