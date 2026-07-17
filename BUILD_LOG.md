# KinBridge — BUILD_LOG (decision journal, append-only)

## Stage 1 checkpoint answers (MAIN_WORKFLOW §1.2)

1. **Acute Tier-1 escalation fires when:** a Tier-1 fact is judged `miss` at confidence ≥ 0.8, **or** `CRI_session < 40` (workflow_agent_spec §4). Judge confidence < 0.8 on a T1 miss downgrades to partial for escalation (§5 fail-safe).
2. **CRI for T1 miss, T2 exact, T3 exact** = 100 × (10·0 + 3·1 + 2·1) / (10+3+2) = 100 × 5/15 = **33.3** ✓ (matches tier1_alert_payload.json).
3. **Mocks & their triggers:** people directory (6 seeded profiles, keyword intent: `teman SMA/dulu di BRI…`) · FCM push (Firestore-listener payload, same schema as WS toast) · demo scenarios via triple-tap on Kenang's message = Shift+D (`t1_miss`, `t2_warning`, `match_found`, forced_verdict path) · voice-onboarding mic button (visual only) · social-worker auth/assignment (seeded caseload) · `?demo=1` cached extraction parse (Android: bundled fallback parse).
4. **Hour-6 gate:** the typed-chat pipeline must score correctly (Gate A) before any voice work; voice is forbidden before it passes. Voice is presentation; scoring is the product.
5. **Never show tes/skor/demensia/diagnosis:** all elder-facing surfaces, all family-facing UI chrome (both languages), and the Diary (love-only). Allowed places: none. The trend is always "wellness".
6. **Translated:** interface chrome, diary summaries, story titles. **Never translated:** verbatim quotes, story audio — her words stay in her language.
7. **Elder-side rule (V2.2 §C, LAW):** no forms, toggles, tables or settings — every input is a spoken `GuidedQuestion`: one question per screen, 2–3 large options, "Tidak tahu" always valid, and "Minta tolong Dewi" delegation on every question.
8. **Failure ladder:** voice loop fails → typed-chat fallback · judge API fails → forced_verdict (offline path) · total network loss → narrate over screen recording.

## Decision journal

- 2026-07-17 — Platform: native Android (Java/XML, no Kotlin) + Firestore, per project owner; the docs' Next.js stack maps to: API routes → repository layer, WS → Firestore listeners, in-memory store → Firestore + bundled seed assets.
- 2026-07-17 — STOP FLAG (MAIN_WORKFLOW §⚠): real Firebase login exists but is **bypassed** — entry is a 3-profile role-select (Ibu Sri / Dewi / Sari), zero credentials. Auth code kept in tree (`LoginActivity` etc.), out of the flow. Onboarding auth-gate removed.
- 2026-07-17 — Elder profile + facts load from bundled `assets/elder_context.json` (LAW shape, 11 facts). Firestore is used for live state only (events/trend/alerts) — profile reads can no longer fail on rules.
- 2026-07-17 — Probe templates kept in Indonesian (`probe_templates_id`, canonical). EN-at-boot LLM translation (V2.1 §2.2) deferred — needs the backend judge anyway.
- 2026-07-17 — Judge remains a local alias/keyword stand-in (hour-6 gate allows typed-chat pipeline first). LLM judge (prompts.md §3) requires a backend-held OpenAI key — deferred, marked in code.
- 2026-07-17 — Event labels humanized from fact `category` (schema has no topic field); label map in CompanionActivity.
- 2026-07-17 — Role-select cards are instant-entry (flag: "click = enter that surface"); senior path keeps the accessibility font step before the companion.
- 2026-07-17 — Reconciled to LAW data: bundled `elder_context.json` (11 facts) parsed via org.json; cooldowns in SharedPreferences; `valid_until` honored by the scheduler; local trend fallback for the dashboard.
- 2026-07-17 — Gate A evidence: `ScoringEngineTest#cri_gateA_example_is33_3` passes (12/12). Determinism: pure function, identical output every run.
- 2026-07-17 — Demo Mode = triple-tap Kenang's message, cycling t1_miss → t2_warning → match_found (forced verdicts through the real pipeline). Match modal auto-dismisses 12s (ui_spec §2). T1 pivot warms the background over 2s.
- 2026-07-17 — Role-select live: Ibu Sri → font step → companion; Dewi → dashboard; Sari → read-only caseload panel (V2.2 §B, detail reuses dashboard for elder_0001). Login screens remain in tree, unreachable.
