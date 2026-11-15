# Stratfinder investigation: route selection and pruning

2026-10-26. Investigation only; no native solver or run-ticks behavior changed. Primary focus is this mod's Stratfinder, with Curryocity's tool checked as a complementary design. The target remains orchestration within the continuous model, with free turning and no replay requirement.

## Main finding

Neither implementation chooses an arbitrary block route. Ours chooses keys for a supplied jump/contact structure and positional constraints; Curryocity's searches within prescribed momentum room, facing and air-time assumptions. Their useful contribution is how to organize discrete candidates around a continuous movement model.

For our block solver, the promising design is a pool of route/profile prefixes, cheap conservative reach tests before IPOPT, and increasingly expensive evaluation of the best candidates. Arrival position and velocity matter together. A graph of blocks can propose routes, but a single visited flag per block cannot decide their feasibility.

## Sources and what was verified

- Our worktree at `54c0374`, native code unchanged from its baseline. Read the actual controller, pool driver, screens, certifier and fallback, not only the early spike document.
- `git blame` attributes the current pad-prefix prune and best-first cross-level queue to `3085f011`, [PR #558](https://github.com/Leg0shii/ParkourCalculatorMod/pull/558), merged 2026-10-18. Read that description, [issue #554](https://github.com/Leg0shii/ParkourCalculatorMod/issues/554), and [the original issue #424](https://github.com/Leg0shii/ParkourCalculatorMod/issues/424) with `gh`.
- The consolidated [native design record](../../docs/noturn-global-solver-design.md) contains later changes that supersede parts of [the early spike](../../docs/noturn-stratfinder-spike.md). In particular, the pool driver is implemented and is the controller's first path; Benders/homotopy research code is not the default window search.
- Curryocity source cloned into ignored `build/stratfinder-source`, pinned at `988695b9b23245ded9f8ffb07c7ffd984091225d`. Its repository returned no issues or pull requests in the API queries. Read `inputCracker`, `segLerp`, `zSolver`, movement code and settings.
- Compiled Curryocity's headless cracker and ran one narrow velocity-window case with risky pruning on/off. Results and a small reproducible probe are in [experiments/stratfinder-review](experiments/stratfinder-review/summary.json).
- Historical native performance numbers below are reported measurements from the cited records, not new benchmark runs in this investigation. Native cold/certification suites were not rerun because this change is research documentation only.

## Ours: what actually runs

`NoTurnSearchController.start` builds a `JumpSpec` from the document, then `NoTurnProblem.from` extracts jump ticks, ground/air regimes, start box, constraints and tied-facing units. The default no-mark structure ties the run-up, including tick 0. Explicit marks can create several tied segments and untied ticks.

The window runs `StructurePoolDriver` first and `NoTurnFinder` as a fallback if the pool returns no result. The latter can eventually re-certify the current document's keys through `warmSeedFallback`; that outcome must not be labeled cold discovery.

### 1. Search a compact grammar, not every tick independently

The pool enumerates constant-key segments from a nine-combo alphabet. It progresses through key-change levels, with minimum holds and exceptions at the initial tap, jump anchors and their neighboring ticks. Forward/diagonal key changes at jump anchors are treated as easy changes. Several sprint-engage timings are considered.

Without explicit marks, single-facing, last-jump-angle and every-jump-angle structures run as separate passes. The current implementation races the structures across the budget rather than relying on only the simplest family to finish first.

Transfer: use a grammar of block transitions and contact profiles, not unconstrained per-tick event enumeration. Ordinary jump, run-off without a jump, ceiling-cancelled hop, step-cancelled hop and obstacle-passage side are the discrete choices. Keep all facings free in the actual IPOPT request. A simple candidate family is a search preference, not a new F constraint or a completeness claim.

### 2. Exploit the movement coefficients

For a fixed key/contact schedule, the pool writes each positional wall using coefficients of a shared facing's cosine and sine. Contributions from other facing units are allowed directional slack. Candidate evaluation asks whether all walls can use a consistent start translation inside the allowed start box, rather than judging only the final landing distance.

Prefix tables let a whole constant-key segment contribute through two table lookups per wall instead of recomputing every tick. Sprint variants update the affected coefficients. `variantBound` uses `2 * max(hypot(deltaA, deltaB) + abs(deltaSlack))` to bound a change in the window deficit before deciding whether another variant needs the same screen work.

Transfer: cache profile/schedule coefficients and evaluate support/crossing conditions cheaply. Keep the inertia rule in the bound. The affine structure is useful with free turning too, but the shared-facing circle test is not the right feasible set for our unrestricted per-tick facings.

### 3. Reject a prefix at constraints it already determines

`StructurePoolDriver.enumSeg` identifies positional-wall groups whose tick is no later than the assigned prefix. Later keys cannot repair those earlier walls. `prefixInfeasible` reuses segment sums and checks sprint variants, so an unpromising subtree can be cut before all full schedules are generated.

Transfer: inspect each newly determined landing, run-support and crossing condition as soon as a route/profile prefix fixes it. Do not complete a long route before discovering that an earlier landing is out of reach. Because our prefix facings remain adjustable, failure of one sampled path does not mean the prefix's feasible region is empty.

Important qualification: the native prefix implementation uses facing grids, keep margins and the no-turn screen model. The historical review checks that the kept pool is unchanged on its measured cases. That is not a general proof that the same thresholds reject nothing in a free-turn, automatic-inertia block problem. Reuse the causal idea and derive the bounds for our model; do not transplant the boolean verdict as a route-infeasibility oracle.

### 4. Keep and rank a pool before expensive solving

`UnitScreen` evaluates a reduced set of facing groups, including joint coarse initialization for two free groups. `PoolCertifier` merges candidates from multiple key-change levels into a shared ordered queue. Near-screen hits normally get solver time first, but miss-tier candidates are tried too, including while waiting for another level's enumeration.

Accepted candidates seed neighboring schedules. The beam fallback deliberately limits how many entries one last-key/edge family can occupy, preventing one locally attractive family from taking the entire beam. That cap is heuristic retention, not safe mathematical dominance.

Transfer: retain several route/profile prefixes with different approach directions/contact choices. Do not commit to the first route that reaches an intermediate block, or discard everything with a worse cheap score. Use the current successful prefix as an IPOPT seed while reoptimizing earlier facings.

### 5. Separate screens, witnesses and rejection proofs

The main certify cascade tries a sweep, a screen-derived warm solve and then the normal search graph. A failed warm screen returns unknown. The native result is independently checked through its exact model. Our equivalent acceptance remains the standalone continuous validator and strict support predicates.

The separate bench-only `NoTurnJointFastCheck` is particularly informative. It loosens affine wall rows to cover possible .005 inertia removals. The historical real-pool audit reports zero rejections on 269 j1150 candidates once that slack is included, despite much stronger apparent rejection without it. A more mathematically elaborate filter need not improve useful pruning.

Transfer: distinguish a sound contradiction, a heuristic score and an IPOPT unknown. Profile the actual pool before implementing an LP/SOCP master. An unknown local solve must not permanently eliminate every route through a block.

### Historical performance evidence

PR #558 reports that correcting the initial facing structure, allowing short human key taps, improving screen initialization and fixing certify-queue scheduling raised cold coverage from 24 to 45 of 59 captures under its stated 120-second setup. It also reports 88.5 to 13.1 seconds for enumeration of an identical pool on an older j1150 fixture. Those gains concern fixed-structure key discovery, not block-route discovery, but show that grammar coverage and queue ordering mattered as much as arithmetic throughput.

## Curryocity's tool: complementary ideas

Source links are pinned to the inspected revision:

- [Input cracker](https://github.com/Curryocity/Stratfinder/blob/988695b9b23245ded9f8ffb07c7ffd984091225d/src/inputCracker.cpp).
- [Segment transforms](https://github.com/Curryocity/Stratfinder/blob/988695b9b23245ded9f8ffb07c7ffd984091225d/src/segLerp.cpp).
- [Single-axis strategy solver](https://github.com/Curryocity/Stratfinder/blob/988695b9b23245ded9f8ffb07c7ffd984091225d/src/zSolver.cpp).
- [Settings and limitations](https://github.com/Curryocity/Stratfinder/blob/988695b9b23245ded9f8ffb07c7ffd984091225d/README.md).

The cracker builds sequences backward from their end, iteratively increasing segment complexity. Segment types distinguish ground, air and jump; a supplied air time constrains the phase transitions. It targets a final Vx/Vz interval at a fixed facing, with optional momentum-room bounds.

With inertia disabled, a segment maps incoming to outgoing velocity as `v_out = alpha * v_in + beta`. Composition is cheap. Two variants carry previous-sprint state. The search applies each current suffix to broad incoming-speed bounds and rejects it if its possible output misses the requested velocity interval. It also compares zero-length and long-run terminal-speed behavior to avoid extending some hopeless segment families. Key combinations equivalent on the only relevant axis are removed, and redundant prefixes of already found suffixes are suppressed.

Candidate acceptance runs its own forward movement engine, checks momentum-room use, then checks actual final velocity against the requested interval. The separate `zSolver` compares a restricted library of delayed/nondelayed single-axis strategy families, including reversal and angled-jump setups; it does not search a 3D block graph.

Transfer: derive broad acceptable entry-position/velocity regions backward from a later landing, and compare them with forward reachable regions. Segment transforms are useful cached summaries. Any terminal-speed argument must cover our allowed input family and incoming momentum, rather than assuming its fixed-facing normal/backwalled model is universal.

Do not import risky pruning. Source and README explicitly allow missed inertia-dependent sequences. In the source, enabling it reduces the approximate-velocity allowance from the version's inertia scale to `1e-5`. The other duration/terminal-speed prunes also rely on that restricted fixed-facing family; they are not proofs for our arbitrary turning.

### Small executed check

Using the external repository's narrow velocity-window example, with air time 12, depth 2, max ticks 50, slowness amplifier 1 and facing .015 degrees:

| Mode | Solutions | DFS calls | Forward checks | Measured seconds |
| --- | ---: | ---: | ---: | ---: |
| Risky off | 4 | 346368 | 273877 | .1650 |
| Risky on | 4 | 100750 | 14977 | .0100 |

Both returned the same four strings and velocities on this one case. This verifies that the headless engine runs and the switch changes the amount of search; it neither proves safe coverage nor reproduces a missed inertia-dependent case. The external engine was not compared byte-for-byte with Minecraft or our continuous solver.

## Proposed adaptation for block routes

This section is a design inference from the source review, not implemented route-search behavior.

1. **Build an optimistic support/profile graph.** Nodes name actual supports; candidate edges carry a possible transition profile and necessary conditions. Keep alternate sub-boxes/supports separate. Reject missing height/profile possibilities only within the declared profile family. Bound horizontal reach using an overestimate of allowed incoming momentum and all free-facing controls. A fixed four-block radius is not a general bound.
2. **Apply reverse reachability from the goal.** Remove nodes that cannot reach the goal even in that optimistic graph. This is safe only if the graph includes every allowed transition; omitting an unimplemented ceiling/stair family is a declared scope restriction, not evidence that the physical route is impossible.
3. **Search route/profile prefixes in a shared queue.** Rank by supported run-cost bounds, estimated continuation difficulty and cheap geometric scores. Keep distinct approach/profile alternatives. Estimates may choose evaluation order but cannot certify the best route or minimum run count.
4. **Carry regions, not one block-level scalar.** A prefix needs support, contact phase and a reachable/required position-and-velocity description, plus its discrete choices and run cost. Use cheap outer regions for pruning and exact prefix requests for solving. Two arrivals on the same block with different velocity directions cannot generally be merged. More speed is not always better near a small pad or a turn.
5. **Intersect forward and backward conditions.** A later narrow landing can rule out parts of an earlier arrival region. Derive the relation using existing movement coefficients, with bounds covering inertia and free controls. Initially use loose intervals and measure how many branches they remove. Do not pretend independent X/Z intervals preserve position-velocity correlations.
6. **Call the existing full-prefix run-ticks/IPOPT path on survivors.** Keep facings free, regenerate all affected positional conditions, and preserve numerical unknowns. The outer graph proposes a route; an accepted prefix/full solve establishes the declared continuous witness.

## First experiment to implement next

Keep the current ordered-route solver as the inner evaluator. Give a small fixture a start, a goal and several alternative support blocks, without a prescribed order. Generate ordinary-profile edges and run a pool of route prefixes through the cheap bounds and the existing evaluator.

Include a branch that reaches an intermediate block but has the wrong momentum for the goal, and a geometrically longer branch that succeeds. Include a disconnected support gap to prevent bounding-box shortcuts. Compare against exhaustive enumeration of the same small declared route/profile family.

Measure route/profile candidates generated, conservative contradictions, heuristic drops, IPOPT calls, time to first witness and best run cost. Audit every prune against known feasible witnesses and report pool-cap drops separately. This tests the route/pruning ideas directly before adding a large state discretization, complex master optimizer or new replay interface.

## Source navigation

- `core/.../NoTurnSearchController.java`: window entry and pool/beam fallback.
- `core/.../anglesolver/noturn/NoTurnProblem.java`: fixed structure, constraints and facing units.
- `StructurePoolDriver.java`: segment enumeration, prefix sums, prefix prune, sprint bounds and pool ranking.
- `UnitScreen.java`: reduced facing screen and warm witness construction.
- `PoolCertifier.java`: shared best-first queue, numerical budgets and neighbor exploration.
- `NoTurnFinder.java`: beam retention, family diversity and current-input fallback.
- `fastcheck/CascadeCheck.java`: staged witness search and unknown results.
- `fastcheck/NoTurnJointFastCheck.java`: separate conservative inertia-aware rejection prototype.
