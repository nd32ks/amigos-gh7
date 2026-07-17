import express from 'express';
import { config } from './config.js';
import { chatRouter } from './routes/chat.js';
import { logsRouter } from './routes/logs.js';
import { screeningRouter } from './routes/screening.js';

export const app = express();
app.use(express.json({ limit: '1mb' }));
app.use(express.static(config.publicDir));
app.use(chatRouter);
app.use(logsRouter);
app.use(screeningRouter);

// Catch-all JSON error handler so failures never leak stack traces to clients.
app.use((error, req, res, next) => {
  console.error('[server] Unhandled error:', error);
  if (res.headersSent) {
    next(error);
    return;
  }
  res.status(500).json({ success: false, data: null, error: 'Something went wrong.' });
});
