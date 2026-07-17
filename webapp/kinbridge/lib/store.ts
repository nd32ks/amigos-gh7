import fs from "fs";
import path from "path";
import type {
  ElderContext,
  Fact,
  Session,
  TrendPoint,
  DashboardEvent,
  AlertPayload,
  CriEvent,
  RawLedgerEntry,
  ConversationTurn,
  MatchResult,
} from "./types";

const ELDER_CONTEXT_PATH = path.join(process.cwd(), "data", "elder_context.json");
const TIER1_PAYLOAD_PATH = path.join(process.cwd(), "data", "tier1_alert_payload.json");
const SEED_PATH = path.join(process.cwd(), "data", "state.seed.json");
const STATE_PATH = path.join(process.cwd(), "data", "state.json");

function loadJson<T>(filePath: string): T {
  const raw = fs.readFileSync(filePath, "utf-8");
  return JSON.parse(raw) as T;
}

export const elderContext: ElderContext = loadJson<ElderContext>(ELDER_CONTEXT_PATH);
export const tier1PayloadExample: AlertPayload = loadJson<AlertPayload>(TIER1_PAYLOAD_PATH);

const seed = loadJson<{ points: TrendPoint[] }>(SEED_PATH);

export interface GlobalState {
  trend: TrendPoint[];
  events: DashboardEvent[];
  alerts: AlertPayload[];
  warnings: string[];
  sessions: Map<string, Session>;
  today: {
    sessions: number;
    minutes_engaged: number;
    probes_scored: number;
  };
}

export const globalStore: GlobalState = {
  trend: [...seed.points],
  events: [],
  alerts: [],
  warnings: [],
  sessions: new Map(),
  today: {
    sessions: 2,
    minutes_engaged: 23,
    probes_scored: 5,
  },
};

export function getContext(): ElderContext {
  return elderContext;
}

export function getFactById(factId: string): Fact | undefined {
  return elderContext.facts.find((f) => f.fact_id === factId);
}

export function createSession(profileId: string = elderContext.profile_id): Session {
  const sessionId = `sess_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
  const session: Session = {
    session_id: sessionId,
    profile_id: profileId,
    state: "idle",
    armed_probe: null,
    cri_events: [],
    raw_ledger: [],
    conversation: [],
    probe_fact_ids_used: [],
    pivot_active: false,
    created_at: new Date().toISOString(),
    last_turn_at: new Date().toISOString(),
  };
  globalStore.sessions.set(sessionId, session);
  globalStore.today.sessions += 1;
  return session;
}

export function getSession(sessionId?: string): Session | undefined {
  if (!sessionId) return undefined;
  return globalStore.sessions.get(sessionId);
}

export function ensureSession(sessionId?: string): Session {
  if (sessionId) {
    const existing = globalStore.sessions.get(sessionId);
    if (existing) return existing;
  }
  return createSession();
}

export function addConversationTurn(session: Session, turn: ConversationTurn) {
  session.conversation.push(turn);
  session.last_turn_at = turn.ts;
}

export function addCriEvent(session: Session, event: CriEvent) {
  session.cri_events.push(event);
}

export function addRawLedger(session: Session, entry: RawLedgerEntry) {
  session.raw_ledger.push(entry);
  globalStore.today.probes_scored += 1;
}

export function addDashboardEvent(event: DashboardEvent) {
  globalStore.events.unshift(event);
  if (globalStore.events.length > 200) {
    globalStore.events = globalStore.events.slice(0, 200);
  }
}

export function addAlert(alert: AlertPayload) {
  globalStore.alerts.unshift(alert);
  if (globalStore.alerts.length > 50) {
    globalStore.alerts = globalStore.alerts.slice(0, 50);
  }
}

export function addWarning(warning: string) {
  if (!globalStore.warnings.includes(warning)) {
    globalStore.warnings.push(warning);
  }
}

export function appendTrendPoint(point: TrendPoint) {
  // Replace if same date exists
  const idx = globalStore.trend.findIndex((p) => p.date === point.date);
  if (idx >= 0) {
    globalStore.trend[idx] = point;
  } else {
    globalStore.trend.push(point);
  }
  // Keep only last 90 days
  if (globalStore.trend.length > 90) {
    globalStore.trend = globalStore.trend.slice(-90);
  }
  persist();
}

export function getTrend(days = 30): TrendPoint[] {
  return globalStore.trend.slice(-days);
}

export function getEvents(limit = 50): DashboardEvent[] {
  return globalStore.events.slice(0, limit);
}

export function getLatestEwma(): number {
  const last = globalStore.trend[globalStore.trend.length - 1];
  return last ? last.ewma : 80;
}

export function getSummary() {
  const latest = getTrend(1)[0];
  const previous = globalStore.trend[globalStore.trend.length - 8];
  const ewmaDelta = latest && previous ? latest.ewma - previous.ewma : 0;
  return {
    elder: {
      profile_id: elderContext.profile_id,
      name: elderContext.elder.name,
      preferred_address: elderContext.elder.preferred_address,
    },
    today: globalStore.today,
    cri_latest: latest ? latest.cri : 0,
    ewma_7d: latest ? latest.ewma : 0,
    trend_direction: ewmaDelta < -5 ? "declining" : ewmaDelta > 5 ? "improving" : "stable",
    active_warnings: globalStore.warnings,
  };
}

export function getMatchResult(): MatchResult {
  return { matched: false };
}

export function persist() {
  try {
    const snapshot = {
      trend: globalStore.trend,
      events: globalStore.events,
      warnings: globalStore.warnings,
      today: globalStore.today,
    };
    fs.writeFileSync(STATE_PATH, JSON.stringify(snapshot, null, 2));
  } catch {
    // Persistence is best-effort; in-memory state is the source of truth.
  }
}

export function loadPersisted() {
  try {
    if (!fs.existsSync(STATE_PATH)) return;
    const raw = fs.readFileSync(STATE_PATH, "utf-8");
    const snapshot = JSON.parse(raw) as Partial<GlobalState>;
    if (snapshot.trend) globalStore.trend = snapshot.trend;
    if (snapshot.events) globalStore.events = snapshot.events;
    if (snapshot.warnings) globalStore.warnings = snapshot.warnings;
    if (snapshot.today) globalStore.today = snapshot.today as GlobalState["today"];
  } catch {
    // Ignore corrupted state file.
  }
}

loadPersisted();
