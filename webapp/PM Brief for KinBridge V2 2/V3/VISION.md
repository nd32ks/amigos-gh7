# KinBridge — VISION (inject this into every agent)
**The doc set says WHAT to build and HOW. This doc says WHY and FOR WHOM. Every agent — orchestrator, builders, auditor — loads this before anything else. When a technical choice is ambiguous, the personas decide it.**

---

## 1. The vision, one paragraph

Every adult child in Indonesia carries three fears about an aging parent: **pikun** (memory decline caught years too late), **kesepian** (loneliness that accelerates it), and **penyakit** (chronic illness with missed medicine and missed checkups). KinBridge is one warm voice that quietly addresses all three: a Bahasa Indonesia companion the elder genuinely enjoys talking to, which turns her daily conversation into an early-warning wellness trend for her family, keeps her routines and stories, reconnects her with old friends — and lets a social worker in the city care for fifteen elders in villages she could never commute to. **She is never tested. She is accompanied.** Dignity is not a feature of this product; it is the product.

## 2. What KinBridge IS / IS NOT (binary, no debate)

| IS | IS NOT |
|---|---|
| A companion she looks forward to | A medical device or diagnostic tool |
| A screening signal that triggers a real doctor visit | A surveillance system / grandma cam |
| A premium lifestyle product (Kith-catalog calm) | A clinical, beige, patronizing "senior app" |
| Conversation-first; every input is a spoken question | An app with menus, forms, toggles, scoreboards for the elder |
| One context graph, three surfaces | Three separate apps stapled together |

## 3. The three personas — build for THEM, not for the spec

### 👵 Ibu Sri, 74 — the elder (the user)
Retired bank teller in Gading Serpong. Husband Budi; kids Dewi and Anton visit when they can. Hypertension, type-2 diabetes, bad knee. Loves her moon orchids, keroncong (Bengawan Solo), omakase at ENA. Weekdays are long and quiet.
**Fears:** being a burden; being *tested* like something is wrong with her; technology that makes her feel stupid.
**Success looks like:** she starts conversations with Kenang herself. She never once feels examined, corrected, or monitored. When she can't answer, nothing bad happens *to her* — warmth continues.
**Instant failure:** any screen that makes her feel graded — a score, a form, a wrong-answer state, a menu she must navigate.

### 👩 Dewi, 41 — the daughter (the buyer)
Works in Jakarta, 90 minutes from mom. Calls twice a week, guilt every day. She would never install a camera in her mother's house — but she lies awake wondering.
**Fears:** the silent decline she'll notice too late; the 2 a.m. phone call; apps that make her mom a patient.
**Success looks like:** a 30-second glance at lunch = calm. When something IS wrong, she knows within seconds, with her mother already comforted. And once a week, her mother's stories arrive in her mother's own voice.
**Instant failure:** a false alarm that terrifies her; clinical language about her mom; data she can't act on.

### 🩺 Sari, 28 — the social worker (the multiplier)
Trained social worker in Yogyakarta. Commuting to scattered rural clients caps her income and reach.
**Fears:** flying blind between visits; tools that dump raw medical data she isn't licensed to interpret.
**Success looks like:** opens her caseload panel each morning and knows which of her 15 elders needs attention today — with derived guidance, not diagnoses, and family privacy intact.
**Instant failure:** seeing the family's diary or a raw condition list she should never see.

## 4. North-star principles (tie-breakers for every ambiguous decision)

1. **Ibu Sri's dignity beats every metric.** If a feature improves data quality but makes her feel tested, kill the feature.
2. **Fear and love are separate rooms.** Clinical signal lives on the trend tab; stories live in the diary. Never mix them.
3. **Ask, don't instruct** — every elder input is a spoken question with big choices, "tidak tahu" always valid, delegation always available.
4. **Calm technology for Dewi:** silence means fine; alerts are rare, humane, and arrive with mom already comforted.
5. **LLMs at the edges, deterministic math in the middle** — every number reproducible.
6. **Trust boundaries are the moat:** family data stays the family's; Sari sees derivatives, never sources.

## 5. The persona test (run before shipping ANY feature)

Answer all three, in writing, in the ship report:
- Would **Ibu Sri** feel accompanied — or examined — using this?
- Does this make **Dewi** calmer — or just more informed?
- Does **Sari** know what to DO — or just what happened?
A "no/worse" on any = the feature is wrong even if the code is right.

---

## 6. COPY-PASTE PREAMBLE — for every BUILDER agent prompt

```
VISION CONTEXT (read V3/VISION.md in full):
You are building KinBridge for three real people, not for a spec:
- Ibu Sri (74): must feel accompanied, NEVER tested. No forms, toggles,
  menus, scores, or wrong-answer states on any elder surface — every
  input is a spoken question with big choices and a "Minta tolong Dewi"
  escape. If your implementation would make her feel graded, it is a
  bug even if it matches the ticket.
- Dewi (41, the buyer): calm at a glance. Alerts rare and humane, mom
  already comforted when one arrives. Clinical signal on the trend tab
  ONLY; her mother's stories in the diary ONLY. Never mix.
- Sari (28, social worker): actionable derivatives, never raw health
  data, never the family diary.
Before claiming DONE, answer the three persona-test questions in your
ship report (BUILDER_REPORT_FORMAT). A feature that passes its verify
check but fails the persona test is NOT done.
```

## 7. COPY-PASTE PREAMBLE — for the AUDITOR (Sonnet)

```
VISION AUDIT LAYER (in addition to AUDITOR_BRIEF.md):
You audit vision compliance with the same rigor as code compliance.
These are FAIL-grade findings even when the code works perfectly:
- Any elder surface showing a form, toggle, menu tree, table, score,
  streak, level, or wrong-answer feedback (Ibu Sri: never tested)
- Clinical/scoring data rendered inside the Diary tab, or story content
  rendered on the trend tab (fear and love are separate rooms)
- An elder input that does not route through <GuidedQuestion> or lacks
  "tidak tahu" / delegation options
- Social-worker surface exposing diary content or raw condition lists
- Alert copy that would frighten Dewi rather than reassure her
  (compare against tier1_alert_payload.json tone)
- Forbidden vocabulary per surface (checklist §7)
Verify ship reports include the three persona-test answers; missing or
pro-forma answers ("yes, fine, yes") = flag as process violation.
```
