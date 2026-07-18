# 📞 Detective Dialer

A personal AI call-screening system: the moment an unknown number rings, the
Android app asks a lightweight cloud endpoint to classify it with Gemini, then
acts on the device — **SPAM is rejected silently, REJECT rings silently, ALLOW
rings normally** — while your contacts always ring through. No call forwarding,
no cloud voice agent, no Twilio.

```
Unknown call ─▶ Android CallScreeningService (3.5s budget, fails open to ALLOW)
            ─▶ POST /screen ─▶ Node backend (Gemini text classifier)
            ─▶ { ALLOW | REJECT | SPAM } ─▶ SPAM rejected · REJECT silenced · rest ring
                                          └▶ FCM push → notification + history
```

Built for the iQOO Neo 9 Pro but works on any Android 10+ phone.

> **📄 [`PROJECT.md`](PROJECT.md) is the single source of truth** — goal, architecture, current
> status, known issues + fixes, the staged roadmap, and a running changelog. Any human or AI
> continuing this project should read it first. (It replaces the old scattered planning docs.)

## Repository layout

| Path | What |
|---|---|
| [`backend/`](backend/) | Node.js + Express — `POST /screen` (Gemini classification), FCM push, app API. |
| [`android/`](android/) | Kotlin app — CallScreeningService, Room, FCM, Compose UI. |

## Status

- ✅ **Backend** — complete and tested (`cd backend && npm test`, 31 tests).
  Runs with or without API keys (graceful heuristic/log-only fallback).
- ✅ **Android** — full source builds a debug APK (JDK 17 + Android SDK required).
- 🟢 **Phase 2 in progress** — evolving into a full default-dialer AI phone app.

See [`PROJECT.md`](PROJECT.md) for detailed status, the roadmap, and known issues.

## Getting started

1. **Backend:** [`backend/README.md`](backend/README.md) — local run + Railway deploy.
2. **Android:** [`android/README.md`](android/README.md) and
   [`android/BUILD_AND_SIDELOAD.md`](android/BUILD_AND_SIDELOAD.md) — build, sideload,
   permissions, and the end-to-end test checklist.

## One-time accounts needed

Google Gemini API (free tier), Railway/Render (host), Firebase (FCM). All have
free tiers.

## Security (optional)

Set `API_KEY` in the backend env and enter the same value as **API key** in the
app (onboarding or Settings) — the backend then requires an `X-Api-Key` header
on `/screen` and `/api/*`. Leave both empty to run open. The app registers its
FCM push token automatically via `POST /api/device`; no manual token copying.

## Privacy

Your contacts never leave the phone. Screening sends only the incoming number
(and caller-ID label, if any) to your backend. Push notifications carry summary
metadata only. You own every API key.
