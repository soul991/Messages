// Keep-warm self-ping — prevents Railway "Serverless" cold starts.
//
// Railway decides to sleep a service based on OUTBOUND traffic only: if the
// process makes no outbound request for ~10 min it sleeps, and the next inbound
// hit then pays a 15–22 s cold start. Inbound pings (uptime monitors, the phone
// itself) do NOT reset that timer. So we generate our own outbound traffic by
// fetching our own public health URL on an interval under the sleep threshold.
// That keeps the dyno awake so the /screen path answers inside the phone's ~5 s
// CallScreeningService budget.
//
// No-ops cleanly when disabled (local dev / no real https public URL).

import config from '../config.js';
import logger from '../utils/logger.js';

let timer = null;

async function pingOnce() {
  // Hit the lightweight health endpoint; this is an OUTBOUND request from the
  // dyno's perspective, which is what resets Railway's sleep timer.
  const url = `${config.publicBaseUrl}/healthz`;
  try {
    const controller = new AbortController();
    const t = setTimeout(() => controller.abort(), 10_000);
    const res = await fetch(url, { signal: controller.signal });
    clearTimeout(t);
    logger.debug(`keep-warm ping ${url} → ${res.status}`);
  } catch (err) {
    // A failed ping isn't fatal — just log and let the next interval retry.
    logger.warn(`keep-warm ping failed: ${err.message}`);
  }
}

/** Start the interval self-ping. Returns true if started, false if disabled. */
export function startKeepWarm() {
  if (!config.keepWarm.enabled) {
    logger.info('Keep-warm disabled (no public https URL, or KEEP_WARM=off).');
    return false;
  }
  if (timer) return true; // already running

  const everyMs = config.keepWarm.intervalMs;
  // Fire the first ping after one interval, not immediately — the process is
  // obviously already warm at boot.
  timer = setInterval(pingOnce, everyMs);
  // Don't let this timer keep the event loop alive on shutdown.
  if (typeof timer.unref === 'function') timer.unref();
  logger.info(`Keep-warm enabled: self-ping ${config.publicBaseUrl}/healthz every ${Math.round(everyMs / 1000)}s.`);
  return true;
}

/** Stop the interval (used by tests). */
export function stopKeepWarm() {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
}

export default { startKeepWarm, stopKeepWarm };
