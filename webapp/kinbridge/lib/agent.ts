import type { Fact, JudgeResult, Session, AlertPayload } from "./types";
import { getFactById, createSession, addConversationTurn, addCriEvent, addRawLedger, addDashboardEvent, addAlert, addWarning, appendTrendPoint, getLatestEwma, elderContext } from "./store";
import { judge } from "./judge";
import { findMatch } from "./match";
import {
  computeCri,
  computeEwma,
  CREDIT,
  RAW_POINTS,
  TIER_WEIGHTS,
  humanizeEvent,
  getT1Misses30d,
  getT2Misses7d,
  getEwmaDrop7d,
} from "./scoring";

export const MIN_PROBES = 3;
const STT_CONFIDENCE_GATE = 0.75;
const PROBE_TIMEOUT_S = 45;

export function pickNextProbe(session: Session): Fact | null {
  const now = new Date();
  const eligible = elderContext.facts.filter((f) => {
    if (session.probe_fact_ids_used.includes(f.fact_id)) return false;
    if (f.valid_until && new Date(f.valid_until) < now) return false;
    if (f.last_probed_at) {
      const cooldown = new Date(f.last_probed_at);
      cooldown.setHours(cooldown.getHours() + f.probe_cooldown_hours);
      if (now < cooldown) return false;
    }
    return true;
  });

  if (eligible.length === 0) return null;

  // Priority: T1 not probed in 48h > expiring T2 > T3 round-robin
  const nowMs = now.getTime();
  eligible.sort((a, b) => {
    const aT1Urgent = a.tier === 1 && (!a.last_probed_at || nowMs - new Date(a.last_probed_at).getTime() > 48 * 3600 * 1000);
    const bT1Urgent = b.tier === 1 && (!b.last_probed_at || nowMs - new Date(b.last_probed_at).getTime() > 48 * 3600 * 1000);
    if (aT1Urgent && !bT1Urgent) return -1;
    if (bT1Urgent && !aT1Urgent) return 1;

    const aExpiring = a.valid_until && new Date(a.valid_until).getTime() - nowMs < 24 * 3600 * 1000;
    const bExpiring = b.valid_until && new Date(b.valid_until).getTime() - nowMs < 24 * 3600 * 1000;
    if (aExpiring && !bExpiring) return -1;
    if (bExpiring && !aExpiring) return 1;

    if (a.tier !== b.tier) return a.tier - b.tier;
    return (a.last_probed_at ? new Date(a.last_probed_at).getTime() : 0) - (b.last_probed_at ? new Date(b.last_probed_at).getTime() : 0);
  });

  return eligible[0];
}

export function buildProbeText(fact: Fact): string {
  const templates = fact.probe_templates_id || [];
  return templates.length > 0 ? templates[0] : "Ngomong-ngomong, boleh cerita sedikit?";
}

export function armProbe(session: Session, fact: Fact) {
  session.armed_probe = {
    fact_id: fact.fact_id,
    fact,
    asked_at: new Date().toISOString(),
    timeout_s: PROBE_TIMEOUT_S,
  };
  session.state = "await_response";
  session.probe_fact_ids_used.push(fact.fact_id);
  fact.last_probed_at = new Date().toISOString();
  addConversationTurn(session, {
    role: "companion",
    text: buildProbeText(fact),
    ts: new Date().toISOString(),
    probe_fact_id: fact.fact_id,
  });
}

export function buildCompanionGreeting(): string {
  const hour = new Date().getHours();
  const timeGreeting =
    hour < 11 ? "Selamat pagi" : hour < 15 ? "Selamat siang" : hour < 19 ? "Selamat sore" : "Selamat malam";
  return `${timeGreeting}, ${elderContext.elder.preferred_address}. Ada cerita menarik hari ini?`;
}

export function buildCalmReassurance(): string {
  return "Sepertinya Ibu sudah lelah hari ini. Kita istirahat dulu ya. Mau saya teleponkan Dewi supaya Ibu bisa dengar suaranya?";
}

export function buildAcuteAlert(
  session: Session,
  fact: Fact,
  verdict: JudgeResult,
  criSession: number,
  ewma: number
): AlertPayload {
  const contact = elderContext.escalation_contacts[0];
  const excerpt = session.conversation.slice(-2).map((t) => ({ role: t.role, text: t.text }));
  return {
    alert_id: `alrt_${Math.random().toString(36).slice(2, 10)}`,
    schema_version: "1.0.0",
    type: "COGNITIVE_ACUTE_T1",
    severity: "HIGH",
    created_at: new Date().toISOString(),
    elder: { profile_id: elderContext.profile_id, name: elderContext.elder.name },
    trigger: {
      fact_id: fact.fact_id,
      tier: fact.tier,
      category: fact.category,
      expected_value: Array.isArray(fact.canonical_value) ? fact.canonical_value.join(" / ") : fact.canonical_value,
      recalled_value: verdict.recalled_value,
      verdict: verdict.verdict,
      judge_confidence: verdict.confidence,
      session_id: session.session_id,
      transcript_excerpt: excerpt,
    },
    cognitive_snapshot: {
      cri_session: Math.round(criSession * 10) / 10,
      ewma_7d: Math.round(ewma * 10) / 10,
      ewma_delta_7d: Math.round((ewma - getLatestEwma()) * 10) / 10,
      t1_misses_30d: getT1Misses30d() + 1,
      trend_direction: "declining",
    },
    companion_action_taken: {
      action: "CALM_REASSURANCE_PIVOT",
      spoken_text: buildCalmReassurance(),
      call_offer_contact_id: contact?.contact_id || "",
    },
    notification: {
      target_contact_id: contact?.contact_id || "",
      push_device_token: contact?.push_device_token || "",
      priority: "high",
      title: "KinBridge: Ibu Sri may need a check-in",
      body: "During today's conversation, Ibu Sri had difficulty recalling a core family memory. She is calm and the companion is with her. Tap to view details or call now.",
      deep_link: "kinbridge://dashboard/alerts/latest",
      actions: [
        { id: "CALL_NOW", label: "Call Ibu Sri" },
        { id: "VIEW_TREND", label: "View wellness trend" },
      ],
    },
    disclaimer: "Early screening signal only. Not a medical diagnosis. Consult a healthcare professional for clinical assessment.",
  };
}

export interface ProcessResult {
  verdict: JudgeResult;
  escalation: "acute" | "warning" | "silent" | "none";
  companion_directive?: string;
  companion_text?: string;
  probe_fact_id?: string;
  match: import("./types").MatchResult;
  cri_session: number;
  ewma_7d: number;
  alert?: AlertPayload;
}

export function updateTrend(session: Session) {
  const cri = computeCri(session.cri_events);
  if (session.cri_events.length < MIN_PROBES) return;
  const previousEwma = getLatestEwma();
  const ewma = computeEwma(cri, previousEwma);
  const date = new Date().toISOString().slice(0, 10);
  appendTrendPoint({ date, cri: Math.round(cri * 10) / 10, ewma: Math.round(ewma * 10) / 10, probes: session.cri_events.length });
}

export function runEscalation(
  session: Session,
  fact: Fact,
  verdict: JudgeResult,
  criSession: number
): { escalation: "acute" | "warning" | "silent" | "none"; companion_directive?: string; alert?: AlertPayload } {
  let escalation: "acute" | "warning" | "silent" | "none" = "none";
  let companion_directive: string | undefined;
  let alert: AlertPayload | undefined;

  if (fact.tier === 1 && verdict.verdict === "miss" && verdict.confidence >= 0.8) {
    escalation = "acute";
    companion_directive = "CALM_REASSURANCE_PIVOT";
    session.pivot_active = true;
    alert = buildAcuteAlert(session, fact, verdict, criSession, getLatestEwma());
    addAlert(alert);
  } else if (criSession < 40 && session.cri_events.length >= MIN_PROBES) {
    escalation = "acute";
    companion_directive = "CALM_REASSURANCE_PIVOT";
    session.pivot_active = true;
    alert = buildAcuteAlert(session, fact, verdict, criSession, getLatestEwma());
    addAlert(alert);
  }

  if (escalation === "none" && fact.tier === 2 && verdict.verdict === "miss") {
    // Defer warning until >= 2 T2 misses in 7d or EWMA drop > 15
    if (getT2Misses7d() + 1 >= 2 || getEwmaDrop7d() > 15) {
      escalation = "warning";
      addWarning("Noticeable decline in recent event recall this week");
    } else {
      escalation = "silent";
    }
  } else if (escalation === "none" && fact.tier === 3 && verdict.verdict === "miss") {
    escalation = "silent";
  }

  return { escalation, companion_directive, alert };
}

export async function processElderTurn(
  session: Session,
  text: string,
  sttConfidence: number = 1.0,
  forced?: Partial<JudgeResult>
): Promise<ProcessResult> {
  const ts = new Date().toISOString();
  addConversationTurn(session, { role: "elder", text, ts });

  const match = findMatch(text);

  // STT confidence gate
  if (sttConfidence < STT_CONFIDENCE_GATE) {
    if (session.armed_probe) {
      addRawLedger(session, {
        fact_id: session.armed_probe.fact.fact_id,
        verdict: "excluded_stt",
        raw_points: 0,
        ts,
      });
      addDashboardEvent({
        ts,
        fact_id: session.armed_probe.fact.fact_id,
        tier: session.armed_probe.fact.tier,
        verdict: "excluded_stt",
        raw_points: 0,
        cri_credit: 0,
        humanized: humanizeEvent("excluded_stt", session.armed_probe.fact),
      });
      session.armed_probe = null;
      session.state = "idle";
    }
    return {
      verdict: { verdict: "excluded_stt", confidence: 1, recalled_value: text, reasoning_short: "Low STT confidence" },
      escalation: "none",
      match,
      cri_session: computeCri(session.cri_events),
      ewma_7d: getLatestEwma(),
    };
  }

  let verdict: JudgeResult = { verdict: "no_answer", confidence: 1, recalled_value: text, reasoning_short: "No probe was armed" };
  let escalation: "acute" | "warning" | "silent" | "none" = "none";
  let companion_directive: string | undefined;
  let alert: AlertPayload | undefined;

  if (session.armed_probe) {
    const fact = session.armed_probe.fact;
    const probeText = buildProbeText(fact);
    verdict = await judge(text, fact, probeText, forced);

    const s = CREDIT[verdict.verdict] ?? 0;
    const w = TIER_WEIGHTS[fact.tier];
    addCriEvent(session, { w, s, fact_id: fact.fact_id, verdict: verdict.verdict });
    addRawLedger(session, {
      fact_id: fact.fact_id,
      verdict: verdict.verdict,
      raw_points: RAW_POINTS[verdict.verdict]?.[fact.tier] ?? 0,
      ts,
    });
    addDashboardEvent({
      ts,
      fact_id: fact.fact_id,
      tier: fact.tier,
      verdict: verdict.verdict,
      raw_points: RAW_POINTS[verdict.verdict]?.[fact.tier] ?? 0,
      cri_credit: s,
      humanized: humanizeEvent(verdict.verdict, fact),
    });

    const criSession = computeCri(session.cri_events);
    const escalationResult = runEscalation(session, fact, verdict, criSession);
    escalation = escalationResult.escalation;
    companion_directive = escalationResult.companion_directive;
    alert = escalationResult.alert;

    session.armed_probe = null;
    session.state = "idle";

    updateTrend(session);
  }

  // Generate next companion move
  let companion_text: string | undefined;
  let probe_fact_id: string | undefined;
  if (!session.pivot_active) {
    const nextFact = pickNextProbe(session);
    if (nextFact) {
      armProbe(session, nextFact);
      companion_text = buildProbeText(nextFact);
      probe_fact_id = nextFact.fact_id;
    } else {
      companion_text = "Terima kasih sudah bercerita, Ibu. Sampai nanti ya.";
    }
  } else {
    companion_text = buildCalmReassurance();
  }

  return {
    verdict,
    escalation,
    companion_directive,
    companion_text,
    probe_fact_id,
    match,
    cri_session: computeCri(session.cri_events),
    ewma_7d: getLatestEwma(),
    ...(alert ? { alert } : {}),
  };
}

export function startSession(): { session_id: string; companion_text: string; probe_fact_id?: string } {
  const session = createSession();
  const firstFact = pickNextProbe(session);
  if (firstFact) {
    armProbe(session, firstFact);
    return {
      session_id: session.session_id,
      companion_text: buildProbeText(firstFact),
      probe_fact_id: firstFact.fact_id,
    };
  }
  return {
    session_id: session.session_id,
    companion_text: buildCompanionGreeting(),
  };
}

export function processCompanionTurn(session: Session, text: string, probeFactId?: string) {
  const ts = new Date().toISOString();
  addConversationTurn(session, { role: "companion", text, ts, probe_fact_id: probeFactId });
  if (probeFactId) {
    const fact = getFactById(probeFactId);
    if (fact) armProbe(session, fact);
  }
}
