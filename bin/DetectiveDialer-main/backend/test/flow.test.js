// End-to-end-ish tests that don't require Gemini or FCM.
// Run with: npm test  (uses the built-in node:test runner).

import { test } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';

import { createApp } from '../src/server.js';
import config from '../src/config.js';
import db, { normalizeNumber } from '../src/store/db.js';
import { heuristicClassify, heuristicScreen } from '../src/services/classifier.js';
import { mentionsDelivery } from '../src/services/delivery.js';
import { classifyNumber } from '../src/services/gemini.js';

// Force the offline heuristic path even when a real key is present in .env —
// tests must be deterministic and must not spend live API quota.
config.gemini.apiKey = '';

// Tiny helper to drive the Express app over a real socket.
function request(app, method, path, body, headers = {}) {
  return new Promise((resolve, reject) => {
    const server = app.listen(0, () => {
      const { port } = server.address();
      const data = body ? new URLSearchParams(body).toString() : null;
      const req = http.request(
        { host: '127.0.0.1', port, method, path,
          headers: { 'Content-Type': 'application/x-www-form-urlencoded',
            'Content-Length': data ? Buffer.byteLength(data) : 0, ...headers } },
        (res) => {
          let chunks = '';
          res.on('data', (c) => (chunks += c));
          res.on('end', () => {
            server.close();
            resolve({ status: res.statusCode, body: chunks });
          });
        },
      );
      req.on('error', (e) => { server.close(); reject(e); });
      if (data) req.write(data);
      req.end();
    });
  });
}

test('heuristic classifier labels spam/delivery/urgent/personal', () => {
  assert.equal(heuristicClassify('I have a pre-approved loan offer for you').category, 'SPAM');
  assert.equal(heuristicClassify('This is your Blinkit delivery agent').category, 'DELIVERY');
  assert.equal(heuristicClassify('there was an accident, he is in hospital').category, 'URGENT');
  assert.equal(heuristicClassify('hi is Rahul there').category, 'PERSONAL');
});

test('delivery detection matches service names and keywords', () => {
  assert.ok(mentionsDelivery('I am from Swiggy'));
  assert.ok(mentionsDelivery('your parcel is here'));
  assert.ok(!mentionsDelivery('I want to talk about a loan'));
});

test('heuristicScreen maps caller-ID labels to a decision', () => {
  assert.equal(heuristicScreen({ callerId: 'pre-approved credit card offer' }).decision, 'SPAM');
  assert.equal(heuristicScreen({ callerId: 'Mom' }).decision, 'ALLOW');
  // No caller-ID label → unknown number is allowed to ring.
  assert.equal(heuristicScreen({ phoneNumber: '+919812345678' }).decision, 'ALLOW');
});

test('heuristicScreen returns a policy action alongside the decision', () => {
  assert.equal(heuristicScreen({ callerId: 'pre-approved credit card offer' }).action, 'VOICEMAIL');
  assert.equal(heuristicScreen({ callerId: 'Mom' }).action, 'RING');
  assert.equal(heuristicScreen({ phoneNumber: '+919812345678' }).action, 'RING');
});

test('classifyNumber() works offline and flags spam labels', async () => {
  const spam = await classifyNumber({ phoneNumber: '+911234567890', callerId: 'loan offer' });
  assert.equal(spam.decision, 'SPAM');
  assert.ok(typeof spam.reason === 'string' && spam.reason.length > 0);

  const ok = await classifyNumber({ phoneNumber: '+919812345678', callerId: 'Rahul' });
  assert.equal(ok.decision, 'ALLOW');
});

test('GET / health endpoint responds ok', async () => {
  const app = createApp();
  const res = await request(app, 'GET', '/');
  assert.equal(res.status, 200);
  assert.match(res.body, /detective-dialer/);
});

test('GET /healthz responds ok', async () => {
  const app = createApp();
  const res = await request(app, 'GET', '/healthz');
  assert.equal(res.status, 200);
  assert.match(res.body, /"ok":true/);
});

test('POST /screen classifies an unknown caller', async () => {
  db._reset();
  const app = createApp();
  const res = await request(app, 'POST', '/screen', {
    phoneNumber: '+919812345678', callerId: 'Rahul', timestamp: '1700000000000',
  });
  assert.equal(res.status, 200);
  assert.match(res.body, /"decision":"ALLOW"/);
});

test('POST /screen requires a phoneNumber', async () => {
  const app = createApp();
  const res = await request(app, 'POST', '/screen', { callerId: 'someone' });
  assert.equal(res.status, 400);
});

test('POST /screen honours the blocklist (returns SPAM, dropped outright)', async () => {
  db._reset();
  const app = createApp();
  await request(app, 'POST', '/api/blocklist', { number: '+911111111111', reason: 'test' });
  const res = await request(app, 'POST', '/screen', { phoneNumber: '+911111111111' });
  assert.match(res.body, /"decision":"SPAM"/);
  // A user block drops the call outright, not to voicemail.
  assert.match(res.body, /"action":"REJECT"/);
});

test('POST /screen includes a policy action for every verdict', async () => {
  db._reset();
  const app = createApp();
  // Allowlisted → RING.
  await request(app, 'POST', '/api/allowlist', { number: '+912222222222', label: 'Dad' });
  const allowed = await request(app, 'POST', '/screen', { phoneNumber: '+912222222222' });
  assert.match(allowed.body, /"action":"RING"/);
  // TRAI 140-series promotional → VOICEMAIL.
  const promo = await request(app, 'POST', '/screen', { phoneNumber: '+911409812345' });
  assert.match(promo.body, /"action":"VOICEMAIL"/);
});

test('blocklist API add + list + delete', async () => {
  db._reset();
  const app = createApp();
  const add = await request(app, 'POST', '/api/blocklist', { number: '+911111111111', reason: 'test' });
  assert.equal(add.status, 201);
  const list = await request(app, 'GET', '/api/blocklist');
  assert.match(list.body, /911111111111/);
  const del = await request(app, 'DELETE', '/api/blocklist/+911111111111');
  assert.equal(del.status, 200);
});

test('normalizeNumber strips formatting and keeps only a leading +', () => {
  assert.equal(normalizeNumber('+91 98-123 (45678)'), '+919812345678');
  assert.equal(normalizeNumber('98+12345678'), '9812345678');
  assert.equal(normalizeNumber('++911111111111'), '+911111111111');
  assert.equal(normalizeNumber(''), '');
  assert.equal(normalizeNumber(null), '');
});

test('POST /api/device registers the FCM token', async () => {
  db._reset();
  const app = createApp();
  const bad = await request(app, 'POST', '/api/device', {});
  assert.equal(bad.status, 400);
  const ok = await request(app, 'POST', '/api/device', { token: 'fcm-token-abc123' });
  assert.equal(ok.status, 201);
  assert.equal(db.getDeviceToken(), 'fcm-token-abc123');
});

test('X-Api-Key auth guards /screen and /api when API_KEY is set', async () => {
  const previous = config.apiKey;
  config.apiKey = 'test-secret';
  try {
    const app = createApp();
    // Health stays open.
    assert.equal((await request(app, 'GET', '/healthz')).status, 200);
    // API and screening require the key.
    assert.equal((await request(app, 'GET', '/api/calls')).status, 401);
    assert.equal((await request(app, 'POST', '/screen', { phoneNumber: '+911234500000' })).status, 401);
    assert.equal((await request(app, 'GET', '/api/calls', null, { 'X-Api-Key': 'wrong' })).status, 401);
    assert.equal((await request(app, 'GET', '/api/calls', null, { 'X-Api-Key': 'test-secret' })).status, 200);
  } finally {
    config.apiKey = previous;
  }
});

test('spam auto-block triggers after threshold via /screen', async () => {
  db._reset();
  const app = createApp();
  const phoneNumber = '+919800000000';
  // Three spam classifications from the same number (caller-ID label triggers SPAM).
  for (let i = 0; i < 3; i++) {
    await request(app, 'POST', '/screen', { phoneNumber, callerId: 'pre-approved loan offer' });
  }
  assert.ok(db.isBlocked(phoneNumber), 'number should be auto-blocked after 3 spam screenings');
});
