import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

function loadDotEnv() {
  try {
    process.loadEnvFile(path.join(ROOT_DIR, '.env'));
  } catch {
    // .env is optional — variables may come from the environment instead.
  }
}

loadDotEnv();

export const config = Object.freeze({
  port: Number(process.env.PORT ?? 3000),
  geminiApiKey: process.env.GEMINI_API_KEY ?? '',
  geminiModel: process.env.GEMINI_MODEL ?? 'gemini-2.5-flash',
  logsDir: process.env.LOGS_DIR ?? path.join(ROOT_DIR, 'logs'),
  publicDir: path.join(ROOT_DIR, 'public'),
});

export function assertConfig() {
  if (!config.geminiApiKey) {
    throw new Error(
      'GEMINI_API_KEY is not set. Copy .env.example to .env and add your key.',
    );
  }
  if (!Number.isInteger(config.port) || config.port <= 0) {
    throw new Error(`PORT must be a positive integer, got "${process.env.PORT}".`);
  }
}
