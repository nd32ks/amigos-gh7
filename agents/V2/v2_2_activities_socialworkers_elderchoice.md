# KinBridge V2.2 — Activities/Routines · Social Worker Profile · Elder Guided-Choice Design (Builder Instructions)
**Standalone addendum. Extends V2 §3 (reminders → routine chains). Overrides ALL elder-facing forms/toggles anywhere in the doc set (§C wins). Social workers = preview build + pitch; full integration is post-hackathon.**

---

## 0. Scope Matrix

| # | Feature | Strategy | Time |
|---|---|---|---|
| A | Activities / Routine chains | **BUILD** (extends reminders engine) | 2h |
| B | Social Worker third profile | **PREVIEW** (read-only panel, mock caseload) | 1.5h |
| C | Elder Guided-Choice interaction layer | **BUILD** — this is a design-criteria winner | 2.5h |
| D | Delegate-to-guardian ("Minta tolong Dewi") | **BUILD** (part of C) | 1h |

---

## A. Activities & Routines — chained care sequences

**Concept:** a reminder is one event; a **routine** is a chain. Med → wait 10 min → "gerak-gerak dulu yuk Bu, lima menit saja" → then → "sekarang minum air putih ya Bu." Each step delivered by Kenang's voice, acked conversationally, next step scheduled off the previous ack.

### A.1 Data — `V2/routines.json`

```json
{
  "routine_id": "rtn_morning_01",
  "label": { "id": "Rutinitas Pagi", "en": "Morning Routine" },
  "trigger": { "type": "time", "time": "07:00", "freq": "daily" },
  "steps": [
    {
      "step_id": "s1", "type": "medication",
      "spoken_prompt": "Bu, sudah waktunya minum obat tekanan darah ya. Sesudah sarapan.",
      "ack_keywords": ["sudah", "sudah minum", "iya"],
      "ack_window_min": 45, "on_miss": "escalate_adherence"
    },
    {
      "step_id": "s2", "type": "movement", "after": { "step_id": "s1", "delay_min": 10 },
      "spoken_prompt": "Sekarang gerak-gerak sebentar yuk Bu, jalan keliling teras lima menit saja. Sambil lihat anggreknya.",
      "ack_keywords": ["sudah", "oke", "iya"], "ack_window_min": 30, "on_miss": "skip_silent"
    },
    {
      "step_id": "s3", "type": "hydration", "after": { "step_id": "s2", "delay_min": 5 },
      "spoken_prompt": "Jangan lupa minum air putih ya Bu.",
      "ack_keywords": ["sudah", "iya"], "ack_window_min": 30, "on_miss": "skip_silent"
    }
  ],
  "tailored_by": ["hipertensi", "osteoartritis_lutut"]
}
```

### A.2 Engine — extend V2 §3 state machine (do not rewrite it)
`SCHEDULED → DELIVERED → ACKED → (schedule next step) | MISSED → per-step on_miss`. Rules: only step 1 of a chain time-triggers; subsequent steps trigger off ack + delay. `on_miss: escalate_adherence` = existing adherence panel path (meds only). `skip_silent` = movement/hydration never nag and never escalate — **meds are duty, movement is invitation.** Komorbid tailoring composes: `osteoartritis_lutut` swaps movement prompts to seated variants (reuse V2 §4 rules table).

Personalization touch (cheap, judges love it): movement prompts reference her Tier-3 facts ("sambil lihat anggreknya"). One template variable, big warmth payoff.

### A.3 Guardian side: routine builder is a simple list editor (guardian-facing, forms allowed there); adherence panel gains chain view (✓✓✗ per step). API: `GET/POST /api/v2/routines`, WS `routine:step` events.

---

## B. Social Workers — the third profile ("Panel Perawat")

**Story:** Indonesia's healthcare access is urban-concentrated. KinBridge lets a certified social worker in Yogyakarta care for 15 elders across rural Java from home — remote caseload monitoring, income without commute, and more equal care distribution. Full integration post-hackathon; hackathon builds a credible preview.

### B.1 Hackathon build — read-only caseload panel at `/care`
- Entry via the role-select screen (see MAIN_WORKFLOW stop flag): "Sari — Social Worker".
- **Caseload list:** 3 mock elders (Ibu Sri + 2 seeded), each card: name, area, wellness trend sparkline, adherence %, last conversation, alert badge if any.
- **Detail view = reuse the guardian dashboard component** with a `role="care_worker"` prop that enforces the permission matrix below. Zero new layouts.
- One mock action: "Tulis catatan kunjungan" (visit note) → appends to a notes feed, WS to guardian dashboard ("Catatan baru dari Perawat Sari").

### B.2 Permission matrix (say this on stage — it's the trust story)

| Data | Guardian | Social worker |
|---|---|---|
| Wellness trend + alerts | ✓ | ✓ |
| Adherence / routines | ✓ | ✓ (can add visit notes) |
| Diary & Cerita Ibu stories | ✓ | ✗ (family-only by default; guardian can grant) |
| Komorbid condition list | ✓ (they entered it) | ✗ — sees derived care directives only |
| Onboarding / profile editing | ✓ | ✗ |

### B.3 Mock data: `V2/care_worker.json` (Sari's profile + caseload of 3 profile_ids). No auth, no assignment flow, no payments — those are the "later integration" and belong in the pitch only.

---

## C. Elder Guided-Choice Layer — design principles (LAW for all elder surfaces)

**Prime rule: the elder is never given an interface — she is asked a question.** Every doc's elder-facing UI is subordinate to these principles, including V2.1's language toggle (guardian keeps the toggle; the elder gets a question).

### The Six Principles ("Tanya, Jangan Suruh" — Ask, Don't Instruct)

1. **One question per screen, spoken AND shown.** Kenang voices every question; the screen shows the same words at 40px. Never two inputs visible at once.
2. **Choices are offered, never configured.** No toggles, dropdowns, tables, or settings on any elder surface — ever. Language example: instead of a toggle, Kenang asks *"Ibu lebih nyaman ngobrol pakai Bahasa Indonesia, atau English?"* with two large buttons; a spoken answer works too.
3. **Three ways to answer, always:** speak it · tap a big button (typed input only where unavoidable) · **delegate it** — every question carries a third option: *"Minta tolong Dewi"* (see §D).
4. **"Tidak tahu" is always a valid answer.** Never blocks progress, never re-asked in the same session, silently queued for the guardian. An elder must never feel tested by a form (the app's entire thesis).
5. **Multi-field data is a conversation, not a form.** Komorbid/medical-history intake = a question feed, one item at a time: *"Ibu punya darah tinggi?"* → Ya / Tidak / Tidak tahu / Minta tolong Dewi. Progress framed socially ("sedikit lagi, Bu"), never as 3/12 steps.
6. **Defaults are pre-chosen and reversible.** Every choice has a sensible default so skipping everything still yields a working app; nothing is ever a blocking wizard on the elder side.

### C.1 Implementation — one reusable component: `<GuidedQuestion>`
Props: `{ spoken_text, display_text, options[2-3], allow_voice, allow_delegate, default_option }`. Renders: question (Fraunces 40px), 2–3 buttons ≥ 72px, mic active, delegate chip. Emits one answer event. **Every elder-side input in the entire app routes through this single component** — komorbid intake, language choice, friend-intro consent, routine confirmations. One component, one test, total consistency.

---

## D. Delegate-to-Guardian — "Minta tolong Dewi"

Any GuidedQuestion delegated → task card on guardian dashboard:

```json
{
  "delegation_id": "dlg_014",
  "from": "elder_0001",
  "question_key": "komorbid.riwayat_jantung",
  "question_text": { "id": "Apakah Ibu punya riwayat penyakit jantung?", "en": "Does Ibu have a history of heart disease?" },
  "elder_note_transcript": "Tanya Dewi saja ya, dia yang lebih hafal.",
  "status": "pending | completed",
  "created_at": "2026-07-17T10:04:00+07:00"
}
```

Guardian answers on their dashboard (forms allowed there) → answer merges into the same profile files (`health_profile.json` etc.) → Kenang closes the loop next session: *"Dewi sudah bantu isi ya Bu, terima kasih."* API: `POST /api/v2/delegate`, `POST /api/v2/delegate/:id/complete`, WS `delegation:new / delegation:completed`.

**Demo beat (15s, strong):** elder is asked a komorbid question → taps "Minta tolong Dewi" → card pops on the guardian screen live. Narrate: *"She's never blocked, never tested by a form — the family fills the gaps. That's what elder-first actually means."*

---

## E. Decisions Made (flagging)

1. **Real login killed, role-select built** (see MAIN_WORKFLOW stop flag) — 3-profile switcher demos better than auth.
2. **Routines extend the reminders engine** rather than a new scheduler — one state machine, chains via `after` pointers.
3. **Meds escalate, movement never does** — nagging an elder about exercise poisons the companion relationship; invitation-only.
4. **Social worker sees derived directives, never diagnoses; diary is family-only by default** — the permission matrix is the pitch, not the panel.
5. **Guardian keeps toggles/forms; elder gets GuidedQuestion for everything** — V2.1 elder-side language toggle is hereby superseded by a spoken choice at onboarding.
6. **One `<GuidedQuestion>` component for all elder input** — consistency by construction.

**Scope flags:** no social-worker auth/assignment/payments · no routine editor on elder side · no free-form typing on elder surfaces except the chat fallback · delegation is one-way elder→guardian (no reverse nagging).
