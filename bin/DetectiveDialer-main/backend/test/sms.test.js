// SMS spam heuristics + the /screen-sms route.
// The TRAI suffix parser lives on-device (TraiSuffix.kt) — these tests cover
// the optional content-fallback path only.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';

import { createApp } from '../src/server.js';
import config from '../src/config.js';
import { heuristicSmsScreen } from '../src/services/classifier.js';
import { classifySms } from '../src/services/gemini.js';

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

test('heuristicSmsScreen flags keyword + shortener combos as SPAM', () => {
  const r = heuristicSmsScreen({
    sender: 'BX-KWIKLN',
    text: 'Congratulations! Pre-approved loan of Rs 5,00,000 waiting. Claim now bit.ly/xy12z',
  });
  assert.equal(r.verdict, 'SPAM');
  assert.ok(r.confidence >= 0.9);
});

test('heuristicSmsScreen flags multiple spam keywords', () => {
  const r = heuristicSmsScreen({ text: 'You are a lottery WINNER! Claim your prize today' });
  assert.equal(r.verdict, 'SPAM');
});

test('heuristicSmsScreen flags bare shortened links', () => {
  assert.equal(heuristicSmsScreen({ text: 'tinyurl.com/abc123' }).verdict, 'SPAM');
  assert.equal(heuristicSmsScreen({ text: 'Update here http://192.168.4.12/kyc' }).verdict, 'SPAM');
});

test('heuristicSmsScreen never flags OTP messages', () => {
  const r = heuristicSmsScreen({
    sender: 'AX-HDFCBK',
    text: '123456 is your OTP for txn of Rs 4999. Do not share it with anyone.',
  });
  assert.equal(r.verdict, 'OK');
  // Even an OTP wrapped in spammy words stays OK.
  const bait = heuristicSmsScreen({
    text: 'Your verification code is 4821 for the lottery winner offer login',
  });
  assert.equal(bait.verdict, 'OK');
});

test('heuristicSmsScreen keeps ordinary messages OK', () => {
  assert.equal(heuristicSmsScreen({ text: 'Hey, are we still on for dinner tonight?' }).verdict, 'OK');
  assert.equal(heuristicSmsScreen({ text: 'Your parcel is out for delivery' }).verdict, 'OK');
  // Single keyword without a link is not conclusive.
  assert.equal(heuristicSmsScreen({ text: 'Can you help me with my loan application form' }).verdict, 'OK');
  assert.equal(heuristicSmsScreen({ text: '' }).verdict, 'OK');
});

test('classifySms works offline via heuristics', async () => {
  const spam = await classifySms({ sender: 'BZ-WINBIG', text: 'Jackpot winner! Claim cashback now bit.ly/win' });
  assert.equal(spam.verdict, 'SPAM');
  const ok = await classifySms({ sender: '+919812345678', text: 'call me when free' });
  assert.equal(ok.verdict, 'OK');
});

test('POST /screen-sms classifies and requires text', async () => {
  const app = createApp();
  const bad = await request(app, 'POST', '/screen-sms', { sender: 'XX-YY' });
  assert.equal(bad.status, 400);

  const spam = await request(app, 'POST', '/screen-sms', {
    sender: 'BX-KWIKLN',
    text: 'Pre-approved loan! Claim now bit.ly/xy12z winner',
  });
  assert.equal(spam.status, 200);
  assert.match(spam.body, /"verdict":"SPAM"/);

  const ok = await request(app, 'POST', '/screen-sms', { sender: 'Mom', text: 'call me back' });
  assert.match(ok.body, /"verdict":"OK"/);
});

test('/screen-sms honours the API key when set', async () => {
  const previous = config.apiKey;
  config.apiKey = 'sms-secret';
  try {
    const app = createApp();
    const noKey = await request(app, 'POST', '/screen-sms', { text: 'hello' });
    assert.equal(noKey.status, 401);
    const withKey = await request(app, 'POST', '/screen-sms', { text: 'hello' }, { 'X-Api-Key': 'sms-secret' });
    assert.equal(withKey.status, 200);
  } finally {
    config.apiKey = previous;
  }
});
