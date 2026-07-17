# SHIP REPORT — Elder POV requirements audit + guided intake — 2026-07-17

Phase: 2–4 · Gate target: B · Checklist rows claimed: GuidedQuestion real surface (V2.2 §C), elder home completeness

## Audit result (owner's elder-POV list vs built state)

| Requirement | State before this ship |
|---|---|
| Large readable typography (transcript + input) | PARTIAL — featured text large; transcript was 15sp → bumped to 17sp now |
| Voice: mic → SpeechRecognizer, Kenang via TTS | BUILT (prior ship) |
| Games injected ~1-in-3, never two in a row, no menu | BUILT (V2.3 ship) |
| GuidedQuestion single-card, 2–3 large buttons | PARTIAL — existed only as a demo trigger inside companion |
| Universal escape hatches ("Tidak tahu" + "Minta tolong Dewi") | BUILT on the inline row; now also on the standalone surface |

## Claim 1: "Real GuidedQuestion surface + entry on the elder home"

STATUS: DONE (compile-verified) / PARTIAL (runtime)

FILES TOUCHED:
- GuidedQuestionActivity.java (new) — komorbid intake as one spoken question per screen (TTS reads each aloud), 4×72dp buttons, sequential flow, thanks/done states
- activity_guided_question.xml (new) — single question card, escape hatches bottom row (delegate highlighted with tint wash)
- activity_elder_home.xml + ElderHomeActivity.java — new "Health questions" feature card launches the intake
- CompanionActivity.java — transcript rows 15sp → 17sp
- AndroidManifest.xml — activity registered

VERIFY CHECK EXECUTED:
```
$ ./gradlew assembleDebug → BUILD SUCCESSFUL in 2s
Trace: elder home → Health questions card → GuidedQuestionActivity
  Ya/Tidak → prefs health_answer_<key> · Tidak tahu → skipped (never re-asked this session)
  Minta tolong Dewi → ElderRepository.saveDelegation("question", …) → guardian dashboard card
```
RESULT: intake works end-to-end with both escape hatches; runtime visual pass pending device.

INVARIANTS TOUCHED: one question per screen, spoken AND shown (§C.1) · choices never configured (§C.2) · "Tidak tahu" never blocks (§C.4) · no step counters — social pacing only.

NOT DONE / KNOWN GAPS: guardian answering a delegated question does not yet loop back to the elder ("Dewi sudah bantu isi ya Bu" next session) — previously logged gap.

DIRECTIVE 9 LOG: BUILD_LOG.md 2026-07-17 — guided intake surfaced on elder home; transcript bumped for readability.
