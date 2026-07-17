import test from 'node:test';
import assert from 'node:assert/strict';
import {
  buildTrend,
  dailyIndex,
  ewmaSeries,
  gamePlan,
  tier1Misses,
} from '../server/screening/score.js';

test('dailyIndex weights tiers: core identity dominates', () => {
  // Arrange — tier-1 exact (10 * 1) + tier-3 miss (2 * 0)
  const results = [
    { tier: 1, verdict: 'exact' },
    { tier: 3, verdict: 'miss' },
  ];

  // Act
  const index = dailyIndex(results);

  // Assert — 100 * 10 / 12 = 83
  assert.equal(index, 83);
});

test('dailyIndex scores partial recall as half credit', () => {
  const index = dailyIndex([{ tier: 2, verdict: 'partial' }]);
  assert.equal(index, 50);
});

test('dailyIndex excludes no_answer from scoring', () => {
  const index = dailyIndex([
    { tier: 1, verdict: 'no_answer' },
    { tier: 3, verdict: 'exact' },
  ]);
  assert.equal(index, 100);
});

test('dailyIndex returns null when nothing is scorable', () => {
  assert.equal(dailyIndex([]), null);
  assert.equal(dailyIndex([{ tier: 1, verdict: 'no_answer' }]), null);
  assert.equal(dailyIndex(undefined), null);
});

test('ewmaSeries starts at the first value and smooths toward new values', () => {
  const series = ewmaSeries([100, 0, 0], 0.5);
  assert.deepEqual(series, [100, 50, 25]);
});

test('buildTrend sorts days ascending and attaches ewma', () => {
  const trend = buildTrend({
    '2026-07-17': [{ tier: 1, verdict: 'miss' }],
    '2026-07-15': [{ tier: 1, verdict: 'exact' }],
  });

  assert.equal(trend.length, 2);
  assert.equal(trend[0].date, '2026-07-15');
  assert.equal(trend[0].index, 100);
  assert.equal(trend[1].date, '2026-07-17');
  assert.equal(trend[1].index, 0);
  assert.ok(trend[1].ewma > 0, 'ewma should smooth the drop');
});

test('buildTrend skips days with only no_answer results', () => {
  const trend = buildTrend({
    '2026-07-16': [{ tier: 2, verdict: 'no_answer' }],
  });
  assert.deepEqual(trend, []);
});

test('tier1Misses counts only tier-1 misses', () => {
  const results = [
    { tier: 1, verdict: 'miss' },
    { tier: 1, verdict: 'exact' },
    { tier: 2, verdict: 'miss' },
  ];
  assert.equal(tier1Misses(results), 1);
  assert.equal(tier1Misses(undefined), 0);
});

test('gamePlan leans hard/medium and raises the goal below the low threshold', () => {
  const plan = gamePlan(35);
  assert.equal(plan.tier, 'focus');
  assert.equal(plan.primary, 'hard');
  assert.equal(plan.goal, 3);
  assert.ok(plan.weights.hard > plan.weights.easy);
});

test('gamePlan mirrors into easy/medium and lowers the goal above the high threshold', () => {
  const plan = gamePlan(90);
  assert.equal(plan.tier, 'light');
  assert.equal(plan.primary, 'easy');
  assert.equal(plan.goal, 1);
  assert.ok(plan.weights.easy > plan.weights.hard);
});

test('gamePlan stays balanced between the thresholds', () => {
  const plan = gamePlan(60);
  assert.equal(plan.tier, 'balanced');
  assert.equal(plan.primary, 'medium');
  assert.equal(plan.goal, 2);
});

test('gamePlan is neutral with no index yet', () => {
  const plan = gamePlan(null);
  assert.equal(plan.tier, 'unknown');
  assert.equal(plan.primary, 'medium');
  assert.equal(plan.index, null);
});

test('gamePlan treats the thresholds as boundaries: 50 is balanced, 75 is light', () => {
  assert.equal(gamePlan(50).tier, 'balanced');
  assert.equal(gamePlan(75).tier, 'light');
});
