# Ladder and movement-refactor status

2026-10-29. The requested speed fixes, common movement-type generators and common initial/appended entry handling are implemented. The ladder/vine closeout audit is recorded in [LADDER_COMPLETION.md](LADDER_COMPLETION.md), with the next movement-type plan in [STEPPING_PLAN.md](STEPPING_PLAN.md). Stepping is the next movement type; water remains deferred. See [MOVEMENT_UNITS.md](MOVEMENT_UNITS.md), [MIXED_MOVEMENT_CONTACTS.md](MIXED_MOVEMENT_CONTACTS.md) and [LONG_LADDER_VALIDATION.md](LONG_LADDER_VALIDATION.md) for accepted contracts and evidence.

## Implemented ladder family

Rectangular regions, optional climb/fall phases, the five agreed phase sequences, climbing-owned beginning/end stops, interspersed falling Sneak, actual component caps, immediate hit-on-entry carry, side/top/bottom exits, successive airborne regions, finite wall/ceiling contacts and whole-route carried-state compilation are implemented. Accepted continuous witnesses cover multiple grounded transitions, five airborne falling regions, three automatically discovered climbing regions, a supplied five-region climbing contract and generated extra contacts in connecting air, the second ladder and final air.

No new movement arithmetic is needed to claim those witnesses. They use the shared movement compiler and scalar/scene acceptance. The numerical worker remains unchanged. Replay/export and game end-to-end testing remain outside this layer's scope.

## Completed ladder orchestration and deferred search coverage

1. The two implementation efficiencies from [GENERATION_PERFORMANCE_PLAN.md](GENERATION_PERFORMANCE_PLAN.md) are complete: an already-invalid occupied-height trace stops early, and open prefixes avoid the duplicate floor-prediction movement call. The measured append is about 35 percent faster with identical emitted continuations.
2. Initial and appended entries now use `movement_entry.py`. Both probe channel/timing alternatives with the shared compiler, IPOPT and full-scene acceptance from the route's original state. Previous support/run and airborne histories stay in the problem; no intermediate X/Z/VXZ endpoint is pinned. An entry wall hit can establish climbing carry immediately. Approach ceilings are proposed from the existing vertical/contact implementation. Delayed entry times remain candidates until their complete route is accepted.
3. Cold long-chain reliability remains a bounded-search gap. Cold three-region discovery previously succeeded; the recorded cold five-region benchmark exhausted generation time before a full numerical attempt. Its supplied 99-input structure is accepted. The speed/refactor work does not establish cold five-region coverage or infeasibility.

Same-region re-entry, changing climb-wall orientation inside a region and rare diagonal transfers remain explicitly deferred. Additional irregular-region work is not a priority; rectangles suffice for the requested scope.

## Common movement generation

`movement_generation.py` now registers actual generators for `normal`, `ladder_climb` and `ladder_fall`. The normal generator reuses the existing jump/run-off profiles in the support graph. Ladder generation composes interval, duration, contact and endpoint-stop choices from the same dispatch interface before Y replay; falling owns its arbitrary Sneak domain. Generated units survive retiming and episode composition and are checked against the selected physical hit/occupancy history before compilation. The old unit parser remains as validation and saved-contract compatibility, not as the generator for new ladder schedules.

The five per-region phase sequences and top/side/bottom exit rules remain enforced. Endpoint stops stay inside climbing, an entering wall hit can start climbing immediately, and extra horizontal contacts cannot introduce climb/fall/climb. Nine new generator tests plus 72 focused regressions pass. Entry tests additionally cover an initial hit, an airborne second entry, launch run retiming, an approach ceiling and entry after four prior climbs, using fresh numerical solves and shared-scene acceptance. All 257 orchestration tests pass across two batches (176 completed in the initial sweep and 81 in the remainder). All 51 numerical-model tests pass. The final focused entry gate also passes all 11 tests, including worker-success/scalar-failure rejection.

Common generation does not merge the support-route graph with the airborne-prefix queue into one graph. They share type dispatch and timeline/compiler contracts; each family retains its appropriate composition grammar. Unit boundaries do not freeze numerical positions or velocities.

The next movement-type work is to move the existing step-up/stair/slab-boost profiles behind a first-class `stepping` type. Their current working profile implementation has not yet been migrated. Add `water` later through the same interface; it is not currently implemented.

The common generator interface can use the current eager generation policy. It does not require the optional resumable-generation proposal. Tick-first and then objective ranking remain unchanged.

## Deferred optimization work

Multiplier-based input-schedule pruning, exact-Y caching, route-specific pruning and optional resumable generation remain separate follow-ups. The user is unconvinced about resumable generation; record it as a possible improvement rather than an agreed next step. Finish movement-type coverage before the pruning pass.

## Entry budgets and coverage

The probe reports the first numerically accepted entry per channel, along with earlier unresolved times. A solver rejection/timeout is `unknown`, never an infeasibility certificate. Validated windows receive generation work first, preserving the previous initial-entry ordering; wholly unvalidated channels are retained in `deferredEntryWindows`. If no channel validates, explicitly unprobed timing windows remain available. Entry-window clipping is reported. This is bounded work ordering, not a route dominance proof.

Appended probes use at most three seconds and 35 percent of the remaining expansion time, leaving time for schedule generation and full-route solving. Up to the configured zero-through-five delayed entries follow each window anchor. Their positions/hits are reoptimized and checked in the full solve; an anchor witness does not certify every later schedule. The old fixed 20-input append horizon is removed in favor of the configured entry horizon and remaining 200-input flight limit.

The low-level pure `append_ladder_programs` API can still run without a numerical probe callback and then labels its entries unprobed. Production `find_climbable_blocks` passes the shared evaluator at every extension. Open nodes remain proposals; only complete numerical/scalar/scene acceptance establishes a route. Probe contact alternatives are bounded (inherited contacts, entry hit/no hit and approach ceilings); the existing mixed-contact generation and final obstacle branches retain their separate work limits.

## Saved state and verification

Feature commits: `3a8d8cd` (speed), `2cee238` (registered generators), `a3a01f7` (entry unification), `bb01f61` (independent scalar acceptance for probe witnesses). Work remains isolated on `codex/run-ticks-planning` in `ParkourCalculatorMod-run-ticks`; no push or replay-layer changes were made.

The 257-case orchestration coverage includes cold top/side/thin/prefix-suffix, ladder ceilings, two grounded ladder transitions, the airborne two-region discovery, fresh/saved long-chain witnesses, generated approach/second-ladder/final-air contacts, bottom exits, all endpoint-stop variants, arbitrary falling Sneak, original-state entry probes, run allocation/retiming, Linkcraft, obstacle interleaving and shared sprint/contact acceptance. The remainder batch passed 81 tests in 64.006 seconds. The numerical-model suite passed 51 tests in 25.045 seconds. Final entry checks passed 11 tests in 11.970 seconds. No Java or numerical-worker arithmetic changed.

## Ladder closeout audit

The audit found and fixed automatic falling-only discovery depending on a climb wall. `movement_contexts` now supplies an explicit wall-independent falling alternative with four horizontal entry faces, no primary wall hits and the existing Sneak/cap physics. It is used for both first interaction and appended airborne regions. The cold hanging-vine fixture is accepted in 17 inputs, with entry state 1, exit state 6, no wall hits and independent scalar/shared-scene acceptance. Finite real walls remain physical alternatives; the falling context contributes no artificial obstacle plane. See LADDER_COMPLETION.md for the requirement-to-test matrix, completion boundary and final regression results.

Closeout verification: all 265 orchestration tests pass, including the eight new hanging-vine tests. The final 215-case continuation passed in 661.037 seconds after the first 50 cases had passed. A native crash in the optional Python diagnostic stack dumper required that continuation; the test/solver implementation reported no failing cases. The pre-closeout numerical-model gate remains 51 passing tests, with its source unchanged. The agreed ladder/vine mechanics milestone is closed for the documented scope, and STEPPING_PLAN.md is the next implementation plan.
