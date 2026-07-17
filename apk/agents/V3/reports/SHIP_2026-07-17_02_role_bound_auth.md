# SHIP REPORT — Role-bound auth flow (welcome → role question → role-scoped login) — 2026-07-17

Phase: 2 · Gate target: B · Checklist rows claimed: role-select screen (superseded per owner directive), auth flow (owner-restored, role-bound)

**Owner directive (supersedes MAIN_WORKFLOW STOP FLAG for this build):** role-select cards are generic (no hardcoded names), every role tap routes to a role-scoped login page, and accounts are role-bound — cross-role sign-in is rejected. Logged in BUILD_LOG.md.

## Claim 1: "Role question without names — welcome → role → login"

STATUS: DONE (compile-verified) / PARTIAL (runtime)

SPEC SOURCE: owner instruction 2026-07-17 ("Remove this ibu sri dewi sari and all the names").

FILES TOUCHED:
- values/strings.xml + values-in/strings.xml — profile_* named strings deleted; role_title = "Are you the elder, guardian, or social worker?" / ID equivalent; generic role_elder/role_guardian_name/role_care_name added
- activity_onboarding.xml — card refs repointed to generic strings
- OnboardingActivity.java — enterRole → LoginActivity (role extra); senior → font step → LoginActivity

VERIFY CHECK EXECUTED:
```
$ grep -rn "Ibu Sri\|Dewi\|Sari" app/src/main/res/values/strings.xml app/src/main/res/values-in/strings.xml
(no matches for removed profile names; only match/friend strings referencing Dewi as the guardian persona remain)
$ ./gradlew assembleDebug → BUILD SUCCESSFUL in 3s
```
RESULT: names removed from the role-select screen; flow is welcome → role question → role-scoped login.

## Claim 2: "Accounts are role-bound; cross-role login rejected"

STATUS: DONE (logic, compile-verified) / PARTIAL (live Firebase round-trip)

SPEC SOURCE: owner instruction ("you cannot log into an elder account as a guardian").

FILES TOUCHED:
- UserRepository.java — createAccount writes users/{uid}.role; signIn verifies doc role vs chosen role: match → success · missing → binds on first login · mismatch → signOut + onRoleMismatch(actualRole) · offline → allow with warning (logged decision)
- LoginActivity.java — role-scoped subtitle, mismatch toast with the account's actual role
- CreateAccountActivity.java — role-scoped subtitle, role persisted to prefs + Firestore
- OnboardingActivity.java — session gate restored: live session routes straight to role home; logged-out → welcome

VERIFY CHECK EXECUTED:
```
$ ./gradlew assembleDebug → BUILD SUCCESSFUL in 3s
```
Code trace: LoginActivity.attemptLogin → UserRepository.signIn(email, pw, role) → FirebaseAuth → verifyRole → users/{uid}.role compare → mismatch → auth.signOut() + toast "This account is registered as <role>".
RESULT: enforcement wired end-to-end; live Firebase verification pending device run (no emulator here) → PARTIAL on the live leg.

INVARIANTS TOUCHED: none — auth screens unchanged in design language; role names localized (en/id).

EDGE CASES COVERED: legacy accounts without role (bind on first login) · offline role check (allow + log, enforcement whenever reachable) · session restore skips welcome for signed-in users.

NOT DONE / KNOWN GAPS: runtime device verification of mismatch toast; "forgot password" still a placeholder.

DIRECTIVE 9 LOG: BUILD_LOG.md 2026-07-17 — owner directive supersedes stop-flag mock; role-bound accounts built; offline role-check policy (allow+log) recorded.
