// Detective Dialer backend — Express entrypoint.
// Wires the on-device screening endpoint (/screen) and the app JSON API (/api/*).

import express from 'express';
import config from './config.js';
import logger from './utils/logger.js';
import screenRoutes from './routes/screen.js';
import screenSmsRoutes from './routes/screenSms.js';
import apiRoutes from './routes/api.js';
import { startKeepWarm } from './services/keepWarm.js';

export function createApp() {
  const app = express();

  // The app posts JSON; accept urlencoded too for easy curl/testing.
  app.use(express.urlencoded({ extended: false }));
  app.use(express.json());

  // Health check (Railway/Render probes hit this).
  app.get('/', (req, res) => {
    res.json({
      service: 'detective-dialer',
      status: 'ok',
      gemini: config.gemini.enabled ? 'live' : 'heuristic-fallback',
      fcm: config.firebase.enabled ? 'configured' : 'log-only',
    });
  });
  app.get('/healthz', (req, res) => res.json({ ok: true }));

  // Optional shared-secret auth: enforced only when API_KEY is set, so a
  // keyless local/dev setup keeps working. Health endpoints above stay open.
  const requireApiKey = (req, res, next) => {
    if (!config.apiKey) return next();
    if (req.get('x-api-key') === config.apiKey) return next();
    logger.warn(`Rejected ${req.method} ${req.originalUrl} — bad or missing X-Api-Key`);
    return res.status(401).json({ error: 'unauthorized' });
  };

  app.use('/screen', requireApiKey, screenRoutes);
  app.use('/screen-sms', requireApiKey, screenSmsRoutes);
  app.use('/api', requireApiKey, apiRoutes);

  // 404
  app.use((req, res) => res.status(404).json({ error: 'not found', path: req.path }));

  // Centralized error handler.
  // eslint-disable-next-line no-unused-vars
  app.use((err, req, res, next) => {
    logger.error('Unhandled error:', err.stack || err.message);
    res.status(500).json({ error: 'internal error' });
  });

  return app;
}

// Only start listening when run directly (not when imported by tests).
const isMain = process.argv[1] && import.meta.url === `file://${process.argv[1]}`;
if (isMain) {
  const app = createApp();
  app.listen(config.port, () => {
    logger.info(`Call screening backend listening on :${config.port}`);
    logger.info(`Public base URL: ${config.publicBaseUrl}`);
    logger.info(`Gemini: ${config.gemini.enabled ? 'enabled' : 'heuristic fallback'} | FCM: ${config.firebase.enabled ? 'enabled' : 'log-only'}`);
    // Prevent Railway cold-start timeouts by keeping the dyno awake (see keepWarm.js).
    startKeepWarm();
  });
}

export default createApp;
