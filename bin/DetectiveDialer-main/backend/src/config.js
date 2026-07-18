import 'dotenv/config';

function int(value, fallback) {
  const n = parseInt(value, 10);
  return Number.isFinite(n) ? n : fallback;
}

export const config = {
  port: int(process.env.PORT, 3000),
  publicBaseUrl: (process.env.PUBLIC_BASE_URL || `http://localhost:${process.env.PORT || 3000}`).replace(/\/$/, ''),

  // Shared secret for the app. When set, /screen and /api/* require a matching
  // X-Api-Key header. When empty, auth is disabled (open, as before).
  apiKey: process.env.API_KEY || '',

  gemini: {
    apiKey: process.env.GEMINI_API_KEY || '',
    // Evergreen alias so a model retirement can't silently kill classification
    // again (gemini-1.5-flash was retired and 404'd every request).
    model: process.env.GEMINI_MODEL || 'gemini-flash-lite-latest',
    // Screening must answer within the phone's ~5s CallScreeningService budget;
    // if Gemini is slower than this we fall back to heuristics.
    timeoutMs: int(process.env.GEMINI_TIMEOUT_MS, 2500),
    get enabled() {
      return Boolean(this.apiKey);
    },
  },

  firebase: {
    serviceAccountPath: process.env.FIREBASE_SERVICE_ACCOUNT_PATH || '',
    serviceAccountJson: process.env.FIREBASE_SERVICE_ACCOUNT_JSON || '',
    defaultDeviceToken: process.env.FCM_DEFAULT_DEVICE_TOKEN || '',
    get enabled() {
      return Boolean(this.serviceAccountPath || this.serviceAccountJson);
    },
  },

  owner: {
    name: process.env.OWNER_NAME || 'the owner',
  },

  screening: {
    spamAutoblockThreshold: int(process.env.SPAM_AUTOBLOCK_THRESHOLD, 3),
  },

  // Keep-warm self-ping. Railway "Serverless" sleeps a service after ~10 min
  // with no OUTBOUND traffic (inbound requests do NOT reset the timer), and the
  // first request after sleep takes 15–22 s — far past the phone's ~5 s call
  // screening budget, so real verdicts miss the ring. An interval self-fetch of
  // our own public URL is outbound traffic, so it keeps the dyno awake and the
  // /screen path warm. Disabled automatically when no PUBLIC_BASE_URL is a real
  // https URL (e.g. local dev), and can be forced off with KEEP_WARM=off.
  keepWarm: {
    // Default 5 min — comfortably under Railway's ~10 min sleep threshold.
    intervalMs: int(process.env.KEEP_WARM_INTERVAL_MS, 5 * 60 * 1000),
    get enabled() {
      if ((process.env.KEEP_WARM || '').toLowerCase() === 'off') return false;
      const url = config.publicBaseUrl;
      return url.startsWith('https://') && !url.includes('localhost');
    },
  },
};

export default config;
