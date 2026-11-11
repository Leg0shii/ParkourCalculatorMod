# Tests and fixtures

Current `dd738c98` acceptance: [600 native-enabled tests](../autonomy/experiments/hard-jump-comparisons/2026-11-11/native-gate/report.json), no failures/errors/skips and unchanged runtime hashes. [Evidence](../autonomy/experiments/hard-jump-comparisons/README.md) also records131 archived-witness prefix-bound checks and80 updated retained-witness scene replays. The [execution reference](ANCHOR_SEARCH_EXECUTION_REFERENCE.md) maps each combination/pruning/query policy to its current module. Use the feature's `hard-jump-comparisons/native_gate.py` for this complete recorded scope; the older gates below validate their own source-era scopes.

Focused additions are `test_candidate_comparison`, `test_proposal_analysis` and `test_transfer_time_bounds`, plus updated anchor DFS/policy and continuation tests. Coverage includes independent count protections, equality/strict bounds, explicit recovery, source hops, ceilings/unknowns, owned-tail isolation, JSON projection flags and resumed raw ordinals. Dedicated autonomy ladder/climbable/vine/movement-entry modules remain outside the selected scope; generic mixed-ladder controls and all IPOPT modules are included. Documentation-only follow-ups need link/status/source-consistency checks, not repeated cold numerical runs.

Lazy queue time-bound follow-up: [51 focused checks](../autonomy/experiments/anchor-runtime-profile/lazy-bounds-2026-11-10/report.json)
pass with native dependencies/no skips. They cover selected-only arrival and
refinement checks, equality, original-state DFS, external resume and persistence.

Current integration acceptance (2026-11-10): [571 native-enabled selected tests](../autonomy/experiments/anchor-runtime-profile/integrated-2026-11-10/native-final/report.json)
pass with zero failures/errors/skips and105 runtime hashes matching the cold
profile. Five additional profiler tests pass. The selected non-ladder autonomy
scope and full IPOPT module list are explicit in the report; this is not an
all-mod/game test claim. [Integration contract](ANCHOR_SEARCH_INTEGRATION.md)
and [cold results](../autonomy/experiments/anchor-runtime-profile/README.md)
record nested persistence, unknown retention, recovery and time-bound checks.

## Anchor DFS and finite terminal task

Hard-transfer policy2 adds source-hop chains after learning/backtracking/resume, noninitial source boundaries, owner aliases, same-input source/target contact, explicit caps and the actual Real11 initial0 prefix. [Corrected-source evidence](../autonomy/experiments/anchor-transfer-preparation/README.md) records535 native passes and source-stable cold controls. Setup impulses remain in J, while only the transfer is restricted to one jump. The normal0/8 cold run's remaining branch-service limit is recorded separately from eligibility.

`test_anchor_policy`, `test_anchor_search` and `test_anchor_dfs` cover backward finite guides, axis/diagonal binary boundaries, local requested-impulse counts, explicit caps, exact-node versus ancestor outcomes, physical repair exhaustion, current-owner hops, departure exclusions, selected-descent feasibility, source-owned suspension/resume, global hard latches across incoming histories, run restoration, logging parity and native cold physical landing. Updated event/semantic integration assertions use physical-task schema8. The finite landing clock is bound before an unused suffix; owned future rows are removed through the existing truncation path.

Run the [new native driver and matched finite-task measurement](../autonomy/experiments/anchor-feasibility/README.md) to a fresh directory. Their source-specific manifests and replay scope supersede older virtual-task timing as evidence for this feature.

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

## Semantic corridor refactor

Add `test_route_regions`, `test_semantic_routes`, `test_route_progress`, `test_route_guards` and `test_semantic_search` to the cell/DFS gate. They cover exact exposed unions and equivalent encodings, checked convex cover, landing versus overflight, 3D layer/passage order, meaningful revisits, no variant splicing, several portals per input, unknown named probes, fixed sweep alternatives, independent initial domains, immutable stage views and continuation. Native tests include inferred intermediate goals/outgoing objectives, exact one-prefix-per-session suspension/resume, finite and airborne virtual finishes, independent replay and archived Real11 witness coverage without seeding discovery.

The semantic schema6 prefix quota can reach zero while already generated ready programs are drained; it cannot silently add an expansion for every new child frame. `test_inferred_intermediate_support_uses_outgoing_guide_objective` checks that boundary as well as actual native guidance wiring. Legacy controls remain under their existing schema5 behavior.

Final spatial-refactor gate: [420 selected native tests](../autonomy/experiments/automatic-corridors/allocation-context-final-native-2026-11-03.json), zero failures/errors/skips,234.1s and sourceUnchanged=true. Its47-module list is explicit; dedicated ladder/climbable/vine/movement-entry modules are excluded, while generic mixed ladder controls execute. [Matched Real11 evidence](../autonomy/experiments/automatic-corridors/matched-allocation-context-2026-11-03/summary.json) preserves the automatic no-finish deadline and supplied T67 result separately from representation coverage.

## Generic event discovery

The new focused suites are `test_event_domain`, `test_event_routes`, `test_interaction_normalization`, `test_guide_progress`, and `test_event_search`. They cover raw incidence/contact provenance, immutable content-checked snapshots, airborne attachment, conservative role/window proofs, interrupted preparation/closure/witness work, cyclic revisits and latent branches, finite local anchors and checked shortcuts, dynamic next goals, atomic pending-child ownership, explicit schema/settings rejection, and absence of inherited raw-cell filters. Pair them with `test_depth_first`, `test_orchestrator_continuation`, `test_repair_resume` and `test_semantic_search`; supplied schema1/2 and archived cell-walk controls remain regression evidence.

[Event implementation evidence](../autonomy/experiments/event-route-discovery/README.md) records the source-specific new native gate. `native_gate.py` uses the exact earlier47-module scope plus the five new suites, recording test/source hashes and exclusions. Run it only to a new report location when preserving an earlier result. Coverage has three fields: raw geometry admits the realized trace, semantic projection is represented, and useful compatible guides were materialized unseeded. None substitutes for cold discovery or native replay.

Final event-discovery source `08b00185`: [447 selected native tests](../autonomy/experiments/event-route-discovery/native-forward-service-2026-11-03.json), zero failures/errors/skips,239.7s and sourceUnchanged=true. Final-source generic/control replays7/7; stored-Real11 automatic24/24 and supplied95/95 independent replays pass. [Manifest](../autonomy/experiments/event-route-discovery/acceptance-manifest-2026-11-03.json) verifies matching runtime hashes and unchanged historical evidence. This does not include dedicated ladder modules or establish executable-angle/actual-world acceptance.
