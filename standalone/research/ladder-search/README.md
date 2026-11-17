# Ladder/vine route search evidence

Geometry-only requests in `../../fixtures/ladder-*.json` supply initial state, actual solids, integer climbable cells, start/goal supports and search budgets. They do not supply facings, entry times, hit times or Sneak choices. The saved results below were found by the new automatic schedule/route evaluator, then reconstructed from its exact compiled movement and independently revalidated with the scalar numerical engine and shared Java scene checker.

| Fixture/result | Global inputs | Entry channel | Exit | Selected Sneak inputs |
| --- | --- | --- | --- | --- |
| [Top](ladder-top.json) | 16 | front X negative | top | none |
| [Side entry](ladder-side-entry.json) | 13 | side Z negative | side | 2, 10 |
| [Side landing](ladder-side-landing.json) | 16 | front X negative | side | none |
| [Thin ladder top](ladder-thin-top.json) | 15 | front X negative | top | none |
| [Prefix/suffix](ladder-prefix-suffix.json) | 39 | side Z negative | top | none |

Tick labels are zero-based input indices. Entry/exit states and hit/Sneak inputs in each summary are local to the ladder transition; `plan` retimes them into the complete route. The prefix/suffix route is `start -> pad -> wall -> goal`, with the ladder interaction between pad and wall. Its incoming X/Z position and velocity are variables of the full solve, not a fixed prefix witness.

The top, side-entry, side-landing and prefix/suffix scenes model climbable cells without a fictional ladder solid, as with vines on actual walls. Thin ladder top additionally supplies its three real thin solids and lands on `ladder#2`. All fixtures use the shared scene's feet-center cell predicate. The side-entry result demonstrates actual selected Sneak inputs under finite wall/cell constraints, rather than only a ranking estimate.

Each saved JSON contains the source fixture path and SHA-256, compact summary, generated numerical `problem`, complete `route`/`profiles`/`plan`, finite `geometry`, and accepted continuous `candidate`. All five saved witnesses have zero scalar violation and pass shared scene acceptance, including exact selected clips, occupancy, component caps, Y/ground state and stored velocity. Their measured cold searches took approximately 37, 32, 16, 14 and 42 seconds respectively on this checkout. Wall time and chosen strategies can vary; tests require a valid witness, not an exact optimum or runtime.

Revalidate all saved candidates and reject deliberately corrupted contracts without another IPOPT search:

```sh
PKC_RUN_TICKS_COMPILER="$PWD/standalone/autonomy/build/compiler.json" \
python3 -m unittest \
  standalone.autonomy.tests.test_climbable_search.SavedLadderEvidenceTests -v
```

Run all five cold geometry searches:

```sh
PKC_RUN_TICKS_COMPILER="$PWD/standalone/autonomy/build/compiler.json" \
PKC_IPOPT_PYTHON="$PWD/.venv-ipopt/bin/python" \
python3 -m unittest \
  standalone.autonomy.tests.test_climbable_search.ClimbableIntegrationTests -v
```

The saved facings are outputs and revalidation witnesses. Cold searches use only the geometry fixtures. Earlier unresolved entry probes, bounded route/schedule pools, local IPOPT and one ladder interaction per route mean neither minimum time nor global infeasibility is proved. Replay/angle realization/TAS export are outside this continuous-model acceptance.


Verification checkpoint: the full orchestration gate passed 174 tests in 240.831 seconds with both runtime variables set. The final bridge/evidence subset passed 13 tests, including a new coplanar-face regression. The numerical suite passed 51 tests. Native core retained its three documented baseline failures.
