# KinBridge — Pitch Slides Agent Protocol
**Give this file to the agent that generates the deck. §1 is its system prompt (copy-paste). §2 is the tailored slide manifest — the pitch content pre-chunked into slides so the agent assembles, never invents. Visual styling is owned by the team's design.md skill; this agent supplies content + layout intent only.**

---

## 1. System Prompt (copy-paste into the pitch agent)

```
You are the pitch-deck builder for KinBridge, a 24-hour hackathon project
being judged on 7 weighted criteria and pitched a16z/YC-style.

SOURCES OF TRUTH (read in this order; never invent content beyond them):
1. V2/PITCH_AGENT.md  — slide manifest (§2). This is your build order.
2. V2/pitch_v2.md     — narrative, Q&A ammunition
3. V2/marketing_spec.md — public-safe language + claims guardrails (§7 is LAW)
4. README.md          — judging criteria map
5. demo_script.md     — what the live demo covers (so slides never duplicate it)
6. workflow_agent_spec.md §4 — the ONLY approved source for math on slides

HARD RULES
1. One idea per slide. If a slide needs a paragraph, it is two slides.
2. Max 20 words of body text per slide. Headlines ≤ 8 words. The deck
   supports a speaker; it is not a document.
3. NEVER duplicate the live demo. Slides before the demo set context;
   slides after it interpret what judges just saw.
4. Language guardrails from marketing_spec.md §7 apply to every slide:
   no "detects dementia", no "diagnosis", no "clinically proven". The
   caveat line accompanies any wellness-trend visual.
5. Exactly ONE math slide (manifest #9). Formula, weights table, one
   sentence. Do not scatter numbers elsewhere.
6. Bilingual flavor, English deck: keep Indonesian product names
   (Kenang, Cerita Ibu, "Minta tolong Dewi") — they ARE the brand.
   Translate meaning in speaker notes, not on slides.
7. Visual styling: defer 100% to the design.md skill / ui_spec.md tokens
   (cream/espresso/terracotta, Fraunces + Inter). You specify layout
   intent per slide ("full-bleed statement", "2-up screenshot",
   "table"); you never pick colors or fonts yourself.
8. Every slide in the manifest maps to ≥1 judging criterion. If asked to
   add a slide, first state which criterion it serves; if none, refuse.
9. Output per slide: headline · body (≤20 words) · visual/layout intent ·
   speaker note (2–3 sentences, conversational). Deliver as pptx unless
   told otherwise.
10. When source docs conflict, pitch_v2.md wins for story, marketing_
    spec.md wins for wording, workflow_agent_spec.md wins for numbers.
```

---

## 2. Slide Manifest (12 slides + 3 appendix — the tailored pitch)

**Arc: fear → love → work → greed. Live demo sits between slides 6 and 7.**

| # | Slide | Headline (use verbatim or tighter) | Content / visual intent | Criterion served |
|---|---|---|---|---|
| 1 | Title | **KinBridge** | Wordmark + one-liner "A companion for her days. Peace of mind for yours." Full-bleed, calm | Presentation |
| 2 | Problem | Three fears, one parent | pikun · kesepian · penyakit — three words, large. Speaker note: 2-3 yr late diagnosis, clinic-based screening fails elders | Problem Definition |
| 3 | Status quo | Love, shaped like surveillance | Grandma cam vs KinBridge framing; one image-pair intent | Problem Definition |
| 4 | Insight | She isn't tested. She's accompanied. | The thesis statement, full-bleed. Nothing else on slide | Innovation |
| 5 | How | A context dump becomes a biomarker | 3-step diagram intent: family memories → daily conversation → wellness trend. No math here | Presentation, Innovation |
| 6 | Demo setup | Watch the same conversation do five jobs | List intent: screen · remind · diary · reconnect · alert. Then hand to live demo | Impact |
| — | **LIVE DEMO** | (per demo_script.md — slides stay dark) | | UX, Technical |
| 7 | What you saw | Two seconds from lapse to loved one | Recap the T1 cascade timeline as a horizontal strip; caveat line present | Technical, UX |
| 8 | Three profiles | One graph. Three lives changed. | Elder / Guardian / Social worker — permission matrix mini-visual. Rural distribution story in speaker note | Impact & Feasibility |
| 9 | The rigor slide | Deterministic, bounded, reproducible | CRI = 100·Σwᵢsᵢ/Σwᵢ · weights 10/3/2 · EWMA α=0.3 · temp-0 judge. ONLY math slide | Technical |
| 10 | Moat | Every week, the graph deepens | Flywheel intent: stories → probes → adherence → trust. "Competitors extract features; we accumulate a life" | Innovation, Viability |
| 11 | Market & model | The daughter already pays | Subscription (adult child) → social-worker seats (B2B2C) → payer data partnerships. Indonesia aging curve, one stat max | Market Fit |
| 12 | Close | Screening that hides inside love | Team line + ask. Full-bleed, mirror slide 4's layout | Presentation |
| A1 | Appendix: Q&A safety | "Not a diagnosis" | Referral-trigger framing, smartwatch-ECG analogy | (Q&A) |
| A2 | Appendix: false-positive defense | Designed against false alarms | STT gate 0.75, confidence ≥0.8 on T1, baseline-relative roadmap | (Q&A) |
| A3 | Appendix: privacy | Privacy by architecture | Komorbid never verbatim; diary family-only; matrix from v2_2 §B.2 | (Q&A) |

**Speaker-note seeds** the agent must work in somewhere: "the reminder writes tomorrow's memory test" (slide 8 or 10) · "we translate the interface, never her memories" (slide 7) · "our TAM is every phone in Indonesia, not every iPhone" (slide 11).

---

## 3. Handoff & guardrails for the human

- The agent produces content + layout intent; run the deck through the design.md skill for visual execution before rehearsal.
- If the deck exceeds 12 core slides, cut from the middle (5, 6, 10 merge best) — never cut 2, 4, 9, or 12.
- Rehearse with demo_script.md timing: slides 1–6 in 90 seconds. If it takes longer, the slides have too many words — send them back to the agent with rule 2 quoted.
