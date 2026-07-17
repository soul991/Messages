// The AI "brain": text classification of an incoming caller.
//
// Uses Google's Gemini API when GEMINI_API_KEY is set. When it isn't (offline,
// no key, or on any error) the module degrades gracefully to the deterministic
// heuristics in classifier.js so the whole server still works end-to-end.

import { GoogleGenerativeAI } from '@google/generative-ai';
import config from '../config.js';
import logger from '../utils/logger.js';
import {
  DECISIONS,
  clampAction,
  heuristicScreen,
  heuristicSmsScreen,
  matchPrefixRule,
} from './classifier.js';

let client = null;
function getClient() {
  if (!config.gemini.enabled) return null;
  if (!client) client = new GoogleGenerativeAI(config.gemini.apiKey);
  return client;
}

// System prompt that frames Gemini as a phone-call screener.
export function classifierPrompt({ name } = {}) {
  const owner = name || config.owner.name;
  return [
    `You screen incoming phone calls on behalf of ${owner}.`,
    `Given an incoming caller's phone number and (optionally) a caller-ID label,`,
    `classify the call into exactly one decision:`,
    `- ALLOW: a legitimate personal, family, vendor, or delivery call that should ring through.`,
    `- REJECT: unwanted or suspicious, but not clearly a mass spam/scam campaign.`,
    `- SPAM: telemarketing, loan/insurance/credit-card sales, robocalls, or scams.`,
    `Then choose exactly one policy action describing what the phone should DO:`,
    `- RING: ring through immediately — saved contacts, verified/known-good numbers,`,
    `  and delivery partners. Pair this with ALLOW.`,
    `- VOICEMAIL: don't ring; divert to voicemail — suspected spam such as customer-care`,
    `  or promotional patterns the owner may still want to hear later. Pair with SPAM.`,
    `- REJECT: drop silently, no ring and no voicemail — clear robocall or automated`,
    `  reminder-call patterns. Pair with REJECT.`,
    `Use number patterns (e.g. toll-free / known telemarketer prefixes) and the`,
    `caller name as evidence. When a caller name is present it is usually the`,
    `network's CNAP name — KYC-verified by the telecom operator, not self-reported —`,
    `so treat it as a strong, trustworthy signal of who is really calling and weight`,
    `it heavily in your decision. When uncertain, prefer ALLOW / RING so real callers`,
    `are never silently dropped.`,
  ].join(' ');
}

const RESPONSE_SCHEMA_HINT = `
Respond ONLY with a compact JSON object, no markdown fences, of this exact shape:
{
  "decision": "ALLOW|REJECT|SPAM",
  "action": "RING|VOICEMAIL|REJECT",
  "reason": "one short sentence explaining the decision",
  "confidence": 0.0
}`;

function safeParse(text) {
  if (!text) return null;
  const match = text.match(/\{[\s\S]*\}/);
  if (!match) return null;
  try {
    return JSON.parse(match[0]);
  } catch {
    return null;
  }
}

function clampDecision(decision) {
  const up = String(decision || '').toUpperCase();
  return DECISIONS.includes(up) ? up : 'ALLOW';
}

function clampConfidence(value, fallback = 0.6) {
  const n = typeof value === 'number' ? value : Number.NaN;
  if (!Number.isFinite(n)) return fallback;
  return Math.min(1, Math.max(0, n));
}

// The phone's CallScreeningService gives us ~5 seconds end to end, so a slow
// Gemini call must lose the race and yield to the instant heuristic fallback.
function withTimeout(promise, ms) {
  let timer;
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => reject(new Error(`Gemini timed out after ${ms}ms`)), ms);
  });
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timer));
}

/**
 * Classify a single incoming call by its number / caller-ID label.
 * @param {object} args
 * @param {string} args.phoneNumber - the caller's number (E.164-ish)
 * @param {string} [args.callerId]  - caller-ID label, if the carrier provided one
 * @param {number} [args.timestamp] - call time (epoch ms), informational
 * @returns {Promise<{decision:'ALLOW'|'REJECT'|'SPAM', action:'RING'|'VOICEMAIL'|'REJECT', reason:string, confidence:number}>}
 */
export async function classifyNumber({ phoneNumber = '', callerId = '', timestamp } = {}) {
  // ── TRAI prefix tier: deterministic, beats the AI ───────────────────
  // 140-series = registered promotional caller, 160-series = transactional.
  // Same rules run on-device; this keeps backend classification consistent.
  const rule = matchPrefixRule(phoneNumber);
  if (rule) {
    return {
      decision: rule.decision,
      action: clampAction(rule.action, rule.decision),
      reason: rule.label,
      confidence: 1,
    };
  }

  const ai = getClient();

  // ── Offline / no-key path: deterministic heuristics ────────────────
  if (!ai) {
    return heuristicScreen({ phoneNumber, callerId });
  }

  // ── Gemini path ────────────────────────────────────────────────────
  try {
    const model = ai.getGenerativeModel({
      model: config.gemini.model,
      systemInstruction: classifierPrompt(config.owner),
    });

    const when = timestamp ? new Date(timestamp).toISOString() : 'unknown';
    const prompt = [
      `Incoming call.`,
      `Phone number: ${phoneNumber || 'unknown'}`,
      `Network-verified caller name (CNAP): ${callerId || '(none provided)'}`,
      `Time: ${when}`,
      RESPONSE_SCHEMA_HINT,
    ].join('\n');

    const result = await withTimeout(model.generateContent(prompt), config.gemini.timeoutMs);
    const parsed = safeParse(result.response.text());

    if (!parsed || !parsed.decision) {
      throw new Error('Unparseable Gemini response');
    }

    const decision = clampDecision(parsed.decision);
    return {
      decision,
      action: clampAction(parsed.action, decision),
      reason: String(parsed.reason || '').trim() || 'classified by AI',
      confidence: clampConfidence(parsed.confidence),
    };
  } catch (err) {
    logger.warn('Gemini failed, falling back to heuristics:', err.message);
    return heuristicScreen({ phoneNumber, callerId });
  }
}

// ── SMS classification (optional content fallback) ────────────────────────
// Used by POST /screen-sms for sender IDs that carry no TRAI suffix. The
// deterministic heuristics always run first; Gemini only gets a say when the
// heuristics found nothing (their SPAM verdicts are already high-precision).

const SMS_SCHEMA_HINT = `
Respond ONLY with a compact JSON object, no markdown fences, of this exact shape:
{
  "verdict": "SPAM|OK",
  "reason": "one short sentence explaining the decision",
  "confidence": 0.0
}`;

export function smsClassifierPrompt() {
  return [
    `You screen incoming SMS messages on an Indian phone.`,
    `Classify each message as exactly one of:`,
    `- SPAM: promotional blasts, gambling/betting/loan/investment bait, phishing`,
    `  or smishing (fake KYC/bank/parcel alerts, opaque shortened links).`,
    `- OK: personal messages, OTPs, transactional and service alerts, anything a`,
    `  real correspondent would send.`,
    `Never mark OTP or verification-code messages as SPAM. When uncertain, prefer OK.`,
  ].join(' ');
}

/**
 * Classify an SMS body. Heuristics first (deterministic, offline), then Gemini
 * for the ambiguous remainder when a key is configured.
 * @returns {Promise<{verdict:'SPAM'|'OK', reason:string, confidence:number}>}
 */
export async function classifySms({ sender = '', text = '' } = {}) {
  const heuristic = heuristicSmsScreen({ sender, text });
  if (heuristic.verdict === 'SPAM') return heuristic;

  const ai = getClient();
  if (!ai) return heuristic;

  try {
    const model = ai.getGenerativeModel({
      model: config.gemini.model,
      systemInstruction: smsClassifierPrompt(),
    });
    const prompt = [
      `Incoming SMS.`,
      `Sender ID: ${sender || 'unknown'}`,
      `Message: ${String(text || '').slice(0, 1000)}`,
      SMS_SCHEMA_HINT,
    ].join('\n');

    const result = await withTimeout(model.generateContent(prompt), config.gemini.timeoutMs);
    const parsed = safeParse(result.response.text());
    if (!parsed || !parsed.verdict) throw new Error('Unparseable Gemini response');

    const verdict = String(parsed.verdict).toUpperCase() === 'SPAM' ? 'SPAM' : 'OK';
    return {
      verdict,
      reason: String(parsed.reason || '').trim() || 'classified by AI',
      confidence: clampConfidence(parsed.confidence),
    };
  } catch (err) {
    logger.warn('Gemini SMS classification failed, using heuristics:', err.message);
    return heuristic;
  }
}

export default { classifyNumber, classifierPrompt, classifySms, smsClassifierPrompt };
