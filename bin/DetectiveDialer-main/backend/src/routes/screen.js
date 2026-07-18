// On-device call screening endpoint.
//
//   POST /screen  { phoneNumber, callerId, timestamp }
//     → classifies the caller with Gemini (text, not voice)
//     → returns { decision: "ALLOW"|"REJECT"|"SPAM",
//                 action:   "RING"|"VOICEMAIL"|"REJECT", reason, confidence }
//     → also pushes the result to the phone via FCM and records it in the store
//
// `decision` is the human-facing label; `action` is what the phone actually does
// (ring, divert to voicemail, or drop). The app branches on `action`, not the label.
//
// The phone calls this synchronously from its CallScreeningService and acts on
// the decision (reject SPAM, ring everything else). The FCM push is the durable
// summary that lands in the call history / notification shade.

import express from 'express';
import config from '../config.js';
import logger from '../utils/logger.js';
import db, { normalizeNumber } from '../store/db.js';
import { classifyNumber } from '../services/gemini.js';
import { clampAction } from '../services/classifier.js';
import { sendCallNotification } from '../services/fcm.js';

const router = express.Router();

function now() {
  return new Date().getTime();
}

// ── POST /screen ───────────────────────────────────────────────────────
router.post('/', async (req, res) => {
  const phoneNumber = normalizeNumber(req.body.phoneNumber || '');
  const callerId = (req.body.callerId || '').toString().trim();
  const timestamp = parseInt(req.body.timestamp, 10) || now();

  if (!phoneNumber) {
    res.status(400).json({ error: 'phoneNumber required' });
    return;
  }

  // Lists win over the AI: an explicit user choice is authoritative. A blocklist
  // hit drops outright (REJECT action) rather than diverting to voicemail — the
  // user has already said they never want to hear from this number.
  let result;
  if (db.isAllowed(phoneNumber)) {
    result = { decision: 'ALLOW', action: 'RING', reason: 'in allowlist', confidence: 1 };
  } else if (db.isBlocked(phoneNumber)) {
    result = { decision: 'SPAM', action: 'REJECT', reason: 'in blocklist', confidence: 1 };
  } else {
    try {
      result = await classifyNumber({ phoneNumber, callerId, timestamp });
    } catch (err) {
      logger.error('classifyNumber() failed hard:', err.message);
      result = { decision: 'ALLOW', action: 'RING', reason: 'classification error — letting it ring', confidence: 0.2 };
    }
  }

  const { decision, reason, confidence } = result;
  // Backfill action for any classifier path that predates the field.
  const action = clampAction(result.action, decision);
  logger.info(`SCREEN ${phoneNumber} (${callerId || 'no caller-id'}) → ${decision}/${action} (${reason})`);

  // Record the screened call so it shows up in history.
  const callId = `screen-${phoneNumber}-${timestamp}`;
  db.upsertCall(callId, {
    id: callId,
    from: phoneNumber,
    callerId,
    startedAt: timestamp,
    category: decision,
    action,
    summary: reason,
    confidence,
    status: 'screened',
  });

  // ── Spam learning: auto-block after N spam classifications ───────────
  let autoBlocked = false;
  if (decision === 'SPAM' && !db.isBlocked(phoneNumber)) {
    const count = db.incrementSpam(phoneNumber);
    if (count >= config.screening.spamAutoblockThreshold) {
      db.block(phoneNumber, { source: 'ai_learned', reason: `${count} spam calls`, addedAt: timestamp });
      autoBlocked = true;
    }
  }

  // Answer the phone first — it is inside its ~5s CallScreeningService budget.
  res.json({ decision, action, reason, confidence });

  // Then push the durable summary via FCM (logged-only when not configured).
  // Fire-and-forget: a slow/failed push must never delay the screening verdict.
  sendCallNotification({
    caller: phoneNumber,
    callerName: callerId,
    category: decision,
    action,
    summary: autoBlocked ? `${reason} — auto-blocked` : reason,
    duration: 0,
    hasRecording: false,
    callId,
  }).catch((err) => logger.error('post-screen FCM push failed:', err.message));
});

export default router;
