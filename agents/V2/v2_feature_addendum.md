# KinBridge V2 — Feature Addendum (Builder Instructions)
**Version 2.0 · STANDALONE — do not modify any V1 file. V1 pipeline (scoring, escalation, voice loop) stays frozen; V2 features bolt on beside it.**
**Positioning update: KinBridge is a wellness companion for the elderly — pikun (memory), kesepian (loneliness), penyakit (chronic illness). One app, three pains. This addendum is scoped for an a16z/YC-style pitch demo.**

---

## 0. V2 Scope Matrix

| # | Feature | Strategy | Time budget |
|---|---|---|---|
| 1 | Guardian Dashboard in Bahasa Indonesia | **BUILD** (copy swap) | 1h |
| 2 | Find My Friends (Cari Teman Lama) | **MOCK** (seeded directory, real matching logic) | 2.5h |
| 3 | Reminders — meds, checkups (Pengingat) | **BUILD** (in-memory scheduler + voice delivery) | 3h |
| 4 | Komorbid private health profile → AI tailoring | **BUILD** (rules table, no ML) | 2h |
| 5 | Guardian onboarding wizard incl. "Context Dump" | **BUILD** — this is the pitch centerpiece | 3.5h |

Hard rule: none of these touch `lib/agent.ts` scoring logic. They read from it or feed into it.

---

## 1. Guardian Dashboard — Bahasa Indonesia

Default locale `id`, EN toggle in header (investors read English; family uses Indonesian — demo the toggle, it reads as "we localize properly").

Copy table (single `i18n.ts` object, no i18n library):

| Key | id (default) | en |
|---|---|---|
| `trend.title` | Tren Kesehatan Kognitif | Cognitive Wellness Trend |
| `today.title` | Hari Ini | Today |
| `today.sessions` | percakapan | conversations |
| `today.minutes` | menit terhubung | minutes engaged |
| `today.memories` | kenangan diceritakan ✓ | memories shared ✓ |
| `feed.title` | Momen Terbaru | Recent Moments |
| `alert.banner` | Ibu Sri mungkin perlu ditelepon hari ini | Ibu Sri may need a check-in |
| `alert.body` | Dalam obrolan hari ini, Ibu kesulitan mengingat kenangan keluarga inti. Ibu tenang — Kenang menemaninya dan menawarkan untuk menelepon Anda. | (V1 copy) |
| `alert.cta` | 📞 Telepon Ibu Sri sekarang | 📞 Call Ibu Sri now |
| `alert.ghost` | Lihat tren kesehatan | View wellness trend |
| `warning.recall` | Terlihat penurunan daya ingat kejadian terbaru minggu ini | Noticeable decline in recent event recall this week |
| `reminders.title` | Kepatuhan Pengingat | Reminder Adherence |
| `friends.title` | Teman Lama Ditemukan | Old Friends Found |
| `disclaimer` | Hanya sinyal penyaringan dini — bukan diagnosis medis. Konsultasikan dengan tenaga kesehatan profesional. | (V1 copy) |

Rule unchanged from V1: never "demensia/tes/skor" in family-facing UI chrome — Indonesian included ("kesehatan", "kenangan", "momen").

---

## 2. Find My Friends — "Cari Teman Lama"

**Pain:** kesepian. Elders lose touch with SMA classmates and old coworkers; reconnecting is the single highest-joy social event available to them.

### 2.1 Profile delta (new fields — additive, do NOT edit V1 `elder_context.json`; V2 loads `V2/profile_delta.json` and merges at boot)

```json
{
  "profile_id": "elder_0001",
  "education_history": [
    { "institution": "SMA Negeri 1 Solo", "type": "sma", "years": [1967, 1970] }
  ],
  "work_history": [
    { "employer": "Bank Rakyat Indonesia, Cabang Solo", "role": "teller", "years": [1972, 1985] },
    { "employer": "BRI Kantor Wilayah Jakarta", "role": "admin", "years": [1985, 1998] }
  ]
}
```

### 2.2 Mock directory — `V2/people_directory.json` (seed 6 profiles; 2 must match Ibu Sri)

```json
[
  {
    "person_id": "dir_003",
    "name": "Ratna Kusuma",
    "photo": "avatar placeholder",
    "education_history": [{ "institution": "SMA Negeri 1 Solo", "type": "sma", "years": [1966, 1969] }],
    "work_history": [],
    "city": "BSD, Tangerang",
    "distance_km": 6.1,
    "on_platform": true
  }
]
```

### 2.3 Matching logic (real code, mock data — defensible to technical judges)

```
match(elder, candidate):
  for each pair (e_entry, c_entry) across education+work histories:
    if normalize(e_entry.institution) == normalize(c_entry.institution)
       and years_overlap(e_entry.years, c_entry.years, tolerance=±2):
         score += 10 if type == "sma" else 7      # school bonds > work bonds
  score += proximity_bonus: +3 if distance_km < 10
  return matches where score >= 7, sorted desc
```

### 2.4 UX
- **Elder side (voice):** she says anything like "teman SMA saya dulu…" → intent detected (keyword list: `teman sekolah, teman SMA, teman kerja, teman kantor, dulu di BRI`) → Kenang replies: *"Ibu mau saya carikan teman lama dari SMA Negeri 1 Solo?"* → yes → full-screen card (V1 match-modal pattern): photo, name, "SMA Negeri 1 Solo, angkatan '69 · 6 km dari Ibu" + one button **"Minta Dewi hubungkan"** (guardian-mediated — elders should not get unsolicited DMs; safety = pitch point).
- **Guardian side:** "Teman Lama Ditemukan" card on dashboard with Approve/Ignore. Approve → mock "introduction sent" state.

### 2.5 API additions
`GET /api/v2/friends/matches?profile_id=` → scored list · `POST /api/v2/friends/introduce { person_id }` → flips card state, pushes WS event `friend:introduced`.

---

## 3. Reminders — "Pengingat" (meds, checkups)

**Pain:** banyak penyakit → missed meds. Voice reminder from a companion she likes beats a phone buzz she ignores.

### 3.1 Data — `V2/reminders.json`

```json
[
  {
    "reminder_id": "rem_med_01",
    "type": "medication",
    "label": "Obat tekanan darah (amlodipine 5mg)",
    "schedule": { "freq": "daily", "time": "07:00" },
    "linked_condition": "hipertensi",
    "spoken_template": "Bu, sudah waktunya minum obat tekanan darah ya. Sesudah sarapan, jangan lupa.",
    "ack_window_min": 45
  },
  {
    "reminder_id": "rem_chk_01",
    "type": "checkup",
    "label": "Kontrol dokter jantung — RS Bethsaida",
    "schedule": { "freq": "once", "datetime": "2026-07-21T09:00:00+07:00" },
    "linked_condition": "hipertensi",
    "spoken_template": "Bu, hari Selasa besok ada jadwal kontrol ke dokter Hartono jam 9 pagi. Dewi yang antar ya.",
    "ack_window_min": 1440
  }
]
```

### 3.2 Engine (in-memory `setInterval` tick, 30s — no cron infra)
```
STATES per reminder instance: SCHEDULED → DELIVERED → ACKED | MISSED
tick():
  for r in due_reminders(now):
    if session_active: kenang.speak(r.spoken_template)      # inject as system directive, same channel as V1 pivot
    else:              mark DELIVERED_UNHEARD; retry in 15m (max 3)
elder says "sudah / sudah minum / iya" within ack_window → ACKED
window expires → MISSED → WS `reminder:missed` → dashboard adherence panel + amber warning if 2+ missed meds in 7d
```

### 3.3 The clever bit — reminders feed the V1 scoring engine
Every ACKED medication reminder auto-generates a Tier-2 fact for the next day (*"Obat apa yang kemarin pagi Ibu minum?"*). Adherence and recall become one loop: **the reminder system writes the memory test.** Say this sentence in the pitch.

### 3.4 Dashboard: "Kepatuhan Pengingat" panel — 7-day grid (✓ green / ✗ amber), per reminder. Read-only.

---

## 4. Komorbid — Private Health Profile → AI Tailoring

**Privacy-by-architecture:** conditions live in a separate file, never merged into conversational context verbatim, never displayed on the dashboard as a list.

### 4.1 Data — `V2/health_profile.json` (guardian-entered at onboarding)

```json
{
  "profile_id": "elder_0001",
  "visibility": "private",
  "conditions": ["hipertensi", "diabetes_tipe_2", "osteoartritis_lutut"],
  "allergies": ["seafood_kerang"],
  "mobility": "walks_unaided_short_distances",
  "diet_flags": ["low_sugar", "low_salt"]
}
```

### 4.2 Tailoring rules table (deterministic, no ML — `V2/tailoring_rules.ts`)
Conditions are compiled into BEHAVIORAL directives appended to the Companion system prompt. The condition name itself is NEVER injected — only the behavior:

| Condition | Companion directive injected | Feature effects |
|---|---|---|
| `diabetes_tipe_2` | "Never suggest sweet foods/desserts. If she mentions wanting them, gently steer: 'buah segar juga enak lho, Bu.'" | Restaurant/matchmaking: filter dessert-tagged groups; Beli suggestions exclude sweet |
| `hipertensi` | "Encourage calm activities. If she reports dizziness/headache, treat as safety event: suggest sitting, offer to call Dewi." | Reminders: salt-intake nudge template enabled |
| `osteoartritis_lutut` | "Never suggest activities involving prolonged standing, stairs, or jogging." | Matchmaking: senam group swapped to **senam kursi (seated)** variant; walking clubs filtered out |
| `seafood_kerang` allergy | "If she mentions eating shellfish, express concern and confirm she is feeling well." | Restaurant suggestions filter |

Demo moment: toggle diabetes ON in onboarding → matchmaking result visibly changes (Komunitas Kuliner Senior card swaps its tagline from "wisata kuliner" to "menu sehat rendah gula"). Cheap to build, lands hard.

### 4.3 Guardian dashboard shows ONLY derived signals ("Aktivitas disesuaikan dengan profil kesehatan ✓", adherence grid) — never the condition list. If asked why: "the elder's dignity survives even a shared laptop."

---

## 5. Onboarding — BUILD & DEMO (reverses V1 "mock the context profile")

**V1 skipped onboarding; investors will ask "where does the context file come from?" — so onboarding IS the answer, and it's the best demo moment V2 has.** Elder-side onboarding remains ZERO (she just talks) — keep saying that.

### 5.1 Guardian wizard — `/onboard`, 3 steps, target < 2 minutes on stage

**Step 1 — Dasar (30s):** elder name/photo, birth year, address (autocomplete mock), guardian contacts. Plus education & work history rows (feeds Find My Friends).

**Step 2 — "Ceritakan tentang Ibu" — THE CONTEXT DUMP (the money moment):**
One big textarea + mic button. Guardian brain-dumps a paragraph in natural language:
> *"Ibu Sri suaminya Pak Budi, anaknya Dewi sama Anton. Suka banget sama anggrek bulan di teras, tiap Rabu senam di taman. Suka keroncong, lagu Bengawan Solo. Minum obat darah tinggi tiap pagi. Suka pesen sate lewat Beli, restoran favoritnya omakase ENA…"*

Click **"Proses"** → single GPT-4o structured-output call parses the dump into tiered facts → animated review screen: facts materialize one-by-one into three columns (Tier 1 Identitas / Tier 2 Rutinitas / Tier 3 Kesukaan), each with editable canonical value + auto-generated probe question. Guardian confirms → writes `elder_context.v2.json`.

Extraction call output schema = exact V1 fact schema (`fact_id, tier, category, canonical_value, accepted_aliases, probe_templates_id, probe_cooldown_hours`). Tier assignment rules in the extraction prompt: family/identity/address → 1; meds/routines/recent → 2; tastes/hobbies → 3.

**Pitch line:** "Ten minutes of a daughter's memories becomes a clinical-grade ground-truth file. That's the moat — every week of conversation makes the context graph deeper and the switching cost higher."

**Step 3 — Kesehatan (30s):** condition checkboxes (komorbid) + medication rows with times → writes `health_profile.json` + `reminders.json`. Toggle demo: flip diabetes, show tailoring preview inline.

### 5.2 API
`POST /api/v2/onboard/context-dump { text }` → `{ facts: [...] }` (extraction call) · `POST /api/v2/onboard/complete { facts, health_profile, reminders }` → persists, redirects to dashboard.

**Fallback:** pre-typed dump paragraph on clipboard; if extraction API fails on stage, `?demo=1` loads a cached parse result. Never re-type live.

---

## 6. Updated Demo Flow (4 min, investor cut)

| Time | Beat |
|---|---|
| 0:00 | Hook: pikun, kesepian, penyakit — one aging parent, three pains, zero apps her kids trust |
| 0:30 | **Onboarding**: paste context dump → facts cascade into tiers → toggle diabetes → matchmaking preview changes |
| 1:30 | V1 money path (unchanged): probe hit → trend tick → **T1 miss → pivot + guardian alert (now in Indonesian)** |
| 2:45 | Reminder fires by voice mid-chat, elder acks, adherence grid ticks — "the reminder writes tomorrow's memory test" |
| 3:15 | "Teman SMA" voice moment → Ratna Kusuma card → guardian approves intro |
| 3:35 | Close: wedge = companion; data moat = longitudinal context graph; buyer = adult children (subscription); market = Indonesia's aging curve, then SEA |

---

## 7. Scope Flags (gatekeeper section — read before building)

- **DO NOT** build a real people directory, friend chat, or notifications to third parties. Directory is 6 seeded profiles; "introduction" is a state flip.
- **DO NOT** add drug-interaction checking or dosage logic. We remind; we never advise. One sentence in Q&A: "adherence, not pharmacology."
- **DO NOT** claim HIPAA/PDP compliance on stage — say "privacy-by-architecture, compliance is roadmap."
- **DO NOT** let the extraction step write directly to the live V1 context file during the hackathon — V2 writes `elder_context.v2.json`; the builder flips one import path when ready to integrate.
- Voice onboarding mic button may be visual-only (typed dump is the reliable path).
