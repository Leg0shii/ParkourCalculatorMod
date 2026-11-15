# Block solver: continuous-model orchestration

2026-10-26. Run ticks are [prototyped](RUN_TICKS_PLAN.md), and route selection plus finite obstacle avoidance are implemented. [COMPACTION_HANDOFF.md](COMPACTION_HANDOFF.md) is the current resume checkpoint. Acceptance remains a validated solve within the declared continuous model; solver-to-replay export and game end-to-end testing are out of scope.

## Problem and boundary

Given an initial state, fixed start/goal supports and a supplied set of candidate supports/collision geometry, choose intermediate supports and derive transition profiles plus the positional constraints needed to visit them. Pass each resulting route to the existing run-ticks search and numerical solver. The output is an accepted continuous trajectory with its chosen transition profiles and run allocation.

The original run-ticks API consumes an ordered route. The [route-selection prototype](BLOCK_ROUTE_SEARCH.md) now chooses intermediate supports from an unordered set at different heights, with only the start and goal fixed, and selects ordinary jumps, short stairs or no-jump run-offs. Inputs stay sprint-jump plus sprint 45; their facings remain free. Multiple ladder/vine interactions with ordinary prefix/suffix and airborne transfers are now implemented in [LADDER_STRATEGY_PLAN.md](LADDER_STRATEGY_PLAN.md). Discovering geometry beyond the supplied scene, multiple climbing interactions and broader contact-selected steps remain later work. Block solving removes the remaining manually supplied block order, air times and contact/obstacle conditions in stages, rather than replacing the angle optimizer or run-ticks allocator.

```text
block/support geometry + initial state + fixed endpoints
    -> candidate support routes, air times and contact/crossing conditions
    -> existing run-ticks allocation and schedule construction
    -> existing coefficient compiler + prepare / solve / validate
    -> best accepted continuous-model candidate
```

The three layers remain distinct: orchestration generates the discrete alternatives, the solver optimizes facings for one numerical request, and replay can consume a chosen result later. Orchestration owns the adapter between profiles and solver requests. It does not depend on recorded facings or a game replay service.

The search objective is now fixed by user ruling: minimize total elapsed inputs, then optimize the configured numerical objective among equal-duration routes and allocations. [SEARCH_OBJECTIVE.md](SEARCH_OBJECTIVE.md) supersedes the earlier first-feasible/run-count-first block-route policy. Normal support-before-horizontal tick ordering is always modeled, including a jump starting beyond an edge. Run-off is another no-jump profile in that search: Y-derived fall time, compiled movement constants, a profile-specific reach bound and four source-exit alternatives. The ordered-route adapter retains the legacy `walk_off` serialization.

## What exists and what does not

| Capability | Current state |
| --- | --- |
| Ordered supports, supplied profiles, run search, free turning and continuous horizontal acceptance | Implemented by the run-ticks prototype |
| Single-support footprints and wall-face derivation | Existing Java `ConstraintDeriver` and `SupportOverlap`; reuse them |
| Scalar Y recurrence and prescribed floor/ceiling/step contacts | Existing `standalone.ipopt.ladder`; reuse it |
| Ordinary open-air air time from source/target heights | First profile-compiler slice implemented in `block_profiles.py` |
| Intermediate support selection with fixed endpoints and sprint-jump/sprint-45 inputs | Implemented for ordinary/stair/run-off profiles in support-plane and collision-box modes, including repeated visits |
| Optimistic transition bounds, reverse goal reachability and a route-prefix pool | Implemented, with separate accounting for heuristic pool drops and unknown numerical attempts |
| Different-height route edges and earlier-floor consistency | Implemented with the existing Y simulation and bounded outside-face positional branching |
| Short half-block staircase profiles and their entry constraints | Implemented as takeoff plus one air input, with ordinary alternatives retained and run contacts retimed |
| Run-off profiles, leaving a support without jumping | Automatically derived for lower supports, with all four exit faces, motion-specific reach pruning and shared route/run search; supplied `walk_off` remains compatible |
| Run-off source as an obstacle after departure, only during vertical overlap | Implemented in collision-box mode through the shared scene helper; support-plane mode still lacks full geometry |
| Finite solid side faces, blockage corridors and collision consistency | Avoidance implemented for normal inert collision boxes; cross neo/wide neo chains and all six edge timings; one-input intentional wall reversals now generated through the shared movement compiler |
| Ceiling avoidance and contact selection | Avoidance and one-input underside contacts implemented in collision-box mode; shortened durations enter graph bounds |
| Choosing among ordinary/stair/run-off profiles and their approach/exit faces, with per-path run-count search | Implemented for the current floor/contact family; broader profile families remain later work |
| Multiple ladder/vine interactions, airborne transfers and composed contacts | Implemented; see [composition interfaces](CONTACT_COMBINATIONS.md) |
| Geometry discovery beyond the supplied scene, within-region climb-plane switching, re-entry, fluids and effects | Later extensions |

## Historical first slice: ordinary open-air profiles

`compile_open_air_route` accepts a request with initial state, rectangular supports and normal movement policy, without `airtimes`. The caller must explicitly declare `sceneModel: open_air_supports`. This is a support-plane scene: it does not assert that finite solid side faces or omitted surroundings are harmless.

For each consecutive pair, `ordinary_jump_profile` uses the existing vertical step/carry functions to find the first descending target-height contact. The resulting profile contains air time, Y/vertical-carry states, ground flags and the floor event. Normal height changes 0, +.5, +1 and -1 give 12, 10, 9 and 14 inputs respectively. It generates the existing run-ticks route and leaves horizontal feasibility to IPOPT. A missing profile within the configured horizon is `unknown`, not a whole-jump infeasibility claim.

This first slice deliberately rejects supplied air times, contact overrides, ceilings, obstacles, composite shapes and other unimplemented profile families. It cannot silently ignore them. It preserves one explicit support per route entry and does not merge separated blocks into a bounding rectangle.

The fixture [blocks-flat-two-jumps.json](fixtures/blocks-flat-two-jumps.json) has no recorded states, facings or air times. The continuous integration test compiles it to `[12,12]`, runs the existing search and validates a `[0,0]` allocation. Profile tests also check the established height tiers through the numerical model's vertical validator. That first-slice checkpoint passed 37 tests with the actual Java compiler and IPOPT runtime; the current full gate passes 120.

This standalone ordinary-profile helper alone is not a general block-scene acceptance test. Its metadata records that solid side faces and other supports are not checked by the Y recurrence. Scene orchestration now adds earlier-floor exclusions, and collision-box mode adds full finite-volume avoidance checks. Keep those responsibilities in orchestration rather than treating a valid Y profile as sufficient world clearance.

```python
import json
from standalone.autonomy.block_profiles import compile_open_air_route
from standalone.autonomy import find_route

request = json.load(open("standalone/autonomy/fixtures/blocks-flat-two-jumps.json"))
compiled = compile_open_air_route(request)
if compiled["status"] == "derived":
    result = find_route(compiled["route"], compiler, seconds=60)
```

`compiler` is the existing `JavaCompiler` described in README.md. The example demonstrates the orchestration composition; it does not introduce another worker or solver schema.

## Next milestones

The [obstacle and blockage design](OBSTACLES_AND_BLOCKAGES.md) now specifies full collision sub-boxes, profile-dependent support/obstacle roles, all six directed edge timings, hit/avoid alternatives and admissible total-input bounds. It reuses the integration agent's scene and movement helpers. The shared scene class/tests and finite avoidance search are implemented. [OBSTACLE_SEARCH.md](OBSTACLE_SEARCH.md) records the input schema, accepted cases and remaining contact families.

Candidate generation starts with the normal-jump height split, approximately 1.24919 blocks, and Y-derived under/over/contact windows. Those windows restrict both profile families and horizontal reach. Generate the common left/right cross neo and wide neo passages first, with caps of two horizontal, two vertical and three combined transitions per physical block. Mixed three-transition chains and interleaving between different blocks are supported. Height/reach contradictions prune candidates, while side preference and complexity primarily choose evaluation order under the total-ticks objective.

The [Stratfinder investigation](STRATFINDER_INVESTIGATION.md) checks both our native search and Curryocity's external tool. Its first proposed experiment is implemented: an optimistic support/profile graph plus a shared pool of route prefixes, using conservative reach checks and the current ordered-route solver as the inner evaluator. Different arrivals and contact profiles remain distinct; neither a visited flag nor a no-turn failure removes a free-turn route. The initial coplanar checkpoint passed a comparison with exhaustive enumeration of a small explicit support set; height-dependent and short stair profiles now pass independent full-model acceptance. Finite avoidance and one-input wall/ceiling contacts are implemented. [ONE_TICK_CONTACT_PLAN.md](ONE_TICK_CONTACT_PLAN.md) records current behavior, evidence and gaps.

1. **Implemented: shared compilation and one-input X/Z reversals.** Collision-box schedules use the integration agent's corrected compiler and input state. Finite reach/overlap guards, normal reset, before/after avoidance, sprint cancellation and delayed air-factor recovery are independently checked. Same-solid corner rounding after a hit remains a corridor extension.
2. **Implemented: one-input ceiling contacts.** Generated underside profiles regenerate Y, retime finite/contact guards and shorten landing time. Existing ceiling avoidance, ordinary alternatives and selected two-input stair profiles remain. General contact-selected step/cancel discovery is still outside this slice.
3. **Implemented: bounds for shorter profiles.** Generated ceiling durations enter graph/time bounds before route search. An ordinary-jump duration cannot prune a faster supported bonk. Total elapsed inputs rank first and numerical objective second.
4. **Broader coupled benchmarks.** Finite avoidance, earlier-floor checks and interleaved per-block chains are implemented. Extend the existing full-prefix search benchmarks to denser multi-obstacle/multi-leg scenes, retaining free earlier facings and momentum. Keep per-physical-block caps at two horizontal, two vertical and three combined across the selected route.
5. **Real-data progression.** Replace supplied Linkcraft j0-j16 profiles where actual collision geometry and supported contact families are available. The existing 130-input witness supplies profiles and declared regions; it is not a complete finite-world test. Use the mod's existing geometry/constraint helpers and recorded traces only as regression diagnostics.
6. **Diagnostics and measured recovery.** Distinguish no reachable passage in the declared family from numerical/budget unknowns more clearly. Profile candidate counts and targeted retry behavior before changing the numerical solver or widening the search. Replay/UI/TAS export and general game validation remain separate ownership.

## Lessons retained from the shelved solver

The [historical post-mortem](../../docs/research/block-solver.md) reports an honest result of about 16-17/53 on its old solver, with both false-positive support and multi-jump convergence problems. Those counts are not a measured ceiling for the new IPOPT implementation.

- Never replace a disjoint set of supports with its bounding box. Select a support per branch or keep an explicit disjunction, and check strict membership in an actual support.
- Keep each obstacle's actual sub-boxes and finite geometry. Do not infer that a captured route being inside broad bounds prevents a solver from exploiting gaps.
- Preserve landing/support indexing and axis order. A support row on the wrong state changes which jumps exist.
- Avoid exact recorded-point pins as a normal input or seed. Keep yaw free; use only block-derived positional conditions.
- Numerical timeout is unknown, not a proof that a block route or contact profile is impossible.
- A future margin objective may help, but the first objective is an accepted route under correctly generated constraints. Do not redesign numerical optimization before measuring the new path.

The old blocks-only pipeline and probe screens were never committed and cannot be recovered. `BlockSolver`/`SweptCollision` are historical references; `ConstraintDeriver` is live. Reuse existing helpers, but do not revive the old inflated success criteria.

## Completion gates

- A fixture starts from block/support geometry and initial state with no supplied air time, recorded facing or authored per-tick rule table for the feature under test.
- Generated profiles expose their geometry/model assumptions and contact timing. All added conditions follow run insertion.
- Every accepted route passes the existing independent continuous-model validator and strict support checks, including the actual support in any alternative-support branch.
- Unsupported mechanics are rejected explicitly; bounded search failure is reported as unknown.
- At least one fixture exercises each newly implemented family and a control case rejects a geometrically inconsistent contact.
- Replay agreement, mod UI and global minimum certificates remain separate future work.
