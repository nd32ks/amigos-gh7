# SHIP REPORT — Elder home dashboard (post-login elder surface) — 2026-07-17

Phase: 2 · Gate target: B · Checklist rows claimed: elder home surface (owner feature, not a checklist row — filed as owner directive)

**Owner directive:** after elder login ("Login successful"), the elder lands on a home dashboard with a welcome + honorific name, elder feature cards, and a top-right account sheet (account info, settings with text size + reset, logout). DESIGN.md scheme throughout.

## Claim 1: "Elder home with welcome + name, features list, account sheet"

STATUS: DONE (compile-verified) / PARTIAL (runtime)

FILES TOUCHED:
- ElderHomeActivity.java (new) — greeting via UserRepository.getUserProfile: gender → honorific (female → "Mrs."/"Ibu", male → "Mr."/"Bapak"), fallback to profile preferredAddress
- activity_elder_home.xml (new) — logo + account button top-right, serif greeting, feature cards (Kenang → companion, Buku Harian → elder diary view)
- dialog_account.xml (new) — account info (name, email, role, gender), text-size slider, Reset settings, Logout pill
- ElderDiaryActivity.java + activity_elder_diary.xml (new) — her own diary in large serif type, verbatim quotes untranslated
- UserRepository.java — getUserProfile(name, gender)
- OnboardingActivity.java — routeForRole: senior → ElderHomeActivity
- AndroidManifest.xml — both activities registered

VERIFY CHECK EXECUTED:
```
$ ./gradlew assembleDebug → BUILD SUCCESSFUL in 2s
Trace: login → SuccessActivity → Continue → routeForRole(senior) → ElderHomeActivity
Account button → dialog_account → slider onStop → prefs font_step + recreateWithFade;
reset → FontScale.reset + toast; logout → signOut + reset → OnboardingActivity.
```
RESULT: feature set wired per directive; runtime visual pass pending device.

INVARIANTS TOUCHED: elder surface stays inside its own role (account sheet is account-level chrome, not cross-role navigation) · font scale applies app-wide via existing FontScale.wrap · no scores/verdicts on this surface.

EDGE CASES COVERED: missing account name (falls back to profile preferredAddress) · unknown gender (no honorific) · signed-out session (email blank).

NOT DONE / KNOWN GAPS: runtime device verification; elder-side games have no card by design (games arrive in conversation only).

DIRECTIVE 9 LOG: BUILD_LOG.md 2026-07-17 — elder home surface built per owner directive; account sheet is account chrome, not a global menu (vision law respected).
