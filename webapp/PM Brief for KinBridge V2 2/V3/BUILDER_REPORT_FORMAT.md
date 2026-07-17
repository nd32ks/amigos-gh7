# KinBridge — BUILDER SHIP REPORT FORMAT
**Instructions for the builder agent (Kimi/Claude/anyone): every time you claim work is complete, you file a ship report in this exact format. The auditor (Sonnet, per `V3/AUDITOR_BRIEF.md`) consumes these mechanically. A completion claim without a ship report does not exist. A ship report without executed evidence is auto-FAIL.**

---

## 1. Where and when

- One file per shipment: `V3/reports/SHIP_<date>_<seq>_<scope>.md` (e.g., `SHIP_2026-07-18_03_reminders.md`)
- File it BEFORE telling the human "done." The report IS the definition of telling.
- One claim block per `MASTER_CHECKLIST.md` row. Never bundle multiple checklist items into one claim.

## 2. Report skeleton (copy verbatim)

```markdown
# SHIP REPORT — <scope> — <date>
Phase: <1-5>  ·  Gate target: <A-D>  ·  Checklist rows claimed: <§n row names>

## Claim 1: <exact checklist row text>
STATUS: DONE | PARTIAL | MOCKED-AS-SPECIFIED | BLOCKED
SPEC SOURCE: <doc + section this implements>

FILES TOUCHED:
- path/file.ts:L12-L84 (new|modified) — <one line what>

VERIFY CHECK EXECUTED:            ← the check from the checklist row, run for real
$ <exact command / action taken>
<pasted raw output — unedited, including timestamps/errors>
RESULT: matches spec? YES/NO — <one line>

DETERMINISM (if scoring/judge touched): run ×2, outputs identical? YES/NO
INVARIANTS TOUCHED (checklist §7): <which rows this could violate + grep/output proving it doesn't>
EDGE CASES COVERED: <list the spec's edge rows you implemented; name the ones you did NOT>
NOT DONE / KNOWN GAPS: <anything missing — empty is a claim too, and auditable>
DIRECTIVE 9 LOG: <BUILD_LOG.md lines appended for judgment calls in this work>
```

## 3. Status definitions — self-grade honestly, the auditor regrades anyway

| Status | Meaning | Rule |
|---|---|---|
| DONE | Verify check executed and passing, wired end-to-end, edge cases per spec | Only status that ticks a checklist box |
| PARTIAL | Works on happy path OR UI unwired OR edge cases missing | MUST list gaps. PARTIAL claimed as DONE = honesty-delta hit |
| MOCKED-AS-SPECIFIED | Stub/hardcode where the spec says 🎭 MOCK | Cite the spec line that authorizes the mock |
| BLOCKED | Cannot complete; 20-min rule triggered | State attempts, exact error, proposed fallback |

## 4. Evidence rules (what Sonnet will hold you to)

1. **Pasted output must be real.** Sonnet re-runs your commands; fabricated or trimmed output is the worst possible finding.
2. **"It should work" is not a status.** If you didn't run the verify check, the status is PARTIAL at best.
3. **Screenshots/UI claims:** describe the exact click path AND the handler→fetch/WS trace (file:line). "The button is there" ≠ wired.
4. **Prompts:** if you touched prompts.md content in code, paste a diff proving verbatim copy.
5. **Demo path:** any shipment touching /turn, scoring, escalation, or WS must re-run all 3 demo scenarios and paste the event names received. Every time. No exceptions.
6. **Gate claims** get their own report (`SHIP_<date>_GATE_<A-D>.md`) containing the full gate test transcript from BUILDER_PROTOCOL §3.

## 5. Why this format (so the builder internalizes it)

Sonnet's audit leads with an honesty delta: claimed-done vs verified-done. Every DONE that fails audit raises it; every honest PARTIAL keeps it clean. **A PARTIAL with listed gaps is a good report. A DONE that audits to PARTIAL is a bad builder.** The fastest path through audit is exactness, not optimism.
```
