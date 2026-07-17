import { promises as fs } from 'node:fs';
import path from 'node:path';

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

function pad(value) {
  return String(value).padStart(2, '0');
}

export function dateStamp(date = new Date()) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function timeStamp(date = new Date()) {
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function isValidDateStamp(value) {
  return typeof value === 'string' && DATE_PATTERN.test(value);
}

function logFilePath(logsDir, stamp) {
  return path.join(logsDir, `${stamp}.txt`);
}

function buildHeader(stamp) {
  return `Kinbridge — Daily Conversation Log\nDate: ${stamp}\n${'='.repeat(24)}\n\n`;
}

async function fileExists(filePath) {
  try {
    await fs.access(filePath);
    return true;
  } catch {
    return false;
  }
}

/**
 * Appends newly learned facts to today's log file, creating the file
 * (one per day) with a header on first write. Returns the file path,
 * or null when there was nothing to write.
 */
export async function appendFacts(logsDir, facts, now = new Date()) {
  if (!Array.isArray(facts) || facts.length === 0) {
    return null;
  }
  await fs.mkdir(logsDir, { recursive: true });

  const stamp = dateStamp(now);
  const filePath = logFilePath(logsDir, stamp);
  const isNewFile = !(await fileExists(filePath));

  const lines = facts.map((fact) => `[${timeStamp(now)}] ${fact}`).join('\n');
  const chunk = `${isNewFile ? buildHeader(stamp) : ''}${lines}\n`;

  await fs.appendFile(filePath, chunk, 'utf8');
  return filePath;
}

/** Returns today's log content, or '' if nothing has been logged yet. */
export async function readTodaysLog(logsDir, now = new Date()) {
  return (await readDayLog(logsDir, dateStamp(now))) ?? '';
}

/** Returns the log content for a given day, or null if absent/invalid. */
export async function readDayLog(logsDir, stamp) {
  if (!isValidDateStamp(stamp)) {
    return null;
  }
  try {
    return await fs.readFile(logFilePath(logsDir, stamp), 'utf8');
  } catch (error) {
    if (error.code === 'ENOENT') {
      return null;
    }
    throw error;
  }
}

/** Lists days that have a log file, newest first. */
export async function listLogDays(logsDir) {
  let entries;
  try {
    entries = await fs.readdir(logsDir);
  } catch (error) {
    if (error.code === 'ENOENT') {
      return [];
    }
    throw error;
  }
  return entries
    .filter((name) => name.endsWith('.txt'))
    .map((name) => name.slice(0, -'.txt'.length))
    .filter(isValidDateStamp)
    .sort()
    .reverse();
}
