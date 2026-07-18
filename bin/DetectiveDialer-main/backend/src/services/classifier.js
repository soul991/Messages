// Shared classification constants + a deterministic heuristic fallback used when
// the Gemini API is unavailable (no key, offline, or on error). Keeps the server
// functional end-to-end without a paid dependency.

import { mentionsDelivery } from './delivery.js';

export const CATEGORIES = ['SPAM', 'DELIVERY', 'VENDOR', 'PERSONAL', 'URGENT'];

export const URGENT_KEYWORDS = [
  'accident', 'hospital', 'emergency', 'police', 'death', 'died', 'ambulance',
  'urgent', 'icu', 'fire', 'blood', 'critical', 'arrested',
  // transliterated / regional
  'durghotona', 'aspatal', 'thana', 'jaruri', 'mrityu',
];

const SPAM_KEYWORDS = [
  'loan', 'credit card', 'insurance', 'policy', 'recharge', 'offer', 'pre-approved',
  'preapproved', 'investment', 'mutual fund', 'lottery', 'winner', 'cashback',
  'interest rate', 'emi', 'demat', 'trading', 'sim upgrade', 'kyc update',
];

const VENDOR_KEYWORDS = [
  'plumber', 'electrician', 'clinic', 'tailor', 'appointment', 'technician',
  'carpenter', 'painter', 'mechanic', 'salon', 'doctor', 'repair', 'service request',
];

export function hasUrgentKeyword(text) {
  if (!text) return false;
  const lower = text.toLowerCase();
  return URGENT_KEYWORDS.some((k) => lower.includes(k));
}

// Screening decisions the /screen endpoint returns to the phone.
//   ALLOW  → let the call ring through
//   REJECT → unwanted but not clearly spam (the app rings it silently)
//   SPAM   → telemarketing/scam → the app rejects the call silently
export const DECISIONS = ['ALLOW', 'REJECT', 'SPAM'];

// Policy action — the *authoritative* behavior signal the app branches on, kept
// separate from the human-facing `decision` label (see the bug batch #4b). The
// app decides whether to ring, divert, or drop from this, NOT from the label.
//   RING      → ring through; bypass any screening delay
//   VOICEMAIL → don't ring, divert to the carrier's voicemail box
//   REJECT    → no ring, no voicemail; drop the call silently
export const ACTIONS = ['RING', 'VOICEMAIL', 'REJECT'];

// Default action for a decision when a classifier didn't return one explicitly.
//   ALLOW  → RING       (contacts, verified/known-good, delivery partners)
//   SPAM   → VOICEMAIL  (suspected spam: customer-care / promo patterns divert)
//   REJECT → REJECT     (robocall / reminder-call patterns: drop outright)
export function actionForDecision(decision) {
  switch (String(decision || '').toUpperCase()) {
    case 'SPAM':
      return 'VOICEMAIL';
    case 'REJECT':
      return 'REJECT';
    default:
      return 'RING';
  }
}

export function clampAction(action, decision) {
  const up = String(action || '').toUpperCase();
  return ACTIONS.includes(up) ? up : actionForDecision(decision);
}

// ── TRAI number-series prefix rules ──────────────────────────────────────
// TRAI mandates that registered telemarketers place promotional calls from the
// 140 number series and transactional/service calls from the 160 series, so
// the prefix alone is a deterministic verdict — no AI needed. The same table
// ships inside the Android app (ScreeningMatcher.DEFAULT_RULES) and is served
// by GET /api/screening-rules so the app can refresh it without a rebuild.
export const PREFIX_RULES = [
  { prefix: '140', decision: 'SPAM', action: 'VOICEMAIL', label: 'TRAI 140-series promotional caller' },
  { prefix: '160', decision: 'ALLOW', action: 'RING', label: 'TRAI 160-series transactional caller' },
];

/**
 * Reduce an Indian number to its domestic form (no +91/0091/trunk-0), or null
 * when the number is not recognizably domestic. Accepts short codes and bare
 * 10-digit subscriber numbers. Mirrored in the app (ScreeningMatcher).
 */
export function domesticForm(raw) {
  const number = String(raw || '').replace(/[^\d+]/g, '').replace(/(?!^)\+/g, '');
  if (!number) return null;
  if (number.startsWith('+')) {
    return number.startsWith('+91') && number.length === 13 ? number.slice(3) : null;
  }
  if (number.startsWith('0091')) {
    return number.length === 14 ? number.slice(4) : null;
  }
  if (number.startsWith('00')) return null; // international dialing prefix
  if (number.startsWith('0')) return number.slice(1) || null; // national trunk prefix
  if (number.length === 12 && number.startsWith('91')) {
    return number.slice(2); // some carriers deliver 91XXXXXXXXXX without the +
  }
  if (number.length <= 10) return number; // 10-digit subscriber or short code
  return null;
}

export function isDomesticNumber(raw) {
  return domesticForm(raw) !== null;
}

/** First prefix rule matching the number's domestic form, or null. */
export function matchPrefixRule(raw, rules = PREFIX_RULES) {
  const domestic = domesticForm(raw);
  if (!domestic) return null;
  return rules.find((r) => r.prefix && domestic.startsWith(r.prefix)) || null;
}

// Map a content category to a screening decision.
export function categoryToDecision(category) {
  return String(category || '').toUpperCase() === 'SPAM' ? 'SPAM' : 'ALLOW';
}

// Deterministic fallback used when Gemini is unavailable. Classifies from any
// caller-ID name we were given; with no name, a bare unknown number is
// allowed to ring (the user — not a robot voice — makes the final call).
// Returns { decision, reason, confidence }.
export function heuristicScreen({ phoneNumber = '', callerId = '' } = {}) {
  const label = String(callerId || '').trim();
  if (!label) {
    return {
      decision: 'ALLOW',
      action: 'RING',
      reason: 'unknown number, no caller ID — letting it ring',
      confidence: 0.3,
    };
  }
  const h = heuristicClassify(label);
  // A caller name is usually the network's KYC-verified CNAP name, not
  // self-reported text — the classification it drives deserves extra weight.
  const confidence = Math.min(1, h.confidence + 0.1);
  const decision = categoryToDecision(h.category);
  return { decision, action: actionForDecision(decision), reason: h.reason, confidence };
}

// Returns { category, confidence, reason }.
export function heuristicClassify(text) {
  const lower = (text || '').toLowerCase();
  if (hasUrgentKeyword(lower)) {
    return { category: 'URGENT', confidence: 0.9, reason: 'urgent keyword detected' };
  }
  if (mentionsDelivery(lower)) {
    return { category: 'DELIVERY', confidence: 0.8, reason: 'delivery service mentioned' };
  }
  if (SPAM_KEYWORDS.some((k) => lower.includes(k))) {
    return { category: 'SPAM', confidence: 0.85, reason: 'sales/spam keyword detected' };
  }
  if (VENDOR_KEYWORDS.some((k) => lower.includes(k))) {
    return { category: 'VENDOR', confidence: 0.7, reason: 'vendor/service keyword detected' };
  }
  // Default: treat as a real person we don't yet understand.
  return { category: 'PERSONAL', confidence: 0.4, reason: 'no spam/delivery markers' };
}

// ── SMS spam heuristics ─────────────────────────────────────────────────
// Content fallback for the app's optional /screen-sms route: used for sender
// IDs that carry no TRAI suffix (the suffix rules run on-device). Deliberately
// conservative — an SMS wrongly kept in the inbox costs a glance; one wrongly
// blocked could be an OTP.

export const SMS_SPAM_KEYWORDS = [
  'loan', 'pre-approved', 'preapproved', 'credit card', 'lottery', 'winner',
  'won', 'prize', 'jackpot', 'cashback', 'casino', 'betting', 'rummy',
  'congratulations', 'claim now', 'limited offer', 'offer expires', 'flat off',
  'discount', 'free gift', 'investment tips', 'stock tips', 'trading tips',
  'earn from home', 'work from home', 'guaranteed returns', 'double your',
  'kyc suspended', 'kyc expired', 'account blocked', 'account suspended',
  'electricity disconnected', 'redeem points', 'reward points expiring',
];

// Link shorteners + bare IPs are the classic smishing carriers.
const SMS_URL_PATTERN = /(https?:\/\/|www\.)\S+/i;
const SMS_SHORTENER_PATTERN = /\b(bit\.ly|tinyurl\.com|t\.co|goo\.gl|cutt\.ly|rb\.gy|is\.gd|surl\.li|tiny\.cc)\b/i;
const SMS_BARE_IP_URL = /https?:\/\/\d{1,3}(\.\d{1,3}){3}/i;

/**
 * Deterministic SMS spam screen. Returns { verdict: 'SPAM'|'OK', reason, confidence }.
 * OTP-looking messages are always OK — blocking an OTP is the worst failure mode.
 */
export function heuristicSmsScreen({ sender = '', text = '' } = {}) {
  const body = String(text || '').toLowerCase();
  if (!body.trim()) {
    return { verdict: 'OK', reason: 'empty message', confidence: 0.5 };
  }

  // OTP guard: never flag verification codes.
  if (/\b(otp|one[- ]?time password|verification code)\b/i.test(body) && /\b\d{4,8}\b/.test(body)) {
    return { verdict: 'OK', reason: 'looks like an OTP/verification message', confidence: 0.9 };
  }

  const spamHits = SMS_SPAM_KEYWORDS.filter((k) => body.includes(k));
  const hasUrl = SMS_URL_PATTERN.test(body);
  const hasShortener = SMS_SHORTENER_PATTERN.test(body) || SMS_BARE_IP_URL.test(body);

  if (hasShortener && spamHits.length > 0) {
    return { verdict: 'SPAM', reason: `spam keyword (${spamHits[0]}) + shortened/opaque link`, confidence: 0.95 };
  }
  if (spamHits.length >= 2) {
    return { verdict: 'SPAM', reason: `multiple spam keywords (${spamHits.slice(0, 2).join(', ')})`, confidence: 0.9 };
  }
  if (hasShortener) {
    return { verdict: 'SPAM', reason: 'shortened/opaque link with no context', confidence: 0.7 };
  }
  if (spamHits.length === 1 && hasUrl) {
    return { verdict: 'SPAM', reason: `spam keyword (${spamHits[0]}) with link`, confidence: 0.8 };
  }
  if (spamHits.length === 1) {
    return { verdict: 'OK', reason: `single spam keyword (${spamHits[0]}) — not conclusive`, confidence: 0.5 };
  }
  return { verdict: 'OK', reason: 'no spam markers', confidence: 0.6 };
}

export default {
  CATEGORIES,
  DECISIONS,
  ACTIONS,
  actionForDecision,
  clampAction,
  PREFIX_RULES,
  SMS_SPAM_KEYWORDS,
  heuristicSmsScreen,
  URGENT_KEYWORDS,
  hasUrgentKeyword,
  heuristicClassify,
  heuristicScreen,
  categoryToDecision,
  domesticForm,
  isDomesticNumber,
  matchPrefixRule,
};
