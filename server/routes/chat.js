import { Router } from 'express';
import { companionReply, extractFacts, judgeRecall } from '../gemini.js';
import { appendFacts, readTodaysLog, timeStamp } from '../dailyLog.js';
import { validateChatRequest } from '../validate.js';
import { config } from '../config.js';
import { loadFacts } from '../screening/facts.js';
import {
  buildProbeInstruction,
  chooseNextFact,
  shouldProbe,
  stateAfterIdleTurn,
  stateAfterJudge,
  stateAfterProbe,
} from '../screening/probes.js';
import { appendResult, loadState, saveState } from '../screening/store.js';

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

/** Last companion message before the elder's reply — the probe question. */
function lastModelText(messages) {
  const modelMessages = messages.filter((message) => message.role === 'model');
  return modelMessages.length > 0 ? modelMessages[modelMessages.length - 1].text : '';
}

/** Judges the elder's reply to a pending probe and records the verdict. */
async function judgePendingProbe(pendingProbe, messages) {
  const facts = await loadFacts(config.factsPath);
  const fact = facts.find((candidate) => candidate.id === pendingProbe.factId);
  if (!fact) {
    console.error(`[screening] Pending probe fact "${pendingProbe.factId}" no longer exists.`);
    return;
  }
  const reply = messages[messages.length - 1].text;
  const { verdict, confidence } = await judgeRecall({
    fact,
    question: lastModelText(messages),
    reply,
  });
  const now = new Date();
  await appendResult(config.logsDir, {
    time: timeStamp(now),
    factId: fact.id,
    label: fact.label,
    tier: fact.tier,
    verdict,
    confidence,
  }, now);
  console.log(`[screening] Judged "${fact.id}" as ${verdict} (${confidence}).`);
}

/**
 * Advances the probe state machine for this turn. Returns the hidden
 * instruction for the companion (or null) and the probe to judge (or null).
 */
async function advanceScreening(messages) {
  const state = await loadState(config.logsDir);

  if (state.pendingProbe) {
    await saveState(config.logsDir, stateAfterJudge(state));
    return { probeInstruction: null, probeToJudge: state.pendingProbe };
  }

  if (shouldProbe(state)) {
    const facts = await loadFacts(config.factsPath);
    const fact = chooseNextFact(facts, state.lastProbed);
    if (fact) {
      await saveState(config.logsDir, stateAfterProbe(state, fact.id, new Date().toISOString()));
      return { probeInstruction: buildProbeInstruction(fact), probeToJudge: null };
    }
  }

  await saveState(config.logsDir, stateAfterIdleTurn(state));
  return { probeInstruction: null, probeToJudge: null };
}

export const chatRouter = Router();

chatRouter.post('/api/chat', async (req, res) => {
  const result = validateChatRequest(req.body);
  if (!result.ok) {
    res.status(400).json({ success: false, data: null, error: result.error });
    return;
  }

  let screening = { probeInstruction: null, probeToJudge: null };
  try {
    screening = await advanceScreening(result.messages);
  } catch (error) {
    console.error('[screening] Probe scheduling failed, continuing without it:', error);
  }

  try {
    const reply = await companionReply(result.messages, screening.probeInstruction);
    res.json({ success: true, data: { reply }, error: null });

    logNewFacts(result.messages).catch((error) => {
      console.error('[daily-log] Failed to update today\'s log:', error);
    });
    if (screening.probeToJudge) {
      judgePendingProbe(screening.probeToJudge, result.messages).catch((error) => {
        console.error('[screening] Failed to judge probe reply:', error);
      });
    }
  } catch (error) {
    console.error('[chat] Gemini request failed:', error);
    res.status(502).json({
      success: false,
      data: null,
      error: 'The companion is unavailable right now. Please try again in a moment.',
    });
  }
});
