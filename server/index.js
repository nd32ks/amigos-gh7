import express from 'express';
import os from 'node:os';
import { config, assertConfig } from './config.js';
import { chatRouter } from './routes/chat.js';
import { logsRouter } from './routes/logs.js';
import { screeningRouter } from './routes/screening.js';

assertConfig();

const app = express();
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

function lanAddresses() {
  return Object.values(os.networkInterfaces())
    .flat()
    .filter((iface) => iface && iface.family === 'IPv4' && !iface.internal)
    .map((iface) => iface.address);
}

app.listen(config.port, '0.0.0.0', () => {
  console.log(`Kinbridge is running:`);
  console.log(`  Local:   http://localhost:${config.port}`);
  for (const address of lanAddresses()) {
    console.log(`  Network: http://${address}:${config.port}  (open this on your phone)`);
  }
});
