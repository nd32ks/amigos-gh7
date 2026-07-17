# KinBridge — MAIN WORKFLOW (read this before anything else)
**The single entry point for any builder — human or AI agent — joining at any time. Three stages: TRAIN → RECONCILE → EXECUTE. Do not write code before passing the Stage 1 checkpoint.**

---

## ⚠ STOP FLAG — Login (read first, it affects work in progress)

Authentication is **SKIP** per the V1 scope matrix (`system_architecture.md`, original brief "Ops Infrastructure: SKIP"). If anyone is currently building real login: **stop and downgrade it to a mock role-select screen** — one page, three hardcoded profiles, zero credentials:

```
[ 👵 Ibu Sri — Elder ]  [ 👩 Dewi — Guardian ]  [ 🩺 Sari — Social Worker ]
```

Click = enter that surface. This costs 30 minutes, demos BETTER than login (judges see all three roles instantly, no password fumbling on stage), and the "three profiles, one platform" story lands visually. Real auth is post-hackathon roadmap. Sunk-cost rule: whatever login code exists, keep the screen chrome, delete the credential logic.

---

## STAGE 1 — TRAIN (60–90 min, no code)

### 1.1 Read order

| # | File | You are learning |
|---|---|---|
| 1 | `V2/MAIN_WORKFLOW.md` | This process |
| 2 | `V2/BUILDER_PROTOCOL.md` | Operating rules, phase gates, escalation matrix |
| 3 | `README.md` | What the product is, judging criteria map |
| 4 | `system_architecture.md` | Contracts (LAW) |
| 5 | `workflow_agent_spec.md` | Scoring math (LAW) |
| 6 | `prompts.md` | The two prompts (copy verbatim) |
| 7 | `elder_context.json` + `tier1_alert_payload.json` | Data shapes (LAW) |
| 8 | `ui_spec.md` + open `ui_mockup.html` in a browser | Design system — click all 4 demo buttons |
| 9 | `V2/v2_feature_addendum.md` | Phase 3 features |
| 10 | `V2/v2_1_diary_and_i18n_addendum.md` | Diary + bilingual |
| 11 | `V2/v2_2_activities_socialworkers_elderchoice.md` | Routines, 3rd profile, elder interaction rules |
| 12 | `demo_script.md` + `V2/pitch_v2.md` | What must work on stage (this defines "important") |
| 13 | `V2/v3_roadmap_pitches.md` | SLIDEWARE — read once so you never accidentally build it |

### 1.2 Comprehension checkpoint — answer in `BUILD_LOG.md` before coding
1. What exact conditions fire an acute Tier-1 escalation? (spec §4)
2. Compute CRI for: T1 miss, T2 exact, T3 exact. (must get 33.3)
3. Which features are MOCK, and what is each mock's trigger? (list all)
4. What is the hour-6 gate, and what is forbidden before it passes?
5. Which surfaces may NEVER show the words tes/skor/demensia/diagnosis?
6. What gets translated and what never does? (V2.1 §1.1, §2.3)
7. What is the elder-side rule for forms and toggles? (V2.2 design principles)
8. What is the demo failure ladder, in order?

Wrong answer on any = re-read the cited doc. An AI builder agent must print these answers; a human should be able to answer from memory. **This checkpoint is the "training workflow" — it exists because every integration bug so far traceable in hackathons comes from a builder who skimmed.**

---

## STAGE 2 — RECONCILE (version precedence, 5 min)

Generic rule for V1 / V2 / V2.x / V3 / any future Vn:

1. **V1 core is frozen.** Scoring, escalation, contracts: never modified, only read from / fed into.
2. **Higher version wins ONLY where it says "supersedes"** and only for that specific item (e.g., V2.1 moves Cerita Ibu into the Diary; V2.2 extends the reminders engine into routine chains).
3. **Anything not explicitly superseded stays as originally written.** Absence of mention ≠ permission to change.
4. **v3_roadmap files are never executable** regardless of version number — they are pitch material unless a feature is explicitly promoted into a numbered addendum (Memory Vault → V2.1 is the example of a correct promotion).
5. Conflict you can't resolve with rules 1–4: STOP, write both citations in BUILD_LOG.md, ask the human PM. Never pick silently.

Current supersede chain: `v2_1 §1 supersedes v3 §1 (story placement)` · `v2_2 §A extends v2 §3 (reminders→routines)` · `v2_2 §C overrides any elder-facing form/toggle in ALL docs, including the V2.1 language toggle on elder surfaces`.

---

## STAGE 3 — EXECUTE

Follow `BUILDER_PROTOCOL.md` phases and gates exactly, with V2.2 slotted in:

| Phase | Content | Gate |
|---|---|---|
| 1 (hr 0–6) | V1 core pipeline, typed-chat | A: T1 transcript → exact payload, CRI 33.3, deterministic ×2 |
| 2 (hr 6–11) | Elder UI + guardian dashboard + **role-select screen** | B: 3 demo scenarios fire from UI |
| 3 (hr 11–17) | V2 features, order: i18n → onboarding → reminders → **routines (V2.2)** → komorbid → **guided-choice layer (V2.2)** → friends → diary → **social-worker panel (read-only)** | C: onboarding dump demo + language choice + one full routine chain fires |
| 4 (hr 17–21) | Voice loop + fallbacks | D: money path by voice < 2s, then again with fallbacks |
| 5 (hr 21–24) | FREEZE — rehearse ×3, tune, fix demo-path bugs only | Demo runs clean twice consecutively |

**Cut order if slipping (first cut → last cut):** social-worker panel → friends → diary → routines → komorbid → onboarding → i18n. Gates and the guided-choice layer are never cut — the interaction pattern IS the design story.

**Cadence:** after every gate, re-run all demo scenarios + append to BUILD_LOG.md. Every 2 hours, one-line status to the team: phase, % to next gate, blockers.
