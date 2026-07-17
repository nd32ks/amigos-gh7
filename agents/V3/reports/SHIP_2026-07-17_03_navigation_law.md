# SHIP REPORT — Navigation law enforcement (role-locked surfaces, zero cross-navigation) — 2026-07-17

Phase: 2 · Gate target: B · Checklist rows claimed: role-locked navigation (VISION law), social-worker permission matrix (V2.2 §B.2)

**Vision law (owner-pasted, authoritative):** no unified global menu; each role is locked into its surface; the elder surface has ZERO navigation. This superseded the floating side drawer built earlier the same day.

## Claim 1: "No global menu; role-locked surfaces"

STATUS: DONE (compile-verified)

FILES TOUCHED:
- CompanionActivity.java / DashboardActivity.java / CarePanelActivity.java — SideMenu.bind calls removed; no drawer exists on any surface
- SideMenu.java, dialog_side_menu.xml, bg_side_tab.xml, anim/slide_*.xml — retained in tree, unreferenced (kept per owner precedent of preserving files)

VERIFY CHECK EXECUTED:
```
$ grep -rn "SideMenu.bind" app/src/main/java/
(no matches — zero bindings)
$ ./gradlew assembleDebug → BUILD SUCCESSFUL
```
RESULT: no cross-navigation between role surfaces; role-select is the only router.

## Claim 2: "Elder surface has ZERO navigation"

STATUS: DONE (compile-verified)

FILES TOUCHED:
- activity_companion.xml — back chevron removed
- CompanionActivity.java — companionBack wiring removed

VERIFY CHECK EXECUTED:
```
$ grep -n "companionBack" app/src/main/res/layout/activity_companion.xml app/src/main/java/com/amigos/kinbridge/CompanionActivity.java
(no matches)
```
RESULT: the elder surface has no menus, tabs, drawers, or back affordances — conversation only, per the law.

## Claim 3: "Social worker permission matrix (privacy moat)"

STATUS: DONE (compile-verified) / PARTIAL (runtime)

SPEC SOURCE: V2.2 §B.2 matrix; owner-pasted architecture ("Sari is completely blocked from the family's Diary tab and raw condition lists").

FILES TOUCHED:
- DashboardActivity.java — careRole detected from prefs; when set: diary tab + content GONE, profile-setup link GONE, delegation/task cards suppressed, selectTab forced to Trend. Trend, adherence grid (+ derived signal), warning banner, events feed, acute alert modal all remain visible (matrix-compliant).

VERIFY CHECK EXECUTED:
```
$ ./gradlew assembleDebug → BUILD SUCCESSFUL
Trace: CarePanelActivity (role=care via prefs) → tap elder_0001 → DashboardActivity.onCreate → careRole filter applied before listeners attach.
```
RESULT: matrix enforced at the shared dashboard; runtime visual verification pending device run → PARTIAL on that leg.

## Also in this shipment — login role chips (external edit integrated)

- activity_login.xml gained a role chip row (roleElder/roleGuardian/roleCare, added outside this session); build was broken on missing `role_label`. Fixed: string added (en/id), chips wired with selected state + subtitle sync in LoginActivity.java. Role binding still enforced by UserRepository.verifyRole.

INVARIANTS TOUCHED: elder surfaces remain form/menu-free (grep-verified) · guardian dashboard keeps tabs + i18n toggle (allowed chrome) · disclaimer still accompanies trend.

NOT DONE / KNOWN GAPS: runtime device pass over all three role paths; visit-notes for care role still absent (per V2.2 preview scope).

DIRECTIVE 9 LOG: BUILD_LOG.md 2026-07-17 — Vision navigation law applied; side drawer removed; care-role dashboard filter; login role chips integrated.
