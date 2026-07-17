import { globalStore } from "./store";
import type { Fact, Verdict, CriEvent, Session } from "./types";

export const TIER_WEIGHTS: Record<Fact["tier"], number> = {
  1: 10,
  2: 3,
  3: 2,
};

export const CREDIT: Record<Verdict, number> = {
  exact: 1.0,
  partial: 0.5,
  miss: 0.0,
  no_answer: 0.0,
  excluded_stt: 0.0,
};

export const RAW_POINTS: Record<Verdict, Record<Fact["tier"], number>> = {
  exact: { 1: 1, 2: 1, 3: 1 },
  partial: { 1: 0, 2: 0, 3: 0 },
  miss: { 1: -10, 2: -4, 3: -2 },
  no_answer: { 1: 0, 2: 0, 3: 0 },
  excluded_stt: { 1: 0, 2: 0, 3: 0 },
};

export const EWMA_ALPHA = 0.3;

export function computeCri(events: CriEvent[]): number {
  if (events.length === 0) return 0;
  const sumW = events.reduce((acc, e) => acc + e.w, 0);
  const sumWS = events.reduce((acc, e) => acc + e.w * e.s, 0);
  return 100 * (sumWS / sumW);
}

export function computeEwma(cri: number, previousEwma: number): number {
  return EWMA_ALPHA * cri + (1 - EWMA_ALPHA) * previousEwma;
}

export function getT1Misses30d(): number {
  const cutoff = new Date();
  cutoff.setDate(cutoff.getDate() - 30);
  return globalStore.events.filter(
    (e) => e.tier === 1 && e.verdict === "miss" && new Date(e.ts) >= cutoff
  ).length;
}

export function getT2Misses7d(): number {
  const cutoff = new Date();
  cutoff.setDate(cutoff.getDate() - 7);
  return globalStore.events.filter(
    (e) => e.tier === 2 && e.verdict === "miss" && new Date(e.ts) >= cutoff
  ).length;
}

export function getEwmaDrop7d(): number {
  const trend = globalStore.trend;
  if (trend.length < 2) return 0;
  const current = trend[trend.length - 1].ewma;
  const sevenDaysAgo = trend[Math.max(0, trend.length - 8)].ewma;
  return sevenDaysAgo - current;
}

export function humanizeEvent(verdict: Verdict, fact: Fact): string {
  const name = fact.canonical_value;
  const label = Array.isArray(name) ? name[0] : name;

  switch (verdict) {
    case "exact":
      return `Remembered ${label}`;
    case "partial":
      return `Hesitated on ${label}`;
    case "miss":
      return `Couldn't recall ${label}`;
    case "no_answer":
      return `Changed topic when asked about ${label}`;
    case "excluded_stt":
      return `Low confidence reply about ${label}`;
    default:
      return `Mentioned ${label}`;
  }
}

export function getCriSession(session: Session): number {
  return computeCri(session.cri_events);
}
