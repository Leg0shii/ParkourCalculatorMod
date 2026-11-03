# Run ticks over a block route

> Current implementation: [DFS/early-interaction results](experiments/dfs-interactions/README.md). This supersedes earlier pending-status and checkpoint-ordering descriptions below; historical evidence remains unchanged.

> Current entry: [standalone AGENTS](../AGENTS.md), [current state](../docs/CURRENT_STATE.md), [approved DFS/early-interaction plan](DFS_INTERACTION_IMPLEMENTATION_PLAN.md). The experiment updates below are chronological; current runtime still uses checkpoint traversal and the new plan is not implemented.

Isolated cell-route experiment: [implementation, non-ladder benchmarks and next decisions](experiments/cell-routes/README.md), branch `research/ipopt-cell-routes`. Bounds remain deferred.

Local decision follow-up: [geometric checkpoint guidance](experiments/checkpoint-guidance/README.md) scores physical alternatives at a common absolute tick using the cell-route direction, with no velocity thresholds and all earlier facings free. Experimental entry point: `python -m standalone.autonomy.cell_routes.checkpoint_experiment`.

Current experiment: [joint decision windows and objective bands](experiments/decision-windows/README.md) cluster supplied opportunity ranges and compare before the next separate opportunity. [Measured failures](experiments/decision-windows/RESULTS.md) distinguish useful early-contact alternatives from dense-cluster generation and turn repair failures. Multi-tick and velocity score changes remain deferred.

Latest investigation: [generic failure causes and tested solution prototypes](experiments/failure-rootcause/RESULTS.md). Queue starvation/eviction, layered incumbent validity and skipped no-threshold fallback starts are isolated before changing default search or score design.

Latest standalone orchestration: [generic movement programs](GENERIC_COMPOSITION.md) chain normal, stepping, ceiling and ladder phases through one open-state queue. Collision-box `solve_blocks` inputs use this path by default; `--support-search` retains the legacy support-profile strategy. Use focused standalone IPOPT/scalar/scene tests, as specified in `standalone/AGENTS.md`.

Canonical branch: `feature/ipopt-consolidated`. Solver, orchestration and local replay now live together. See [standalone setup](../README.md) and [the consolidation record](../CONSOLIDATION.md). For a compacted session, start with [COMPACTION_HANDOFF.md](COMPACTION_HANDOFF.md): implementation checkpoint, user decisions, tested results, remaining gaps and next tasks.

Current direction, 2026-10-30: water is deferred. See [PRUNING_RESEARCH_HANDOFF.md](PRUNING_RESEARCH_HANDOFF.md) for the current solver/schedule-generation state and research pointers for improving discovery speed. Generic movement composition and climbing-owned ceilings are implemented; older milestone sections below retain historical limits.

Status, 2026-10-26: **run ticks are prototyped** within the continuous model. [RUN_TICKS_PLAN.md](RUN_TICKS_PLAN.md) records what works, the measured results and deferred work. Current orchestration work has moved to the [block solver](BLOCK_SOLVER_PLAN.md). Replay export and game integration are outside this milestone.

The [collision-box obstacle search](OBSTACLE_SEARCH.md) now implements height pruning, cross neo/wide neo blockage chains, finite source-obstacle clearance and shared swept-collision acceptance. It reuses the integration agent's `StaticScene`; the corrected shared movement compiler now supports generated one-input wall reversals and ceiling-hit profiles.

[ONE_TICK_CONTACT_PLAN.md](ONE_TICK_CONTACT_PLAN.md) records the implemented side-wall reversals and ceiling cancellations, before/after avoidance, shared sprint compilation, saved results and remaining gaps. Ladder/vine climbing is implemented separately in the plan below.

[LADDER_STRATEGY_PLAN.md](LADDER_STRATEGY_PLAN.md) records the implemented schedule generator, numerical entry probes, cap-aware ranking and one-interaction ladder route integration. Pure generator results retain vertical-only validation; accepted full routes additionally pass the shared finite scene and movement contract.

Its expanded generator covers front/side entry, solver-found entry times plus up to five delayed inputs, optional one-input stops at the beginning/end of climbing and arbitrary interspersed non-climb Sneak masks. Equal total/post-exit airtimes are grouped for straight-line horizontal ranking, with explicit cap-aware first-input Sneak priority and diverse geometry backups.

The [block-route search prototype](BLOCK_ROUTE_SEARCH.md) accepts fixed start/goal supports and an unordered set of intermediate supports, keeps sprint-jump/sprint-45 inputs fixed, chooses height-dependent ordinary jumps, short stairs or no-jump run-offs, checks their floor/entry conditions and selects a route through this run-ticks evaluator. Use `sceneModel: support_planes` for different heights and `python3 -m standalone.autonomy.solve_blocks`; `standalone.autonomy.solve` remains the ordered-route entry. Short stairs use a takeoff plus one air input, and all accepted profile schedules pass the existing continuous Y validator. Collision-box mode adds wall reversals, ceiling bonks and full finite-volume checks.

Block-route search ranks [total elapsed ticks first, then objective](SEARCH_OBJECTIVE.md), and continues across tied routes/profiles/allocations. The first-feasible and run-count improvement behavior below describes the standalone ordered-route CLI, not the block-route stopping policy.

This headless planner implements the first-failing-jump heuristic over the existing standalone IPOPT solver:

1. Try zero added run ticks before each jump.
2. At a failing jump, try one run tick, then two, and so on.
3. Before retrying the jump, solve the extended run-up with support constraints on the current block.
4. Backtrack to the preceding jump when run support cannot be found within the retries/budget or a configured run limit is reached.
5. Keep the previous prefix as a warm guess, but let IPOPT reoptimize its facings. No facing constraints are added, including during run-up.

A successful independently validated IPOPT solution with correctly compiled movement and constraints is the success criterion for this module. Air times, landing blocks and additional positional conditions are supplied assumptions. Generating those profiles and conditions belongs to the block solver. Unsupported numerical features are rejected rather than silently dropped.

The default returns the first full feasible allocation. `--improve` continues backtracking below the incumbent total to look for a lower count. A positive result is the best found under the search and model, not automatically a certified minimum. Unknown attempts are recorded separately from interval reach contradictions. The result's `minimumProven` is currently true only when the witnessed count reaches the explicit nonnegative/RT lower bound.

Run-off means leaving a block without jumping and then falling. The existing serialized kind for this transition is `walk_off`, with exit faces stored in `walkOffExits`; these names remain compatible with saved fixtures. It is distinct from ordinary movement followed by a jump. A supplied run-off's departure input starts grounded, uses ground acceleration/drag, and has no jump boost. Following inputs are airborne. Its duration includes the grounded departure input. The mandatory departure belongs to the supplied transition; only additional run ticks before it count as added delays. A source-exit constraint requires leaving the actual support. The exit face can be supplied explicitly; otherwise the existing heuristic selects the face toward the target. Alternate exit-face enumeration is not implemented in this ordered-route adapter.

Run-offs additionally require preceding-state source support and current-state source clearance. In `collision_boxes` mode, the source is also checked as an obstacle after departure whenever its actual volume overlaps the player in Y. Legacy support-plane solves still lack that volume and retain their narrower scope. [Run-off obstacle requirements](RUN_OFF_OBSTACLES.md) record the conditions, side-exit example and acceptance boundary.

Automatic block-route search derives run-off profiles to lower supports with the existing Y simulation, enumerates all four exit faces and compares them with jump/stair alternatives under the same total-ticks/objective ranking. Each profile uses its own compiled movement constants and duration for reach pruning. [Saved run-off results](experiments/runoff-profiles/README.md) cover a selected fall and a jump followed immediately by a fall. A prescribed `step` is a separate grounded non-jump input with a post-horizontal target check, not a run-off.

## Setup and run

From the isolated worktree root, prepare the coefficient compiler once:

```sh
./gradlew --configure-on-demand \
  -Dorg.gradle.java.home=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  -I standalone/autonomy/compiler.gradle :core:prepareRunTicksCompiler

python3.12 -m venv .venv-ipopt
.venv-ipopt/bin/python -m pip install -r standalone/ipopt/requirements.txt

.venv-ipopt/bin/python -m standalone.autonomy.solve \
  --input standalone/autonomy/fixtures/backtrack.json \
  --output standalone/autonomy/build/result.json \
  --seconds 30 --candidate-seconds 2
```

Use another valid JDK 21 path on another machine. The generated `standalone/autonomy/build/compiler.json` contains a local Java launch command and must be regenerated after moving worktrees or changing the core compiler. The Java process derives usable block footprints with the existing `ConstraintDeriver.deriveFootprint`/`SupportOverlap` code, then compiles candidate ground/jump/input arrays through `JumpPhysicsInputs` and `JumpLinearModel`. The planner assigns those derived bounds to landing/run ticks; it does not implement another block-geometry derivation. Explicit center regions pass through without being expanded again. The optional `--python` argument selects a separate worker interpreter.

The CLI writes the full result atomically and prints a short summary. Exit codes match the standalone API: 0 feasible, 2 unknown, 3 error, 130 cancelled without an incumbent. Cancellation during an improving search retains an already accepted complete route, with termination `cancelled`.

## Route format

```json
{
  "version": 1,
  "initial": {
    "position": [0.7, 1, 0.5],
    "velocity": [0, -0.0784000015258789, 0],
    "yaw": -90
  },
  "blocks": [
    {"id": "start", "bounds": [0, 1, 0, 1], "top": 1},
    {"id": "goal", "bounds": [4, 5, 0, 1], "top": 1}
  ],
  "airtimes": [12],
  "runBounds": [[0, 6]],
  "maxTotalRunTicks": 6
}
```

Positions/velocities are X,Y,Z. Each support's bounds are minX,maxX,minZ,maxZ. `airtimes[i]` is the supplied valid input count from takeoff to first-grounded state on the next block. A run count excludes the jump input. Zero means jump immediately at that grounded state. The initial run-up counts toward the total.

`runBounds` defaults to 0 through `maxTotalRunTicks` for each jump; the total defaults to 40. These are configurable search limits. With free turning, a player may be able to keep circling on a support, so the planner does not interpret that limit as the block's physical maximum. `supportClearance` defaults to 1e-6 blocks and can be explicitly set to zero for strict-overlap-only acceptance. The default family holds W and sprint; `inputs.sprint` can disable sprint and `inputs.incomingSprint` describes the prior input's sprint state. These are declared effective policies, not a live client key-state compiler. All per-tick facings remain free.

The first family uses normal 1.8.9 movement, no effects, prescribed valid airtimes, and rectangular supports. The default is W-only. Set `inputs.force45` to true for the existing Force 45 policy: diagonal movement except on grounded jump inputs, with all facings free. Heights are route metadata and initial-height checks; the run-ticks module does not derive or verify Y flight physics. It does not derive obstacle collisions, ceiling contacts, step-assist, ladder occupancy, fluids, or finite solid block side faces. It can consume prescribed short-jump/contact profiles and positional rows. A nonempty `obstacles` list is rejected. The result explicitly reports `validationLevel: continuous_horizontal_support_with_supplied_airtimes` and `gameReplay: not_run`; the latter is provenance, not a missing acceptance step for this continuous prototype.

For a captured course without picked geometry, `centerBounds` can supply an authored allowable player-center rectangle instead of block `bounds`. It is not expanded by the player half-width again and is not represented as measured block geometry. Such results report `continuous_horizontal_declared_regions_with_supplied_airtimes`. Extra X/Z rules can be anchored to a leg's departure or run input with `constraints`; those rules follow retiming and no F rules are accepted.

Horizontal support is checked before the landing-producing input's horizontal movement, and on each run input that must retain grounded state. A jump uses the previous input's grounded flag even if horizontal movement has since carried the player beyond the edge. This is ordinary Minecraft movement, not a run-off transition. Every accepted candidate is independently replayed by the standalone scalar validator and checked with strict AABB support overlap, using the widened legacy float half-width. This establishes the declared continuous support model, not byte-exact game feasibility.

## Search behavior

The planner solves prefixes from the original initial state, carrying momentum through all earlier jumps. It does not reset the player at each block or freeze a single prefix's exit state. Failed jump candidates cause an increment at that jump. An unresolved run-up probe is retried, then causes heuristic backtracking with an unresolved record; it does not establish that every longer or differently seeded run-up is physically impossible.

Before IPOPT, a conservative per-axis interval screen can reject an unreachable support row. Its carry interval includes both survival and zero carry, and its control interval includes every facing. It therefore covers automatic per-axis inertia in the declared continuous model. It is deliberately loose and never drops inertia to assume that extra travel is always helpful. Prescribed contacts anchor the relevant position/carry intervals, and ladder inputs clamp their movement intervals. Vertical state is validated separately with the existing scalar Y engine. A rejected run-only prefix excludes its longer run-only extensions under the same earlier allocation; a rejected jump prefix only advances that jump's run count.

Each IPOPT probe uses the configured per-candidate seconds. Unknowns receive up to `--retries` retries, default one, with increased budget and a fallback guess. The outer deadline covers compilation and numerical probes. Worker errors stop the search rather than masquerading as impossible jumps. The original route object and live TAS are never edited.

Result `best` contains allocation, optional-delay cost, independently replayed trajectory and facings, phase/event mapping, and compiled problem hash. `mandatoryGroundTicks` counts prescribed step inputs; `best.groundRunTicks` adds those to the searched delays. `attempts` records compact jump/run probes and diagnostics; `unresolved` contains unknown prefixes that could still beat the best total. `unknownAttempts` includes every unresolved attempt made, including equal/more expensive ones.

## Verification

```sh
python3 -m unittest discover -s standalone/autonomy/tests -v

PKC_RUN_TICKS_COMPILER="$PWD/standalone/autonomy/build/compiler.json" \
PKC_IPOPT_PYTHON="$PWD/.venv-ipopt/bin/python" \
python3 -m unittest discover -s standalone/autonomy/tests -v
```

The real integration cases find `[0,0]` on two jumps, `[1]` when one initial run tick is required, `[2,0]` after backtracking from a second jump with RT=0, and `[0,0]` on a turning route requiring IPOPT adjustment. Unit cases cover backtracking, unknown support, lower-total improvement, limits, cancellation, event remapping, independent arrays, and free facings. A seeded randomized screen check retains 150 known per-axis-inertia/turning witnesses.

The current native angle-solver suite has three existing failures: CrossJvmDeterminismTest, InertiaFoldingTest, and CostateSmoothingScaleTest. The native angle-solver sources and standalone numerical worker are unchanged; the shared core scene helper was deliberately imported and extended for collision checks. Record the baseline failures separately from this module's tests rather than modifying another agent's numerical work.

## Linkcraft 11 experiment

The copied `fixtures/linkcraft11-j5-j9.json` covers source states 55 through 80: jumps 5 through 9, plus the run-off at input 64. Its input is the support footprints, captured entry position/velocity, Force 45, and supplied transition types/durations. The original 13 extra authored scalar X/Z rules are discarded. Constraints are generated from the supplied support regions and run allocation. Recorded facings are absent; initial yaw is only a zero-valued guess. The fixture's footprints come from the save's landing regions and are identified as declared center regions rather than measured block shapes.

```sh
.venv-ipopt/bin/python -m standalone.autonomy.solve \
  --input standalone/autonomy/fixtures/linkcraft11-j5-j9.json \
  --output standalone/autonomy/build/linkcraft-result.json \
  --seconds 60 --candidate-seconds 3
```

The original allocation is `[0,0,0,0,0,1]`: five jump transitions and one mandatory run-off, with one optional run input before the last jump. The footprint-only planner found `[0,0,0,0,0,0]` in about 0.47 seconds, shortening the supplied 25-input section to 24 inputs. Acceptance uses the unchanged 1e-7 scalar tolerance plus the independent support/exit predicates. This is a successful numerical solve under the declared timing/footprint model.

The run-off starts at local input 9: grounded with no Jump, followed by three air inputs, landing at state 13. Its compiled acceleration magnitude is about .13 rather than the approximately .3274 sprint-jump input magnitude. The accepted departure lies outside the source region. As a separate earlier diagnostic, the original recorded facings on the original allocation pass even the original 13 additional scalar rules with zero violation; those facings were not fed to search.

`python3 -m standalone.autonomy.linkcraft --input /path/to/real/11.json --output /path/to/section.json --start 55 --end 80` regenerates the small fixture. Without expanded mode the extractor rejects missing complete landing regions and grounded height changes.

The full jumps 0-16 experiment is now implemented and documented in [experiments/linkcraft11-j0-j16/README.md](experiments/linkcraft11-j0-j16/README.md). It succeeds in about 81 seconds with 12 ground run inputs, including one mandatory step, and zero final scalar violation. It exercises incrementing and backtracking at the late jumps rather than relying on the easy zero-run subsection.

For full-route extraction, `--expanded` records explicit assumptions for missing normal grid blocks, extends a same-width coplanar standing strip from grounded samples, and preserves step/early-entry contact phases. A landing region and standing region may differ: `standingBounds` is raw support geometry for run inputs, while `centerBounds` is the landing region. The Java adapter derives both with the existing footprint helper. `landingPhases` selects pre/post-horizontal landing checks; `contactEntries` keeps the player outside a target until the prescribed crossing and inside at its entry state. These are block-derived positional conditions, not facing constraints.


## Ladder and vine route search

Collision-box requests can now declare `climbableCells: [[x,y,z], ...]` and optional `ladderSearch` budgets. The planner discovers front/side entry timing, generates climb/fall/Sneak/stop schedules, ranks promising timing groups and solves one ladder interaction with ordinary approach/exit jumps in a common continuous problem. Actual thin ladder boxes remain solids; vine cells do not create fictional collision boxes. Final acceptance checks all supplied cells/solids, exact chosen hits, component caps, Y and stored carry through the shared compiler/scene helper.

See [the detailed plan and implemented limits](LADDER_STRATEGY_PLAN.md) and [five saved geometry-only experiments](experiments/ladder-search/README.md). A direct side-entry result includes two chosen Sneak inputs. One ladder interaction per route is currently supported; minimum-time/objective proofs remain false.

```sh
PKC_IPOPT_PYTHON="$PWD/.venv-ipopt/bin/python" \
python3 -m standalone.autonomy.solve_blocks \
  --input standalone/autonomy/fixtures/ladder-side-entry.json \
  --output standalone/autonomy/build/ladder-side-entry-result.json \
  --seconds 60 --route-seconds 30 --candidate-seconds 1 --retries 0
```


The [contact-combination extension](CONTACT_COMBINATIONS.md) now composes multiple ladder transitions and airborne episodes, ceiling/horizontal hits, actual step contacts and blockage avoidance. See [current saved witnesses](experiments/contact-combinations/README.md). The earlier single-interaction descriptions above are the original prototype checkpoint; current limits and APIs are in the extension document. Route-specific pruning remains deferred.
