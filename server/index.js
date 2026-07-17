import os from 'node:os';
import { config, assertConfig } from './config.js';
import { app } from './app.js';

assertConfig();

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
