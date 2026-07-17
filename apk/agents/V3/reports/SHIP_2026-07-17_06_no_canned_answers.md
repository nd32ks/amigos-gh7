# SHIP REPORT — Removal of preprogrammed companion answers — 2026-07-17

Phase: 4 · Gate target: D · Checklist rows claimed: Kenang conversational replies are AI-only

## Claim 1: "No canned companion replies remain"

STATUS: DONE (compile-verified)

FILES TOUCHED:
- CompanionActivity.java — all five template reply sites rewired to `chatWithKenang()`: reminder ack, friend-consent decline, cocok_kata T1 miss guard, WARNING branch, and the chat error path
- values/strings.xml + values-in/strings.xml — `ack_exact`, `ack_warm`, `ack_neutral` deleted; `kenang_offline` added (honest error state, not a fake reply)
- GeminiClient.java — failures now log to Logcat tag `GeminiClient` (silent fallback removed)

VERIFY CHECK EXECUTED:
```
$ grep -rn "ack_neutral|ack_exact|ack_warm" app/src/main/java/ app/src/main/res/
(zero references)
$ ./gradlew assembleDebug → BUILD SUCCESSFUL in 2s
```
RESULT: zero preprogrammed companion answers in code or resources.

DESIGN KEPT (deliberately scripted, per spec — not "answers"): probe questions (`probe_templates_id` are the ground-truth test), the Tier-1 CALM_REASSURANCE_PIVOT line (safety-critical, prompts.md §1), reminder delivery text (reminders.json spoken templates), the opening greeting.

FALLBACK BEHAVIOR NOW: on AI failure the featured text shows the thinking state then `kenang_offline` ("I'm having trouble connecting — please try again") — an explicit error state, never a fabricated companion answer. Root-cause visibility via `adb logcat -s GeminiClient`.

NOT DONE / KNOWN GAPS: device-side confirmation that live replies render (endpoint + key curl-verified earlier).

DIRECTIVE 9 LOG: BUILD_LOG.md 2026-07-17 — canned acks removed; AI-only replies; offline state is honest, not a fake answer.
