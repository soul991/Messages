# Detective Dialer — Backend

Node.js + Express backend that powers the cloud half of the call screening app.
The Android app posts each unknown incoming number to `POST /screen`; a Gemini
text classifier decides **ALLOW / REJECT / SPAM**, the result is returned to the
phone (which rejects spam on-device), and a summary is pushed via Firebase Cloud
Messaging.

## Features

- **`POST /screen`** — classify an incoming caller by number + caller-ID label.
- **Gemini classifier** with a graceful fallback to deterministic heuristics when
  no API key is set, so the server is fully functional offline.
- **Decisions** — `ALLOW | REJECT | SPAM` with a reason and confidence.
- **Spam learning** — auto-blocks a number after `SPAM_AUTOBLOCK_THRESHOLD` spam
  classifications (default 3).
- **FCM push** — sends the decision + reason to the phone for the notification
  shade and call history (strictly data-only messages, sent *after* the screening
  response so the phone's ~5s budget is never wasted on the push).
- **Optional auth** — set `API_KEY` and every `/screen` and `/api/*` request must
  carry a matching `X-Api-Key` header (health endpoints stay open). Unset = open.
- **App JSON API** — call history, call detail, blocklist/allowlist management,
  and `POST /api/device` FCM-token registration.
- **Zero native deps** — uses a file-backed JSON store (`data/store.json`), so it
  deploys anywhere with no database to provision.

## Quick start (local)

```bash
cd backend
cp .env.example .env      # fill in keys (optional — works without them)
npm install
npm test                  # run the offline test suite
npm run dev               # starts on http://localhost:3000
```

Visit `http://localhost:3000/` — you should see a JSON health payload showing
whether Gemini and FCM are live or in fallback mode.

Without any API keys the server still runs: Gemini → heuristic classifier,
FCM → notifications are logged to the console.

## Environment variables

See `.env.example` for the full list. Highlights:

| Var | Purpose |
|---|---|
| `PUBLIC_BASE_URL` | Public HTTPS URL of this server |
| `API_KEY` | Optional shared secret — when set, `/screen` and `/api/*` require the `X-Api-Key` header |
| `GEMINI_API_KEY` | Google Gemini API key — enables the real AI classifier |
| `GEMINI_MODEL` | Default `gemini-flash-lite-latest` — an evergreen alias, chosen after the pinned `gemini-1.5-flash` was retired by Google and silently 404'd every classification |
| `GEMINI_TIMEOUT_MS` | Classification deadline (default 2500) — on timeout the heuristic fallback answers so the phone stays inside its ~5s screening budget |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Inline service-account JSON (easiest for Railway) |
| `FCM_DEFAULT_DEVICE_TOKEN` | Fallback FCM token — normally unnecessary, the app auto-registers via `POST /api/device` |
| `OWNER_NAME` | Framing for the screening prompt |
| `SPAM_AUTOBLOCK_THRESHOLD` | Auto-block after this many spam classifications |

## Endpoints

### Screening
- `POST /screen` — body `{ phoneNumber, callerId, timestamp }` →
  `{ decision: "ALLOW"|"REJECT"|"SPAM", reason, confidence }`. Also records the
  call and pushes an FCM notification.

  ```bash
  curl -X POST http://localhost:3000/screen \
    -H 'Content-Type: application/json' \
    -d '{"phoneNumber":"+919812345678","callerId":"Unknown","timestamp":1700000000000}'
  ```

### App API (JSON)
- `POST /api/device` — body `{ token }` — registers the phone's FCM token
  (called automatically by the app on startup and on token rotation).
- `GET  /api/calls` — list call records (newest first).
- `GET  /api/call/:id` — full call record.
- `GET  /api/blocklist` · `POST /api/blocklist` · `DELETE /api/blocklist/:number`
- `GET  /api/allowlist` · `POST /api/allowlist` · `DELETE /api/allowlist/:number`
- `GET  /` · `GET /healthz` — health checks (never require the API key).

When `API_KEY` is set, add `-H 'X-Api-Key: <key>'` to the curl examples above.

## Deploy to Railway (free)

1. Push this repo to GitHub.
2. On [railway.app](https://railway.app): **New Project → Deploy from GitHub repo**.
3. Set the **Root Directory** to `backend` (Settings → Service).
4. Railway auto-detects Node and runs `npm install` + `npm start`.
5. Add environment variables (Variables tab) — at minimum `GEMINI_API_KEY`,
   `FIREBASE_SERVICE_ACCOUNT_JSON`, and `OWNER_NAME` (plus `API_KEY` if you want
   auth; the FCM token registers itself once the app connects).
6. After the first deploy, copy the public domain Railway gives you and set it as
   the **Backend URL** in the Android app's onboarding/Settings.

> The JSON store lives in `backend/data/store.json`. On Railway's ephemeral
> filesystem this resets on redeploy — fine for personal use. Attach a Railway
> Volume mounted at `backend/data` if you want call history to persist across
> deploys.

### Deploy to Render (alternative)

- New **Web Service** → connect repo → Root Directory `backend` →
  Build `npm install` → Start `npm start`. Add the same env vars.

## FCM device token

The app fetches its FCM token on startup and registers it via `POST /api/device`
(also whenever Firebase rotates it) — nothing to copy. The Settings screen still
shows the token, and `FCM_DEFAULT_DEVICE_TOKEN` remains as a manual fallback.
Push messages are **data-only** (no `notification` block) so delivery is reliable
when the app is backgrounded; the app builds its own notification.

## Notes

- This backend is intentionally dependency-light. Classification is **text only**
  (number + caller-ID label) — there is no voice/IVR path and no Twilio.
- `GEMINI_MODEL` defaults to the evergreen `gemini-flash-lite-latest` alias so a
  model retirement can't silently kill classification again (this happened with
  the pinned `gemini-1.5-flash`). If the model ever 404s, the health endpoint
  shows it and the server falls back to heuristics meanwhile.
