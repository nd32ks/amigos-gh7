import { promises as fs } from 'node:fs';
import path from 'node:path';
import { dateStamp, isValidDateStamp } from '../dailyLog.js';
import { INITIAL_STATE } from './probes.js';

const SCREENING_SUBDIR = 'screening';
const STATE_FILE = 'state.json';

function screeningDir(logsDir) {
  return path.join(logsDir, SCREENING_SUBDIR);
}

async function readJsonFile(filePath, fallback) {
  try {
    return JSON.parse(await fs.readFile(filePath, 'utf8'));
  } catch (error) {
    if (error.code !== 'ENOENT') {
      console.error(`[screening] Could not read ${filePath}, using fallback:`, error.message);
    }
    return fallback;
  }
}

export async function loadState(logsDir) {
  const state = await readJsonFile(path.join(screeningDir(logsDir), STATE_FILE), null);
  return state && typeof state === 'object'
    ? { ...INITIAL_STATE, ...state }
    : { ...INITIAL_STATE };
}

export async function saveState(logsDir, state) {
  const dir = screeningDir(logsDir);
  await fs.mkdir(dir, { recursive: true });
  await fs.writeFile(path.join(dir, STATE_FILE), JSON.stringify(state, null, 2), 'utf8');
}

/** Appends one judged probe result to the day's screening file. */
export async function appendResult(logsDir, result, now = new Date()) {
  const dir = screeningDir(logsDir);
  await fs.mkdir(dir, { recursive: true });
  const filePath = path.join(dir, `${dateStamp(now)}.json`);
  const existing = await readJsonFile(filePath, []);
  const updated = [...(Array.isArray(existing) ? existing : []), result];
  await fs.writeFile(filePath, JSON.stringify(updated, null, 2), 'utf8');
  return filePath;
}

/** Reads every day's screening results: { 'YYYY-MM-DD': [results] }. */
export async function readResultsByDate(logsDir) {
  const dir = screeningDir(logsDir);
  let entries;
  try {
    entries = await fs.readdir(dir);
  } catch (error) {
    if (error.code === 'ENOENT') {
      return {};
    }
    throw error;
  }
  const days = entries
    .filter((name) => name.endsWith('.json'))
    .map((name) => name.slice(0, -'.json'.length))
    .filter(isValidDateStamp);

  const loaded = await Promise.all(
    days.map(async (day) => [day, await readJsonFile(path.join(dir, `${day}.json`), [])]),
  );
  return Object.fromEntries(loaded);
}
