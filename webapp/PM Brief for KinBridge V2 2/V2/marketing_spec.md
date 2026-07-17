# KinBridge — Marketing Spec V1
**Companion to `pitch_v2.md` (investors) — this doc is for the PUBLIC: landing page, app store, social. The buyer is the adult child; the user is her mother. Market to the daughter, design for the mother, and never let either feel the word "dementia" land on them.**

---

## 1. Positioning

| Element | Value |
|---|---|
| Category (public) | Wellness companion for aging parents — NOT "health tech", NOT "dementia app" |
| One-liner (id) | **Teman ngobrol untuk Ibu. Ketenangan untuk Anda.** |
| One-liner (en) | A companion for her days. Peace of mind for yours. |
| Brand personality | Warm, dignified, premium-calm. Kith-catalog restraint, grandmother-warmth content |
| Emotional promise | To the daughter: "you'll know she's okay." To the mother: "someone lovely to talk to." |
| Enemy (implicit, never named) | The grandma cam. Surveillance-shaped love. |

**The one sentence everything hangs on:** *"Dia tidak diuji. Dia ditemani."* (She isn't tested. She's accompanied.)

## 2. Audiences & message hierarchy

| Audience | Lead message | Proof point to show |
|---|---|---|
| Adult child 30–50 (BUYER) | Know how mom is really doing — without cameras, without nagging her | Wellness trend + diary screenshot pair |
| Elder (USER — marketed to via the child) | A friendly voice that remembers your stories and your orchids | The mic orb; zero forms |
| Siblings (viral loop) | Her stories, saved in her own voice | Shared Cerita Ibu card |
| Social workers / clinics | Carry a caseload from home; care reaches past cities | Panel Perawat preview |

## 3. Feature marketing names & benefit copy (feature → feeling)

| Product feature | Marketing name | Benefit line (id) | Benefit line (en) |
|---|---|---|---|
| Voice companion | **Kenang** | Teman ngobrol yang selalu ada, dalam Bahasa Indonesia yang hangat | A warm voice, always there |
| Contextual recall pipeline | **Tren Kesehatan** (never "screening engine") | Kenali perubahan lebih awal, dari obrolan biasa | Notice changes early — from ordinary chats |
| Diary + Memory Vault | **Buku Harian & Cerita Ibu** | Kisah hidup Ibu, tersimpan dengan suaranya sendiri | Her life's stories, kept in her own voice |
| Reminders & routines | **Rutinitas Sehat** | Obat, gerak, dan air putih — diingatkan dengan sayang, bukan alarm | Reminded with love, not alarms |
| Find my friends | **Cari Teman Lama** | Teman SMA dan teman sekantor dulu — lebih dekat dari yang Ibu kira | Old classmates, closer than she thinks |
| Guided choice / delegation | **Tanpa Formulir** | Tidak ada formulir. Hanya pertanyaan hangat — dan Dewi bisa bantu | No forms. Just gentle questions — family can help |
| Social worker panel | **Panel Perawat** | Perawatan profesional, sampai ke pelosok | Professional care, beyond the cities |

**Naming rules:** public copy NEVER uses: skrining, tes kognitif, demensia, Alzheimer, deteksi, pasien, monitoring/pantau (surveillance vibe). ALWAYS: kesehatan, kenangan, momen, temani, kabar.

## 4. "How it works" — consumer version (3 steps, the only diagram the public sees)

1. **Ceritakan tentang Ibu (10 menit).** Anda menulis bebas: keluarga, kebiasaan, kesukaannya. KinBridge menyusunnya jadi profil pribadi. *(You brain-dump; we build her profile.)*
2. **Kenang menemani setiap hari.** Ngobrol santai, pengingat obat yang ramah, cerita-cerita lama. Ibu tidak perlu belajar aplikasi — cukup bicara. *(She just talks.)*
3. **Anda selalu tahu kabarnya.** Tren kesehatannya, buku hariannya, dan kabar penting — langsung ke ponsel Anda. *(You see the trend, the diary, and anything urgent.)*

Never explain scoring math, tiers, or probes in public copy. That lives in the clinician/investor layer only.

## 5. Launch asset checklist (hackathon-weekend scope)

| Asset | Status / source |
|---|---|
| Animated landing intro (Apple-style "Halo.") | **GUIDANCE ONLY — §9 below.** Build with the team's design.md skill |
| Landing page sections | Structure in §8; visual execution via design.md skill |
| App store copy | §6 below, ready to paste |
| Demo video 60s | Cut from rehearsal recording; subtitle id+en |
| Social teasers | 3 stills from `ui_mockup.html` + benefit lines from §3 |
| Founder story post | "We built this for our own grandmothers" angle — write in first person, no feature list |

## 6. App store copy (draft, id)

**Title:** KinBridge — Teman untuk Orang Tua Kita
**Subtitle:** Teman ngobrol, buku harian, dan kabar baik untuk keluarga
**Description (short):** Kenang menemani Ibu ngobrol setiap hari dalam Bahasa Indonesia — mengingatkan obat dengan sayang, menyimpan cerita-ceritanya, dan membantu menemukan teman lama. Anda mendapat tren kesehatannya, buku hariannya, dan ketenangan pikiran. Tanpa kamera. Tanpa formulir. Tanpa merepotkan Ibu.
**Keywords:** lansia, orang tua, wellness, pengingat obat, teman ngobrol, keluarga

## 7. Claims guardrails (legal-safe marketing)

- Allowed: "kenali perubahan lebih awal", "sinyal dini", "tren kesehatan kognitif"
- Forbidden: any claim of detecting/diagnosing/predicting dementia or Alzheimer's; "clinically proven"; "recommended by doctors" (until it is)
- Every wellness-trend visual in public materials carries the caveat line in small text: *"Sinyal penyaringan dini — bukan diagnosis medis."*
- Testimonials must be about companionship/peace of mind, never about medical outcomes

## 8. Landing page structure (mirrors `hello_intro.html`)

1. **Halo.** — animated cursive greeting cycling id/en, then wordmark + one-liner
2. "Dia tidak diuji. Dia ditemani." — full-viewport statement
3. Three feature moments (fade on scroll): Kenang orb · Buku Harian quote card · Tren + alert (softened)
4. How-it-works 3 steps (§4)
5. CTA: "Mulai dengan cerita tentang Ibu Anda" → onboarding
6. Footer: caveat line + trust marks

## 9. Animated "Halo." intro — creative direction (guidance for the design.md skill, not built here)

**Reference:** Apple's original Mac "hello" — a single cursive word drawing itself on an empty screen, held longer than feels comfortable, then everything else earns its way in.

- **Sequence:** blank cream screen → cursive **"Halo."** draws itself in terracotta stroke (~2.5s) → brief hold → cycles to **"Hello."** → **"Apa kabar, Bu?"** → final word stays, KINBRIDGE wordmark + one-liner fade up beneath. Total ≤ 10s, skippable by scroll.
- **Technique hints:** SVG stroke-dash draw-on for the cursive (or a left-to-right clip-path sweep on a script font — cheaper, near-identical read). Stroke first, fill blooms in at the end. `prefers-reduced-motion` → static final frame.
- **Type:** cursive word in a warm script; everything after in Fraunces/Inter per `ui_spec.md` tokens. Never animate more than one element at a time — restraint IS the Apple effect.
- **Motion rules:** slow (≥1s transitions), one idea per viewport, fade+rise reveals on scroll, no parallax, no confetti, nothing bounces.
- **Do NOT:** autoplay sound, loop the hello forever, or let the animation delay the CTA on mobile.
