# KinBridge V3 — Architect's Feature Pitches (a16z-backed framing)
**Not builder instructions. This is the "what would we do with $3M" layer: each feature scored on venture logic (moat, TAM, who-pays) and hackathon cost. Nothing here enters the build without explicit GO.**

---

## Decision Matrix

| # | Feature | Venture logic | Hackathon cost | Verdict |
|---|---|---|---|---|
| 1 | **Memory Vault (Cerita Ibu)** | Emotional retention + data flywheel + viral loop | ~2h | **BUILD if time remains** |
| 2 | **Landline/phone access (no smartphone needed)** | 10× TAM in Indonesia | Mock: 0.5h (slide + fake dial-in) | **MOCK — say it, don't build it** |
| 3 | **Personal cognitive baseline calibration** | Clinical defensibility | 0h (one slide + one param) | **SLIDE ONLY** |
| 4 | **Clinician trend report (PDF export)** | Revenue path #2, B2B2C via hospitals | Slide only | **SLIDE ONLY** |
| 5 | **Sibling care-circle (family graph)** | Network effects on the PAYER side | Slide only | **SLIDE ONLY** |
| 6 | **Insurance/payer channel (BPJS, private)** | The "who ultimately pays" answer | Slide only | **SLIDE ONLY** |
| 7 | Caregiver/services marketplace | Take-rate expansion revenue | — | **ROADMAP, do not pitch yet** |
| 8 | Anonymized Bahasa cognition dataset | Research moat | — | **DO NOT PITCH** (ethics minefield on stage) |

---

## 1. Memory Vault — "Cerita Ibu" (the one I'd actually build)

**What:** Kenang already transcribes every conversation. When the elder tells a story ("waktu Dewi lahir tahun '75, hujan deras sekali…"), a background call tags it as a STORY (not a probe answer), extracts it, and compiles a weekly **"Cerita Ibu Minggu Ini"** digest on the guardian dashboard — 3 story cards + a 30-second audio clip of her actually telling it.

**Why a16z cares:**
- **Retention:** the guardian (payer) opens the app weekly for love, not fear. Alert-only products churn when nothing is wrong; stories give the subscription a heartbeat.
- **Viral loop:** story cards have a share button → siblings see it → sibling installs → care-circle (#5) is seeded organically.
- **Data flywheel:** every harvested story becomes a future Tier-3 probe ("Ibu, cerita dong waktu Dewi lahir — hujan ya waktu itu?"). The product literally writes its own test set, forever. This compounds — same argument as the reminder→probe loop, one level up.
- **The dagger vs competitors:** audio-biomarker startups extract features; we extract *heirlooms*. When grandma is gone, the family keeps the vault. Zero-churn story.

**Hackathon build (2h):** one LLM call per session end ("extract self-contained personal stories, verbatim quotes preserved") → `stories.json` → dashboard card panel + one pre-recorded audio clip. Demo beat: after the T1 alert lands (fear), pan down to the story card (love). That emotional one-two is the best 15 seconds available to us.

## 2. Phone-line access (mock, but SAY it)
Most Indonesian 70+ elders don't own capable smartphones; their children do. Kenang over a regular phone call (Twilio voice → same Realtime pipeline) means the elder needs zero hardware. One sentence on stage: *"She doesn't need a smartphone — Kenang can call her landline. Our TAM is every phone in Indonesia, not every iPhone."* Fake dial-in slide; do not build telephony in 24h.

## 3. Personal baseline calibration (slide + one param)
First 14 days = calibration window: no alerts, only baseline CRĪ per tier. Thereafter thresholds are relative (alert on Δ from personal baseline, not absolute 40). Kills the smartest judge objection ("an elder with lifelong bad memory would false-alarm forever"). Implementation post-funding; on stage it's one formula on the rigor slide: `alert if CRI < baseline − 2σ`.

## 4–6. Revenue slides (30 seconds total on stage)
- **Clinician report:** one-click PDF of the trend + probe log, formatted for a geriatrician. Turns "not a diagnosis" into "referral with data." Hospitals (Siloam/Mayapada) become channel partners.
- **Care-circle:** siblings split reminders/visits on one dashboard; each added sibling is a free acquisition and a churn anchor.
- **Payers:** adherence + early-detection data is exactly what insurers price on. Long-term: subsidized subscriptions via insurance, family pays $0.

---

## Recommended stage sequencing (if Memory Vault is built)
Fear → love → greed: T1 alert (fear, V1) → story card (love, V3.1) → payer slide (greed, V3.4-6). Investors remember arcs, not features.

**Gatekeeper note:** V2 addendum §1–5 remains the priority. Memory Vault enters the build ONLY after the onboarding wizard demo is rehearsed and stable. Everything else in this file is slideware by design.
