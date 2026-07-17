# SHIP REPORT — Chatbot AI replies fix (template → live Gemini) — 2026-07-17

Phase: 4 · Gate target: D · Checklist rows claimed: Kenang companion chat (AI replies, fallback preserved)

## Claim 1: "Companion replies come from the AI, not templates"

STATUS: DONE (endpoint-verified) / PARTIAL (on-device runtime)

ROOT CAUSE (executed):
```
$ curl -X POST ".../gemini-2.5-flash:generateContent?key=<user-pasted key>"
{"error":{"code":403,"message":"Your API key was reported as leaked. Please use another API key."}}
```
The originally supplied key is revoked by Google → every AI call failed → `onError` → canned `ack_neutral` fallback — exactly the "template answers" reported.

FIX (verified working):
```
$ curl -X POST ".../gemini-2.5-flash:generateContent?key=<local.properties GEMINI_API_KEY>"
{"candidates":[{"content":{"parts":[{"text":"Halo semua, apa kabar kalian?"}]...}...}]}
$ grep BuildConfig (debug) → GEMINI_API_KEY = "AQ.Ab8RN…" (baked from git-ignored local.properties)
$ ./gradlew assembleDebug → BUILD SUCCESSFUL
```

FILES TOUCHED:
- CompanionActivity.java — unified AI reply path: small talk + post-verdict warm replies both route through `chatWithKenang()` (multi-turn Gemini, bounded 12-turn history, "Kenang is thinking…" state); stray `replyWithAi` call removed
- GeminiClient.java (external edit integrated) — chat() with alternating-turn contents builder, BuildConfig key source
- app/build.gradle (external) — buildConfigField from local.properties

RESULT: chat replies are live Gemini (temp 0.7); templates survive only as the offline failure-ladder fallback and for scripted safety moments (acute pivot, reminder delivery — by design).

INVARIANTS TOUCHED: persona rules kept (never corrects, no clinical words, honorific) · judge stays temp 0 (scoring determinism untouched) · key not in source (local.properties, git-ignored).

NOT DONE / KNOWN GAPS: on-device conversational quality not yet observed (endpoint verified by curl; key rotated — the previously pasted key is publicly burned and should be considered compromised).

DIRECTIVE 9 LOG: BUILD_LOG.md 2026-07-17 — root cause was a revoked key + stale build; endpoint-level proof in ship report.
