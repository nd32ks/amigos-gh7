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
- 2026-07-17 — Gender picker added to CreateAccountActivity (female/male, required, stored in users/{uid}); auth path reachable via ghost link on the role-select step (keeps stop-flag flow + auth both usable).
- 2026-07-17 — Guardian i18n toggle restored on the dashboard (guardian surfaces keep toggles per V2.2 §C).
- 2026-07-17 — Context-dump wizard: `DumpParser` (deterministic keyword rules, 3/3 unit tests incl. the V2 §5 demo paragraph), review cascade animation, facts persist to prefs and merge into the live profile at load (live V1 asset untouched per §7).
- 2026-07-17 — Reminders engine (SCHEDULED→DELIVERED→ACKED|MISSED, ack keywords, in-app delivery) + routine chain with demo-compressed 12s step delays (logged deviation) + komorbid tailoring (seated movement swap, culinary tagline swap, derived-only dashboard signal) + 7-day adherence grid (past days mocked).
- 2026-07-17 — Friends matching: `FriendMatcher` (real algorithm per V2 §2.3, 4/4 unit tests: Ratna 13, Sutrisno 7, tolerance/proximity/sort). Elder intent keywords → consent question → friend card → guardian Approve card via Firestore delegations.
- 2026-07-17 — Guided-choice layer (V2.2 §C/D): 4th demo scenario shows the komorbid question with four big options; "Minta tolong Dewi" delegates to a guardian dashboard card (Approve → completed).
- 2026-07-17 — Diary (V2.1): session-end entry generation (bilingual summaries, derived mood, verbatim stories with probe answers excluded), dashboard Trend|Diary tabs, story cards with share state-flip, elder "buku harian" read-back command. Phase 3 complete; 19/19 unit tests green.
- 2026-07-17 — White-screen fix: tapping the already-active locale triggered no recreate and stranded the faded-out content; same-locale taps now swap steps in place. Root cause of the logout-then-stuck report.
- 2026-07-17 — Companion exit: back chevron returns to role-select (elder surface stays menu-free otherwise).
- 2026-07-17 — V2.3 games built per the corrected addendum: game_variants on 4 facts in elder_context.json, playful delivery ~1-in-3 never-two-in-a-row, delivery_style on events, dashboard flavor line. CHECKLIST §5 fixes applied: cocok_kata exact = 0.75 credit (unit-tested, 79.17 case), miss full weight, T1 cocok_kata miss never auto-acutes.
- 2026-07-17 — Gemini wired for 🤖2 judge (prompts.md §3 verbatim rules, temp 0, JSON) + 🤖3 dump extraction; local keyword judge + DumpParser remain as the failure-ladder fallbacks. Key is user-supplied, flagged in code as demo-scope.
- 2026-07-17 — Voice layer: mic button → SpeechRecognizer (id-ID/en-US per locale) auto-sends the turn; Kenang speaks every message via TextToSpeech (0.9 rate, locale-matched). RECORD_AUDIO requested at tap.
- 2026-07-17 — Evidence: 21/21 unit tests green (scoring 14, dump parser 3, friends 4).
- 2026-07-17 — Navigation: floating right-edge tab (hairline half-pill, programmatic, zero layout intrusion) on companion/dashboard/care surfaces → slide-in right drawer with all features: Kenang, Dashboard, Care panel, Diary (deep-link opens dashboard on the diary tab), Profile setup, Role select, Sign in.
