// JSON API consumed by the Android app: call history, single call detail,
// and blocklist / allowlist management.

import express from 'express';
import db, { normalizeNumber } from '../store/db.js';
import { PREFIX_RULES } from '../services/classifier.js';

const router = express.Router();

function now() {
  return new Date().getTime();
}

// ── Device registration ─────────────────────────────────────────────────
// The app posts its FCM token here on startup and whenever Firebase rotates
// it, so pushes keep working without manually editing the backend env.
router.post('/device', (req, res) => {
  const token = (req.body.token || '').toString().trim();
  if (!token) {
    res.status(400).json({ error: 'token required' });
    return;
  }
  db.setDeviceToken(token);
  res.status(201).json({ ok: true });
});

// ── Screening rules ──────────────────────────────────────────────────────
// The app ships the same defaults and refreshes from here, so new prefix
// rules (e.g. future TRAI series) reach phones without an APK rebuild.
router.get('/screening-rules', (req, res) => {
  res.json({ rules: PREFIX_RULES });
});

// ── Calls ───────────────────────────────────────────────────────────────
router.get('/calls', (req, res) => {
  res.json({ calls: db.listCalls() });
});

router.get('/call/:id', (req, res) => {
  const call = db.getCall(req.params.id);
  if (!call) {
    res.status(404).json({ error: 'not found' });
    return;
  }
  res.json(call);
});

// ── Blocklist ─────────────────────────────────────────────────────────
router.get('/blocklist', (req, res) => {
  res.json({ blocked: db.listBlocked() });
});

router.post('/blocklist', (req, res) => {
  const number = normalizeNumber(req.body.number);
  if (!number) {
    res.status(400).json({ error: 'number required' });
    return;
  }
  const entry = db.block(number, {
    source: req.body.source || 'user',
    reason: req.body.reason || '',
    addedAt: now(),
  });
  res.status(201).json(entry);
});

router.delete('/blocklist/:number', (req, res) => {
  db.unblock(req.params.number);
  res.json({ ok: true });
});

// ── Allowlist ─────────────────────────────────────────────────────────
router.get('/allowlist', (req, res) => {
  res.json({ allowed: db.listAllowed() });
});

router.post('/allowlist', (req, res) => {
  const number = normalizeNumber(req.body.number);
  if (!number) {
    res.status(400).json({ error: 'number required' });
    return;
  }
  const entry = db.allow(number, { label: req.body.label || '', addedAt: now() });
  res.status(201).json(entry);
});

router.delete('/allowlist/:number', (req, res) => {
  db.removeAllow(req.params.number);
  res.json({ ok: true });
});

export default router;
