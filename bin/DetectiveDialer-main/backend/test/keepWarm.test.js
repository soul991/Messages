// Keep-warm enable/disable logic. Pure config checks — no real network calls,
// so it stays deterministic and doesn't hit Railway or spend anything.

import { test } from 'node:test';
import assert from 'node:assert/strict';

import config from '../src/config.js';
import { startKeepWarm, stopKeepWarm } from '../src/services/keepWarm.js';

// config.keepWarm.enabled is a getter derived from publicBaseUrl + KEEP_WARM.
// We flip publicBaseUrl (a plain string on the config object) to exercise both
// branches without touching process.env.

test('keep-warm is disabled for localhost / non-https base URLs', () => {
  const original = config.publicBaseUrl;
  try {
    config.publicBaseUrl = 'http://localhost:3000';
    assert.equal(config.keepWarm.enabled, false);
    assert.equal(startKeepWarm(), false);

    config.publicBaseUrl = 'https://localhost:3000';
    assert.equal(config.keepWarm.enabled, false, 'https on localhost still disabled');
  } finally {
    config.publicBaseUrl = original;
    stopKeepWarm();
  }
});

test('keep-warm is enabled for a real https public URL and starts once', () => {
  const original = config.publicBaseUrl;
  const originalEnv = process.env.KEEP_WARM;
  try {
    delete process.env.KEEP_WARM;
    config.publicBaseUrl = 'https://detective-dialer.up.railway.app';
    assert.equal(config.keepWarm.enabled, true);
    assert.equal(startKeepWarm(), true);
    // Idempotent — calling again while running returns true without a 2nd timer.
    assert.equal(startKeepWarm(), true);
  } finally {
    stopKeepWarm();
    config.publicBaseUrl = original;
    if (originalEnv === undefined) delete process.env.KEEP_WARM;
    else process.env.KEEP_WARM = originalEnv;
  }
});

test('KEEP_WARM=off forces keep-warm disabled even with a real URL', () => {
  const original = config.publicBaseUrl;
  const originalEnv = process.env.KEEP_WARM;
  try {
    process.env.KEEP_WARM = 'off';
    config.publicBaseUrl = 'https://detective-dialer.up.railway.app';
    assert.equal(config.keepWarm.enabled, false);
    assert.equal(startKeepWarm(), false);
  } finally {
    stopKeepWarm();
    config.publicBaseUrl = original;
    if (originalEnv === undefined) delete process.env.KEEP_WARM;
    else process.env.KEEP_WARM = originalEnv;
  }
});
