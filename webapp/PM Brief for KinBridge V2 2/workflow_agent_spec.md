# KinBridge — Contextual Recall Pipeline (Workflow Evaluation Agent)
**Version 1.0 · 24-hour hackathon scope · Lead Architect spec**

---

## 1. Architecture Decision Matrix

| Decision | Option A (chosen) | Option B (rejected) | Why |
|---|---|---|---|
| Trend metric | **Normalized Cognitive Recall Index (CRI, 0–100)** | Raw cumulative ± points | Raw points are unbounded and frequency-biased (more chat = lower score). CRI is defensible to technical judges. Raw ledger kept for the event log. |
| Judge invocation | **One structured-output LLM call per probed turn** | Batch evaluation end-of-session | Enables live Tier 1 pivot mid-conversation (the demo moment). |
| Alert transport | **WebSocket → dashboard toast (mocked FCM payload)** | Real FCM push | Real push = device provisioning hell. Same payload schema, zero infra. |
| Recall matching | **LLM judge with enum verdict + confidence** | Fuzzy string match | Bahasa Indonesia paraphrase ("suami saya, Pak Budi") kills string matching. |

---

## 2. State Machine

```
STATES: IDLE → PROBE_ARMED → AWAIT_RESPONSE → EVALUATE → SCORE_UPDATE → ESCALATION_CHECK → IDLE
```

```python
# ---- Workflow Evaluation Agent: main loop (pseudocode) ----

state = IDLE
session = { probes: [], cri_events: [], raw_ledger: [] }

on_companion_turn(msg):
    # Companion LLM tags its own probes via function-call metadata
    if msg.metadata.probe_fact_id is not None:
        armed_probe = {
            fact_id: msg.metadata.probe_fact_id,
            fact: context.facts[msg.metadata.probe_fact_id],
            asked_at: now(),
            timeout_s: 45
        }
        state = AWAIT_RESPONSE

on_elder_turn(msg):
    if state != AWAIT_RESPONSE: return          # small talk, ignore
    if msg.stt_confidence < 0.75:
        log(fact_id, verdict="excluded_stt")     # never penalize bad transcription
        state = IDLE; return
    if elapsed(armed_probe.asked_at) > armed_probe.timeout_s or topic_changed(msg):
        log(fact_id, verdict="no_answer")        # excluded from CRI, counts toward probe_yield KPI
        state = IDLE; return
    state = EVALUATE
    verdict = judge_llm(msg.text, armed_probe.fact)   # structured output, schema in §3
    state = SCORE_UPDATE
    apply_score(verdict, armed_probe.fact)
    state = ESCALATION_CHECK
    run_escalation(verdict, armed_probe.fact)
    armed_probe.fact.last_probed_at = now()      # start cooldown
    state = IDLE

apply_score(verdict, fact):
    raw = RAW_POINTS[verdict.verdict][fact.tier]         # ledger (§4, Table A)
    s   = CREDIT[verdict.verdict]                        # CRI credit ∈ {1, 0.5, 0}
    w   = TIER_WEIGHT[fact.tier]                         # {10, 3, 2}
    session.raw_ledger.append({fact.fact_id, raw})
    session.cri_events.append({w, s})

run_escalation(verdict, fact):
    # Rule 1 — ACUTE (fires immediately, mid-session)
    if fact.tier == 1 and verdict.verdict == "miss" and verdict.confidence >= 0.8:
        companion.inject_pivot_prompt(CALM_REASSURANCE_ID)   # tone shift + offer to call daughter
        dashboard.dispatch(build_tier1_alert(...))            # payload → tier1_alert_payload.json
        return
    # Rule 2 — WARNING (session close or rolling window)
    if count_misses(tier=2, window="7d") >= 2 or ewma_drop("7d") > 15:
        dashboard.flag_warning("Noticeable decline in recent event recall this week")
    # Rule 3 — Tier 3 miss: silent. Trend line absorbs it. No UI event.

on_session_end():
    if len(session.cri_events) >= MIN_PROBES:    # MIN_PROBES = 3, else session excluded
        cri  = 100 * Σ(w_i * s_i) / Σ(w_i)
        ewma = ALPHA * cri + (1 - ALPHA) * prev_ewma     # ALPHA = 0.3
        dashboard.append_trend_point(cri, ewma)
```

---

## 3. Judge LLM Contract (structured output, one call per probe)

```json
{
  "verdict": "exact | partial | miss | no_answer",
  "confidence": 0.93,
  "recalled_value": "string — what the elder actually said",
  "reasoning_short": "≤ 20 words"
}
```
Judge system prompt receives only: `fact.canonical_value`, `fact.accepted_aliases`, elder's reply text. Deterministic: `temperature=0`.

---

## 4. Quantitative Framework

**Table A — Raw ledger points (per brief, unchanged):**

| Verdict | Tier 1 | Tier 2 | Tier 3 |
|---|---|---|---|
| exact | +1 | +1 | +1 |
| partial | 0 | 0 | 0 |
| miss | **−10** | −4 | **−2** |

**Table B — CRI inputs (drives the dashboard trend line):**

| Parameter | Value | Rationale |
|---|---|---|
| Tier weight `w` | T1=10, T2=3, T3=2 | Mirrors clinical severity ratio of the penalty scheme |
| Credit `s` | exact=1.0, partial=0.5, miss=0.0 | Partial recall is signal, not noise |
| `CRI_session` | `100 · Σ(wᵢsᵢ) / Σ(wᵢ)` | Bounded [0,100], probe-count invariant |
| `EWMA_t` | `0.3·CRI_t + 0.7·EWMA_{t−1}` | Smooths daily variance; 7-day half-life ≈ intent |
| MIN_PROBES | 3 per session | Prevents single-probe sessions from swinging the trend |
| STT gate | confidence ≥ 0.75 | Excludes transcription errors from scoring |
| Fact cooldown | per `probe_cooldown_hours` in JSON | Prevents repeat-probe gaming / annoyance |

**Escalation thresholds:**

| Tier | Trigger | Action |
|---|---|---|
| Acute (T1) | Any T1 `miss` @ confidence ≥ 0.8, OR `CRI_session < 40` | Companion pivot + dashboard alert payload |
| Warning (T2) | ≥ 2 T2 misses in 7d, OR EWMA drop > 15 pts in 7d | Dashboard warning banner |
| Silent (T3) | Any T3 miss | Trend line only |

---

## 5. Edge Cases

| Case | Handling |
|---|---|
| Elder changes topic instead of answering | `no_answer` — excluded from CRI, logged for probe-yield KPI |
| STT mishears (low confidence) | Excluded entirely; never scored |
| Elder corrects themselves mid-answer | Judge scores final statement only (in judge prompt) |
| Contradiction vs omission | Contradiction ("suami saya Anton") = `miss`; vague ("suami saya… ya dia") = `partial` |
| Tier 2 fact expired (`valid_until` passed) | Fact skipped by probe scheduler |
| Two T1 misses in one session | One alert only; dedupe on `(fact_id, session_id)` |
| Judge confidence < 0.8 on a T1 miss | Downgrade to `partial` for escalation purposes (fail-safe against false alarms) |

---

## 6. Operational KPIs (put these on a slide)

| KPI | Target | Definition |
|---|---|---|
| Probe yield rate | ≥ 70% | scored probes / probes injected |
| Eval latency | < 2s | elder turn end → verdict |
| False acute alert rate | ~0 in demo | T1 alerts overturned by human review |
| Judge determinism | 100% | identical transcript → identical verdict (temp=0) |

---

## 7. Changes I Made to the Brief (flagging per directive)

1. **Added CRI (0–100) as the trend metric.** Your raw ± points are kept as the event ledger, but an unbounded cumulative score is indefensible under judge questioning ("does chatting more make grandma look healthier?"). CRI is probe-count invariant.
2. **Set Tier 2 miss = −4** (brief only defined T1=−10, T3=−2; the gap needed an interior point).
3. **Partial recall earns 0.5 CRI credit** (raw ledger still 0) — finer-grained trend line, better demo visuals.
4. **STT confidence gate + no-answer exclusion** — the #1 source of false positives in voice pipelines; costs 3 lines of code.
5. **T1 alert requires judge confidence ≥ 0.8** — a false "your mother is disoriented" push is the worst possible demo failure.
6. **Mocked FCM as WebSocket toast** — identical payload, zero device setup. Judges see the alert land live on a second screen.
7. **Pitch — Demo Mode hotkey:** hidden keypress forces a scripted T1 miss so you can trigger the full escalation cascade on stage deterministically. Build it. Live-demo roulette loses hackathons.

**Scope flags:** brief is well-scoped. Do NOT add: real FCM, onboarding, auth, audio-feature analysis, multi-elder support. Matchmaking stays keyword→toast as specified.
