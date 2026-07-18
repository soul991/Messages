// TRAI prefix-rule matcher + domestic-number detection.
// The same logic ships inside the Android app (ScreeningMatcher) — keep in sync.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';

import { createApp } from '../src/server.js';
import config from '../src/config.js';
import db from '../src/store/db.js';
import {
  PREFIX_RULES,
  actionForDecision,
  clampAction,
  domesticForm,
  isDomesticNumber,
  matchPrefixRule,
} from '../src/services/classifier.js';
import { classifyNumber } from '../src/services/gemini.js';

// Deterministic offline classification (no live Gemini quota).
config.gemini.apiKey = '';

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

test('domesticForm reduces Indian formats and rejects foreign ones', () => {
  // +91 E.164
  assert.equal(domesticForm('+919812345678'), '9812345678');
  // 0091 international dialing form
  assert.equal(domesticForm('00919812345678'), '9812345678');
  // bare 10-digit subscriber
  assert.equal(domesticForm('9812345678'), '9812345678');
  // 91-prefixed without the + (some carriers)
  assert.equal(domesticForm('919812345678'), '9812345678');
  // national trunk prefix
  assert.equal(domesticForm('09812345678'), '9812345678');
  // short codes are domestic
  assert.equal(domesticForm('121'), '121');
  assert.equal(domesticForm('56789'), '56789');
  // formatting noise is stripped
  assert.equal(domesticForm('+91 98-123 45678'), '9812345678');
  // foreign numbers are not domestic
  assert.equal(domesticForm('+14155552671'), null);
  assert.equal(domesticForm('+442071838750'), null);
  assert.equal(domesticForm('0014155552671'), null);
  // wrong-length +91 is not trusted
  assert.equal(domesticForm('+91981234567'), null);
  assert.equal(domesticForm(''), null);
  assert.equal(domesticForm(null), null);
});

test('isDomesticNumber mirrors domesticForm', () => {
  assert.ok(isDomesticNumber('+919812345678'));
  assert.ok(isDomesticNumber('1409812345'));
  assert.ok(!isDomesticNumber('+14155552671'));
});

test('matchPrefixRule flags TRAI 140 (promotional) and 160 (transactional)', () => {
  const promo = matchPrefixRule('+911409812345');
  assert.ok(promo);
  assert.equal(promo.decision, 'SPAM');
  assert.match(promo.label, /140-series promotional/);

  const service = matchPrefixRule('1609812345');
  assert.ok(service);
  assert.equal(service.decision, 'ALLOW');
  assert.match(service.label, /160-series transactional/);

  // Trunk-0 and 0091 forms still match.
  assert.equal(matchPrefixRule('01409812345')?.decision, 'SPAM');
  assert.equal(matchPrefixRule('00911409812345')?.decision, 'SPAM');

  // Ordinary numbers don't match; foreign numbers never match.
  assert.equal(matchPrefixRule('+919812345678'), null);
  assert.equal(matchPrefixRule('+11409812345'), null);
  // A mobile that merely contains 140 later in the number doesn't match.
  assert.equal(matchPrefixRule('+919814012345'), null);
});

test('classifyNumber applies TRAI prefix rules before anything else', async () => {
  const promo = await classifyNumber({ phoneNumber: '+911409812345', callerId: 'Friendly Bank' });
  assert.equal(promo.decision, 'SPAM');
  assert.equal(promo.action, 'VOICEMAIL');
  assert.match(promo.reason, /TRAI 140-series/);
  assert.equal(promo.confidence, 1);

  const service = await classifyNumber({ phoneNumber: '+911609812345' });
  assert.equal(service.decision, 'ALLOW');
  assert.equal(service.action, 'RING');
  assert.match(service.reason, /TRAI 160-series/);
});

test('actionForDecision maps decisions to policy actions', () => {
  assert.equal(actionForDecision('ALLOW'), 'RING');
  assert.equal(actionForDecision('SPAM'), 'VOICEMAIL');
  assert.equal(actionForDecision('REJECT'), 'REJECT');
  // Unknown / missing decisions fail safe to RING (never silently drop).
  assert.equal(actionForDecision('WHATEVER'), 'RING');
  assert.equal(actionForDecision(''), 'RING');
  assert.equal(actionForDecision(null), 'RING');
});

test('clampAction accepts valid actions and falls back on the decision', () => {
  assert.equal(clampAction('voicemail', 'ALLOW'), 'VOICEMAIL');
  assert.equal(clampAction('RING', 'SPAM'), 'RING');
  // Invalid/blank action → derive from the decision.
  assert.equal(clampAction('', 'SPAM'), 'VOICEMAIL');
  assert.equal(clampAction('NONSENSE', 'REJECT'), 'REJECT');
  assert.equal(clampAction(undefined, 'ALLOW'), 'RING');
});

test('PREFIX_RULES carry an action alongside the decision', () => {
  for (const rule of PREFIX_RULES) {
    assert.ok(['RING', 'VOICEMAIL', 'REJECT'].includes(rule.action), `${rule.prefix} has a valid action`);
  }
});

test('GET /api/screening-rules serves the prefix table', async () => {
  const app = createApp();
  const res = await request(app, 'GET', '/api/screening-rules');
  assert.equal(res.status, 200);
  const parsed = JSON.parse(res.body);
  assert.deepEqual(parsed.rules, PREFIX_RULES);
});

test('POST /screen returns the TRAI verdict for a 140-series caller', async () => {
  db._reset();
  const app = createApp();
  const res = await request(app, 'POST', '/screen', { phoneNumber: '+911409812345' });
  assert.equal(res.status, 200);
  assert.match(res.body, /"decision":"SPAM"/);
  assert.match(res.body, /TRAI 140-series promotional caller/);
});
