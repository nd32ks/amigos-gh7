# KinBridge V2.1 — Diary Integration + Full Bilingual Support (Builder Instructions)
**Standalone addendum. Do not modify V1 files or V2 addendum files. Supersedes: Memory Vault dashboard-card placement in `v3_roadmap_pitches.md` §1 — stories now live inside the Diary.**

---

## 0. Scope Matrix

| Feature | Strategy | Time budget |
|---|---|---|
| 1 | Diary ("Buku Harian Ibu") with Cerita Ibu stories integrated | **BUILD** | 3h |
| 2 | Full app i18n — English + Indonesian, all surfaces | **BUILD** | 2h |

---

## 1. The Diary — "Buku Harian Ibu"

**Concept:** one auto-written diary entry per day, composed from that day's conversations. The Cerita Ibu story cards are no longer a separate dashboard panel — they are pinned inside the day's diary entry. The diary is the emotional home of the product; the trend chart is the clinical home. Keep them as separate tabs.

### 1.1 Data — `V2/diary.json`

```json
{
  "entries": [
    {
      "date": "2026-07-17",
      "summary": {
        "id": "Hari ini Ibu bercerita tentang anggrek bulannya yang mulai berbunga, dan tertawa mengingat masa SMA di Solo. Ibu minum obat pagi tepat waktu.",
        "en": "Today Ibu talked about her moon orchids starting to bloom, and laughed remembering her high school days in Solo. She took her morning medicine on time."
      },
      "mood_tag": "ceria",
      "stories": [
        {
          "story_id": "story_0012",
          "title": { "id": "Hujan deras waktu Dewi lahir", "en": "The downpour when Dewi was born" },
          "verbatim_quote": "Waktu Dewi lahir tahun '75 itu, hujannya deras sekali... Bapak sampai basah kuyup cari becak.",
          "audio_clip_url": "/clips/story_0012.mp3",
          "topics": ["keluarga", "Dewi", "1975"],
          "shareable": true
        }
      ],
      "reminders_acked": ["rem_med_01"],
      "sessions": 2,
      "minutes": 23
    }
  ]
}
```

**Rules:**
- `verbatim_quote` is NEVER translated. Her words, her language, always. Only titles and summaries are bilingual. (Authenticity is the feature; a translated quote is a different grandmother.)
- `mood_tag` enum: `ceria | tenang | rindu | lelah` — derived, never asked. Displayed as a small icon, no copy like "mood tracking."
- Wellness data does NOT appear in diary entries beyond gentle facts ("took her medicine on time"). No CRI, no verdicts, no misses. The diary is love-only surface; fear lives in the trend tab.

### 1.2 Generation — ONE session-end LLM call (merged, not two)
Extend the existing session-end hook: single gpt-4o call, structured output, produces `{ diary_summary_id, diary_summary_en, mood_tag, stories[] }` in one shot. Story extraction rules: self-contained personal narrative ≥ 2 sentences, past-tense, first-person; preserve verbatim; skip anything that was a probe answer (probes are scoring data, not stories — keeps the diary honest and the scoring engine uncontaminated).

Each extracted story also emits a Tier-3 fact candidate into a `pending_facts` queue (guardian approves in dashboard → becomes a probe). Flywheel preserved from V3 pitch, now with human-in-the-loop.

### 1.3 UI

**Guardian dashboard:** new tab **"Buku Harian" / "Diary"** next to the trend view. Vertical timeline, newest first: date header (Fraunces), summary paragraph, story cards (sand background, quote in large Fraunces italic, ▶ audio chip, share button), mood icon. Empty state: *"Kenang sedang menemani Ibu — cerita pertama akan muncul di sini." / "Kenang is with Ibu — her first story will appear here."*

**Elder side (cheap, high-impact):** voice command *"bacakan buku harian saya"* → Kenang reads yesterday's `summary.id` aloud. Zero new UI. Demo line: "she can listen to her own life, retold."

### 1.4 API
`GET /api/v2/diary?days=30&locale=id|en` · `POST /api/v2/diary/story/:id/share` (mock — flips shared state, WS `diary:shared`) · `POST /api/v2/facts/approve { story_id }`.

---

## 2. Full Bilingual Support (id + en)

### 2.1 Architecture — one pattern everywhere, no i18n library

```
lib/i18n.ts
export const STR = {
  "diary.tab":        { id: "Buku Harian",  en: "Diary" },
  "trend.title":      { id: "Tren Kesehatan Kognitif", en: "Cognitive Wellness Trend" },
  ...merge ALL keys from v2_feature_addendum.md §1 table...
}
export const t = (key, locale) => STR[key][locale]
```

Locale state: `?lang=` URL param + persisted per user. **Two independent locale settings:**

| Setting | Who | Default | Where set |
|---|---|---|---|
| `guardian_locale` | dashboard, onboarding wizard | `id` | header toggle ID/EN, instant |
| `elder_locale` | voice UI chrome + Kenang's spoken language | `id` | onboarding Step 1, NOT toggleable mid-session |

Elder locale is deliberately not a live toggle — switching a confused elder's language mid-conversation is a product hazard, and saying so on stage is a credibility point.

### 2.2 Surface-by-surface

| Surface | id | en | How |
|---|---|---|---|
| Dashboard chrome | ✓ | ✓ | `t()` everywhere; V2 §1 copy table is the seed |
| Onboarding wizard | ✓ | ✓ | `t()`; context-dump textarea accepts EITHER language (extraction prompt: "input may be Indonesian or English; output canonical_values in the elder's language") |
| Diary summaries | ✓ | ✓ | generated bilingually at write time (§1.2) — no on-demand translation calls |
| Story quotes/audio | original only | original only | never translated (§1.1) |
| Kenang (voice) | ✓ | ✓ | Companion prompt: replace "Speak ONLY Bahasa Indonesia" with "Speak ONLY {{elder_locale_name}}"; keep the honorific rule per language ("Ibu" / "Ma'am" register) |
| Probe templates | ✓ | ✓ | if `elder_locale=en`, translate `probe_templates_id` once at boot (single batched LLM call, cached to `probes_en.json`) — do not hand-write duplicates |
| Judge | n/a | n/a | already language-agnostic (compares reply to canonical value; prompt says reply may be id or en) |
| Alerts & push copy | ✓ | ✓ | payload gains `title_id/title_en`, `body_id/body_en`; dashboard renders per `guardian_locale` |

### 2.3 Demo beat (10 seconds, worth it)
During dashboard walkthrough, click ID→EN: entire dashboard AND today's diary summary switch instantly — but the story quote stays in her Indonesian. Narrate: *"We translate the interface, never her memories."* That sentence does more for the i18n feature than the feature itself.

---

## 3. Decisions Made (flagging)

1. **Stories moved from dashboard cards into the Diary** — one emotional surface, not two competing ones. Trend tab = clinical, Diary tab = love. Supersedes V3 §1 placement.
2. **Merged diary summary + story extraction into one session-end call** — half the cost, no ordering bugs.
3. **Verbatim quotes never translated** — authenticity rule, also our best i18n demo line.
4. **Elder locale locked at onboarding** — live language switching on an elder-facing surface is a hazard, framed as a deliberate safety decision.
5. **Probe answers excluded from story extraction** — keeps scoring data and diary content strictly separated; judges will probe this boundary.
6. **Story→probe flywheel now guardian-approved** (`pending_facts` queue) — human-in-the-loop before anything becomes a memory test.

**Scope flags:** no real audio clipping pipeline (pre-cut 1–2 mp3s for demo); no share-to-WhatsApp (state flip + toast only); no third language; diary is read-only (no manual entries — "the app writes it" IS the product).
