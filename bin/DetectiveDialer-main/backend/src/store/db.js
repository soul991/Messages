import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import logger from '../utils/logger.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DATA_DIR = path.resolve(__dirname, '../../data');
const STORE_PATH = path.join(DATA_DIR, 'store.json');

const EMPTY = {
  calls: {}, // callSid -> call record
  blocklist: {}, // number -> { number, source, addedAt, reason }
  allowlist: {}, // number -> { number, label, addedAt }
  spamCounts: {}, // number -> integer
  device: {}, // { token, updatedAt } — the phone's FCM registration token
};

let state = null;

function ensureLoaded() {
  if (state) return;
  try {
    if (fs.existsSync(STORE_PATH)) {
      state = { ...EMPTY, ...JSON.parse(fs.readFileSync(STORE_PATH, 'utf8')) };
    } else {
      state = structuredClone(EMPTY);
    }
  } catch (err) {
    logger.error('Failed to read store, starting fresh:', err.message);
    state = structuredClone(EMPTY);
  }
}

function persist() {
  try {
    if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
    fs.writeFileSync(STORE_PATH, JSON.stringify(state, null, 2));
  } catch (err) {
    logger.error('Failed to persist store:', err.message);
  }
}

// Normalize a phone number for consistent keys: strip spaces/dashes/parens
// and keep '+' only as the leading character.
export function normalizeNumber(raw) {
  if (!raw) return '';
  return String(raw)
    .replace(/[^\d+]/g, '')
    .replace(/(?!^)\+/g, '');
}

export const db = {
  // ── Calls ──────────────────────────────────────────────
  getCall(callSid) {
    ensureLoaded();
    return state.calls[callSid] || null;
  },
  upsertCall(callSid, patch) {
    ensureLoaded();
    const existing = state.calls[callSid] || { id: callSid, turns: [] };
    state.calls[callSid] = { ...existing, ...patch };
    persist();
    return state.calls[callSid];
  },
  appendTurn(callSid, turn) {
    ensureLoaded();
    const call = state.calls[callSid] || { id: callSid, turns: [] };
    call.turns = call.turns || [];
    call.turns.push(turn);
    state.calls[callSid] = call;
    persist();
    return call;
  },
  listCalls() {
    ensureLoaded();
    return Object.values(state.calls).sort((a, b) => (b.startedAt || 0) - (a.startedAt || 0));
  },

  // ── Blocklist ──────────────────────────────────────────
  isBlocked(number) {
    ensureLoaded();
    return Boolean(state.blocklist[normalizeNumber(number)]);
  },
  block(number, { source = 'user', reason = '', addedAt } = {}) {
    ensureLoaded();
    const key = normalizeNumber(number);
    state.blocklist[key] = { number: key, source, reason, addedAt: addedAt || 0 };
    persist();
    return state.blocklist[key];
  },
  unblock(number) {
    ensureLoaded();
    delete state.blocklist[normalizeNumber(number)];
    persist();
  },
  listBlocked() {
    ensureLoaded();
    return Object.values(state.blocklist);
  },

  // ── Allowlist ──────────────────────────────────────────
  isAllowed(number) {
    ensureLoaded();
    return Boolean(state.allowlist[normalizeNumber(number)]);
  },
  allow(number, { label = '', addedAt } = {}) {
    ensureLoaded();
    const key = normalizeNumber(number);
    state.allowlist[key] = { number: key, label, addedAt: addedAt || 0 };
    persist();
    return state.allowlist[key];
  },
  removeAllow(number) {
    ensureLoaded();
    delete state.allowlist[normalizeNumber(number)];
    persist();
  },
  listAllowed() {
    ensureLoaded();
    return Object.values(state.allowlist);
  },

  // ── Spam learning ──────────────────────────────────────
  incrementSpam(number) {
    ensureLoaded();
    const key = normalizeNumber(number);
    state.spamCounts[key] = (state.spamCounts[key] || 0) + 1;
    persist();
    return state.spamCounts[key];
  },
  getSpamCount(number) {
    ensureLoaded();
    return state.spamCounts[normalizeNumber(number)] || 0;
  },

  // ── Device (FCM token registration) ────────────────────
  setDeviceToken(token) {
    ensureLoaded();
    state.device = { token: String(token || ''), updatedAt: Date.now() };
    persist();
    return state.device;
  },
  getDeviceToken() {
    ensureLoaded();
    return (state.device && state.device.token) || '';
  },

  // Test helper: wipe in-memory + on-disk state.
  _reset() {
    state = structuredClone(EMPTY);
    persist();
  },
};

export default db;
