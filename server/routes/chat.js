import { Router } from 'express';
import { companionReply, extractFacts } from '../gemini.js';
import { appendFacts, readTodaysLog } from '../dailyLog.js';
import { validateChatRequest } from '../validate.js';
import { config } from '../config.js';

const RECENT_CONTEXT_SIZE = 6;

/**
 * Runs after the reply is sent: extracts new personal information from the
 * elder's latest message and appends it to today's log file.
 */
async function logNewFacts(messages) {
  const recent = messages.slice(-RECENT_CONTEXT_SIZE);
  const todaysLog = await readTodaysLog(config.logsDir);
  const facts = await extractFacts(recent, todaysLog);
  const filePath = await appendFacts(config.logsDir, facts);
  if (filePath) {
    console.log(`[daily-log] Appended ${facts.length} fact(s) to ${filePath}`);
  }
}

export const chatRouter = Router();

chatRouter.post('/api/chat', async (req, res) => {
  const result = validateChatRequest(req.body);
  if (!result.ok) {
    res.status(400).json({ success: false, data: null, error: result.error });
    return;
  }

  try {
    const reply = await companionReply(result.messages);
    res.json({ success: true, data: { reply }, error: null });

    logNewFacts(result.messages).catch((error) => {
      console.error('[daily-log] Failed to update today\'s log:', error);
    });
  } catch (error) {
    console.error('[chat] Gemini request failed:', error);
    res.status(502).json({
      success: false,
      data: null,
      error: 'The companion is unavailable right now. Please try again in a moment.',
    });
  }
});
