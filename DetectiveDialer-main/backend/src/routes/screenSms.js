// Optional SMS content screening endpoint.
//
//   POST /screen-sms  { sender, text }
//     → classifies the message body (heuristics first, Gemini for the rest)
//     → returns { verdict: "SPAM"|"OK", reason, confidence }
//
// The app calls this ONLY when the user has opted in (Settings → content
// fallback, off by default) and only for sender IDs that carry no TRAI
// suffix — the suffix rules are handled entirely on-device. Nothing is
// stored server-side: message content is classified and discarded.

import express from 'express';
import logger from '../utils/logger.js';
import { classifySms } from '../services/gemini.js';

const router = express.Router();

router.post('/', async (req, res) => {
  const sender = (req.body.sender || '').toString().trim();
  const text = (req.body.text || '').toString();

  if (!text.trim()) {
    res.status(400).json({ error: 'text required' });
    return;
  }

  let result;
  try {
    result = await classifySms({ sender, text });
  } catch (err) {
    logger.error('classifySms() failed hard:', err.message);
    result = { verdict: 'OK', reason: 'classification error — keeping in inbox', confidence: 0.2 };
  }

  // Log the verdict, never the message body.
  logger.info(`SCREEN-SMS ${sender || '(no sender)'} → ${result.verdict} (${result.reason})`);
  res.json(result);
});

export default router;
