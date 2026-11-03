# Tests and fixtures

[Runbook](RUNBOOK.md) supplies commands/environment. [File index](FILE_INDEX.md) lists every tracked test and fixture; [function index](FUNCTION_INDEX.md) indexes production definitions. Tests use `unittest`; many require explicit compiler/worker environment variables. Always report skips. No new test runner or formatting tool is required.

## Choose tests by contract

| Area changed | Focused modules under `autonomy/tests/` |
| --- | --- |
| Cells/corridors and bounds | `test_cell_geometry`, `test_cell_routes`, `test_corridor_guards`, `test_cell_local`, `test_cell_experiment` |
| Schedule composition/full original state | `test_movement_composition`, `test_movement_units`, `test_movement_generation`, `test_generic_composition`, `test_shared_contacts` |
| Reachability/scope/retention/persistent generation | `test_orchestrator_continuation`, `test_search_fixes`, `test_screen` |
| Inner/terminal repair continuation | `test_repair_resume`, `test_search_fixes`, `test_failure_diagnostic` |
| Timing, progress, goal comparisons/windows | `test_time_progress`, `test_comparison_goals`, `test_checkpoint_guidance`, `test_decision_windows`, `test_fixed_clock_goal` |
| Landings/run-off/normal obstacles | `test_block_profiles`, `test_block_contacts`, `test_block_search`, `test_runoff`, `test_obstacles`, `test_block_route_witnesses` |
| Stepping, ceilings, combinations | `test_stepping_cases`, `test_ceiling_contacts`, `test_hit_events`, `test_contact_combinations`, `test_contacts` |
| Full acceptance | `test_acceptance`, `test_full_route_witness`, `test_integration` |
| Audit accounting without new solve | `test_candidate_audit` |
| Historical relaxed route models | `test_route_guidance` |
| Existing ladders | `test_ladder_*`, `test_climbable_search`, `test_hanging_vines`, `test_long_ladder_chains`, `test_movement_entry` |

`ipopt/tests/` separates `test_model` (scalar equations/constraints), `test_solver` (worker lifecycle, independent validation and failure handling), `test_continuation` (incumbents/inertia phases), `test_capture`, `test_ladder_model`, `test_movement` and real-worker `test_integration`. Reuse these instead of creating a second acceptance oracle.

Useful existing contracts in `test_orchestrator_continuation`: all swept acceleration directions remain in the envelope; required and unwanted interactions share one query; selected clips preserve feasible controls; complete scope cannot omit blocks; fall rejection needs required future contact; schedule equality is stronger than count equality; independent velocity/speed guards; interrupted generation and terminal resume retain work; omitted future obstacles do not reject preliminary guidance. The newer `test_depth_first` adds traversal, promotion, ownership, accepted-variant, resume and early-builder coverage.

## Non-Real11 regression families

Use fixture paths under [autonomy/fixtures](../autonomy/fixtures). Examples:

- `blocks-flat-two-jumps.json`, `two-jumps.json`, `turning-two-jumps.json`, `blocks-route-choice.json`: composition, turns, alternate route and common-goal comparisons.
- `blocks-required-run.json`, `one-required-run-tick.json`, `blocks-momentum-choice.json`: grounded run requirements, timing/state tradeoffs and preserved momentum.
- `blocks-runoff.json`, `blocks-jump-runoff.json`, `blocks-lower-target.json`: run-off, lower goals and airborne continuation.
- `obstacle-cross-neo.json`, `obstacle-wide-neo.json`, `obstacle-interleaved.json`, `obstacle-under-ceiling.json`, `contact-wide-neo.json`: signed faces, interacting obstacles, Y clearance and passage order.
- `ceiling-bonk.json`, `appended-air-ceiling.json`, `final-air-ceiling.json`, `generic-repeat-ceiling.json`, `generic-grind-blip.json` and [stepping fixtures](../autonomy/fixtures/stepping): ceiling regeneration, step/corner/blip variants and phase-changing events.
- Existing ladder families remain compatibility coverage, not the target for new movement expansion. Fixes must not remove their current behavior incidentally.

The [experiments](../autonomy/experiments) directories contain fixture-specific runbooks/results, rendered trajectories, archived reports and diagnostic scripts. These are evidence, not all unit-test inputs. In particular the old 200/400-input IPOPT Real11 captures are different from the seven-box/state64 autonomous opening.

## DFS/interaction acceptance coverage

Follow the detailed acceptance section in [the current plan](../autonomy/DFS_INTERACTION_IMPLEMENTATION_PLAN.md). At minimum:

1. Two siblings with different partial lengths, measured at one spatial goal; earlier actual arrival completes before the root sibling is continued; tie rules and fallback preserved.
2. Suspend during scoring, generation, numerical dispatch and backtracking; resume without repeating completed exact work, losing unknown siblings or replacing an incumbent with a partial.
3. A future late winner, coupled nearby decisions, differing schedules, position/velocity tradeoffs and heuristic exclusion continuation audit.
4. Impossible block/tick interactions disappear from **every** builder/check before product generation. Required contacts remain. Unknown eligibility preserves choices. Generic above-block and phase-specific counterexamples.
5. Full-proposal screening before truncation, owned approach prerequisites and no orphan rows. Normal, stepping and ceiling coverage, translated/reflected cases.
6. Equivalent face/gate descriptions dedup before expensive refinement, while distinct physics/future capabilities remain distinct. No speedup claim without fixed work/counters.
7. Cold Real11 `[0,8]` order/coverage, no P04/P05 orphan aliases, reduced irrelevant P06/P07 descriptions, and complete independent validation. Fixed reference remains separately labelled.

## Recorded baseline and honest reporting

[Orchestrator results](../autonomy/experiments/orchestrator-fixes/README.md) records: final focused numerical gate 94 passes; wider numerical autonomy discovery 450 tests, 449 passes and one baseline failure (~742s); fast discovery 451 tests with 89 skips and the same failure; IPOPT 54 tests with nine skips and executed tests passing. The broad numerical process preceded the final queue-copy regression; the focused gate covered that addition. These numbers describe the pre-DFS source; current validation is recorded in the DFS experiment results.

Known baseline: `test_ladder_composition.LadderRegionTests.test_repeated_ladder_ceiling_events_regenerate_the_complete_schedule`, reproduced unchanged on clean `3082f8fc`. Report it explicitly if encountered. Do not suppress it or casually fix an unrelated ladder mechanism during the non-ladder work.

For new runs record source revision plus dirty diff, Python/runtime/compiler context, command, wall time, counts/skips/failures, full acceptance tier and limits. Increasing budgets is useful for accuracy, but is not a substitute for explaining an exclusion or generation defect. Unknown outcomes remain in the audit. Documentation changes need local navigation/inventory validation, not solver reruns.


Current DFS acceptance: [results and logs](../autonomy/experiments/dfs-interactions/README.md) record368/368 selected non-ladder numerical autonomy tests,54/54 IPOPT tests and25/25 post-logging repair/terminal tests passing. The previous broader466-test process exposed two grind/blip regressions, corrected by retaining named-probe uncertainty; both pass in the final non-ladder gate. The pre-existing ladder baseline remains. Current cold/resume reports reconcile14 worker calls, zero repeated completed requests and all independent witness replays. The final affected-scope pass intentionally excludes dedicated ladder/climbable/vine tests and is not an all-suite green claim.

## Virtual terminal goals

`test_terminal_goals` checks descending-height timing, one-sided player-center rows, strict-bound clearance, no fake floor, generic airborne overshoot and independent native scene replay. Run with the native environment from the runbook; otherwise the integration case skips. Existing fixed-clock and physical landing tests remain separate regressions.

## Latest-jump run pruning

`test_minimum_runs` combines generic dispatch/resume/unknown controls with archived Real11 programs. It checks local preparation counts and preserves different prior interactions, hard obstacle-side rows and free/physical terminal contacts. Full counterfactual controls can set `minimum_run_pruning=False` in fresh fixture settings; policy changes intentionally invalidate exact resume. Saved `minimumRunExclusions` include each discrete program and the representative causing exclusion. Continuation-time pruning remains deferred and is not required for this policy.
