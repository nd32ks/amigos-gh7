# SHIP REPORT — GATE A — 2026-07-17

Phase: 1 · Gate target: A · Checklist rows claimed: scoring engine, escalation rules, demo mode (forced_verdict)

## Gate test transcript (BUILDER_PROTOCOL §3, GATE A)

GATE A requires: typed input only → T1-miss transcript pair → `verdict: miss`, `escalation: acute`, alert payload received, CRI = 33.3, run twice — identical output.

### Executed

```
$ ./gradlew :app:testDebugUnitTest --console=plain --rerun-tasks
BUILD SUCCESSFUL in 3s
23 actionable tasks: 23 executed
name="com.amigos.kinbridge.ScoringEngineTest" tests="14" skipped="0" failures="0" errors="0"
```

Key assertions (all PASS):

| Gate element | Test | Result |
|---|---|---|
| verdict: miss on T1 transcript | escalation_t1MissHighConfidenceIsAcute (miss, conf 0.94, tier 1 → ACUTE) | PASS |
| escalation: acute rule | same | PASS |
| CRI = 33.3 | cri_gateA_example_is33_3 (T1 miss + T2 exact + T3 exact → 33.3 ±0.05) | PASS |
| Determinism ×2 | suite run twice (--rerun-tasks + prior run), identical 14/14 | PASS |
| Low-confidence fail-safe | escalation_t1MissLowConfidenceIsDowngraded (conf 0.7 → NONE) | PASS |

Pipeline trace for the T1 transcript pair (typed, offline — forced_verdict path per demo_script §4):

```
CompanionActivity.runT1MissScenario            (arms T1_SPOUSE_NAME, forced MISS 0.94)
  → onElderTurn("Suami saya... saya tidak ingat, siapa ya namanya?")
  → evaluate → applyVerdict
      → sessionEvents += Event(tier=1, MISS)
      → repository.saveEvent(tier 1, "miss", raw −10, credit 0.0, label, "conversational")
      → ScoringEngine.escalation(MISS, 0.94, tier 1, …) → ACUTE
      → repository.saveAlert("acute_t1", title, body)     [elders/ibu_sri/alerts]
      → warmBackground() + companionSay(pivot_t1)          [CALM_REASSURANCE_PIVOT]
  → DashboardActivity.listenAlerts → showAcuteAlert modal  [listener path, device-run pending]
```

### Verdict

**GATE A: PARTIAL** — every logic element of the gate executes and passes (miss verdict, acute rule, CRI 33.3, determinism ×2, fail-safe). The gate's wire-level requirement (alert payload landing on the dashboard UI) is wired via Firestore listeners but **not executed on a device** — no emulator on this machine. Claiming full GATE A without that run would violate the evidence rules, so it is not claimed.

Blocking item for full gate: one device/emulator run firing the triple-tap T1 scenario and pasting the dashboard modal + Firestore `alerts` document as evidence.

DIRECTIVE 9 LOG: BUILD_LOG.md 2026-07-17 — Gate A graded PARTIAL pending device evidence; logic elements all green.
