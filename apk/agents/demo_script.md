# KinBridge — 3-Minute Demo Runbook
**Rehearse ×3 before submission. The pairwise judging format rewards the team whose demo lands cleanly, not the team with the most features.**

---

## Stage Setup (do this BEFORE walking up)

- [ ] Laptop 1 (facing judges via projector): **Family Dashboard** `/dashboard`, trend chart pre-seeded with 30-day gentle decline (82→60)
- [ ] Laptop 2 / phone (held by presenter 1): **Elder voice UI** `/`
- [ ] Both connected to phone hotspot (NOT venue wifi)
- [ ] Demo Mode tested 5 minutes prior (Shift+D on elder client)
- [ ] Fallback: typed-chat toggle ON standby; screen-recording of full flow on desktop as last resort
- [ ] One presenter speaks; one presenter operates. Never both.

---

## Script (3:00)

### 0:00–0:30 — The Hook (Problem Definition points)
> "Indonesia has one of the fastest-aging populations in Asia. Dementia is usually caught 2–3 years too late — because screening requires a clinic visit, and elders don't go. What if the screening happened invisibly, inside a daily conversation your mother already enjoys?
> This is KinBridge. It doesn't test her. It talks to her."

### 0:30–1:20 — Live Conversation (UX + Technical points)
Presenter 1 plays the elder, speaks Bahasa Indonesia to the voice UI.

1. Kenang greets warmly; small talk (1 exchange).
2. Kenang weaves probe: *"Bagaimana kabar tanaman kesayangan Ibu? Sudah berbunga?"* → presenter answers correctly: *"Anggrek bulan saya sudah berbunga!"*
   → **point at dashboard**: event feed ticks green `+1 · T3 exact`, trend nudges up.
3. Keyword "anggrek" also fires **Match Found modal**: *"Klub Anggrek Gading Serpong — 1.2 km away."*
   > "Notice the same transcript feeds two engines: cognitive scoring AND social matchmaking. Loneliness is a dementia risk factor — we treat both."

### 1:20–2:10 — The Money Moment (Innovation points)
4. Kenang probes: *"Bagaimana kabar suami Ibu hari ini?"* → presenter (as elder): *"Suami saya... saya tidak ingat, siapa ya namanya?"*
   *(If voice misbehaves: Shift+D → `t1_miss`. Judges cannot tell the difference — it runs the real pipeline.)*
5. Narrate what lands, in order, live:
   - Kenang's voice softens mid-conversation: *"Kita istirahat dulu ya. Mau saya teleponkan Dewi?"* — **no correction, no alarm, just care**
   - Dashboard: red alert toast + push-notification mock on the family side
   - Trend line ticks down
   > "A Tier-1 memory—her husband's name—was missed. Within two seconds: the AI pivoted to reassurance, the daughter got an alert, and the wellness trend updated. No pause-length analysis, no wearables. Just a context file and a deterministic judge."

### 2:10–2:45 — The Rigor Slide (Technical Implementation points)
Show one slide: CRI formula `100·Σwᵢsᵢ/Σwᵢ`, tier weights 10/3/2, EWMA α=0.3, STT confidence gate, escalation thresholds.
> "Every verdict is a temperature-zero structured LLM call scored against a weighted recall index — bounded, probe-count invariant, and reproducible. Same transcript, same score, every time."

### 2:45–3:00 — Close (Market Fit points)
> "The context file takes a family 10 minutes to fill in. The elder never downloads a 'dementia app' — she gets a premium companion that speaks her language. Families pay for peace of mind; we already know they do — that's the entire market for CCTV grandma cams. KinBridge is the version with dignity."

---

## Q&A Ammunition (predictable judge questions)

| Question | Answer |
|---|---|
| "Isn't this diagnosing without a doctor?" | No — dashboard copy everywhere says "early screening signal, not a diagnosis." It's a referral trigger, like a smartwatch ECG. |
| "What if STT mishears her?" | Confidence gate at 0.75 — low-confidence turns are excluded, never scored. False positives are our #1 designed-against failure. |
| "Why not analyze speech patterns/pauses?" | Fragile, compute-heavy, language-dependent. Structural recall against ground truth is deterministic and explainable to a doctor. |
| "Privacy?" | Context file is family-provided, stays on-device/server, no third-party sharing. (MVP: acknowledge auth/encryption is post-hackathon scope.) |
| "Does more chatting inflate the score?" | No — CRI is a weighted ratio, probe-count invariant. That's exactly why we don't use the raw point ledger for the trend. |
| "Bahasa quality?" | Native id-ID via Realtime API voice; register-correct honorifics ("Ibu") enforced in prompt. |

---

## Failure Ladder (execute top-down, never apologize on stage)

1. Voice loop fails → toggle typed-chat mode, keep narrating identically
2. Judge API fails → Shift+D scenarios use `forced_verdict` (offline path)
3. Total network loss → play 90-second screen recording, narrate live over it
