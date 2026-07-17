import os from 'node:os';
import path from 'node:path';

// Serverless filesystems are read-only except for the tmp dir, so the daily
// journal and screening state live there instead of beside the code.
// NOTE: tmp storage is per-instance and ephemeral — hosted journals are not
// durable across cold starts. For real persistence move this to a database.
process.env.LOGS_DIR ??= path.join(os.tmpdir(), 'kinbridge-logs');

// Dynamic imports so LOGS_DIR is set before config.js freezes the config.
const { assertConfig } = await import('../server/config.js');
const { app } = await import('../server/app.js');

assertConfig();

export default app;
