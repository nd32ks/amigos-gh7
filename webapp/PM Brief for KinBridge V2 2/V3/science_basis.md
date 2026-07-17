# KinBridge — Scientific Basis of the Cognitive Scoring Design
**For pitch Q&A, the rigor slide's speaker notes, and the pitch agent (appendix material). Two layers: (1) established memory science we borrow from, (2) our engineering on top — which is explicitly NOT clinically validated.**

---

## 1. Why the tiers — the severity gradient is neuroscience, not taste

| Our tier | Memory system | AD trajectory | Design consequence |
|---|---|---|---|
| Tier 2 — recent events (meals, visits, orders) | Episodic memory, hippocampus-dependent | **Fails FIRST** — anterograde episodic deficits are the earliest and most sensitive neuropsychological marker of prodromal AD | Early-warning tier: moderate weight (w=3), pattern-based warnings |
| Tier 3 — preferences, hobbies | Personal semantics (mid-consolidation) | Degrades gradually | Trend texture: low weight (w=2), silent logging |
| Tier 1 — spouse/children names, home, birth year | Remote, deeply consolidated personal semantic memory | **Preserved LONGEST** (temporal gradient, "Ribot's law"); loss indicates advanced disease | Acute tier: w=10, single confirmed miss escalates immediately |

One sentence for judges: *"Forgetting breakfast is a data point; forgetting your husband's name is an emergency — our weights encode the clinical order in which Alzheimer's dismantles memory."*

## 2. Precedent instruments we personalize (the "same primitives" argument)

| Established instrument | What it does | What KinBridge borrows |
|---|---|---|
| MMSE / MoCA | Orientation questions + delayed recall + naming, in clinic, ~annually | Same item types, personalized to the elder's own life, sampled daily in conversation |
| CDR (Clinical Dementia Rating) | Clinician rates recent vs remote memory via informant interview | The recent/remote split IS our T2/T1 split |
| AD8, IQCODE | Family-informant questionnaires on everyday forgetting | The context dump = informant ground truth, made continuous and checkable |
| AMI (Autobiographical Memory Interview, Kopelman) | Validated scoring of personal semantic facts + autobiographical incidents | Closest academic ancestor of context-dump scoring |
| FCSRT (Free & Cued Selective Reminding Test) | Exploits free recall → cued recall → recognition failure gradient; sensitive to prodromal AD | Our probe/game difficulty ladder: cerita_foto (free) > conversational cue > cocok_kata (recognition), and the 0.75 recognition discount |

## 3. Design decisions with direct scientific rationale

- **Recognition discount (cocok_kata exact = 0.75 credit; miss = full weight):** recognition is the last recall mode to fail — success is weak evidence of health, failure is strong evidence of decline. (FCSRT logic.)
- **Partial recall = 0.5 credit:** word-finding difficulty and tip-of-the-tongue states increase in early decline; hesitant-but-directionally-correct answers are intermediate signal, not noise.
- **EWMA trend + (roadmap) personal baseline:** within-person longitudinal change is more diagnostic than any single score against population norms; daily conversational sampling captures what annual clinic snapshots miss.
- **Difficulty adapts DOWN on decline, never up:** avoids frustration/failure spirals; consistent with graded-support practice in dementia care.
- **Confound gates:** STT confidence ≥0.75, judge confidence ≥0.8 on acute, no-answer exclusion, MIN_PROBES=3 — because recall is confounded by sleep, mood, hearing, medication, and transcription error. One bad morning must not look like disease.
- **Activity side (games, social, routines):** cognitive stimulation and social engagement are associated with reduced dementia risk (stats + citations in `Kinbridge_Health_Path.md`); KinBridge both encourages the activity AND measures the trend, closing the evidence loop.

## 4. What we may NEVER claim (guardrails — consistent with marketing_spec §7)

- CRI is **not a validated clinical instrument**: no sensitivity/specificity data, no clinical trial, no norming study.
- We do not detect, diagnose, or predict Alzheimer's or dementia. We surface a **declining-recall trend** as an early screening signal that should prompt a real clinical evaluation (MoCA + clinician).
- Approved analogy: *smartwatch ECG → cardiologist*. We are the prompt, not the verdict.
- Validation study (correlating CRI trend against MoCA/CDR in a cohort) is explicitly the post-funding roadmap — say so proactively when asked.

## 5. The 30-second spoken answer (memorize)

*"We didn't invent new memory science — we personalized the old, validated primitives. Clinic tests ask 'what year is it' once a year; we ask 'how's your husband, what did you eat, tell me about your orchids' every day, scored against family-provided ground truth. The tier weights follow the known order in which Alzheimer's dismantles memory — recent events first, core identity last. It's not a diagnosis; it's a continuously-sampled screening signal with a referral trigger. The validation study against MoCA is what the funding is for."*
