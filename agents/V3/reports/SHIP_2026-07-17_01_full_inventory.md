# SHIP REPORT — Full inventory (V1 core + V2 + V2.1 + V2.2 + V2.3 + navigation + voice) — 2026-07-17

Phase: 1–4 (in progress) · Gate target: A/B · Checklist rows claimed: V1 §1 (9 rows), V2 §2 (6 rows), V2.1 §3 (4 rows), V2.2 §4 (6 rows), V2.3 §5 (2 rows + §5 fixes)

Environment note: this machine has **no emulator/device** (`adb devices` empty, no AVDs). Per the evidence rules, every check requiring a running UI is therefore graded **PARTIAL** even where wiring is complete and compiles. Logic-level checks (unit tests, static greps, JSON validation) were executed for real and are graded DONE.

Executed evidence (shared, raw):

```
$ ./gradlew :app:testDebugUnitTest --console=plain --rerun-tasks
BUILD SUCCESSFUL in 3s
23 actionable tasks: 23 executed
=== test suites ===
name="com.amigos.kinbridge.DumpParserTest" tests="3" skipped="0" failures="0" errors="0"
name="com.amigos.kinbridge.FriendMatcherTest" tests="4" skipped="0" failures="0" errors="0"
name="com.amigos.kinbridge.ScoringEngineTest" tests="14" skipped="0" failures="0" errors="0"
```

```
$ grep -rniE "tes|skor|demensia|diagnos|score|test" app/src/main/res/layout/activity_companion.xml app/src/main/res/layout/activity_onboarding.xml
(no matches)
$ grep -niE "tes|skor|demensia|diagnos" app/src/main/res/values-in/strings.xml
59: <string name="disclaimer">Sinyal penyaringan awal — bukan diagnosis medis…</string>
  → reviewed: this IS the mandated caveat line, not a violation.
$ python3 -c "fact_id uniqueness"
facts: 11 unique: 11
```

---

## Claim 1: "Scoring engine (raw ledger + CRI + EWMA)"

STATUS: DONE

SPEC SOURCE: workflow_agent_spec.md §4; MASTER_CHECKLIST §1.

FILES TOUCHED:
- app/src/main/java/com/amigos/kinbridge/scoring/ScoringEngine.java (new)
- app/src/test/java/com/amigos/kinbridge/ScoringEngineTest.java (new)

VERIFY CHECK EXECUTED: CRI(T1 miss, T2 exact, T3 exact) = 33.3
```
$ ./gradlew :app:testDebugUnitTest --rerun-tasks
name="com.amigos.kinbridge.ScoringEngineTest" tests="14" skipped="0" failures="0" errors="0"
  incl. cri_gateA_example_is33_3 → 33.3 ±0.05 PASS
```
RESULT: matches spec? YES — Table A/B math, tier weights 10/3/2, EWMA α=0.3, MIN_PROBES=3 all unit-tested.

DETERMINISM: YES — pure function; test run twice (`--rerun-tasks` + prior run), identical 14/14 both times.

INVARIANTS TOUCHED: none (no UI).

EDGE CASES COVERED: empty-event CRI=0 · T1 low-confidence downgrade · CRI<40 acute · T3 silent. None omitted.

NOT DONE / KNOWN GAPS: none.

DIRECTIVE 9 LOG: BUILD_LOG.md 2026-07-17 (scoring engine entry).

---

## Claim 2: "Escalation rules (acute/warning/silent)"

STATUS: DONE (rule level) — e2e delivery noted below.

SPEC SOURCE: workflow_agent_spec.md §4–5; tier1_alert_payload.json.

FILES TOUCHED:
- scoring/ScoringEngine.java (escalation())
- CompanionActivity.java (applyVerdict → ACUTE → saveAlert + CALM_REASSURANCE_PIVOT text + warm background)
- ElderRepository.java (saveAlert), DashboardActivity.java (alert listener → modal)

VERIFY CHECK EXECUTED: T1 miss conf ≥0.8 → acute; conf <0.8 → downgraded; T2×2/ΔEWMA>15 → warning; T3 → silent
```
ScoringEngineTest: escalation_t1MissHighConfidenceIsAcute / …LowConfidenceIsDowngraded /
…lowSessionCriIsAcute / …twoT2MissesIsWarning / …ewmaDropOver15IsWarning / …t3MissIsSilent — 14/14 PASS
```
RESULT: matches spec? YES at rule level.

DETERMINISM: YES.

INVARIANTS TOUCHED: §7 "games never escalate; only meds + T1/T2 recall do" — cocok_kata T1 miss explicitly blocked from auto-acute (CompanionActivity.java applyVerdict).

EDGE CASES COVERED: confidence downgrade fail-safe · dedupe via cooldowns · no-answer exclusion. NOT covered: ewma_drop_7d live computation (passed 0 in-session; rolling computation pending backend).

NOT DONE / KNOWN GAPS: alert→device delivery is Firestore-listener, not FCM push (spec-authorised mock per workflow_agent_spec §1); not runtime-verified on device.

DIRECTIVE 9 LOG: BUILD_LOG.md (escalation + cocok_kata guard entries).

---

## Claim 3: "Context profile elder_context.json"

STATUS: DONE

SPEC SOURCE: system_architecture.md §5; elder_context.json.

FILES TOUCHED:
- app/src/main/assets/elder_context.json (bundled LAW copy, + game_variants per V2.3 §A)
- ElderRepository.java (parser), ElderFact.java

VERIFY CHECK EXECUTED: JSON parses; all fact_ids unique
```
$ python3 fact_id check → facts: 11 unique: 11
```
RESULT: matches spec? YES — parsed at boot via org.json; cooldowns + valid_until honored.

DETERMINISM: YES (static file).

INVARIANTS TOUCHED: contract shapes — additive-only field added (game_variants), nothing renamed/removed.

EDGE CASES COVERED: canonical arrays (children names) · null valid_until · expired facts skipped.

NOT DONE / KNOWN GAPS: none.

DIRECTIVE 9 LOG: BUILD_LOG.md (LAW data realignment).

---

## Claim 4: "Judge (gpt-4o-mini, JSON, temp 0)"

STATUS: PARTIAL

SPEC SOURCE: prompts.md §3; MASTER_CHECKLIST §1.

FILES TOUCHED:
- GeminiClient.java (judge: verbatim §3 rules, temp 0, responseMimeType json)
- CompanionActivity.java (evaluate → GeminiClient.judge → applyVerdict; onError → local judge)

VERIFY CHECK EXECUTED: T1 transcript pair → miss, conf ≥0.9, ×2 identical — **not executed** (requires live API call; no network test performed from this environment). Executed instead: local keyword judge drives the demo forced-verdict path; Gemini path is code-complete with verbatim prompt rules.
RESULT: matches spec? PARTIAL — contract-equivalent judge wired, but (a) model is Gemini 2.5-flash, not gpt-4o-mini (owner-supplied key, BUILD_LOG note), (b) live round-trip unverified.

DETERMINISM: temp 0 configured; not verified live.

INVARIANTS TOUCHED: failure ladder preserved (forced_verdict path untouched).

EDGE CASES COVERED: alias matching · miss markers · vague→partial · topic-change→no_answer (local judge); self-correction rule is in the Gemini prompt verbatim.

NOT DONE / KNOWN GAPS: live API verification pending device run; OpenAI vs Gemini model delta is a spec deviation (owner decision).

DIRECTIVE 9 LOG: BUILD_LOG.md (Gemini wiring entry).

---

## Claim 5: "Social matchmaking (keyword → group card)"

STATUS: PARTIAL

SPEC SOURCE: system_architecture.md §2; ui_spec.md §2 (match modal); elder_context.json community_groups_mock.

FILES TOUCHED:
- CompanionActivity.java (scanForMatch → dialog_match, 12s auto-dismiss, saveMatchEvent)
- ElderRepository.java (saveMatchEvent), DashboardActivity.java (+ violet match icon in feed)

VERIFY CHECK EXECUTED: saying "anggrek" fires grp_01 modal — code-trace only; not runtime-executed (no device).
Trace: CompanionActivity.onElderTurn → scanForMatch (keyword loop over profile.groups) → showMatchModal + saveMatchEvent → DashboardActivity.onEvents renders "+".
RESULT: wired per spec, runtime unverified → PARTIAL.

DETERMINISM: n/a.

INVARIANTS TOUCHED: match shown once per group per session (shownMatches set).

NOT DONE / KNOWN GAPS: runtime verification pending.

---

## Claim 6: "Demo mode (Shift+D / /demo/trigger, forced_verdict)"

STATUS: PARTIAL

SPEC SOURCE: demo_script.md §4; BUILDER_PROTOCOL §3 Phase 1.

FILES TOUCHED:
- CompanionActivity.java (onDemoTap triple-tap → cycles t1_miss → t2_warning → match_found → guided komorbid; forced verdicts through the real pipeline)

VERIFY CHECK EXECUTED: all 3 scenarios fire offline-ish — not runtime-executed. Executed instead: scenario code paths run through the same evaluate()/applyVerdict() the unit-tested scoring engine consumes; forced_verdict requires no network.
RESULT: PARTIAL (runtime evidence pending).

INVARIANTS TOUCHED: demo path sacred — forced verdicts never bypass applyVerdict; offline-capable (no LLM needed for scenarios).

NOT DONE / KNOWN GAPS: 4th scenario (guided) is an addition beyond spec — logged.

---

## Claim 7: "Elder voice UI (mic orb, captions, typed fallback)" and Claim 8: "Kenang companion (gpt-4o-realtime, mark_probe)"

STATUS: PARTIAL (both)

SPEC SOURCE: ui_spec.md §2; prompts.md §1.

FILES TOUCHED:
- activity_companion.xml (mic button, large serif companion text, transcript, input row)
- CompanionActivity.java (SpeechRecognizer STT auto-send, TextToSpeech companion speech 0.9 rate, probe scheduler with mark_probe-equivalent armedProbe tagging)

VERIFY CHECK EXECUTED: orb states render; typed toggle works — not executed (no orb; layout differs from ui_spec mock by design decision logged in BUILD_LOG: typed-first per hour-6 gate, mic added after). STT/TTS code-complete; runtime unverified.
RESULT: PARTIAL — voice loop exists via built-in STT+TTS (the spec's documented fallback layer), not gpt-4o-realtime; probe tagging works (`armedProbe` = mark_probe equivalent feeding the same judge/scoring path).

NOT DONE / KNOWN GAPS: no orb visual, no live captions, no Realtime API (needs backend-held key); mark_probe is local state, not a function-call from an LLM companion.

---

## Claim 9: "Family dashboard (trend, events, alert toast)"

STATUS: PARTIAL

SPEC SOURCE: ui_spec.md §3–4.

FILES TOUCHED:
- activity_dashboard.xml, DashboardActivity.java, TrendChartView.java (EWMA violet line, CRI ink dots, dashed 40-threshold), dialog_alert.xml

VERIFY CHECK EXECUTED: 30-day seeded chart renders; alert modal on WS event — not executed (no device). Executed instead: local seed fallback generates the 30-day 82→60 series (ElderRepository.localSeedTrend, deterministic); listeners wired (trend/events/alerts/delegations/diary).
RESULT: PARTIAL (runtime evidence pending).

INVARIANTS TOUCHED: caveat line present on the trend surface (disclaimer); "wellness" wording only.

---

## Claim 10: "Auth / DB / payments — Confirm ABSENT; role-select screen instead"

STATUS: PARTIAL

SPEC SOURCE: MASTER_CHECKLIST §1 last row; MAIN_WORKFLOW STOP FLAG.

FILES TOUCHED:
- OnboardingActivity.java (3-profile instant role-select), CarePanelActivity.java

VERIFY CHECK EXECUTED: 3 cards route correctly; zero auth code — cards route per code trace; **auth code is NOT absent**: LoginActivity/CreateAccountActivity/SuccessActivity + Firebase remain in the tree (owner-mandated features: Firebase auth + gender picker), reachable via a ghost link, outside the role-select flow.
RESULT: role-select per flag ✓; "zero auth code" ✗ by deliberate owner override → PARTIAL.

DIRECTIVE 9 LOG: BUILD_LOG.md (stop-flag reconciliation entry).

---

## Claim 11: "Dashboard i18n (id default, EN toggle)"

STATUS: PARTIAL

SPEC SOURCE: v2_feature_addendum.md §1; v2_2 §C (guardian keeps toggles).

FILES TOUCHED:
- activity_dashboard.xml (toggle include), DashboardActivity.java (LanguageToggle.bind), values-in/strings.xml (full §1 key coverage)

VERIFY CHECK EXECUTED: toggle flips every §1 key; no hardcoded strings — static verification: all dashboard strings come from resources (layout grep: no hardcoded text besides "Aa" glyph); toggle mechanism = AppCompatDelegate locale + recreate (same mechanism as onboarding, code-verified). Runtime flip not executed.
RESULT: PARTIAL (runtime evidence pending).

---

## Claim 12: "Find My Friends (school/work matching)"

STATUS: PARTIAL — logic DONE, UI PARTIAL

SPEC SOURCE: v2_feature_addendum.md §2.

FILES TOUCHED:
- friends/FriendMatcher.java, FriendMatcherTest.java
- assets/v2/profile_delta.json, assets/v2/people_directory.json
- CompanionActivity.java (intent → consent → friend card), dialog_friend.xml, DashboardActivity.java (approve card)

VERIFY CHECK EXECUTED: score ≥7 logic unit-tested
```
FriendMatcherTest 4/4: Ratna=13 (sma+proximity) · Sutrisno=7 (work only) · >±2y no-match · desc sort — PASS
```
"teman SMA" → Ratna card: code-traced (hasFriendIntent → friend_ask → isYes → showFriendDialog), not runtime-executed.
RESULT: algorithm matches spec exactly; UI wiring unverified → PARTIAL.

DETERMINISM: YES (pure matcher, tested).

---

## Claim 13: "Reminders engine (meds/checkups, voice ack)" · Claim 14: "Reminder→probe flywheel"

STATUS: PARTIAL (both)

SPEC SOURCE: v2_feature_addendum.md §3.

FILES TOUCHED:
- ReminderEngine.java (SCHEDULED→DELIVERED→ACKED|MISSED, ack keywords, window expiry), assets/v2/reminders.json
- CompanionActivity.java (delivery via companionSay; onElderReply ack routing)
- DashboardActivity.java (7-day adherence grid)

VERIFY CHECK EXECUTED: ack "sudah" → ACKED; miss → reminder:missed — code-trace; runtime unverified. Flywheel: acked med appends MED_RECALL_01 T2 fact to custom_facts_json (same merge path the wizard uses — load-merge code-verified).
RESULT: PARTIAL (runtime evidence pending).

NOT DONE / KNOWN GAPS: `reminder:missed` goes to adherence grid, not a WS event name; adherence history for past days is mocked (logged).

---

## Claim 15: "Komorbid profile → tailoring rules"

STATUS: PARTIAL

SPEC SOURCE: v2_feature_addendum.md §4.

FILES TOUCHED:
- TailoringRules.java (conditions → seated movement swap, culinary tagline swap, derived signal)
- assets/v2/health_profile.json, ReminderEngine.java (movement swap), DashboardActivity.java (derived signal only)

VERIFY CHECK EXECUTED: diabetes ON → dining card copy changes — code-traced (TailoringRules.groupTagline: grp_03 → "menu sehat rendah gula"); condition names never surface (dashboard shows only "Activities tailored ✓").
RESULT: PARTIAL (runtime evidence pending).

INVARIANTS TOUCHED: §7 privacy — no condition list anywhere in UI (grep-verified: no condition strings in layouts).

---

## Claim 16: "Onboarding wizard + Context Dump extraction"

STATUS: PARTIAL

SPEC SOURCE: v2_feature_addendum.md §5.

FILES TOUCHED:
- onboard/DumpParser.java, DumpParserTest.java (3/3 on the §5 demo paragraph)
- OnboardWizardActivity.java (dump → Gemini extract → tier cascade review → persist), activity_onboard_wizard.xml
- ElderRepository.java (custom-facts merge at profile load)

VERIFY CHECK EXECUTED: paste dump → facts in 3 tiers; cached fallback works offline
```
DumpParserTest 3/3 PASS — demo paragraph → Pak Budi (T1), Dewi dan Anton (T1), anggrek (T3),
senam (T2), Bengawan Solo (T3), obat darah tinggi (T2), omakase ENA (T3); tier rules enforced; empty → none.
```
Cached fallback (= DumpParser, the §7 escape hatch) is unit-verified; Gemini live extraction unverified.
RESULT: PARTIAL.

DETERMINISM: YES for the fallback parser (pure function, tested).

---

## Claim 17: "Diary (daily entry, bilingual summary, mood)" · Claim 18: "Cerita Ibu (story extraction, share)" · Claim 19: '"Bacakan buku harian saya" voice command'

STATUS: PARTIAL (all three)

SPEC SOURCE: v2_1_diary_and_i18n_addendum.md §1.

FILES TOUCHED:
- CompanionActivity.java (generateDiary on session end: bilingual summary, derived mood, verbatim stories with probe answers excluded; "buku harian" read-back)
- DashboardActivity.java (Trend|Diary tabs, entry rendering, share state-flip)

VERIFY CHECK EXECUTED: session end → entry with id+en summaries — code-traced; runtime unverified. Quote never translated — invariant holds by construction (story.quote = raw elder turn; summary fields are the only bilingual fields).
RESULT: PARTIAL (runtime evidence pending).

NOT DONE / KNOWN GAPS: story→probe pending_facts guardian-approve queue NOT built (checklist row unclaimed) · audio chips absent (spec allows pre-cut mocks; not included) · merged-LLM-call generation is heuristic (no LLM diary call yet).

---

## Claim 20: "Routine chains (med→gerak→air; after pointers)"

STATUS: PARTIAL

SPEC SOURCE: v2_2 §A.

FILES TOUCHED:
- ReminderEngine.java (startRoutineChain/scheduleStep off med-ack), assets/v2/routines.json

VERIFY CHECK EXECUTED: full rtn_morning_01 chain fires; meds escalate, movement never — code-traced; runtime unverified. Deviation (logged): inter-step delays compressed to 12s for live demo.
RESULT: PARTIAL.

---

## Claim 21: "Social worker panel /care (caseload, notes)"

STATUS: PARTIAL

SPEC SOURCE: v2_2 §B.

FILES TOUCHED:
- CarePanelActivity.java (3-elder caseload, sparklines, adherence %, alert badges, detail→dashboard), assets/v2/care_worker.json

VERIFY CHECK EXECUTED: permission matrix enforced — PARTIAL: care panel itself shows only trend/adherence (matrix-compliant), but tapping elder_0001 opens the shared dashboard **without** role filtering (diary/friends visible there — matrix gap).
RESULT: PARTIAL.

NOT DONE / KNOWN GAPS: visit-note action not built; role-filtered dashboard view not built.

---

## Claim 22: "<GuidedQuestion> component (speak/tap/delegate)" · Claim 23: 'Delegation "Minta tolong Dewi"'

STATUS: PARTIAL (both)

SPEC SOURCE: v2_2 §C/§D.

FILES TOUCHED:
- activity_companion.xml (guidedRow: Ya/Tidak/Tidak tahu/Minta tolong Dewi, ≥56dp targets)
- CompanionActivity.java (runGuidedQuestionScenario/answerGuided)
- ElderRepository.java (delegations), DashboardActivity.java (delegation cards, approve→completed)

VERIFY CHECK EXECUTED: delegate → live card — code-traced; runtime unverified. "Tidak tahu" never blocks (all four options dismiss gracefully).
RESULT: PARTIAL.

NOT DONE / KNOWN GAPS: not literally one reusable component class (inline implementation in companion); "guardian completes → Kenang closes the loop next session" NOT built.

---

## Claim 24: "Game probes (cocok_kata, tebak_angka, cerita_foto)" + "1:1 alternation" + MASTER_CHECKLIST §5 required fixes

STATUS: PARTIAL — fixes DONE + unit-tested, runtime PARTIAL

SPEC SOURCE: V3/v2_3_cognitive_games.md (corrected phrasing-layer design); MASTER_CHECKLIST §5.

FILES TOUCHED:
- elder_context.json (+game_variants on T1_SPOUSE_NAME, T1_BIRTH_YEAR, T3_GARDENING_HOBBY, T3_MUSIC_PREFERENCE)
- CompanionActivity.java (playful delivery ~1-in-3, never two in a row; cocok_kata guards)
- ScoringEngine.java (creditOverride), ElderRepository.java (deliveryStyle on events), DashboardActivity.java (flavor line)

VERIFY CHECK EXECUTED (fixes): cocok_kata exact = 0.75 credit; miss full weight; T1 game miss ≠ auto-acute
```
ScoringEngineTest: cocokKataExact_earnsDiscountedCredit (79.17 case) · cocokKataMiss_countsFullWeight — PASS
```
Alternation: never-two-in-a-row enforced by lastProbeWasGame flag (code-trace).
RESULT: fixes verified at logic level; runtime delivery unverified → PARTIAL.

DETERMINISM: YES (scoring, tested).

NOT DONE / KNOWN GAPS: "difficulty adapts DOWN on declining trend" + "Aktivitas Kognitif panel" + "group-game static card" — NOT built; these belong to the superseded games.json draft (v2_3 shipped doc §E.1 removed them; flavor line built instead). Logged as conscious supersede-chain call.

---

## Invariants (checklist §7) — executed checks

| Invariant | Evidence |
|---|---|
| No forbidden words on elder surfaces | grep: no matches (above) |
| Disclaimer accompanies trend | strings.xml `disclaimer` in both locales; rendered on dashboard |
| Verbatim quotes never translated | Diary story.quote = raw turn; only summaries bilingual (code trace) |
| Demo scenarios offline-capable | forced_verdict path, no LLM needed |
| Games/movement never escalate | cocok_kata T1 guard; movement on_miss=skip_silent in routines.json |

## NOT DONE inventory (complete list)

Story→probe pending_facts queue · visit notes (care) · role-filtered dashboard for care role · GuidedQuestion as single reusable class · delegation loop-close by Kenang · game difficulty adaptation + Aktivitas Kognitif panel + group-game card (superseded draft rows) · Realtime voice orb/captions · FCM push (listener mock per spec) · LLM diary generation · probes_en boot translation · live Gemini round-trip verification · all runtime/device verification (no emulator available).

DIRECTIVE 9 LOG: BUILD_LOG.md 2026-07-17 — ship-report protocol adopted; statuses graded strictly (no UI-runtime check graded DONE without execution).
