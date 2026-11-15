# Coupled contact and ladder evidence

The snapshots contain the exact compiled continuous problem, selected route/event schedule, accepted candidate and independent shared finite-scene acceptance. They are witnesses, not minimum-time or objective certificates.

| Snapshot | Inputs | Evidence |
| --- | --- | --- |
| `corner-result.json` | 3 | Cold geometry search: ceiling plus Z hit on input 0, X hit on input 1 |
| `contact-blockage.json` | 12 | Z contact at state 4 plus a wide-neo X-to-Z gate at state 4 and Z-to-X handoff at states 8/9 |
| `ladder-ceiling.json` | 12 | Cold geometry search: required ceiling cancellation composed with entry and climbing |
| `two-ladders.json` | 42 | Cold geometry search: start, ladder A top support, ladder B top support; joint prefix solve |
| `airborne-transfer.json` | 28 | Prescribed episode-contract solve: two regions, one launch, no intermediate landing |
| `automatic-airborne-transfer.json` | 28 | Cold geometry search independently discovers the two-region airborne transfer |
| `five-airborne-ladders.json` | 58 | Prescribed five-region falling-only contract, one launch, no intermediate landing; fresh IPOPT and shared scene acceptance |
| `ladder-bottom-exit.json` | 21 | Prescribed falling-only bottom exit at state 15, then landing; fresh IPOPT and shared scene acceptance |
| `appended-air-ceiling.json` | 29 | Generated ceiling contact and second episode from a prescribed first-region prefix |
| `appended-ladder-ceiling.json` | 29 | Generated ceiling contact during the second climb; its top exit is regenerated one input later |
| `final-air-ceiling.json` | 27 | Generated ceiling contact after a prescribed two-region prefix; landing is one input earlier |
| `three-climbing-ladders.json` | 50 | Cold geometry-only discovery of three climbing regions, one launch and no intermediate landing |
| `five-climbing-ladders.json` | 99 | Supplied five-region order/transfer policy, Y-derived climbing durations, fresh IPOPT and full scene acceptance |

The three new mixed-contact witnesses are generated continuations, not cold whole-route discoveries. They pass fresh numerical and full-scene checks through `test_movement_contacts.py`. Their complete compiled problems and source geometry are retained. See [MIXED_MOVEMENT_CONTACTS.md](../../MIXED_MOVEMENT_CONTACTS.md).

The three-region climbing search independently discovers entry/exit timing and facings. The five-region climbing snapshot is a prescribed-contract witness. A separate bounded cold run returned unknown; its limits and counters are saved in `five-climbing-search-limit.json`. See [LONG_LADDER_VALIDATION.md](../../LONG_LADDER_VALIDATION.md) for exact scope and reproduction. Current regression gate: 228 orchestration tests pass, plus two new long-chain tests.

The five-region and bottom-exit fixtures contain geometry and the initial state. Their accepted episode timings are prescribed by the contract tests, not discovered cold from those fixtures. Their saved problems/candidates revalidate independently; no optimality is claimed. See [MOVEMENT_UNITS.md](../../MOVEMENT_UNITS.md) for the common unit interface, new tests and chain search limits.

The automatic transfer snapshot records the geometry-only fixture SHA-256. No entry time, hit schedule or facing is supplied by that fixture. Its accepted episodes occupy states 1 through 5 and 11 through 24, exit at states 6 and 25, and land at state 28. There is only one jump impulse and no intermediate grounded state. Local measured cold time was about 88 seconds under the documented work budgets.

The contact/blockage snapshot is derived from a successful geometry-only `contact-wide-neo.json` search. Its accepted contact is input 3 against the back wall, while the path avoids the central wall. The explicit wide-neo gates were reconstructed from that witness, added as constraints and revalidated with a feasible-seed IPOPT call plus scalar/shared scene acceptance. This verifies their simultaneous constraint composition; it does not claim that the original cold search selected explicit passage rows. The raw geometry-only search found an incumbent in its first successful branch after about 9.5 seconds and continued under a 50-second total budget.

Unit/NLP checks additionally cover all 63 nonempty subsets of X/Y/Z opportunities across two consecutive inputs; full ladder-window combinations; signed/finite face alternatives; every same-input X/Z subset with ceiling or landing; actual step plus two retained wall contacts; ceiling plus both wall contacts while occupied, followed by a ceiling-only tick and an occupied landing; and rejection of missing/extra/wrong-timed contacts. Step diagnostics now preserve contacts from the selected step path while preserving movement arithmetic.

Revalidate saved contracts without a new search:

```sh
PKC_RUN_TICKS_COMPILER="$PWD/standalone/autonomy/build/compiler.json" \
python3 -m unittest standalone.autonomy.tests.test_ladder_composition.CompositionEvidenceTests -v
```

The numerical integration gate requires both `PKC_RUN_TICKS_COMPILER` and `PKC_IPOPT_PYTHON`. Tests without the worker skip the cold searches. See [CONTACT_COMBINATIONS.md](../../CONTACT_COMBINATIONS.md) and [LADDER_COMPOSITION_PLAN.md](../../LADDER_COMPOSITION_PLAN.md) for interfaces, limits and remaining work.


Earlier composition gate: 200 orchestration tests passed in 554.373 seconds with the Java compiler and actual IPOPT worker, followed by 23 passing focused checks. The numerical suite passed 51 cases. Native core's 799-case run retained the same three baseline failures, with 33 skips; shared-scene step diagnostics and coupled-contact coverage pass. No Minecraft acceptance or global optimum is claimed.

Movement-unit refactor gate: 27 focused tests passed in 18.487 seconds and 51 numerical tests passed in 24.973 seconds. The 215-test orchestration sweep finished in 460.748 seconds; its five legacy metadata subcase errors were fixed and all affected witness methods pass in the final focused rerun. Full-suite rerun after that compatibility fix was not performed. New evidence above is prescribed-contract acceptance, distinct from the existing cold two-region discovery.

Hanging-vine closeout: `hanging-vines.json` is a cold geometry-only accepted 17-input route through a climbable region without a selected climb wall. Entry is state 1, exit is state 6, and no horizontal hits are supplied or accepted. The retained problem/candidate pass independent scalar and shared scene checks. The discovery uses an explicit falling context, not an invented finite wall. Revalidation and cold-discovery gates are in `test_hanging_vines.py`; no minimum or global objective proof is claimed.

Ladder/vine closeout verification: 265 orchestration cases passed across the completed initial 50 and the remaining 215 tests. All eight new hanging-vine tests passed. The completion matrix and scope are in LADDER_COMPLETION.md; stepping is planned in STEPPING_PLAN.md.
