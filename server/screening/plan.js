/**
 * A gentle, non-clinical play plan derived from the existing recall index.
 *
 * This is deliberately a recommendation, not a prescription: the person can
 * always choose any crossword level. Lower scores receive more *short* medium
 * puzzles rather than being labelled or restricted by the app.
 */

const LOW_INDEX_THRESHOLD = 40;
const STEADY_INDEX_THRESHOLD = 70;

function plan({ key, difficulty, gamesPerWeek, optionalDifficulty, title, description }) {
  return Object.freeze({
    key,
    difficulty,
    gamesPerWeek,
    optionalDifficulty,
    title,
    description,
  });
}

const WELCOME_PLAN = plan({
  key: 'welcome',
  difficulty: 'easy',
  gamesPerWeek: 2,
  optionalDifficulty: 'medium',
  title: 'Start with a smile',
  description: 'There is no score to guide today’s rhythm yet. Begin with two gentle games this week, or choose any level that feels fun.',
});

const EXTRA_PLAY_PLAN = plan({
  key: 'extra-play',
  difficulty: 'medium',
  gamesPerWeek: 4,
  optionalDifficulty: 'hard',
  title: 'A little extra play',
  description: 'Try four short, steady puzzles this week. If you feel like stretching, the Challenge crossword is waiting too.',
});

const STEADY_PLAY_PLAN = plan({
  key: 'steady-play',
  difficulty: 'medium',
  gamesPerWeek: 3,
  optionalDifficulty: 'easy',
  title: 'Keep a gentle rhythm',
  description: 'Three steady puzzles this week make a lovely little routine. Take them one at a time, whenever you feel fresh.',
});

const LIGHT_PLAY_PLAN = plan({
  key: 'light-play',
  difficulty: 'easy',
  gamesPerWeek: 2,
  optionalDifficulty: 'medium',
  title: 'Keep it light and lively',
  description: 'Two gentle puzzles this week are plenty for a bright, playful check-in. Choose a steadier one whenever you want a little more.',
});

/**
 * Returns an adaptive crossword recommendation for a 0–100 recall index.
 * A missing or invalid index is treated as unknown, never as a low score.
 */
export function gamePlanForIndex(index) {
  if (!Number.isFinite(index)) {
    return WELCOME_PLAN;
  }
  if (index < LOW_INDEX_THRESHOLD) {
    return EXTRA_PLAY_PLAN;
  }
  if (index < STEADY_INDEX_THRESHOLD) {
    return STEADY_PLAY_PLAN;
  }
  return LIGHT_PLAY_PLAN;
}

export const GAME_PLAN_THRESHOLDS = Object.freeze({
  low: LOW_INDEX_THRESHOLD,
  steady: STEADY_INDEX_THRESHOLD,
});
