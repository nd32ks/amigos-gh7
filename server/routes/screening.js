import { Router } from 'express';
import { config } from '../config.js';
import { loadFacts } from '../screening/facts.js';
import { readResultsByDate } from '../screening/store.js';
import { buildTrend, dailyIndex, tier1Misses } from '../screening/score.js';
import { gamePlanForIndex } from '../screening/plan.js';
import { dateStamp } from '../dailyLog.js';

export const screeningRouter = Router();

screeningRouter.get('/api/screening', async (req, res) => {
  try {
    const [facts, resultsByDate] = await Promise.all([
      loadFacts(config.factsPath),
      readResultsByDate(config.logsDir),
    ]);

    const today = dateStamp();
    const todaysResults = resultsByDate[today] ?? [];
    const todayIndex = dailyIndex(todaysResults);
    const trend = buildTrend(resultsByDate);
    const latestTrend = trend.at(-1) ?? null;
    // The smoothed value makes the game plan less jumpy than a single day.
    const gamePlan = gamePlanForIndex(latestTrend?.ewma ?? todayIndex);

    res.json({
      success: true,
      data: {
        facts: facts.map((fact) => ({ id: fact.id, label: fact.label, tier: fact.tier })),
        today: {
          date: today,
          index: todayIndex,
          probes: todaysResults,
          tier1Misses: tier1Misses(todaysResults),
        },
        trend,
        gamePlan,
      },
      error: null,
    });
  } catch (error) {
    console.error('[screening] Failed to build dashboard data:', error);
    res.status(500).json({ success: false, data: null, error: 'Could not load wellness data.' });
  }
});
