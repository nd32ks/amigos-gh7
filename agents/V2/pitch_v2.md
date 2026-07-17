# KinBridge — Pitch V2 (a16z / YC cut)
**Supersedes the pitch framing in README.md and demo_script.md §closing for stage use. The demo mechanics in demo_script.md remain valid.**

---

## One-liner
**KinBridge is the operating system for aging in Indonesia** — a voice companion that screens for cognitive decline invisibly, a dashboard that gives families peace of mind, and a remote-care panel that gives social workers a living. Three profiles, one context graph.

## The problem (three pains, one parent)
Every adult child in Indonesia carries the same three fears about an aging parent: **pikun** (dementia caught 2–3 years too late because screening needs a clinic visit elders refuse), **kesepian** (loneliness — itself a major dementia risk factor), and **penyakit** (chronic conditions with missed meds and missed checkups). Today that's three apps, none of which the elder will use, because they're all built for the child.

## The insight
We don't test her. We talk to her. A family "context dump" — ten minutes of a daughter's memories — becomes a ground-truth file. A warm Bahasa Indonesia voice companion weaves those facts into daily conversation; a deterministic judge scores every recall against ground truth (weighted index, temperature-zero, reproducible). Conversation in, digital biomarker out. The same transcript also powers her diary, her reminders, and her reconnection with old friends. **One conversation, five products.**

## The three profiles

| Profile | Surface | Job |
|---|---|---|
| 👵 Elder | Voice-only. No forms, no toggles, no menus — every input is a spoken question with big buttons and a "Minta tolong Dewi" escape hatch | Companionship, routines, dignity |
| 👩 Guardian | Dashboard (Indonesian/English): wellness trend, alerts, diary of her mother's stories, routine adherence, delegated questions | Peace of mind — the payer |
| 🩺 Social worker | Caseload panel: 15 elders across rural Java monitored from home, visit notes, derived care directives (never raw diagnoses, never the family diary) | Income without commute; care distribution |

## Why the social worker profile is the venture story
Indonesia's care capacity is urban-concentrated; its aging curve is not. KinBridge turns remote elder care into **distributed work**: a certified social worker anywhere can carry a rural caseload — the labor-marketplace layer on top of a consumer subscription. Family pays for one elder; a Puskesmas or insurer pays for a thousand. That's the B2C wedge and the B2B2C expansion in one architecture, and the permission matrix (family data stays family's) is what makes institutions able to say yes.

## Moat
The longitudinal context graph. Every conversation deepens the fact base; every acked reminder writes tomorrow's memory probe; every story she tells becomes both a family heirloom and a future test item. Switching cost compounds weekly. Audio-biomarker competitors extract features; we accumulate a life.

## Why now / why us
LLM voice latency crossed the "feels human" threshold this year; Bahasa-native voice models are new; Indonesia's 60+ population is entering its steepest climb. And our approach is language-agnostic by construction (ground-truth recall, not audio DSP) — SEA expansion is a config change, not a research program.

## Business
Subscription paid by the adult child (the market that already pays for grandma cams — we're the version with dignity). Expansion: social-worker seats (B2B2C via clinics/Puskesmas), insurer partnerships priced on adherence + early-detection data, clinician referral reports.

## Demo arc (4 min) — fear → love → work
1. **Onboarding** (0:30): paste the context dump → facts cascade into tiers; komorbid toggle visibly changes her recommendations
2. **The conversation** (1:00): probe hit, trend ticks; morning routine chain fires by voice — med, then "gerak-gerak," then water
3. **Fear** (0:45): Tier-1 miss → companion pivots to reassurance mid-sentence → guardian alert lands in Indonesian on the second screen
4. **Love** (0:30): guardian opens the Diary — her mother's story about the downpour when Dewi was born, in her own voice. "We translate the interface, never her memories."
5. **Choice** (0:20): elder asked a health question → taps "Minta tolong Dewi" → task card lands on guardian screen live
6. **Work** (0:30): switch role → Sari's caseload panel, 3 elders, rural map. "Same graph, third livelihood."
7. **Close** (0:25): one slide — moat flywheel + who pays. *"Screening that hides inside love, care that scales past cities."*

## Q&A additions (beyond demo_script.md table)

| Question | Answer |
|---|---|
| "Isn't the social worker feature vaporware?" | It's a preview on purpose — the permission model and panel are built; assignment/payments are post-raise. We're showing the architecture supports three trust levels today. |
| "Why will elders answer health questions?" | They don't fill forms — ever. One spoken question at a time, 'I don't know' always valid, and anything hard goes to the daughter with one tap. Watch the delegation demo again. |
| "What's defensible vs a big tech clone?" | The context graph + the family trust position. A horizontal assistant won't get a daughter to hand over her mother's memories; a wellness brand that starts with dignity will. |
