# KinBridge — UI Spec & Design Tokens
**Aesthetic north star: premium lifestyle brand (Kith / Aimé Leon Dore), not medical software. If a screen looks like a hospital portal, redo it.**

---

## 1. Design Tokens (drop into `tailwind.config` / CSS vars)

### Color — warm, grounded, high contrast
| Token | Hex | Use |
|---|---|---|
| `--cream` | `#F6F1E7` | App background (never pure white) |
| `--espresso` | `#231A12` | Primary text (contrast on cream ≈ 14:1) |
| `--terracotta` | `#C05A2E` | Primary accent, mic orb active, CTAs |
| `--sage` | `#7C8B6F` | Positive states, "exact" recall ticks |
| `--sand` | `#E4D9C3` | Cards, dividers |
| `--amber` | `#B7791F` | Tier-2 warnings |
| `--brick` | `#8C2F1B` | Tier-1 acute alert (reserved — appears ONLY on alerts) |

Rules: no gradients, no drop shadows heavier than `0 1px 2px`, generous whitespace, 12-col grid with fat margins. Restraint = premium.

### Typography
| Role | Font | Notes |
|---|---|---|
| Display / headings | **Fraunces** (Google) | High-contrast serif, editorial warmth |
| Body / UI | **Inter** | Tracking slightly tight (-0.01em) |

Elder-screen scale (non-negotiable): captions **32px+**, companion speech text **40px**, buttons ≥ **64px** tap target, single-column only. Dashboard scale: normal (16px base) — it's for the adult child.

### Accessibility floor
Contrast ≥ 7:1 for all elder-facing text · no interaction requires reading under 32px · every state change also announced by voice · reduced-motion respected.

---

## 2. Screen 1 — Elder Voice Home (`/`)

The app opens HERE. No login, no menu, no onboarding.

```
┌──────────────────────────────┐
│  Selamat pagi, Ibu Sri  🌤   │   ← Fraunces 44px, time-aware greeting
│                              │
│                              │
│         ╭────────╮           │
│         │  MIC   │           │   ← 200px orb, center screen
│         │  ORB   │           │      idle: sand · listening: terracotta pulse
│         ╰────────╯           │      speaking: sage breathing animation
│                              │
│  "Bagaimana kabar tanaman    │   ← live caption of companion speech,
│   kesayangan Ibu?"           │      40px, espresso on cream
│                              │
│  [ ⏸ Berhenti sebentar ]     │   ← single 72px secondary button
└──────────────────────────────┘
```

- Orb is the ONLY primary control. Tap = talk / pause. It pulses gently while listening (200ms ease) — motion communicates state without text.
- Match Found → full-screen modal, Fraunces headline: *"Ada teman baru untuk Ibu!"* + group card (name, distance, schedule) + one button *"Beritahu Dewi"*. Auto-dismiss 12s.
- CALM_REASSURANCE_PIVOT → background warms slightly (`--cream` → `#F3E9DA`, 2s fade), orb slows. Subliminal, never labeled.
- Hidden: Shift+D demo trigger; typed-chat fallback toggle behind triple-tap on greeting.

---

## 3. Screen 2 — Family Dashboard (`/dashboard`)

Framing rule: the word is always **"Wellness"**, never "dementia", "decline" (in UI chrome), or "diagnosis".

```
┌────────────────────────────────────────────────┐
│  KINBRIDGE          Ibu Sri · Gading Serpong   │
├────────────────────────────────────────────────┤
│  ⚠ ACUTE ALERT BANNER (brick, only when live)  │
├──────────────────────────────┬─────────────────┤
│  Cognitive Wellness Trend    │  Today          │
│  [Recharts line: EWMA solid, │  2 sessions     │
│   daily CRI dots, 30d]       │  23 min engaged │
│                              │  5 memories ✓   │
├──────────────────────────────┴─────────────────┤
│  Recent Moments (event feed)                   │
│  ✓ sage   Remembered her orchids       +T3     │
│  ~ amber  Hesitated on Sunday's visit   T2     │
│  ✗ brick  Couldn't recall Pak Budi      T1     │
├────────────────────────────────────────────────┤
│  Early screening signal only — not a medical   │
│  diagnosis. Consult a professional. (persistent)│
└────────────────────────────────────────────────┘
```

- Trend chart: EWMA line in espresso, daily CRI as terracotta dots, 40-threshold as faint dashed sand line. No red zones on the chart itself.
- Event feed copy is humanized ("Remembered her orchids"), never raw fact IDs.
- Disclaimer footer is persistent, not dismissible.

---

## 4. Screen 3 — Acute Alert Modal (dashboard, pushed via WS)

```
┌────────────────────────────────┐
│  ●  Ibu Sri may need a check-in│   ← brick dot, Fraunces 28px
│                                │
│  During today's chat, she had  │
│  difficulty recalling a core   │
│  family memory. She is calm —  │
│  Kenang is with her and offered│
│  to call you.                  │
│                                │
│  [ 📞 Call Ibu Sri now ]        │   ← terracotta primary
│  [ View wellness trend ]        │   ← ghost secondary
└────────────────────────────────┘
```

Tone: reassuring, action-first, zero clinical language. Mirrors the push `notification.body` in `tier1_alert_payload.json` — keep the copy in sync.

---

## 5. Anti-Patterns (instant disqualifiers)

Baby blue / hospital teal palettes · stock photos of smiling seniors · hamburger menus on the elder screen · confetti or gamification on cognitive data · any UI copy that says "test", "score", or "diagnosis" on elder-facing surfaces.
