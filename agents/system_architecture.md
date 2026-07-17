# KinBridge — System Architecture (24-Hour Build)
**Version 1.0 · Single source of truth. If code disagrees with this doc, fix one of them before integrating.**

---

## 1. Component Diagram

```
┌─────────────────────┐   WebRTC / WS audio    ┌──────────────────────────┐
│  ELDER CLIENT       │◄──────────────────────►│  VOICE LAYER             │
│  (Next.js page /)   │                        │  OpenAI Realtime API     │
│  mic orb + captions │                        │  (STT+LLM+TTS, id-ID)    │
└─────────┬───────────┘                        └────────────┬─────────────┘
          │ transcript events                               │ probe metadata
          ▼                                                 │ (function call)
┌─────────────────────────────────────────────┐             │
│  SERVER (Next.js API routes, in-memory)     │◄────────────┘
│                                             │
│  ┌───────────────────┐  ┌────────────────┐  │     ┌────────────────────┐
│  │ Workflow Eval     │  │ Match Engine   │  │     │ elder_context.json │
│  │ Agent (judge LLM, │  │ (keyword →     │  │◄────│ (loaded at boot,   │
│  │ CRI/EWMA math)    │  │  mock groups)  │  │     │  mutated in memory)│
│  └────────┬──────────┘  └───────┬────────┘  │     └────────────────────┘
└───────────┼─────────────────────┼───────────┘
            │ WS /ws/alerts       │ WS match event
            ▼                     ▼
┌──────────────────────────────────────────────┐
│  FAMILY DASHBOARD (Next.js page /dashboard)  │
│  trend chart · event feed · alert toast      │
└──────────────────────────────────────────────┘
```

---

## 2. Stack Decision Matrix (binary, pre-decided — do not relitigate mid-build)

| Layer | Option A (CHOSEN) | Option B (rejected) | Why A wins in 24h |
|---|---|---|---|
| Repo | **One Next.js 14 app, 2 pages + API routes** | Separate FE/BE repos | One deploy, one port, shared types |
| Voice | **OpenAI Realtime API (gpt-4o-realtime)** | Whisper + GPT-4o + ElevenLabs chain | 1 API = STT+LLM+TTS, ~500ms latency, native id-ID. Chain = 3 failure points |
| Voice fallback | **Browser Web Speech API + typed chat toggle** | None | Conference wifi WILL fail. Fallback is demo insurance |
| Judge LLM | **gpt-4o-mini, JSON mode, temp=0** | gpt-4o | Judge task is trivial classification; mini is faster + cheaper, determinism identical |
| State | **In-memory JS Map + write-through to `state.json`** | SQLite/Postgres | Brief says SKIP databases. File persist survives dev-server restarts |
| Dashboard→client push | **Socket.io (`/ws/alerts`)** | FCM / polling | Live toast on second screen during demo; FCM needs device provisioning |
| Trend chart | **Recharts LineChart** | D3 custom | 20 min vs 4 hours |
| Styling | **Tailwind + 2 Google Fonts** | Component library | Full control over the premium aesthetic (see ui_spec.md) |

---

## 3. API Contracts

All routes under `/api/v1`. In-memory session store keyed by `session_id`. No auth (per brief).

### 3.1 `POST /api/v1/turn` — every finalized transcript turn
Request:
```json
{
  "session_id": "sess_20260717_1420",
  "role": "elder | companion",
  "text": "Suami saya... siapa ya namanya?",
  "stt_confidence": 0.91,
  "probe_fact_id": null,
  "ts": "2026-07-17T14:32:01+07:00"
}
```
`probe_fact_id` is non-null only on companion turns that carry a probe (set from the Realtime API function-call metadata, see prompts.md §2).

Response:
```json
{
  "verdict": { "verdict": "miss", "confidence": 0.94, "recalled_value": "...", "reasoning_short": "..." },
  "escalation": "acute | warning | silent | none",
  "match": { "matched": true, "group_id": "grp_01" },
  "companion_directive": "CALM_REASSURANCE_PIVOT | null"
}
```
`companion_directive` is injected back into the Realtime session as a system message when non-null — this is how the Tier 1 pivot happens mid-conversation.

### 3.2 `GET /api/v1/dashboard/summary`
```json
{
  "elder": { "profile_id": "elder_0001", "name": "Sri Rahayu Wijaya" },
  "today": { "sessions": 2, "minutes_engaged": 23, "probes_scored": 5 },
  "cri_latest": 33.3,
  "ewma_7d": 62.5,
  "trend_direction": "declining",
  "active_warnings": ["Noticeable decline in recent event recall this week"]
}
```

### 3.3 `GET /api/v1/dashboard/trend?days=30`
```json
{ "points": [ { "date": "2026-07-17", "cri": 33.3, "ewma": 62.5, "probes": 5 } ] }
```
**Demo note:** pre-seed 30 days of mock trend points (gentle decline 82→60) in `state.json` so the chart is never empty on stage.

### 3.4 `GET /api/v1/dashboard/events?limit=50`
Raw ledger feed: `{ ts, fact_id, tier, verdict, raw_points, cri_credit }[]`.

### 3.5 `WS /ws/alerts` (Socket.io namespace)
Events pushed to dashboard:
- `alert:acute_t1` → full payload per `tier1_alert_payload.json`
- `alert:warning` → `{ text, ewma_delta_7d, ts }`
- `match:found` → `{ group, matched_keyword, ts }`
- `trend:update` → same shape as one trend point (live chart tick)

### 3.6 `POST /api/v1/demo/trigger` — Demo Mode (hidden, judge-proof)
```json
{ "scenario": "t1_miss | t2_warning | match_found" }
```
Forces the corresponding scripted event through the real pipeline (real payloads, real WS push). Bound to hotkey Shift+D on the elder client. Live-demo roulette loses hackathons.

---

## 4. Data Flow — the money path (Tier 1 escalation, end to end)

1. Companion (Realtime API) decides to probe → calls function `mark_probe(fact_id="T1_SPOUSE_NAME")` → speaks the probe.
2. Client posts companion turn to `/api/v1/turn` with `probe_fact_id` → agent state = AWAIT_RESPONSE.
3. Elder replies; client posts elder turn with `stt_confidence`.
4. Agent runs judge (gpt-4o-mini, temp=0) → `verdict: miss, confidence: 0.94`.
5. `apply_score` updates ledger + CRI events; `run_escalation` matches Rule 1 (T1 miss ≥ 0.8).
6. Server broadcasts `alert:acute_t1` on `/ws/alerts` → dashboard toast + alert card.
7. Server returns `companion_directive: CALM_REASSURANCE_PIVOT` → client injects system message → companion says the reassurance line and offers to call Dewi.
8. Dashboard trend ticks down live via `trend:update`.

Total budget for steps 3–7: **< 2s** (KPI in workflow_agent_spec.md §6).

---

## 5. Repo Structure

```
kinbridge/
├── app/
│   ├── page.tsx                 # Elder voice UI (mic orb, captions)
│   ├── dashboard/page.tsx       # Family dashboard
│   └── api/v1/
│       ├── turn/route.ts        # ingest + judge + escalate
│       ├── dashboard/{summary,trend,events}/route.ts
│       └── demo/trigger/route.ts
├── lib/
│   ├── agent.ts                 # state machine (workflow_agent_spec.md §2)
│   ├── scoring.ts               # CRI, EWMA, thresholds (spec §4)
│   ├── judge.ts                 # gpt-4o-mini structured call (prompts.md §3)
│   ├── match.ts                 # keyword → mock group
│   └── store.ts                 # in-memory Map + state.json persist
├── data/
│   ├── elder_context.json
│   └── state.seed.json          # 30 days of pre-seeded trend
└── server.ts                    # custom server: Next + Socket.io
```

---

## 6. Build Order & Time Budget (24h, 2–3 builders)

| Hours | Workstream A (backend) | Workstream B (frontend) |
|---|---|---|
| 0–2 | Repo scaffold, store, seed data | Design tokens, elder page shell |
| 2–6 | `/turn` + judge + scoring (test with typed chat, no voice) | Dashboard: trend chart + event feed on mock data |
| 6–10 | Escalation rules + Socket.io + demo trigger | Alert toast, match modal, warning banner |
| 10–16 | Realtime API voice loop + probe function-calling | Voice UI polish: orb states, live captions |
| 16–20 | Integration: full money path end-to-end | Typed-chat fallback toggle, mobile check |
| 20–24 | **Freeze. Demo rehearsal ×3, seed data tuning, README** | Same |

**Hard rule:** voice integration does not begin until the typed-chat pipeline scores correctly (hour 6 gate). Voice is presentation; scoring is the product.
