# Cell-route experiment results

The cell-route/local-optimization hierarchy is implemented as an isolated experiment. Forward propagation helps, but the current broad route alone does not reliably reduce every scene to a small local search. Keep bounds deferred. Next, improve spatial proposal ordering and forward interaction-sequence generation before committing to a general third search layer.

Branch: `research/ipopt-cell-routes`. Worktree: `/Users/arvindmurty/CS/ParkourCalculatorMod-ipopt-cell-routes`. Baseline: `99ecfe9`. Measurement source checkpoint: `3257571`. The production search selection is unchanged; experimental hooks are optional. All results concern continuous standalone scalar/support/Y/shared-scene acceptance.

Current user scope: stepping, blockages, ceilings, run ticks and landing. Ladders and explicit wall-hit experiments are excluded from these measurements and the final focused gate. Historical implementation notes are retained separately in [IMPLEMENTATION_NOTES.md](IMPLEMENTATION_NOTES.md).

Latest coordinated model recommendation: [circle-preserving continuous decision relaxation](../../CONTINUOUS_DECISION_RELAXATION.md), retaining full effective acceleration and relaxing decision selectors first. This design has not been implemented or measured.

Earlier design discussion: [route-specific optimistic continuation guidance](../../ROUTE_BASELINE_GUIDANCE.md) and [mixed-integer outer relaxation](../../ROUTE_MIP_RELAXATION.md). The user proposes using a relaxed full-route baseline to evaluate discrete decisions while every facing stays variable. This is a new design proposal, not implemented or included in the measurements below.

## Current status in plain terms

The experimental pipeline exists and works on small scenes. The main efficiency goal is still unfinished. The production default has not been switched.

1. Cell decomposition is implemented and tested on the selected AABB controls.
2. Spatial route generation is implemented as a basic heuristic. The small numerical scenes retained one to three families; a known valid family was missed under the geometric expansion cap in the multi-support case.
3. Local solving currently reuses the existing movement-program generator, with corridor filtering and forward progress checks added around it. Many choices are still constructed before the forward check rejects them. This is not yet a local generator driven efficiently by the next interaction along the fixed route.
4. The numerical benchmark solves seven of eight scenes within its declared limits. In the remaining feasible multi-support scene, even the supplied corridor reaches its prefix limit before a complete numerical proposal. A separately validated two-jump witness establishes that this scene is solvable.

The next implementation priority is to let the fixed route drive forward interaction generation and ordering, while retaining alternative timings and whole-history facing optimization. Then repeat the prescribed-route comparison. These results do not yet establish that the architecture needs bounds or a mandatory additional abstraction layer. Bounds, ladder tests and explicit wall-hit tests remain deferred.

## What is implemented

- Expanded-AABB free-space decomposition into cuboids, exact boundary strata, finite portals and physical contact patches with provenance. The supplied scene bounds are eroded by the player body dimensions. These controls produced 11 through 35 cells.
- A bounded geometric route generator with explicit revisit/expansion/work limits and normalized ordered obstacle-side signatures. It targets the full finite landing region, including cases whose goal center is blocked. Ordering is geometric, not a time bound.
- Broad horizontal corridor families that permit local changes in Y/contact method. Artificial interfaces are not block contacts. Full portals/finite support patches define automatic corridor envelopes; prescribed routes use spatial points only.
- Early block/region opportunity filtering, forward prefix-domain propagation through ordered cell stages, and timed corridor repairs before collision-branch enumeration. Earlier facings remain free; no optimized intermediate X/Z endpoint becomes a fixed numerical start.
- A three-arm benchmark and optional no-forward ablation, real solver-call accounting including retries, generation records retained on deadline, geometry/route dumps, profiles, saved witnesses and independent replay.

Code: [geometry](../../cell_routes/geometry.py), [routes](../../cell_routes/routes.py), [local decisions](../../cell_routes/local.py), [experiment CLI](../../cell_routes/experiment.py). Design: [CELL_ROUTE_EXPERIMENT_PLAN.md](../../CELL_ROUTE_EXPERIMENT_PLAN.md).

## Measurements

309 capped runs: nine generation scenes, eight numerical scenes, three generation ablations and one numerical ablation. Each configuration repeats three times. Automatic shortlist caps are 10, 50 and 100; the geometric expansion limit is 300. These small scenes returned only one through three normalized families, so increasing the shortlist cap did not test larger retained pools. The expansion/signature policy remains a separate limit.

Generation uses 3 s and 32 expanded prefixes per route. Numerical runs use 6 s total, at most 12 standalone `solve(...)` requests including retries, 64 prefixes per route, 2 s per local contact search and 1 s per numerical call, with no retries. The `solverCalls` counter counts those API requests, not internal IPOPT continuation phases or iterations. Automatic routes share the total budget. Each selected route receives an initial allocation. Java helper startup/sample compilation is excluded; worker startup, geometry preparation and route generation are included. Timings are unprofiled medians; complete ranges, per-route counters, inputs, source hashes and runtime settings are in [measurements.json](evidence/measurements.json).

A = existing generic search; B = supplied geometric corridor with no event method/timing/facing seeds; C = automatic cell routes. Table entries are A / B / C. Every solved entry succeeded in all three repeats at all tested shortlist caps. Unknown is a capped search outcome.

| Scene | Best ticks, A / B / C | First accepted seconds, A / B / C |
| --- | --- | --- |
| Open / landing | 12 / 12 / 12 | 0.23 / 0.25 / 0.25 |
| Distant supports | 12 / 12 / 12 | 0.53 / 0.24 / 0.25 |
| Normal step | 1 / 1 / 1 | 0.15 / 0.14 / 0.15 |
| Grind then blip | 5 / 5 / 5 | 1.77 / 0.83 / 1.46 |
| Wide neo | 13 / 13 / 12 | 3.54 / 3.44 / 0.56 |
| Run-up | 17 / 17 / 17 | 0.43 / 0.47 / 1.55 |
| Under ceiling | 12 / 12 / 12 | 0.28 / 0.28 / 0.32 |
| Several supports in one corridor | unknown / unknown / unknown | - / - / - |

The objective agrees exactly across A/B/C for every solved scene except the wide neo. There C finds 12 ticks with objective 2.954926, while A/B find 13 ticks with objectives 3.273404/3.274256. Tick count is primary. These are best found results, not per-route or global optimality certificates. The run-up witness contains five ground inputs before its twelve-input jump.

### What the route and forward pass remove

In the distant-support generation control, A emits 464 choices under its prefix cap, while B and C emit 68. Distant support opportunities are removed before their local choice products. The same twelve-tick objective is retained.

For the grind, disabling only forward prefix propagation changes B's median first accepted time from 0.833 s to 1.726 s, and total time from 1.602 s to 3.391 s. Both obtain the same five-tick objective, 2.4158197585, with one numerical call. With propagation, 1,088 inconsistent prefix attempts are rejected before queuing/Java screening. This supports evaluating constraints forward within a fixed route; it does not establish a universal speedup.

Filtering after a choice is built leaves substantial generation cost. The separate 32-prefix grind profile still performs 865 append attempts, 1,988 scalar Y traces, 22,445 Y updates and about 899,000 recursive `deepcopy` calls. The profile is instrumented and excluded from timing comparisons. Counts and profiles: [profiles.json](evidence/profiles.json), [grind profile](evidence/grind-forward-profile.txt).

### The important counterexample

The several-support scene reaches the 64-prefix limit with zero complete numerical proposals in A, B and C. A separately authored 24-input witness jumps from the start to `middle3`, then to the goal. It passes independent scalar/support/Y/shared-scene replay. It uses the original full initial state and fits B's corridor and forward progress test. It was never supplied to the timed searches.

Thus B's failure is real residual local generation/order complexity, not evidence that the corridor is impossible. The current earliest-tick queue spends its prefix budget on shorter partial programs before reaching that two-jump program.

C also fails a different audit: at 300 geometric expansions it retains a left-side family but misses this known through-support family. That does not rule out useful solutions in the retained family. Portal-center ordering can penalize an opening with a large vertical extent even when a nearby point on that opening would give a short spatial route. Fix proposal ordering/normalization before treating automatic failures as evidence that the local optimizer needs another abstraction.

Saved counterexample: [authored witness](evidence/witnesses/same-corridor-authored.json). Coverage audits are in [measurements.json](evidence/measurements.json). The authored witness is a diagnostic, not cold discovery or a timing result.

## Decisions and next work

1. Retain cells plus whole-trajectory local optimization as the base model. Forward constraint propagation is useful and remains enabled in the experiment. Bounds remain deferred.
2. Improve portal-patch proposal ordering and normalize incidental support/source-region crossings. Keep finite openings rather than making portal centers mandatory waypoints.
3. Within an ambiguous corridor, prioritize a small set of untimed interaction sequences, such as start -> middle3 -> goal, before enumerating all input counts. Compare that with pushing the existing forward checks into choice generation. This is the next experiment, not a decision to build a general third graph or branch-and-bound engine.
4. Reuse symbolic/Y/contact work and compress equivalent stage-path variants. Carry useful incumbent information between routes. Preserve the complete discrete context for reuse and keep original-state X/Z optimization; cell identity alone cannot merge numerical arrivals.
5. Repeat the prescribed-route and automatic-route comparison after those changes. Use both retained known families and cold solutions to decide whether a separate interaction-plan layer is warranted.

Automatic routing is already beneficial on the wide neo, but repeats work on the run-up case: nine solver calls versus one for A/B, with the same 17-tick result. The grind's automatic run also spends the remaining budget refining other families after its first witness. Route selection, local forward generation and cross-route reuse are distinct improvement targets.

## Validation and evidence

- Focused non-ladder gate: 153 selected tests, 11 environment skips, all executed tests pass.
- Numerical gate: ten tests pass, covering original-state solving, blockage repairs, goal-region landing, stepping on both axes, ceiling bonks, grind/blip, run inputs, run-off and landing timing.
- Independently replayed 22 saved witnesses: 21 cold results across seven scenes and three arms, plus the authored multi-support diagnostic. See [validation.json](evidence/validation.json) for exact test IDs and runtime versions.
- A tiny one-input stepping grammar exhausts with one complete program in both A and B, with no queue drops or prefix truncation. [Finite grammar evidence](evidence/tiny-step-exhaustive.json).
- Geometry checks cover overlapping/nested/split/reordered AABBs, free-space coverage, exact-height contact, corner incidence, multiple crossings per input and a blocked goal center.

The graph/signature shortlist and lazy timed repairs are heuristic. Counts on capped runs are observed work, not the number of all feasible schedules. Broad signatures currently use dominant-axis midpoint crossings and can omit more complex spatial distinctions. Corridor acceptance is horizontal; exact internal step-assist phases are not reconstructed. Full 3D physical validity comes from the shared scene checker. These controls are small and do not establish dense-map scaling or an optimal local solver.

Inspect [grind cells](evidence/grind-geometry.json), [grind routes](evidence/grind-routes.json), [initial-height slice](evidence/grind-slice.svg) and [saved witnesses](evidence/witnesses/).

## Reproduce

Build the local helper and prepare the existing IPOPT environment using [standalone setup](../../../README.md). Set `PKC_IPOPT_PYTHON` to the prepared worker interpreter. All helper class/config outputs stay in this worktree.

Run the complete non-ladder matrix, including the ablations:

```sh
python3 standalone/autonomy/experiments/cell-routes/run_benchmarks.py \
  --output standalone/autonomy/build/cell-route-benchmark
```

Run one scene, with configurable work limits:

```sh
python3 -m standalone.autonomy.cell_routes.experiment \
  --input standalone/autonomy/fixtures/generic-grind-blip.json \
  --spatial-route standalone/autonomy/experiments/cell-routes/controls/grind-route.json \
  --output standalone/autonomy/build/cell-grind \
  --mode solve --seconds 6 --route-seconds 2 --candidate-seconds 1 \
  --max-solver-calls 12 --max-prefixes 64 --max-expansions 300 \
  --route-caps 10 50 100 --repeat 3
```

Use `--no-forward` for the ablation, `--mode generation` to isolate generation, or `--witness <saved JSON>` to independently replay a witness and derive its spatial corridor before cold local search. Witness facings/timings are not used as search seeds. `--profile` marks a separate instrumented run; do not mix it with timing medians.

The saved validation IDs can be loaded with `unittest.defaultTestLoader.loadTestsFromNames`. Choose `unit.ids` without compiler environment variables, or `numerical.ids` with `PKC_RUN_TICKS_COMPILER` and `PKC_IPOPT_PYTHON` set. The original planning and milestone discussion remains in [IMPLEMENTATION_NOTES.md](IMPLEMENTATION_NOTES.md) and [PRUNING_RESEARCH.md](../../PRUNING_RESEARCH.md).
