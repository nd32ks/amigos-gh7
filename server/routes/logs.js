import { Router } from 'express';
import { isValidDateStamp, listLogDays, readDayLog } from '../dailyLog.js';
import { config } from '../config.js';

export const logsRouter = Router();

logsRouter.get('/api/logs', async (req, res) => {
  try {
    const days = await listLogDays(config.logsDir);
    res.json({ success: true, data: { days }, error: null });
  } catch (error) {
    console.error('[logs] Failed to list log days:', error);
    res.status(500).json({ success: false, data: null, error: 'Could not list journals.' });
  }
});

logsRouter.get('/api/logs/:date', async (req, res) => {
  const { date } = req.params;
  if (!isValidDateStamp(date)) {
    res.status(400).json({ success: false, data: null, error: 'Date must look like 2026-07-17.' });
    return;
  }
  try {
    const content = await readDayLog(config.logsDir, date);
    if (content === null) {
      res.status(404).json({ success: false, data: null, error: 'No journal for that day yet.' });
      return;
    }
    res.json({ success: true, data: { date, content }, error: null });
  } catch (error) {
    console.error('[logs] Failed to read log:', error);
    res.status(500).json({ success: false, data: null, error: 'Could not read that journal.' });
  }
});
