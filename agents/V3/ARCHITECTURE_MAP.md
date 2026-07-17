# KinBridge — ARCHITECTURE MAP (the whole system on one page)
**For pitching AND building: the full component diagram, exactly where AI is used and which AI, the complete API surface, and every data file. Companion visual: `V3/architecture_diagram.mermaid` (renders as a graphic — use it in the deck).**

---

## 1. Master Diagram

```
                        ┌──────────────── ROLE SELECT (no auth) ────────────────┐
                        ▼                        ▼                              ▼
            ┌───────────────────┐   ┌────────────────────────┐   ┌────────────────────────┐
            │ 👵 ELDER CLIENT   │   │ 👩 GUARDIAN DASHBOARD  │   │ 🩺 SOCIAL WORKER /care │
            │ voice-first, zero │   │ id/en · trend · diary  │   │ caseload · notes       │
            │ forms, GuidedQ    │   │ adherence · delegation │   │ (permission-filtered)  │
            └───┬───────▲───────┘   └──────────▲─────────────┘   └───────────▲────────────┘
     audio/text │       │ directives           │ REST + WebSocket            │ REST (read)
                ▼       │ (pivot·reminders·    │                             │
      ┌──────────────────┐ games·routines)     │                             │
      │ VOICE LAYER  🤖1 │                     │                             │
      │ gpt-4o-realtime  │            ┌────────┴─────────────────────────────┴──────┐
      │ Kenang persona   │ transcript │              SERVER (Next.js, in-memory)     │
      │ mark_probe fn    ├───────────►│                                              │
      └──────────────────┘            │  ┌─────────────────┐   ┌──────────────────┐  │
                                      │  │ WORKFLOW EVAL   │   │ PROBE SCHEDULER  │  │
   ┌era──────────────────┐            │  │ AGENT           │◄──┤ conversational + │  │
   │ DATA LAYER (JSON)   │            │  │ state machine   │   │ game probes,     │  │
   │ elder_context ──────┼───────────►│  │ Judge 🤖2 →     │   │ cooldowns, 1:1   │  │
   │ health_profile      │            │  │ CRI · EWMA ·    │   │ alternation      │  │
   │ reminders/routines  │            │  │ escalation      │   └──────────────────┘  │
   │ games · diary       │            │  └───┬─────────────┘                         │
   │ people_directory    │            │      │ trend/alerts   ┌──────────────────┐   │
   │ care_worker         │            │      ├───────────────►│ ADJUSTED CARE    │   │
   │ pending_facts       │            │      │                │ pivot · game     │   │
   └─────────▲───────────┘            │      │                │ difficulty ↓     │   │
             │ writes                 │      │                └──────────────────┘   │
   ┌─────────┴───────────┐            │  ┌───▼─────────────┐  ┌───────────────────┐  │
   │ ONBOARDING WIZARD   │            │  │ DIARY GENERATOR │  │ MATCH ENGINE      │  │
   │ Context Dump 🤖3    │            │  │ 🤖4 session-end │  │ friends (dtrmnstc)│  │
   │ guardian-side forms │            │  │ story+summary   │  │ groups (keyword)  │  │
   └─────────────────────┘            │  └─────────────────┘  └───────────────────┘  │
                                      │  ┌─────────────────────────────────────────┐ │
                                      │  │ REMINDER/ROUTINE ENGINE (chains, acks)  │ │
                                      │  │ DELEGATION QUEUE (elder→guardian)       │ │
                                      │  └─────────────────────────────────────────┘ │
                                      └────────────────┬─────────────────────────────┘
                                                       │ Socket.io /ws/alerts
                                        alert:acute_t1 · alert:warning · trend:update
                                        match:found · friend:introduced · reminder:missed
                                        routine:step · diary:shared · delegation:new/completed
```

**The sentence that explains it:** two inputs (conversation + context files), one deterministic engine in the middle, and every surface — guardian, social worker, diary, matchmaking, adjusted care — is just a different consumer of the same trend signal. New consumers (clinician view) bolt on without touching the engine.

---

## 2. AI Inventory — exactly what kind of AI, where, and why (the pitch answer)

| # | AI | Model | Job | Call pattern | Temp | Latency budget | Fallback |
|---|---|---|---|---|---|---|---|
| 🤖1 | Companion "Kenang" | gpt-4o-realtime | Voice conversation (STT+LLM+TTS, id/en), weaves probes, delivers reminders/games, executes pivots | Streaming session | default | ~500ms/turn | Web Speech API + typed chat |
| 🤖2 | Judge | gpt-4o-mini, JSON mode | Classify each probed reply: exact/partial/miss/no_answer + confidence | 1 call per probed turn | **0** | <2s | `forced_verdict` (demo path) |
| 🤖3 | Context-Dump Extractor | gpt-4o, structured output | Parse guardian's free-text brain dump → tiered fact schema | 1 call at onboarding | 0 | <10s | `?demo=1` cached parse |
| 🤖4 | Diary Generator | gpt-4o, structured output | Session end: bilingual summary + mood + story extraction (one merged call) | 1 call per session | 0.3 | async | Skip entry, retry next session |
| 🤖5 | Probe Translator | gpt-4o-mini | Batch-translate probe templates when elder_locale=en | 1 call at boot, cached | 0 | boot-time | Ship id-only |

**Deliberately NOT AI (say this to technical judges):** scoring math (CRI/EWMA — pure arithmetic), escalation thresholds (rules), friend matching (deterministic overlap scoring), group matching (keywords), routines (state machine). **"LLMs at the edges, deterministic math in the middle"** — every number on the dashboard is reproducible; the same transcript always yields the same trend. That's the defensibility line.

---

## 3. Complete API Surface

### REST — V1 core
| Method & path | Purpose |
|---|---|
| `POST /api/v1/turn` | Ingest every transcript turn; runs judge + scoring + escalation + match; returns verdict + `companion_directive` |
| `GET /api/v1/dashboard/summary` | Header stats + latest CRI/EWMA + active warnings |
| `GET /api/v1/dashboard/trend?days=` | Trend points (cri, ewma per day) |
| `GET /api/v1/dashboard/events?limit=` | Raw ledger feed |
| `POST /api/v1/demo/trigger` | Scripted scenarios through real pipeline (`forced_verdict` capable) |

### REST — V2.x
| Method & path | Purpose |
|---|---|
| `POST /api/v2/onboard/context-dump` | Free text → tiered facts (🤖3) |
| `POST /api/v2/onboard/complete` | Persist facts + health profile + reminders |
| `GET /api/v2/friends/matches` | Scored friend candidates |
| `POST /api/v2/friends/introduce` | Guardian-mediated intro (state flip) |
| `GET·POST /api/v2/routines` | Routine chains CRUD (guardian side) |
| `GET /api/v2/diary?days=&locale=` | Diary entries, bilingual |
| `POST /api/v2/diary/story/:id/share` | Share state flip |
| `POST /api/v2/facts/approve` | Promote pending story-fact to live probe |
| `POST /api/v2/delegate` · `POST /api/v2/delegate/:id/complete` | Elder→guardian question delegation |
| `GET /api/v2/games/next` | Scheduler's next game pick (per checklist §5 fix) |
| `GET /api/v2/games/summary` | Guardian Aktivitas Kognitif panel |

Game answers, reminder acks, and friend-intent keywords all ride `POST /api/v1/turn` — one ingest path, by design.

### WebSocket `/ws/alerts` events
`alert:acute_t1` · `alert:warning` · `trend:update` · `match:found` · `friend:introduced` · `reminder:missed` · `routine:step` · `diary:shared` · `delegation:new` · `delegation:completed`

---

## 4. Data files (all JSON, in-memory + file persist — no DB by design)

| File | Written by | Read by |
|---|---|---|
| `elder_context(.v2).json` | Onboarding 🤖3, facts/approve | Scheduler, Judge, Kenang, Friends |
| `health_profile.json` | Onboarding step 3, delegation answers | Tailoring rules → Kenang prompt, matchmaking, routines, games |
| `reminders.json` / `routines.json` | Onboarding, guardian editor | Reminder engine → Kenang |
| `games.json` | Static seed | Probe scheduler |
| `diary.json` + `pending_facts` | Diary generator 🤖4 | Diary tab, facts/approve |
| `people_directory.json`, `care_worker.json` | Static seed | Friends engine, /care panel |
| `state(.seed).json` | Scoring engine | Trend endpoints |
| `probes_en.json` | Boot translator 🤖5 | Kenang (en elders) |
| `BUILD_LOG.md` | Builder agent | Humans |

---

## 5. Trust & privacy boundaries (one slide, one answer)

| Boundary | Rule |
|---|---|
| Elder ↔ system | Never sees scores, forms, or condition names; only conversation and choices |
| Family ↔ social worker | Worker gets trend/adherence/alerts + derived directives — never diary, never raw komorbid list |
| Health data ↔ AI prompt | Conditions compiled to behavioral directives; names never injected verbatim |
| Public ↔ product | "Early screening signal, not a diagnosis" on every trend surface |
```
