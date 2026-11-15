# Heuristic source review: Baritone and Stratfinder

2026-10-30. Source investigation for the sparse timed constraint-event graph. User priority: use heuristics to restrict expensive route attempts to tens or hundreds of promising candidates, with that selection informing A*, another best-first policy, or branch and bound. This revises the earlier emphasis on proof-preserving pruning as the primary route-count reduction. Heuristic selection is in scope for design; no implementation was requested or performed in this review.

## Inspected sources and scope

- Baritone default checkout `1.21.4`, commit `c55a9c5d9a333c9f0336179a96c13a178fcbc2d4`, freshly shallow-cloned into `/tmp/pkc-pruning-baritone`. Reviewed ordinary walking/parkour search, not its separate elytra pathfinder.
- Curryocity/Stratfinder `main`, commit `988695b9b23245ded9f8ffb07c7ffd984091225d`, freshly shallow-cloned into `/tmp/pkc-pruning-stratfinder`. Same revision as the earlier local investigation. Reviewed Input Cracker, segment transforms, zSolver and settings.
- This mod's native Stratfinder at local `dc10e68`, `/Users/arvindmurty/CS/ParkourCalculatorMod`, as a supplementary comparison: `StructurePoolDriver`, `UnitScreen`, `PoolCertifier`, `NoTurnFinder`. This native code is not a claim about the consolidated standalone search's behavior. Existing unrelated working changes were left untouched.
- Read [STRATFINDER_INVESTIGATION.md](STRATFINDER_INVESTIGATION.md) for earlier provenance and measurements. No runtime benchmarks or new numerical witnesses were generated here. The source repositories were read, not built or executed.

Interpretation: the external named Stratfinder means Curryocity's tool. Our native tool was also inspected to cover the other plausible meaning and because it already has relevant candidate-pool machinery.

## Baritone: what actually keeps its search manageable

### Restricted local movement library and state

[`Moves.java`](https://github.com/cabaletta/baritone/blob/c55a9c5d9a333c9f0336179a96c13a178fcbc2d4/src/main/java/baritone/pathing/movement/Moves.java) defines 22 movement templates: downward/pillar plus four directions each of traverse, ascend, descend, diagonal and parkour. Dynamic moves can choose their landing/destination while computing cost. It does not generate arbitrary controls or contact schedules.

[`PathNode`](https://github.com/cabaletta/baritone/blob/c55a9c5d9a333c9f0336179a96c13a178fcbc2d4/src/main/java/baritone/pathing/calc/PathNode.java) stores block X/Y/Z, cost and predecessor. Arrival velocity is absent from this graph state. The A* implementation shares the node for an XYZ destination and updates it for a better cost. This is an aggressive abstraction relative to our momentum-dependent event graph, not evidence that coordinate-only merging preserves every parkour route.

### Goal estimate and action cost

[`GoalXZ.calculate`](https://github.com/cabaletta/baritone/blob/c55a9c5d9a333c9f0336179a96c13a178fcbc2d4/src/api/java/baritone/api/pathing/goals/GoalXZ.java) uses octile distance: `max(|dx|,|dz|) + (sqrt(2)-1)*min(|dx|,|dz|)`, multiplied by `costHeuristic`. [`GoalBlock`](https://github.com/cabaletta/baritone/blob/c55a9c5d9a333c9f0336179a96c13a178fcbc2d4/src/api/java/baritone/api/pathing/goals/GoalBlock.java) adds a vertical estimate from [`GoalYLevel`](https://github.com/cabaletta/baritone/blob/c55a9c5d9a333c9f0336179a96c13a178fcbc2d4/src/api/java/baritone/api/pathing/goals/GoalYLevel.java): upward cost scales with one-block jump cost, downward cost with a fall-derived factor.

[`ActionCosts`](https://github.com/cabaletta/baritone/blob/c55a9c5d9a333c9f0336179a96c13a178fcbc2d4/src/api/java/baritone/api/pathing/movement/ActionCosts.java) expresses costs approximately in ticks, with different rates for walking, sprinting, sneaking and ladders, plus a fall-time table. [`Settings`](https://github.com/cabaletta/baritone/blob/c55a9c5d9a333c9f0336179a96c13a178fcbc2d4/src/api/java/baritone/api/Settings.java) defaults `costHeuristic` to 3.563 and documents the speed-versus-path-quality tradeoff from increasing it. It also has penalties such as jumping and block placement. These are practical preferences, not purely elapsed-time costs.

Transfer: estimate remaining time on our actual event/mode graph. Do not transplant octile movement assumptions or add arbitrary jump penalties to the final TAS objective. Horizontal and vertical progress can occur together in our movements. A ranking estimate can be deliberately biased, but keep it separate from a certified lower bound and from accepted-result ranking.

### Geometry filters and a concrete shortcut

[`MovementParkour.cost`](https://github.com/cabaletta/baritone/blob/c55a9c5d9a333c9f0336179a96c13a178fcbc2d4/src/main/java/baritone/pathing/movement/movements/MovementParkour.java) rejects unsuitable headroom, takeoff blocks, landing support and overshoot hazards before execution. It tries shorter supported jumps first and returns a selected landing rather than all later landing alternatives. It also suppresses parkour when an adjacent ordinary traverse is available, and bounds jump distance using local conditions such as sprint availability. These are important reductions of the candidate family, not merely a clever A* priority.

Transfer: use geometry to propose a small set of credible next interactions. Treat nearest-landing and ordinary-movement preferences as heuristics; longer jumps, deliberate hits and self jumps can be essential or faster in our problem.

### Search and anytime choices

[`AStarPathFinder`](https://github.com/cabaletta/baritone/blob/c55a9c5d9a333c9f0336179a96c13a178fcbc2d4/src/main/java/baritone/pathing/calc/AStarPathFinder.java) expands `g+h`, drops invalid local moves, and normally suppresses cost improvements of at most .01 ticks. On timeout it can select a useful partial segment using multiple `h+g/c` metrics. [`AbstractNodeCostSearch`](https://github.com/cabaletta/baritone/blob/c55a9c5d9a333c9f0336179a96c13a178fcbc2d4/src/main/java/baritone/pathing/calc/AbstractNodeCostSearch.java) uses coefficients 1.5, 2, 2.5, 3, 4, 5 and 10, choosing a sufficiently progressed partial path. These coefficients track fallback endpoints; they are not seven separately expanded weighted-A* searches.

[`Favoring`](https://github.com/cabaletta/baritone/blob/c55a9c5d9a333c9f0336179a96c13a178fcbc2d4/src/main/java/baritone/utils/pathing/Favoring.java) discounts destinations on an existing path, with optional avoidance modifiers. The [feature record](https://github.com/cabaletta/baritone/blob/c55a9c5d9a333c9f0336179a96c13a178fcbc2d4/FEATURES.md) describes segmented calculation and execution. None of this establishes a fixed tens/hundreds bound on internal routes or nodes. Our whole-route momentum optimization cannot freeze an executed segment's arrival as a general substitute for all earlier possibilities.

## Curryocity's Stratfinder: a structured inverse search

[`Input Cracker`](https://github.com/Curryocity/Stratfinder/blob/988695b9b23245ded9f8ffb07c7ffd984091225d/src/inputCracker.cpp) builds suffixes backward from a desired terminal velocity window. It iteratively increases key-change depth, refunds depth for allowed bridge transitions, and obeys tick/result limits. It removes certain equivalent keys when only one axis matters, suppresses redundant extensions of known solutions, and uses approximate incoming-velocity bounds through a suffix to reject misses. Near the depth limit, zero-duration versus terminal-speed comparisons and increasing-error checks stop some duration loops. These rely on its supplied facing, airtime and movement family; they are not universal free-turn route proofs.

[`segLerp`](https://github.com/Curryocity/Stratfinder/blob/988695b9b23245ded9f8ffb07c7ffd984091225d/src/segLerp.cpp) precomputes inertia-disabled affine velocity maps and composes them cheaply, carrying alternatives for previous sprint state. Promising sequences then get forward movement evaluation. This is the useful architectural pattern: cheap summaries before detailed evaluation, and backward information about what the final movement needs.

The [`README`](https://github.com/Curryocity/Stratfinder/blob/988695b9b23245ded9f8ffb07c7ffd984091225d/README.md) documents preference for simpler segments, optional restriction to empty transition bridges, and a UI-default risky interpolation mode that can miss inertia-dependent sequences. Its aggressive selection is a deliberate practical tradeoff. We can borrow the staged-selection pattern while retaining our model and validation.

[`zSolver`](https://github.com/Curryocity/Stratfinder/blob/988695b9b23245ded9f8ffb07c7ffd984091225d/src/zSolver.cpp) evaluates a small library of named momentum strategy families, including slingshot, robo, boomerang and pendulum, with delayed/nondelayed variants; its header also declares backwall families. It estimates how many jumps fit available room, constructs useful speed bounds, compares family results and sometimes returns before evaluating the remaining expensive family. Delay search is reduced using room and terminal-speed behavior. This is domain-specific family selection, not arbitrary 3D route discovery.

The main implication is that its small number of candidate strategies is obtained by a strong library and restricted problem assumptions. Free-turn route finding with arbitrary contacts needs its own library and features.

## Native Stratfinder: directly reusable candidate-pool ideas

The local implementation uses key-change complexity levels, minimum holds and jump-adjacent exceptions. Distinct facing structures are searched separately. The shared pool ranks approximate constraint feasibility before expensive certification.

`StructurePoolDriver.Config` has defaults `poolCap=4000`, `perEdgeCertify=20`, `maxCertify=400`; these are class defaults, not an assertion that every controller invocation uses them. The score distinguishes screen-exact, near and miss tiers (deficit thresholds .01 and .05); exact-tier ties favor fewer presses and backward inputs, while other tiers favor lower violation first. Prefix checks exploit walls already determined by assigned inputs.

`UnitScreen` uses coarse angle search, selective refinement and exact forward movement screening. It measures joint constraint deficit, not only final distance. The exact/near tier names describe screen quality, not a full accepted certificate.

`NoTurnFinder` is a beam fallback: rank uses the number of surviving facing-grid possibilities with an edge-count preference, then retention diversifies across final key and edge count. `PoolCertifier` tries ranked batches, uses separate budgets for near candidates, explores warm neighboring schedules and can try miss-tier candidates. Useful analogy: preserve feasibility flexibility and different solution mechanisms rather than keeping many copies of the same leading candidate. These facing-grid filters cannot be directly used as free-turn route-infeasibility tests.

## Proposed heuristic architecture for our graph

This section is a design inference from the source review. Values below are starting experiments, not chosen production settings or measured results.

### 1. Generate route families before detailed schedules

Use the user's sparse event graph. A family identifies supports/regions, interaction types and passage sides, without eagerly multiplying every tick and Sneak mask. Generate time choices near relevant vertical crossings, entry/exit transitions and changes in contact opportunity. Retain boundary alternatives where caps, inertia, endpoint stops or ceiling refresh can matter. Ordinary movement, ladder transfers, stepping, hit-based and avoid-based continuations all stay eligible families.

This is the strongest lesson from both tools: restrict how candidates are proposed, not only how an enormous generated list is sorted. Avoid the rule that every extra contact or revisit is worse. A mechanism should be judged by what it enables next.

### 2. Score continuation compatibility, not just progress or speed

Build an estimated completion-time feature from relaxed event transitions. Add a cheap forecast of how well the arrival position and momentum can satisfy the next event and its successor: required direction, braking/reversal, ladder cap/entry, landing width and vertical phase. Use backward target-state information, as Input Cracker does for final velocity. Two-step lookahead can distinguish a fast arrival that cannot turn from a slower useful approach.

A possible queue priority is:

```text
score = elapsed_ticks + w * estimated_remaining_ticks
        + momentum_mismatch_penalty
        + intermediate_constraint_deficit_penalty
        + extra_setup_complexity_penalty
```

Normalize penalties into interpretable scales and tune them by retained useful routes. An alternative is estimated feasible completion time first, then these features as tie preferences. No weight choice is established. Do not let an event count replace real time, and avoid double-counting a braking delay both inside estimated time and as a penalty.

The estimate/penalties are allowed to reject poor-looking candidates under the user's heuristic-selection direction. They are not automatically valid branch-and-bound lower bounds. Store a separate safe bound where available.

### 3. Retain a diverse small pool, with explicit total work accounting

Use a bounded best-first/beam policy on the shared graph, guided by the score. A* ordering alone does not impose a route-count limit. Starting experiments can target 50, 100 and 200 complete expensive proposals. Count the actual expanded timing/contact schedules sent to numerical solving, not only route skeletons that secretly expand into thousands of calls.

Deduplicate by event/geometry sequence and group nearby timing variants. Reserve representation for different passage sides, movement mechanisms, and occupied incoming-direction/speed categories. Do not preinstantiate a full velocity grid. Keep a few genuinely different arrivals at a shared event. Similar nearby timings should not fill the entire budget and exclude a different route.

Possible policy: most slots follow estimated quality, with a minority reserved for diversity and near-miss families. Native per-family retention supplies the pattern; the exact share and per-event cap need measurement. Such retention is heuristic; exact dominance is an additional opportunity where proved, not the prerequisite for candidate selection.

### 4. Use coarse evaluation and local improvement

Apply geometry/Y checks, a cheap movement/constraint-deficit screen, then detailed IPOPT and scalar/shared-scene acceptance to the retained proposals. Free facings remain free in acceptance. A reduced-facing forecast may inform selection without becoming a new facing constraint.

Use successful or promising routes to seed small changes in contact times, entry/exit choices and run allocation, as native warm-neighbor certification does. Keep all expensive initial and local-improvement attempts within the same reported work budget. Near misses can merit some reserved exploration; repeatedly retrying one failing family should not exhaust the pool.

### 5. Widen deliberately when needed

Within the user's total budget, start with simple/high-ranked route families and then spend remaining slots on additional mechanisms or more timing alternatives if no witness appears. A hard stop after the selected candidates is also consistent with the requested heuristic goal; report it as bounded candidate selection. Preserve the established final ranking by actual ticks and equal-time X/Z objective.

Branch and bound can prune selected families using valid incumbent bounds. It is not required to carry the primary route-count reduction. Numerical unknowns and heuristic exclusions remain distinct from physical impossibility, without imposing an exhaustive-search requirement on this mode.

## How to choose the heuristic rather than just its name

Measure route proposal quality at K=10, 50, 100 and 200 expensive attempts. Use real cold scenes as well as small exhaustively evaluated controls. Track first accepted attempt/time, best actual ticks and objective, diversity, generation effort and numerical effort. For larger scenes use known accepted routes/best-known solutions as coverage references without calling them global optima.

Ablate separately: geometry/time only; add momentum lookahead; add diversity; add strategy-family generation and timing grouping; add warm local changes. Test whether the shortlist contains useful routes before adding a more complicated search algorithm. Include routes needing backward setup, self jumps, unusual entry faces and repeated ceiling/stepping/ladder effects. A heuristic that only succeeds on ordinary forward jumps has not met our movement scope.

Decision checkpoint: the user has prioritized heuristic route selection to tens/hundreds. Current recommendation is a diverse bounded candidate search on the sparse event graph with template generation, estimated completion time, momentum compatibility and constraint-deficit screening. Particular weights, caps and graph-search backend are still open. No speedup, recall or solution-quality claim has yet been measured.

## Terminology follow-up, 2026-10-31

[The living research record](PRUNING_RESEARCH.md#search-terminology-clarification-bounded-best-first-and-beam-search) now defines the proposed globally capped best-first frontier versus classical layer-based beam retention. Live width, total expansions and complete numerical attempts are separate budgets. No width, layering or policy has been selected.

## Spatial-route formalization follow-up, 2026-10-31

The user clarified why Baritone was requested: derive a broad spatial path representation separately from local interaction/timing optimization. [The living research record](PRUNING_RESEARCH.md#hierarchical-encoding-spatial-route-families-and-local-realizations) now proposes shared region/passage nodes, untimed spatial route signatures and a local timed event graph. Baritone's location-plus-movement-action graph informs this abstraction, but the two-level route-family/local-realization design is our adaptation. Local alternatives include changes in event structure, such as overflight versus landing/run-off, rather than only tick shifts. The original-state continuous solve and momentum continuity couple the levels.

## Geometry extraction research follow-up, 2026-10-31

[SPATIAL_ROUTE_RESEARCH.md](SPATIAL_ROUTE_RESEARCH.md) now supplies a concrete proposal for deriving spatial regions/passages from AABB geometry, comparing cell decomposition, navmesh links, hierarchical entrances, topo-geometric path classes and diverse top-K route selection. This extends the Baritone-inspired abstraction beyond a list of heuristic scores. No source/runtime implementation or measurements were added.

## General cell-decomposition foundation follow-up, 2026-10-31

The user requested the general context before adapting the method. SPATIAL_ROUTE_RESEARCH.md now separates standard exact/approximate/coverage decomposition and graph guarantees from our AABB-specific proposal. It also records alternative cuboid constructions and the local solver benefit of replacing obstacle-side disjunctions by corridor membership bounds. These are research conclusions and design candidates; no code or benchmark changed.
