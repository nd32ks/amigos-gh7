import { promises as fs } from 'node:fs';

let cachedFacts = null;

function isValidFact(fact) {
  return typeof fact === 'object' && fact !== null
    && typeof fact.id === 'string'
    && [1, 2, 3].includes(fact.tier)
    && typeof fact.label === 'string'
    && typeof fact.probe === 'string'
    && typeof fact.canonical === 'string'
    && Array.isArray(fact.aliases);
}

/** Loads and validates the ground-truth facts file (cached after first read). */
export async function loadFacts(factsPath) {
  if (cachedFacts) {
    return cachedFacts;
  }
  const parsed = JSON.parse(await fs.readFile(factsPath, 'utf8'));
  const facts = Array.isArray(parsed?.facts) ? parsed.facts.filter(isValidFact) : [];
  if (facts.length === 0) {
    throw new Error(`No valid facts found in ${factsPath}.`);
  }
  cachedFacts = Object.freeze(facts.map((fact) => Object.freeze({ ...fact })));
  return cachedFacts;
}
