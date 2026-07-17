export interface Fact {
  fact_id: string;
  tier: 1 | 2 | 3;
  category: string;
  canonical_value: string | string[];
  accepted_aliases: string[];
  probe_templates_id: string[];
  probe_cooldown_hours: number;
  last_probed_at: string | null;
  valid_until?: string | null;
}

export interface Contact {
  contact_id: string;
  name: string;
  relation: string;
  phone: string;
  push_device_token: string;
  priority: number;
}

export interface ElderProfile {
  profile_id: string;
  name: string;
  preferred_address: string;
  birth_year: number;
  sex: string;
  city: string;
  timezone: string;
}

export interface CommunityGroup {
  group_id: string;
  name: string;
  interest_keywords: string[];
  distance_km: number;
  meets: string;
}

export interface ElderContext {
  schema_version: string;
  profile_id: string;
  locale: string;
  elder: ElderProfile;
  escalation_contacts: Contact[];
  facts: Fact[];
  community_groups_mock: CommunityGroup[];
}

export type Verdict = "exact" | "partial" | "miss" | "no_answer" | "excluded_stt";

export interface JudgeResult {
  verdict: Verdict;
  confidence: number;
  recalled_value: string;
  reasoning_short: string;
}

export interface ArmedProbe {
  fact_id: string;
  fact: Fact;
  asked_at: string;
  timeout_s: number;
}

export interface CriEvent {
  w: number;
  s: number;
  fact_id: string;
  verdict: Verdict;
}

export interface RawLedgerEntry {
  fact_id: string;
  verdict: Verdict;
  raw_points: number;
  ts: string;
}

export interface ConversationTurn {
  role: "companion" | "elder";
  text: string;
  ts: string;
  probe_fact_id?: string;
}

export interface Session {
  session_id: string;
  profile_id: string;
  state: "idle" | "await_response";
  armed_probe: ArmedProbe | null;
  cri_events: CriEvent[];
  raw_ledger: RawLedgerEntry[];
  conversation: ConversationTurn[];
  probe_fact_ids_used: string[];
  pivot_active: boolean;
  created_at: string;
  last_turn_at: string;
}

export interface TrendPoint {
  date: string;
  cri: number;
  ewma: number;
  probes: number;
}

export interface DashboardEvent {
  ts: string;
  fact_id: string;
  tier: 1 | 2 | 3;
  verdict: Verdict;
  raw_points: number;
  cri_credit: number;
  humanized: string;
}

export interface AlertPayload {
  alert_id: string;
  schema_version: string;
  type: "COGNITIVE_ACUTE_T1";
  severity: "HIGH";
  created_at: string;
  elder: { profile_id: string; name: string };
  trigger: {
    fact_id: string;
    tier: number;
    category: string;
    expected_value: string;
    recalled_value: string;
    verdict: string;
    judge_confidence: number;
    session_id: string;
    transcript_excerpt: { role: string; text: string }[];
  };
  cognitive_snapshot: {
    cri_session: number;
    ewma_7d: number;
    ewma_delta_7d: number;
    t1_misses_30d: number;
    trend_direction: string;
  };
  companion_action_taken: {
    action: string;
    spoken_text: string;
    call_offer_contact_id: string;
  };
  notification: {
    target_contact_id: string;
    push_device_token: string;
    priority: string;
    title: string;
    body: string;
    deep_link: string;
    actions: { id: string; label: string }[];
  };
  disclaimer: string;
}

export interface MatchResult {
  matched: boolean;
  group_id?: string;
  group?: CommunityGroup;
  matched_keyword?: string;
}
