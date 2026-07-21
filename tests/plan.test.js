import test from 'node:test';
import assert from 'node:assert/strict';
import { gamePlanForIndex } from '../server/screening/plan.js';

test('gamePlanForIndex welcomes people without a recall signal', () => {
  const plan = gamePlanForIndex(null);

  assert.equal(plan.key, 'welcome');
  assert.equal(plan.difficulty, 'easy');
  assert.equal(plan.gamesPerWeek, 2);
});

test('gamePlanForIndex recommends more medium play below the low threshold', () => {
  const plan = gamePlanForIndex(39);

  assert.equal(plan.key, 'extra-play');
  assert.equal(plan.difficulty, 'medium');
  assert.equal(plan.gamesPerWeek, 4);
  assert.equal(plan.optionalDifficulty, 'hard');
});

test('gamePlanForIndex keeps a steady medium rhythm in the middle band', () => {
  const plan = gamePlanForIndex(40);

  assert.equal(plan.key, 'steady-play');
  assert.equal(plan.difficulty, 'medium');
  assert.equal(plan.gamesPerWeek, 3);
});

test('gamePlanForIndex eases to gentle games at the upper threshold', () => {
  const plan = gamePlanForIndex(70);

  assert.equal(plan.key, 'light-play');
  assert.equal(plan.difficulty, 'easy');
  assert.equal(plan.gamesPerWeek, 2);
});
