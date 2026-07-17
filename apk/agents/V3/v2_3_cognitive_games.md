# KinBridge V2.3 — Playful Probe Framing ("Main & Asah Otak") (Builder Instructions)
**Standalone addendum. Adds ZERO new engine, ZERO new API routes, ZERO new JSON files. Extends `elder_context.json` fact schema additively (new optional field only) and adds one paragraph to the Companion prompt (`prompts.md` §1). Judge, scoring, escalation, and state machine (`workflow_agent_spec.md`) are untouched — this doc does not redefine or duplicate any of them.**

---

## 0. Why this exists, and why it's small

The doc set names "number games," "picture description," and "active mental activities" as the research-backed activities behind the product (cited in the pitch/health research), but no addendum actually built a games/puzzle function — the existing pipeline only delivers probes as organic small talk (`prompts.md` §1: "Naturally weave AT MOST one probe question per 3 conversational turns"). This addendum closes that gap the cheapest way the architecture allows: **a game is not a new feature, it's a second phrasing style for a probe Kenang already asks.** Same `fact_id`, same `mark_probe` function call, same judge, same CRI math — only the words changed.

This keeps the addendum inside `BUILDER_PROTOCOL.md`'s scope wall: no new file, no new state, no new gate. It's a content + one-prompt-paragraph change, estimated **45 min**, not a new phase.

---

## 0.1 Scope Matrix

| # | Item | Strategy | Time |
|---|---|---|---|
| A | `game_variants` field on existing facts (`elder_context.json`) | **BUILD** (additive schema field) | 20 min |
| B | Companion prompt: playful-framing instruction | **BUILD** (one paragraph, `prompts.md` §1) | 10 min |
| C | Event log tag `delivery_style` for dashboard flavor text | **BUILD** (additive field on raw ledger entry) | 15 min |
| D | New probe types, new UI, multiplayer, scoreboards | **NOT BUILT** — explicitly out of scope, see §D below |

---

## A. Data — additive field on existing facts, not a new file

`elder_context.json` facts gain one optional array, `game_variants`. Nothing else on the fact changes; `fact_id`, `tier`, `canonical_value`, `accepted_aliases`, `probe_cooldown_hours` are untouched (per `BUILDER_PROTOCOL.md` rule 3: "If you need a new field, ADD it — never rename or remove an existing one").

```json
{
  "fact_id": "T1_SPOUSE_NAME",
  "tier": 1,
  "category": "core_identity.family",
  "canonical_value": "Budi Wijaya",
  "accepted_aliases": ["Budi", "Pak Budi", "Bapak Budi"],
  "probe_templates_id": [
    "Ngomong-ngomong Bu, bagaimana kabar suami Ibu hari ini?",
    "Ibu biasanya minum kopi pagi sama siapa di rumah?"
  ],
  "game_variants": [
    {
      "style": "cocok_kata",
      "prompt_id": "Main tebak-tebakan yuk, Bu. Nama suami Ibu itu... Pak Budi atau Pak Slamet?"
    }
  ],
  "probe_cooldown_hours": 24,
  "last_probed_at": null
}
```

Three playful styles, matching the activity categories named in the research brief:

| Style | What it does | Best fits | Example (from `elder_context.json` facts) |
|---|---|---|---|
| `cocok_kata` | Two-choice guess, one true + one plausible-false option | Tier 1 identity facts (lowest cognitive load — Tier 1 is never the fact you want a struggling elder tested hardest on) | "Pak Budi atau Pak Slamet?" |
| `tebak_angka` | Turns a number-shaped fact into a guessing frame | `T1_BIRTH_YEAR`, dates, ages | "Ibu lahir tahun berapa ya... coba tebak sendiri dulu, hehe" |
| `cerita_foto` | Invites a short story instead of a single fact, framed around a hobby/photo | Tier 3 preference facts (`T3_GARDENING_HOBBY`, `T3_MUSIC_PREFERENCE`) | "Ceritakan dong soal anggrek bulan Ibu — dari kapan mulai suka?" |

`game_variants` is optional per fact — a fact with none simply never gets played-with, it stays purely conversational. Seed `game_variants` for at least the 4–5 facts already in `elder_context.json` that map cleanly (T1_SPOUSE_NAME → cocok_kata, T1_BIRTH_YEAR → tebak_angka, T3_GARDENING_HOBBY / T3_MUSIC_PREFERENCE → cerita_foto).

---

## B. Companion prompt — one paragraph added to `prompts.md` §1

Insert directly under the existing "MEMORY PROBES" section, changing nothing already written there:

```
PLAYFUL FRAMING (optional, only when game_variants exists for the chosen fact)
- If the fact you are about to probe has a game_variants entry, you MAY use
  its prompt_id instead of a probe_templates_id — roughly one probe in three,
  never two in a row. Deliver it exactly as warmly as any other question;
  "main tebak-tebakan" is an invitation between friends, not a test format.
- The mark_probe function call and fact_id are IDENTICAL regardless of which
  phrasing you use. Nothing downstream changes.
- If she answers wrong, respond exactly as instructed for any miss: warmly,
  no correction. A missed guess is not a missed test.
```

No change to the `mark_probe` function tool definition, the judge prompt, or the `/api/v1/turn` contract in `system_architecture.md` §3.1 — `probe_fact_id` flows through identically whether Kenang asked conversationally or playfully. The judge evaluates the reply against `fact.canonical_value` exactly as it always has; it has no concept of "game" and needs none.

---

## C. Dashboard — flavor text only, no new panel, no scores

The only visible change on the guardian dashboard is a small addition to the raw ledger event shape (`GET /api/v1/dashboard/events`, `system_architecture.md` §3.4), one additive field:

```json
{ "ts": "...", "fact_id": "T1_SPOUSE_NAME", "tier": 1, "verdict": "exact",
  "raw_points": 1, "cri_credit": 1.0, "delivery_style": "playful" }
```

`delivery_style` is `"playful" | "conversational"`, purely descriptive — it does not feed the CRI/EWMA formula (`workflow_agent_spec.md` §4 is unchanged; this field is not one of its inputs). Its only consumer is one optional line of copy on the existing dashboard summary, in the same register as the rest of the marketing/product copy rules (`marketing_spec.md` §3, never "test/skor"):

| Key | id | en |
|---|---|---|
| `games.flavor_line` | Ibu suka main tebak-tebakan minggu ini | Ibu enjoyed a few guessing games this week |

This is copy, not a metric — it should never appear next to a number.

---

## D. Explicitly not built (and why)

- **No new probe types beyond the three styles above.** They map 1:1 to the three activity categories the research cites (number, story/picture, recognition); an open-ended game library is scope creep with no doc to justify it.
- **No new UI component.** The elder surface stays voice-only (mic orb + captions per `system_architecture.md` §1); games are not a menu item, screen, or `<GuidedQuestion>` flow — that component is reserved for structured intake (komorbid, delegation, routine confirmation) per `v2_2_activities_socialworkers_elderchoice.md` §C, and probes (game-framed or not) are not structured intake, they're conversation.
- **No scoreboard, streak counter, or score shown to the elder — ever.** Same rule as every other elder surface in the doc set: she is never tested by a form, and a visible score is a test with extra steps.
- **No multiplayer / group game sessions.** If wanted later, it belongs in `v3_roadmap_pitches.md` as a venture-logic pitch item (would need real-time multi-elder session infra, which V1 explicitly scopes out — "multi-elder support" is on the `workflow_agent_spec.md` §7 do-not-add list), not a hackathon build.
- **No separate scoring pipeline.** `delivery_style` is metadata, not a parallel CRI. Two scoring engines for one product is the exact kind of duplication `BUILDER_PROTOCOL.md` rule 3 forbids.

---

## E. Decisions Made (flagging)

1. **Games are a phrasing layer on the existing probe pipeline, not a new feature.** Same `fact_id`, same `mark_probe`, same judge, same CRI. This was a correction from an earlier draft of this doc, which had proposed a parallel `games.json` + new probe-kind schema — that duplicated the scoring engine and didn't fit any Phase's time budget. Rebuilt against `workflow_agent_spec.md` and `system_architecture.md` directly.
2. **Tier 1 facts get the lowest-cognitive-load style (`cocok_kata`)** — consistent with the doc set's general posture of protecting the elder from feeling tested on her own identity.
3. **`delivery_style` is descriptive metadata only, never a scoring input** — keeps `workflow_agent_spec.md` §4 the single source of truth for the trend line.

**Scope flags:** no new JSON data file · no new API route · no new WS event · no elder-facing score/streak/leaderboard · no multiplayer · games never override the "max one probe per 3 turns" pacing rule already in `prompts.md`.
