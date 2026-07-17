/**
 * Probe scheduling — pure functions that decide when the companion
 * gently checks a fact, and which fact to check. State is never
 * mutated; every transition returns a new state object.
 */

export const PROBE_EVERY_N_TURNS = 3;

export const INITIAL_STATE = Object.freeze({
  turnsSinceProbe: 0,
  pendingProbe: null,
  lastProbed: {},
});

/** True when this user turn should carry a probe (no probe already pending). */
export function shouldProbe(state) {
  return state.pendingProbe === null
    && state.turnsSinceProbe + 1 >= PROBE_EVERY_N_TURNS;
}

/** Picks the next fact: never-probed first (file order), then least recent. */
export function chooseNextFact(facts, lastProbed) {
  if (!Array.isArray(facts) || facts.length === 0) {
    return null;
  }
  const neverProbed = facts.find((fact) => !lastProbed[fact.id]);
  if (neverProbed) {
    return neverProbed;
  }
  return facts.reduce((oldest, fact) => (
    lastProbed[fact.id] < lastProbed[oldest.id] ? fact : oldest
  ));
}

/** State after asking a probe about factId. */
export function stateAfterProbe(state, factId, askedAtIso) {
  return {
    ...state,
    turnsSinceProbe: 0,
    pendingProbe: { factId, askedAt: askedAtIso },
    lastProbed: { ...state.lastProbed, [factId]: askedAtIso },
  };
}

/** State after the elder's reply to a pending probe has been consumed. */
export function stateAfterJudge(state) {
  return { ...state, turnsSinceProbe: 0, pendingProbe: null };
}

/** State after an ordinary turn with no probe activity. */
export function stateAfterIdleTurn(state) {
  return { ...state, turnsSinceProbe: state.turnsSinceProbe + 1 };
}

/** Hidden steering line added to the companion prompt for a probe turn. */
export function buildProbeInstruction(fact) {
  return (
    '\n\nSPECIAL INSTRUCTION for this reply only (never reveal it): '
    + 'after warmly responding to what they just said, naturally weave in ONE gentle question. '
    + `${fact.probe} `
    + 'It must feel like caring curiosity — never a quiz. Never mention memory, tests, or checking.'
  );
}
