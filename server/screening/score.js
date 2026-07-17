/**
 * Cognitive Recall Index scoring — pure functions.
 * Tier weights follow the KinBridge spec: core identity ×10,
 * recent events ×3, preferences ×2. "no_answer" is excluded
 * from scoring (a topic change is not a miss).
 */

export const TIER_WEIGHTS = Object.freeze({ 1: 10, 2: 3, 3: 2 });

export const VERDICT_POINTS = Object.freeze({
  exact: 1,
  partial: 0.5,
  miss: 0,
  no_answer: null,
});

const EWMA_ALPHA = 0.3;
const INDEX_SCALE = 100;

/** Weighted recall index (0-100) for one day's results, or null if unscorable. */
export function dailyIndex(results) {
  const scored = (results ?? []).filter(
    (result) => VERDICT_POINTS[result.verdict] !== null
      && VERDICT_POINTS[result.verdict] !== undefined
      && TIER_WEIGHTS[result.tier] !== undefined,
  );
  if (scored.length === 0) {
    return null;
  }
  const totalWeight = scored.reduce((sum, result) => sum + TIER_WEIGHTS[result.tier], 0);
  const earned = scored.reduce(
    (sum, result) => sum + TIER_WEIGHTS[result.tier] * VERDICT_POINTS[result.verdict],
    0,
  );
  return Math.round((INDEX_SCALE * earned) / totalWeight);
}

/** Exponentially weighted moving average over a numeric series. */
export function ewmaSeries(values, alpha = EWMA_ALPHA) {
  return values.reduce((series, value) => {
    const previous = series.length > 0 ? series[series.length - 1] : value;
    return [...series, Math.round(alpha * value + (1 - alpha) * previous)];
  }, []);
}

/**
 * Builds the dashboard trend: one entry per day that has scorable results,
 * dates ascending, each with the day's index and the smoothed EWMA value.
 */
export function buildTrend(resultsByDate) {
  const days = Object.keys(resultsByDate)
    .sort()
    .map((date) => ({ date, index: dailyIndex(resultsByDate[date]) }))
    .filter((day) => day.index !== null);

  const smoothed = ewmaSeries(days.map((day) => day.index));
  return days.map((day, position) => ({ ...day, ewma: smoothed[position] }));
}

/** Counts tier-1 (core identity) misses in a day's results. */
export function tier1Misses(results) {
  return (results ?? []).filter(
    (result) => result.tier === 1 && result.verdict === 'miss',
  ).length;
}
