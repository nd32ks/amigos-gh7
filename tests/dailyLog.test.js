import test from 'node:test';
import assert from 'node:assert/strict';
import { promises as fs } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {
  appendFacts,
  dateStamp,
  isValidDateStamp,
  listLogDays,
  readDayLog,
  readTodaysLog,
  timeStamp,
} from '../server/dailyLog.js';

async function makeTmpDir() {
  return fs.mkdtemp(path.join(os.tmpdir(), 'kinbridge-logs-'));
}

test('dateStamp formats a date as YYYY-MM-DD', () => {
  // Arrange
  const date = new Date(2026, 6, 17, 9, 5);

  // Act
  const stamp = dateStamp(date);

  // Assert
  assert.equal(stamp, '2026-07-17');
});

test('timeStamp formats a date as HH:MM with zero padding', () => {
  const date = new Date(2026, 6, 17, 9, 5);
  assert.equal(timeStamp(date), '09:05');
});

test('isValidDateStamp rejects path traversal attempts', () => {
  assert.equal(isValidDateStamp('2026-07-17'), true);
  assert.equal(isValidDateStamp('../../etc/passwd'), false);
  assert.equal(isValidDateStamp('2026-07-17/../x'), false);
  assert.equal(isValidDateStamp(''), false);
  assert.equal(isValidDateStamp(null), false);
});

test('appendFacts creates one file per day with a header', async () => {
  // Arrange
  const dir = await makeTmpDir();
  const now = new Date(2026, 6, 17, 14, 30);

  // Act
  const filePath = await appendFacts(dir, ['She had soup for lunch.'], now);
  const content = await fs.readFile(filePath, 'utf8');

  // Assert
  assert.equal(path.basename(filePath), '2026-07-17.txt');
  assert.match(content, /Kinbridge — Daily Conversation Log/);
  assert.match(content, /Date: 2026-07-17/);
  assert.match(content, /\[14:30\] She had soup for lunch\./);
});

test('appendFacts appends to the same file for later conversations that day', async () => {
  const dir = await makeTmpDir();
  const morning = new Date(2026, 6, 17, 9, 0);
  const evening = new Date(2026, 6, 17, 19, 45);

  const first = await appendFacts(dir, ['Her grandson Budi visited.'], morning);
  const second = await appendFacts(dir, ['She plans to garden tomorrow.'], evening);
  const content = await fs.readFile(second, 'utf8');

  assert.equal(first, second);
  assert.match(content, /\[09:00\] Her grandson Budi visited\./);
  assert.match(content, /\[19:45\] She plans to garden tomorrow\./);
  const headerCount = content.split('Kinbridge — Daily Conversation Log').length - 1;
  assert.equal(headerCount, 1);
});

test('appendFacts returns null and writes nothing when there are no facts', async () => {
  const dir = await makeTmpDir();

  const filePath = await appendFacts(dir, [], new Date());

  assert.equal(filePath, null);
  assert.deepEqual(await listLogDays(dir), []);
});

test('listLogDays returns days newest first and ignores foreign files', async () => {
  const dir = await makeTmpDir();
  await appendFacts(dir, ['Fact A.'], new Date(2026, 6, 15, 10, 0));
  await appendFacts(dir, ['Fact B.'], new Date(2026, 6, 17, 10, 0));
  await fs.writeFile(path.join(dir, 'notes.md'), 'not a log', 'utf8');

  const days = await listLogDays(dir);

  assert.deepEqual(days, ['2026-07-17', '2026-07-15']);
});

test('listLogDays returns [] when the logs directory does not exist yet', async () => {
  const days = await listLogDays(path.join(os.tmpdir(), 'kinbridge-does-not-exist'));
  assert.deepEqual(days, []);
});

test('readDayLog returns null for missing or invalid days', async () => {
  const dir = await makeTmpDir();

  assert.equal(await readDayLog(dir, '2020-01-01'), null);
  assert.equal(await readDayLog(dir, '../secrets'), null);
});

test('readTodaysLog returns empty string before anything is logged', async () => {
  const dir = await makeTmpDir();
  assert.equal(await readTodaysLog(dir), '');
});
