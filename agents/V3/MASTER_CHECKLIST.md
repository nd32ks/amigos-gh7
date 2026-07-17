# KinBridge — MASTER CHECKLIST (everything, one place)
**The full inventory: every feature, its source doc, what it depends on, what it feeds, and a verifiable check. Humans tick boxes; the AI builder verifies each check programmatically and records evidence in BUILD_LOG.md. A box may only be ticked with its check passing — no vibes.**

Legend: 🔨 BUILD · 🎭 MOCK/PREVIEW · 📊 SLIDE-ONLY · ⛔ SKIP

---

## 1. V1 Core (source: `system_architecture.md`, `workflow_agent_spec.md`, `prompts.md`)

| ✓ | Feature | Strat | Depends on | Feeds | Verify check |
|---|---|---|---|---|---|
| ☐ | Elder voice UI (mic orb, captions, typed fallback) | 🔨 | Voice layer | /turn | Orb states render; typed toggle works |
| ☐ | Kenang companion (gpt-4o-realtime, id, `mark_probe` fn) | 🔨 | prompts.md §1 | Transcripts + probe tags | Probe turn arrives with `probe_fact_id` set |
| ☐ | Judge (gpt-4o-mini, JSON, temp 0) | 🔨 | prompts.md §3 | probe_result | T1 transcript pair → `miss`, conf ≥0.9, ×2 identical |
| ☐ | Scoring engine (raw ledger + CRI + EWMA) | 🔨 | Judge output | Trend, escalation | CRI(T1 miss, T2 exact, T3 exact) = 33.3 |
| ☐ | Escalation rules (acute/warning/silent) | 🔨 | Scoring | WS alerts, pivot | T1 miss conf ≥0.8 → `alert:acute_t1` + `CALM_REASSURANCE_PIVOT` directive |
| ☐ | Family dashboard (trend, events, alert toast) | 🔨 | dashboard APIs, WS | — | 30-day seeded chart renders; alert modal on WS event |
| ☐ | Context profile `elder_context.json` | 🎭 | — | Everything | JSON parses; all fact_ids unique |
| ☐ | Social matchmaking (keyword → group card) | 🎭 | Transcripts | WS `match:found` | Saying "anggrek" fires grp_01 modal |
| ☐ | Demo mode (Shift+D / `/demo/trigger`, forced_verdict) | 🔨 | Full pipeline | — | All 3 scenarios fire offline-ish |
| ☐ | Auth / DB / payments | ⛔ | — | — | Confirm ABSENT; role-select screen instead |

## 2. V2 (source: `v2_feature_addendum.md`)

| ✓ | Feature | Strat | Depends on | Feeds | Verify check |
|---|---|---|---|---|---|
| ☐ | Dashboard i18n (id default, EN toggle) | 🔨 | i18n dict | All guardian UI | Toggle flips every §1 key; no hardcoded strings |
| ☐ | Find My Friends (school/work matching) | 🎭 | profile_delta, people_directory | Guardian approve card | "teman SMA" → Ratna card; score ≥7 logic unit-tested |
| ☐ | Reminders engine (meds/checkups, voice ack) | 🔨 | Kenang directive channel | Adherence panel, T2 facts | Ack "sudah" → ACKED; miss → `reminder:missed` |
| ☐ | Reminder→probe flywheel | 🔨 | Reminders | Scoring | Acked med generates next-day T2 fact |
| ☐ | Komorbid profile → tailoring rules | 🔨 | health_profile.json | Companion prompt, matchmaking, routines | Diabetes ON → dining card copy changes |
| ☐ | Onboarding wizard + Context Dump extraction | 🔨 | gpt-4o extraction | elder_context.v2.json, health_profile, reminders | Paste dump → facts in 3 tiers; `?demo=1` cached fallback works offline |

## 3. V2.1 (source: `v2_1_diary_and_i18n_addendum.md`)

| ✓ | Feature | Strat | Depends on | Feeds | Verify check |
|---|---|---|---|---|---|
| ☐ | Diary (daily entry, bilingual summary, mood) | 🔨 | Session-end LLM call | Guardian Diary tab | Session end → entry with id+en summaries |
| ☐ | Cerita Ibu (story extraction, audio chip, share) | 🔨 | Same merged call | Diary entries, pending_facts | Story card renders; quote NEVER translated |
| ☐ | Story→probe flywheel (guardian-approved queue) | 🔨 | pending_facts | Scoring | Approve → fact appears with cooldown |
| ☐ | Full app i18n (guardian toggle; elder locale locked) | 🔨 | i18n dict, probes_en.json | All surfaces | ID→EN flips dashboard + diary summary; quote stays id |
| ☐ | "Bacakan buku harian saya" voice command | 🔨 | Diary | Kenang | Command reads yesterday's summary aloud |

## 4. V2.2 (source: `v2_2_activities_socialworkers_elderchoice.md`)

| ✓ | Feature | Strat | Depends on | Feeds | Verify check |
|---|---|---|---|---|---|
| ☐ | Routine chains (med→gerak→air; `after` pointers) | 🔨 | Reminders engine | Adherence chain view | Full rtn_morning_01 chain fires; meds escalate, movement never |
| ☐ | Role-select screen (3 profiles, no credentials) | 🔨 | — | All surfaces | 3 cards route correctly; zero auth code |
| ☐ | Social worker panel `/care` (caseload, notes) | 🎭 | care_worker.json, dashboard shell | Guardian notes feed | Permission matrix enforced: no diary, no komorbid list |
| ☐ | `<GuidedQuestion>` component (speak/tap/delegate) | 🔨 | — | ALL elder inputs | Every elder input routes through it; "Tidak tahu" never blocks |
| ☐ | Delegation "Minta tolong Dewi" | 🔨 | GuidedQuestion | Guardian task cards | Delegate → live card; complete → Kenang closes loop |

## 5. V2.3 Games (source: `V3/v2_3_cognitive_games.md`) — **approved with fixes below**

| ✓ | Feature | Strat | Depends on | Feeds | Verify check |
|---|---|---|---|---|---|
| ☐ | Game probes (tebak_angka, cerita_foto, cocok_kata) | 🔨 | games.json, probe scheduler | Same probe_result → CRI | Game result lands in trend identically to conversational probe |
| ☐ | 1:1 alternation rule, never 2 games back-to-back | 🔨 | Scheduler | — | Session log shows alternation |
| ☐ | Difficulty adapts DOWN on declining trend | 🔨 | EWMA | Game selection | Declining seed → cocok_kata selected over cerita_foto |
| ☐ | Guardian "Aktivitas Kognitif" panel (streak) | 🔨 | Game sessions | — | Panel shows counts; no "skor/tes" strings |
| ☐ | Group game sessions via matchmaking | 🎭 | Match engine | Suggestion card | Static card only; no multiplayer code exists |

**Required fixes before building V2.3 (review findings):**

| ✓ | Fix | Why |
|---|---|---|
| ☐ | Align `expected_source` refs to real fact_ids (`fact:pasangan.nama` → `T1_SPOUSE_NAME`; `fact:hobi.anggrek` → `T3_GARDENING_HOBBY`) | Current refs don't resolve against `elder_context.json` — pipeline would 404 |
| ☐ | `gm_tebak_angka_01` references grandchild "Rara" who doesn't exist in the profile — either add a `T2_GRANDCHILD_AGE` fact via onboarding, or retarget to an existing numeric fact (birth year) | Unresolvable probe |
| ☐ | Recognition discount: `cocok_kata` (2-button) exact earns CRI credit s=0.75, not 1.0; a miss on 2-choice counts full weight | 50% guess chance would otherwise inflate the trend; recognition-failure is strong signal, recognition-success is weak signal |
| ☐ | `cocok_kata` T1 miss must NOT auto-fire acute escalation on its own — require judge-confidence path or a confirming conversational T1 probe next session | A mis-tap on a binary button is not "couldn't recall her husband" |
| ☐ | `cerita_foto` needs mock asset + note: guardian photo upload = roadmap, hackathon ships 1 hardcoded image | No photo pipeline exists |
| ☐ | Add API: `GET /api/v2/games/next` (scheduler pick) + `GET /api/v2/games/summary` (guardian panel); game answers ride existing `POST /api/v1/turn` | Doc defined data but no endpoints |

## 6. Slideware / pitch layer (never built)

| ✓ | Item | Source |
|---|---|---|
| ☐ | Memory-vault→Diary promotion noted; v3_roadmap remaining items stay slides | `v3_roadmap_pitches.md` |
| ☐ | Volunteers & medical students audience (Health Path doc) — pitch layer only; they use the social-worker panel, no 4th surface | `Kinbridge_Health_Path.md` |
| ☐ | Phone-line access, baseline calibration, clinician export, care-circle, payer channel | `v3_roadmap_pitches.md` |
| ☐ | Pitch deck via slides agent | `PITCH_AGENT.md` |
| ☐ | Marketing: landing structure + "Halo." guidance (design.md skill executes) | `marketing_spec.md` |

## 7. Cross-cutting invariants (check at EVERY gate)

| ✓ | Invariant |
|---|---|
| ☐ | No elder surface ever shows: forms, toggles, tables, scores, streaks, "tes/skor/demensia" |
| ☐ | Guardian/marketing surfaces never show: skrining, tes kognitif, demensia, diagnosis, pantau |
| ☐ | Verbatim elder quotes never translated |
| ☐ | Caveat line accompanies every wellness-trend visual (app + marketing + deck) |
| ☐ | All 3 demo scenarios + onboarding `?demo=1` work with network throttled |
| ☐ | Every elder input routes through `<GuidedQuestion>`; delegation available on every question |
| ☐ | Games/movement/friends never escalate; only meds adherence and T1/T2 recall do |

## 8. Updated supersede chain & cut order (authoritative as of V2.3)

Supersede chain: `v2_1 §1 ⊃ v3 §1 (story placement)` · `v2_2 §A ⊃ v2 §3 (reminders→routines)` · `v2_2 §C ⊃ all elder-facing forms/toggles everywhere` · `v2_3 ⊂ probe schema (extends, adds probe_kind)` · **this checklist §5-fixes ⊃ v2_3 details listed**.

Cut order if slipping (first → last): social-worker panel → group-game preview → friends → **games** → diary → routines → komorbid → onboarding → i18n. Gates and GuidedQuestion never cut.
