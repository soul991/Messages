// Firebase Cloud Messaging — sends call-summary push notifications to the phone.
// Initializes firebase-admin from either a service-account file path or inline
// JSON (handy for Railway/Render env vars). No-ops cleanly when not configured.

import fs from 'node:fs';
import admin from 'firebase-admin';
import config from '../config.js';
import logger from '../utils/logger.js';
import db from '../store/db.js';

let app = null;
let initTried = false;

function init() {
  if (initTried) return app;
  initTried = true;

  if (!config.firebase.enabled) {
    logger.warn('FCM not configured — notifications will be logged only.');
    return null;
  }

  try {
    let credentialJson;
    if (config.firebase.serviceAccountJson) {
      credentialJson = JSON.parse(config.firebase.serviceAccountJson);
    } else if (config.firebase.serviceAccountPath) {
      credentialJson = JSON.parse(fs.readFileSync(config.firebase.serviceAccountPath, 'utf8'));
    }
    app = admin.initializeApp({ credential: admin.credential.cert(credentialJson) });
    logger.info('FCM initialized.');
    return app;
  } catch (err) {
    logger.error('FCM init failed:', err.message);
    return null;
  }
}

const CATEGORY_ICON = {
  // Screening decisions
  ALLOW: '✅',
  REJECT: '⛔',
  SPAM: '🚫',
  // Legacy content categories (still understood)
  DELIVERY: '🚚',
  VENDOR: '🔧',
  PERSONAL: '👤',
  URGENT: '⚠️',
};

/**
 * Send a call summary notification.
 * @param {object} payload
 * @param {string} payload.caller        caller phone number
 * @param {string} [payload.callerName]  network-verified caller name (CNAP), if any
 * @param {string} payload.category      SPAM|DELIVERY|VENDOR|PERSONAL|URGENT
 * @param {string} [payload.action]      RING|VOICEMAIL|REJECT — the policy action
 * @param {string} payload.summary       one-line summary
 * @param {number} payload.duration      seconds
 * @param {boolean} payload.hasRecording
 * @param {string} [payload.callId]
 * @param {string} [payload.token]       override device token
 */
export async function sendCallNotification(payload) {
  const {
    caller = 'Unknown',
    callerName = '',
    category = 'PERSONAL',
    action = '',
    summary = '',
    duration = 0,
    hasRecording = false,
    callId = '',
    token,
  } = payload;

  const icon = CATEGORY_ICON[category] || '📞';

  // Always log — this is the audit trail and the offline fallback.
  logger.info(`NOTIFY ${icon} [${category}] ${caller} — ${summary} (${duration}s, rec=${hasRecording})`);

  const application = init();
  // Prefer the token the app registered via POST /api/device (survives FCM
  // token rotation); the env var is only a manual fallback.
  const deviceToken = token || db.getDeviceToken() || config.firebase.defaultDeviceToken;
  if (!application || !deviceToken) {
    return { sent: false, reason: !application ? 'fcm-disabled' : 'no-device-token' };
  }

  // Data-only fields must be strings.
  const data = {
    caller: String(caller),
    callerName: String(callerName || ''),
    category: String(category),
    action: String(action || ''),
    summary: String(summary),
    duration: String(duration),
    hasRecording: String(hasRecording),
    callId: String(callId),
  };

  // Strictly data-only: with a `notification` block Android posts the tray
  // notification itself when the app is backgrounded and never invokes
  // onMessageReceived — so no Room entry and no deep link. The app builds its
  // own notification from `data`. High priority so doze doesn't delay it.
  const message = {
    token: deviceToken,
    data,
    android: { priority: 'high' },
  };

  try {
    const id = await admin.messaging().send(message);
    return { sent: true, id };
  } catch (err) {
    logger.error('FCM send failed:', err.message);
    return { sent: false, reason: err.message };
  }
}

export default { sendCallNotification };
