# Stepping and normal-air ceiling implementation

Latest extension, 2026-10-30: [generic movement composition](GENERIC_COMPOSITION.md) now permits repeated and mixed stepping, ceiling, normal and ladder units through one open-program queue. The one-event support-leg restriction described below belongs to the legacy support-profile adapter. Standalone IPOPT/scalar/scene gates are the development criteria; mod Java gates are outside this workstream.

2026-10-30, isolated worktree `ParkourCalculatorMod-run-ticks`, branch `codex/run-ticks-planning`. Ladder-ceiling ownership changes are deferred. Existing ladder ceiling routes still use their existing scheduling behavior, with neutral helpers extracted for reuse. Water and route/input pruning remain later work.

## Generation and representation

The full `collision_boxes` support graph now calls the registered `stepping` and `ceiling` generators alongside normal movement. Legacy support-plane generation and import names remain compatible. Ordinary jumps are retained beside the new alternatives.

| Type | Named case | Input producing the named state | Result |
| --- | --- | --- | --- |
| stepping | normal_step | Ground/run | One-input rise through 0.6, grounded |
| stepping | slab_boost | Ground/jump | Rise above jump impulse through 0.6, not grounded; one air input follows |
| stepping | blip | Air into H | H at higher top, grounded |
| stepping | blip_up | Air into H | H retains yL, grounded without a fabricated support |
| stepping | corner_jump_cancel | Ground/jump into J | X-facing approach, next-state Z escape, zero rise, airborne |
| stepping | corner_blip_up | Air into H | X-facing L/H corner escape, retained yL, grounded |
| ceiling | ceiling_hit | Normal upward air, or initial jump | Actual underside plane and zero vertical carry before gravity/drag |
| ceiling | grind | Ground/jump into J | Outside before J, under low ceiling on J, zero rise; ordinary air follows |

`case_profiles` builds named case constraints and reuses `vertical_profiles` plus the existing scalar movement functions. `stepping_generation` owns support/corner case proposals. `ceiling_generation` owns normal ceiling/grind proposals and normal airborne ceiling prediction through the registry. `ceiling_primitives` owns shared underside/profile/sequence helpers; old `ceiling_contacts.ceiling_profile` and `mixed_ladder.ceiling_sequence` imports remain adapters.

Profiles record Y/VY/ground history, explicit jump inputs, relative case events and position constraints. `profile_program` splits a case's physical input from its ordinary prefix/tail. `compile_block_path` retains actual landing phase; a blip checks lower L separately from higher H. Run insertion shifts the program, controls, L/H/J anchors, positional rows, finite contacts and scene contracts once. Profile controls are authoritative, and contradictory/malformed unit controls are rejected.

Normal-air ceiling units survive between ladder units. Overlay never replaces a ladder-owned interval with the normal ceiling type, including an entry-producing climb input. Existing ladder ceiling generation is preserved; its new dedicated local handler has not been introduced.

## Blip wall handling

On L the higher block is an ordinary finite obstacle. Generate four outside-face alternatives and four real face-hit alternatives. A real hit uses a standard `role=contact` event, rather than a reversal event. Shared contact compilation supplies its finite-face constraints, inertia cancellation and actual sprint state. The following input remains air into H even after the horizontal hit.

Normal blip-up additionally requires being outside the higher block on L. Otherwise the Y-first resolver lands on that block normally before the planned step. Corner cases share X-approach and next-state Z-escape constraints. No avoided corner face is turned into a retained horizontal hit. Z-facing counterparts do not receive the X-facing cancel/retained-height result.

## Elevated continuation and acceptance

The blip-up unit ends at H. Its following normal interval either jumps from the retained height or consumes a ground-regime input and continues through ordinary air to a real floor. Both choices are generated within one complete support-to-support profile. No optimizer X/Z position or velocity is fixed at H, and no block at the elevated Y is fabricated.

The scalar contact format has named outcome cases for zero-rise and retained-height events, without introducing another Minecraft step resolver. The Java acceptance bridge still uses shared `StaticScene.move`. A named case must match a selected real step, a real triggering finite solid, the complete predicted position/ground state and carried velocity. Unexpected step resolution is rejected. The bridge no longer demands that a retained-height result equal a nonexistent block top. Standard step contracts retain their existing checks.

Every accepted route is a fresh full-history IPOPT solution plus scalar and shared-scene acceptance. Game replay/export is outside this orchestration milestone.

## Bounds and work limits

The ordinary jump-height gate no longer rejects composite full-geometry profiles before the stepping generator can propose them. Flight-bound cache keys retain complete jump/ground schedules, including elevated second jumps, while keeping legacy keys for classic movement. Reach bounds use each profile's actual input schedule. A cheap initial support/constraint intersection check rejects only empty entry regions.

`maxSteppingProfiles` defaults to 128 per source/target pair. The graph reports emitted counts and incomplete generation; an incomplete stepping pool cannot establish `minimumTicksProven`. This is a bounded family of candidates, not exhaustive movement or a global optimality proof. Existing contact/solver/route budgets continue to apply.

The legacy support-profile case family contains one named stepping event per full support leg, with an ordinary continuation and optional elevated jump for blip-up. Arbitrary chains of new stepping events within the same airborne leg, or automatic selection of a step into an airborne ladder entry, are follow-up composition work. Existing multi-ladder chains and support-to-support mixes retain their current behavior. No rarity pruning, dominance rule, new cache or resumable enumeration has been added.

## Validation

`tests/test_stepping_cases.py` covers scalar height/control windows, the hypothetical free H crossing, retained Y and carry, all case anchor/control/contact retiming, unit-control rejection, ladder ownership, X-facing versus mirrored Z-facing controls, both horizontal axes for normal stepping, and cold geometry discovery across the catalogue. The height-bound regression generates a composite blip-up transition above the original ordinary jump-height limit.

Fresh numerical/shared-scene cases cover normal step, slab boost, blip avoidance, blip wall hit, blip-up with jump and no-jump continuations, both corner cases, flat grind and grind followed by a lower floor. Removing selected case contracts is rejected by scene acceptance. Fixtures contain geometry, initial state and a discrete case index, with no supplied airtimes, solved facings or frozen intermediate states. Existing ceiling/wall combinations and ladder regressions remain separate gates.

Focused new suite: 15 methods passed, including nine saved geometry/case fixtures and additional fresh numerical controls. Full orchestration and numerical regression totals are recorded in COMPACTION_HANDOFF.md after the final run.

## Existing Java core gate failures

The required fast Java core run completed 799 tests with 33 skipped and three failures. A temporary isolated checkout at the pre-implementation commit `06673fd` reproduced all three with identical assertion messages. The `core` tree hash is identical before and after this work (`a37b94340b9383d75e94c06eaf10fb72358f358a`). No Java solver sources or expectations were changed.

- `CrossJvmDeterminismTest.searchPathIsBitIdenticalAcrossJvms`: closed-form hash differs from the existing golden value.
- `InertiaFoldingTest.loopmmInertiaTermDropsOutOfFoldedModel`: clamp-free inertia term is about 2.63378e-5 rather than exceeding the existing threshold.
- `CostateSmoothingScaleTest.bothRecoveryPathsUseObjectiveDirectionOnlyForVanishingCostates`: expected 0 degrees, observed 180 degrees.

The baseline check ran only these three classes, five methods total, with the same JDK 21 toolchain. Its disposable worktree was removed afterward. These are existing gate failures, separate from the new standalone movement implementation.

All 51 numerical tests passed with `PKC_IPOPT_PYTHON` set to the isolated `.venv-ipopt/bin/python` (24.914 seconds), including the existing Real 11 runs. The new focused suite passed all 15 methods, including fresh original-state IPOPT and shared-scene controls.

## Final regression coverage

The full orchestration sweep executed all 280 methods in 675.320 seconds. It found two compatibility defects and an outdated assertion: supplied legacy step contacts were duplicated, run-only prefixes included future landing contacts, and one old test expected the ordinary-only jump-height rejection label. Legacy one-input steps also retain post-state landing timing even when a generic supplied profile carries a pre-landing default. These issues were corrected and the affected integration, contact-combination, obstacle, new stepping and ceiling modules were run together: all 75 methods passed in 86.681 seconds.

Combining the full sweep with that final rerun covers all 280 unique methods with passing results. The entire expensive sweep was not repeated after the corrections. Existing long ladder/ceiling routes, five-region append, saved scene witnesses and new original-state movement solves are included in that coverage. The numerical 51-method gate passes separately. The Java core's three baseline failures remain as documented above.
