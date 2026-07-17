import test from 'node:test';
import assert from 'node:assert/strict';
import {
  INITIAL_STATE,
  PROBE_EVERY_N_TURNS,
  buildProbeInstruction,
  chooseNextFact,
  shouldProbe,
  stateAfterIdleTurn,
  stateAfterJudge,
  stateAfterProbe,
} from '../server/screening/probes.js';

const FACTS = [
  { id: 'a', tier: 1, label: 'A', probe: 'Ask about A.', canonical: 'x', aliases: [] },
  { id: 'b', tier: 2, label: 'B', probe: 'Ask about B.', canonical: 'y', aliases: [] },
];

test('does not probe before enough turns have passed', () => {
  assert.equal(shouldProbe(INITIAL_STATE), false);
});

test('probes once the turn threshold is reached', () => {
  const state = { ...INITIAL_STATE, turnsSinceProbe: PROBE_EVERY_N_TURNS - 1 };
  assert.equal(shouldProbe(state), true);
});

test('never probes while another probe is pending', () => {
  const state = {
    ...INITIAL_STATE,
    turnsSinceProbe: PROBE_EVERY_N_TURNS,
    pendingProbe: { factId: 'a', askedAt: '2026-07-17T10:00:00Z' },
  };
  assert.equal(shouldProbe(state), false);
});

test('chooseNextFact prefers never-probed facts in file order', () => {
  const fact = chooseNextFact(FACTS, { a: '2026-07-17T10:00:00Z' });
  assert.equal(fact.id, 'b');
});

test('chooseNextFact falls back to the least recently probed fact', () => {
  const fact = chooseNextFact(FACTS, {
    a: '2026-07-10T10:00:00Z',
    b: '2026-07-16T10:00:00Z',
  });
  assert.equal(fact.id, 'a');
});

test('chooseNextFact returns null for an empty fact list', () => {
  assert.equal(chooseNextFact([], {}), null);
});

test('state transitions never mutate the original state', () => {
  const state = { ...INITIAL_STATE, turnsSinceProbe: 2 };

  const probed = stateAfterProbe(state, 'a', '2026-07-17T10:00:00Z');
  const judged = stateAfterJudge(probed);
  const idled = stateAfterIdleTurn(state);

  assert.equal(state.turnsSinceProbe, 2);
  assert.equal(state.pendingProbe, null);
  assert.equal(probed.pendingProbe.factId, 'a');
  assert.equal(probed.lastProbed.a, '2026-07-17T10:00:00Z');
  assert.equal(judged.pendingProbe, null);
  assert.equal(judged.turnsSinceProbe, 0);
  assert.equal(idled.turnsSinceProbe, 3);
});

test('probe instruction includes the fact hint but never the answer', () => {
  const fact = {
    id: 'spouse',
    tier: 1,
    label: 'Spouse',
    probe: 'Ask how their spouse is doing.',
    canonical: 'Harto',
    aliases: ['Pak Harto'],
  };
  const instruction = buildProbeInstruction(fact);
  assert.match(instruction, /Ask how their spouse is doing\./);
  assert.doesNotMatch(instruction, /Harto/);
  assert.match(instruction, /never a quiz/i);
});
