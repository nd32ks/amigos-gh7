# KinBridge

**Cognitive wellness screening hidden inside a conversation your mother already loves.**

A voice-first Bahasa Indonesia AI companion for aging adults that turns natural daily small talk into a continuous, non-invasive early-warning signal for cognitive decline — plus a family dashboard and hyper-local social matchmaking.

---

## The Problem

Indonesia's 60+ population is projected to keep climbing sharply through 2050, while dementia is typically diagnosed years after onset. Clinical screening (MMSE/MoCA) requires a clinic visit, feels stigmatizing, and happens at best once a year. Families notice "grandma repeated herself" only when decline is advanced. Meanwhile elders face chronic loneliness — itself a major dementia risk factor.

## The Insight: Context-Dump Dementia Detection

Instead of fragile audio biomarkers (pause-length analysis, prosody models), we pre-load a structural ground-truth file about the elder — `elder_context.json` — spouse's name, home cluster, what she ate today, the restaurant she rated 5 stars on Beli. A warm AI companion weaves these facts into natural conversation as gentle questions. A background evaluation agent deterministically scores every recall attempt against ground truth. Conversation in; digital biomarker out.

**She never takes a test. The test is the conversation.**

## How It Works

1. **Kenang**, a Bahasa Indonesia voice companion, chats daily — and covertly probes at most 1 fact per 3 turns (`mark_probe` function-calling).
2. The **Workflow Evaluation Agent** judges each reply (temperature-0 structured LLM call): exact / partial / miss / no_answer.
3. Scores roll into a **Cognitive Recall Index** (0–100, tier-weighted: core identity ×10, recent events ×3, preferences ×2) smoothed by 7-day EWMA.
4. **Adjusted Care escalation:** Tier-3 misses chart silently; Tier-2 patterns raise a dashboard warning; a Tier-1 miss (e.g., spouse's name) triggers an instant dual response — the companion pivots to calm reassurance and offers to call the daughter, while the family gets a high-priority alert in under 2 seconds.
5. **Social matchmaking:** the same transcript is mined for interests; mentioning her orchids matches her with a gardening club 1.2 km away.

## Judging Criteria Map

| Criterion | Where we score |
|---|---|
| Presentation & Communication | 3-min live demo, one-formula rigor slide, zero jargon hook |
| Problem Definition | Late dementia diagnosis + elder loneliness in Indonesia, precisely scoped to screening (not diagnosis) |
| Impact & Feasibility | Runs on one context file + commodity LLM APIs; family fills the file in 10 minutes |
| UX & Design | Elder-first: voice-only, massive type, zero navigation. Premium lifestyle aesthetic — dignity, not medical beige |
| Market Fit / Viability | Families already pay for grandma cams and caregiver apps; subscription via adult children, Indonesian market first |
| Technical Implementation | Realtime voice loop + deterministic judge pipeline + weighted scoring engine + live WS escalation, end-to-end < 2s |
| Innovation & Novelty | Context-dump ground-truth auditing as a cognitive biomarker — no wearables, no audio DSP, language-agnostic by design |

## Stack

Next.js 14 · OpenAI Realtime API (voice, id-ID) · gpt-4o-mini judge (JSON mode, temp 0) · Socket.io · Recharts · in-memory store (hackathon scope: no auth, no DB)

## Repo Docs

| File | What |
|---|---|
| `system_architecture.md` | Components, API contracts, build order |
| `workflow_agent_spec.md` | State machine, CRI/EWMA math, edge cases, KPIs |
| `elder_context.json` | Mock ground-truth profile (Tiers 1–3) |
| `tier1_alert_payload.json` | Acute escalation payload |
| `prompts.md` | Companion + Judge prompts, calibration examples |
| `ui_spec.md` | Design tokens, 3 screens |
| `demo_script.md` | 3-minute runbook + failure ladder |

## Honest Limits

This is an early screening signal, **never a diagnosis**. Every dashboard surface carries that caveat. Clinical validation, consent flows, auth, and data protection are explicitly post-hackathon scope.
