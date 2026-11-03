# Cell-route experiment plan

Current spatial-contract follow-up, 2026-11-03: [SPATIAL_CORRIDOR_REFACTOR_PLAN.md](SPATIAL_CORRIDOR_REFACTOR_PLAN.md) specifies the requested refactor after investigation of automatic Real11 routing. Different support visits and 3D passages, including overflight versus landing/run-off, must remain different routes. Preserve this source-era experiment plan/results; use the refactor plan together with the current DFS/interaction plan for new implementation. No refactor runtime is claimed by this note.

Latest local follow-up, 2026-11-01: [joint decision windows](experiments/decision-windows/README.md) and [results](experiments/decision-windows/RESULTS.md), source `d9cffafc`. Supplied opportunity ranges are clustered, shared comparisons extend to the next separate opportunity, and near-tie bands replace fixed top-k as the experimental quality filter. The 108 cold/controlled trials show persistent ceiling/step score reversals, and separate dense-cluster/repair limits. A richer score remains deferred while next-contact timing diversity is investigated; no new bound or production search switch is selected.

Current follow-up, 2026-10-31: the geometry experiment below and the [optimistic-guidance pilot](experiments/route-guidance/README.md) are completed. The user's subsequent choice is geometric position-only guidance at shared post-turn absolute ticks, superseding the mixed/provisional-initializer-first recommendations. This is implemented in [checkpoint guidance](experiments/checkpoint-guidance/README.md), including physical stepping/ceiling choices, no velocity thresholds and free whole-history facings. Checkpoint times are explicit; cold spatial-route selection and bounds remain separate work.

2026-10-31. Current planning baseline: `feature/ipopt-consolidated@99ecfe9`, recheck at implementation start. This is the active next-work plan. The user has deferred lower-bound research and wants to establish the cell decomposition, spatial paths and local decisions, then benchmark whether this hierarchy sufficiently reduces the remaining search. This turn changes documentation only.

Background: [SPATIAL_ROUTE_RESEARCH.md](SPATIAL_ROUTE_RESEARCH.md), [PRUNING_RESEARCH.md](PRUNING_RESEARCH.md), and [HEURISTIC_SOURCE_REVIEW.md](HEURISTIC_SOURCE_REVIEW.md).

## Objective and scope

Build an isolated experimental pipeline that:

1. Converts the existing normalized collision geometry into free cells, finite openings and physical interaction patches.
2. Enumerates a small diverse set of spatial routes.
3. Generates and optimizes the local interaction methods/timings within each selected route from the original initial state.
4. Measures whether knowing the spatial route removes enough discrete work, and identifies what decisions remain expensive.

Success is not a particular search algorithm name. It is retained useful routes, accepted whole-trajectory solutions, measured local decision reduction and a clear diagnosis of whether another abstraction is needed. Continue to rank accepted results by elapsed ticks and then the configured X/Z objective. Results are best found, not global optima.

Current user test scope: stepping, blockages, ceilings, run ticks and landing. Ladder and explicit wall-hit experiments are deferred for this iteration.

Deferred: new time/objective lower bounds, exact reachable-set propagation, a new branch-and-bound engine, sophisticated estimated edge-time models, production routing changes, water and game/UI integration. Preserve existing necessary validity checks and apply the same existing screens in comparisons; do not remove them just because new lower-bound research is deferred.

## Isolation before any implementation

At implementation start, create a new branch/worktree from the then-current canonical consolidated commit:

```text
branch:   research/ipopt-cell-routes
worktree: /Users/arvindmurty/CS/ParkourCalculatorMod-ipopt-cell-routes
```

These names were checked as unused during planning. Recheck before creation. Do not write implementation code into the integration worktree or the separate `/Users/arvindmurty/CS/ParkourCalculatorMod-ipopt-mip-spike` worktree. The mixed-integer experiment remains independently owned. Coordinate interfaces/results if later useful; do not make it a prerequisite.

Snapshot/copy the relevant currently uncommitted research documents into the isolated checkout without moving or staging unrelated integration changes. Record the exact code commit and research snapshot in the experiment manifest. Use a per-worktree build/configuration directory for compiler helpers, preserving shared Java movement/scene code and the native worker's executable path. Do not rebuild helpers during active numerical solves. Commit completed implementation milestones separately per `standalone/AGENTS.md`.

Planning created no branch/worktree. The user subsequently authorized implementation. Created `research/ipopt-cell-routes` in `/Users/arvindmurty/CS/ParkourCalculatorMod-ipopt-cell-routes` from `99ecfe9`; copied the research snapshot here. No further cross-thread communication is requested or needed.

## Stage 1: geometric decomposition and contact metadata

Implement a small experimental geometry module against the existing normalized AABBs and climbable regions, without designing a new public input format.

Preferred initial construction: expand solids for the player's foot-position representation; subtract intersecting expanded boxes locally to produce disjoint-interior free cuboids; merge adjacent cuboids only when their union is a cuboid and their interfaces/interaction metadata remain recoverable. Record the split/merge policy deterministically. Keep the height-sweep construction as an alternative if this proves unnecessarily fragmented; do not implement multiple decomposition engines before measuring the first.

Store:

- cell bounds and spatial lookup information;
- full finite portal patches, including partial shared faces between unequal-sized neighbors;
- actual physical support/wall/ceiling/contact patches with source block/box identity;
- climbable membership and step opportunities or links to the existing detectors;
- distinction between artificial open-space interfaces and physical contacts;
- required boundary strata for exact contacts, rather than introducing an arbitrary clearance that discards tight witnesses.

Do not infer physical collisions from artificial subdivision planes or union unrelated solid boxes across gaps. Reuse body dimensions, physical provenance and ladder/step semantics already present in the standalone/shared helpers.

Validation should check free-volume coverage and disjointness, absence of obstacle-interior overlap, neighbor openings, blocked interfaces, overlapping/nested AABBs, stacked layers, tight finite contact patches and merged-cell metadata. Include equivalent solid geometry encoded with different AABB splits and different insertion orders: compare represented free space and witness/corridor coverage, not identical raw cell IDs. Boundary-only cases are explicit coverage obligations, not silently discarded ordinary portals.

Deliverable: dumpable cells/portals/contact metadata and simple diagnostic views/slices, plus meaningful geometry tests. No numerical solve is needed for most of this stage.

## Stage 2: spatial routes and mapping real trajectories to them

Implement a bounded spatial walk/corridor generator over the shared graph. It must also be possible to supply one spatial route directly for diagnostics.

Define both:

- a raw cell/interface itinerary;
- a normalized broad corridor signature that ignores incidental subdivision details while preserving significant passage sides and transit landmarks.

Specify which local method replacements can stay in the same family even when their elementary cell crossings differ. A route may therefore carry a small corridor subgraph/union of allowed nearby itineraries, rather than forcing one overly specific tessellation trace. Do not use a wide union that accidentally reconnects the opposite side of a meaningful obstacle.

Use simple deterministic geometric ordering and diversity first. Distance or event-count hints may order proposals, but are not time bounds and do not justify claims of impossibility. Expose route, expansion and spatial-walk work caps. Count omissions/truncation. Avoid a global visited-cell rule: nonconsecutive returns can build momentum. Keep within-neighborhood self jumps in the local grammar and allow broad revisits under explicit finite enumeration limits. Any corridor simplification must preserve this distinction.

Map saved valid trajectories to cell/interface itineraries using the actual movement segments/phases, not only pre-tick samples. One input can cross several cells, while several ticks can remain in one cell. Preserve Y/X/Z order and contact boundaries. This mapping supplies route coverage audits and the prescribed-route diagnostic, not warm facing/timing seeds for cold discovery.

Deliverable: a reproducible spatial candidate list with signatures and coverage/truncation records; every known witness in the selected controls has a representable spatial route, or an explicit diagnosed gap.

## Stage 3: decisions inside a selected route

Add an experimental adapter that conditions the existing movement generators on the selected spatial corridor. Reuse `movement_generation`, `open_movement`, `open_ladder`, stepping/ceiling/contact generation, composition, scalar replay and shared-scene acceptance.

Expose the actual local decision domains, rather than hiding them behind a route count:

- jump versus no jump, run duration and support contact;
- overflight versus landing, running and run-off;
- permitted hit/avoid methods and finite contact footprints;
- contact time, portal-crossing time/window and necessary ordering;
- ladder entry/exit, climb/fall phases, Sneak masks and endpoint stops;
- stepping/cancel/blip/ceiling method alternatives and simultaneous/consecutive combinations.

Generate opportunities from corridor geometry early. Restrict boxes/faces/timing opportunities before full skeleton/contact/mask products where possible. Filtering a fully generated list after the expensive work is not enough to demonstrate useful decision reduction; instrument both stages if an initial adapter uses late filtering.

Keep the full original-state X/Z history adjustable. Never solve cells independently and freeze their arrival state. Materializing a discrete local program derives its actual Y/ground/contact history; changed contacts regenerate affected schedules and timings. Portal crossing is spatial bookkeeping, not an extra input, a fixed center waypoint or a per-crossing integer cost.

Latest user clarification: exploit the relatively fixed spatial route to evaluate constraints and local decisions in order from the beginning, propagating timing/progress alternatives forward with little backtracking. Keep whole-history facing optimization and explicit route revisits. Benchmark forward domain propagation against corridor filtering alone before inferring that another abstraction is necessary.

For each selected route, return its best accepted time/objective, explored decision counts, remaining/truncated alternatives and numerical-unknown status. A failing local solve does not eliminate the whole route as infeasible. Give every selected route an initial allocation before spending most of the budget refining one family.

Deliverable: accepted route-conditioned whole-trajectory solves on focused controls, including local method replacement and timing changes, with the existing model/scene validation level preserved. No new physics implementation or backend is part of this stage.

## Stage 4: benchmark matrix that answers the abstraction question

Use three main arms:

| Arm | Spatial information | Purpose |
| --- | --- | --- |
| A: existing generic search | Current automatic discovery | Establish the actual current generation/local-solve workload. |
| B: prescribed spatial route | Supply only the broad corridor extracted from an accepted witness or a manually specified geometry route; hide exact contact methods, input timings and facings | Diagnose residual local search when the broad path is already known. This is not cold spatial discovery. |
| C: automatic cell-route pipeline | Generate the shortlisted spatial routes from geometry | Measure end-to-end discovery and compare against arm B's remaining local complexity. |

A saved witness may be used to audit representation and define B. Arm C must start with the original scene/initial/goal data and no recorded yaw/contact/timing or selected route. Do not describe a prescribed five-ladder witness as cold discovery. A B failure can expose either an adapter/coverage bug or insufficient local search; diagnose correctness before declaring the abstraction ineffective.

### Scene coverage

Start with small controlled AABB scenes where all spatial alternatives/local modes can be enumerated, then use existing standalone fixtures and accepted evidence:

- sparse open space with multiple ticks in one cell and several cells crossed in one input;
- left/right obstacle passages, a skipped intermediate region, and routes that rejoin;
- the same broad corridor permitting overflight versus landing/run-off;
- a narrow intermediate support requiring useful onward momentum;
- stacked floors, a low ceiling, wide/cross neos and combined X/Y/Z contacts;
- airborne ladder transfers, endpoint stops and repeated ladder-ceiling effects;
- stepping/blip/grind chains and useful self jumps/revisits;
- geometry that genuinely offers no route, tested separately from numerical-unknown cases;
- equivalent geometry represented by differently subdivided boxes.

Use the existing `generic-grind-blip`, obstacle, ladder and stepping resources where their geometry and acceptance evidence support the case. Add focused small fixtures for missing contrasts. Existing known successful programs are controls, not proof that the new cold pipeline will discover them.

### Measurements

For every arm and scene, record:

- geometry preparation time, primitive/merged cell and portal counts, contact-patch counts;
- broad candidates generated, distinct signatures, duplicates, dropped and truncated alternatives;
- local decision domains, branching by decision type and number of unique discrete programs explored;
- skeletons, contact combinations, Sneak masks, Y traces/tick updates, regeneration and copy/hash work;
- compiler/screen calls, complete numerical proposals, actual solver calls including retries, and validation failures;
- per-route costs and counts as well as totals, to expose one route consuming most of the work;
- time to first complete numerical proposal, first accepted witness and best accepted ticks/objective;
- saved-witness route coverage and cold-discovery success separately.

Do not substitute a naive product of domain sizes for actual surviving schedule count. For small scenes, exhaustively count local alternatives; for larger scenes, report observed work and explicit caps. Never present a cap-limited count as full enumeration.

### Fair comparison and initial sweeps

Pin scene data, code commit, compiler/runtime settings, enabled movement families and deterministic ordering/seeds. Run correctness first, then unprofiled timings; use instrumented profiles to explain cost, not as speed measurements. Keep compiler warm-up/build overhead separate and run repeated timing samples, reporting medians and spread where feasible.

Compare both equal end-to-end wall-clock budgets and equal complete numerical-proposal budgets, since one method may shift cost from generation to solving. Include inner retries and local neighbor exploration in those budgets. First route-cap sweeps can use 10, 50 and 100 broad candidates; these are experimental values, not chosen production defaults. Keep per-route and total local work controls explicit, and record when a selected route receives no meaningful solve budget.

After the main arms work, compare raw cell itineraries versus normalized corridor families to see whether grouping removes near-duplicates without hiding useful local variants. Do not implement another abstraction merely to produce this ablation.

## Stage 5: make the next architectural decision from the results

| Observation | Interpretation and next action |
| --- | --- |
| B substantially reduces generation and local decision work; C retains useful corridors and performs similarly | The two-level hierarchy is promising. Improve engineering and tune caps rather than immediately adding a new layer. |
| B works well but C misses the useful route or spends heavily selecting routes | Improve decomposition, corridor grouping, diversity or route enumeration. Another local solver layer does not address this bottleneck. |
| B is correct but still explodes in contact/mask/timing combinations | Investigate an intermediate untimed interaction/mode plan, grouping methods before choosing ticks, or schedule-state reuse. Revisit bounds only as a separate evidence-driven proposal. |
| Cell/portal count or route identity depends badly on incidental AABB subdivision | Improve partition/merge/signature construction before drawing conclusions about local search. |
| Discrete work is small but numerical calls dominate | Investigate solver formulation, warm starts and backend efficiency; use the separate MIP spike's results as evidence without coupling worktrees. |
| Known valid trajectories cannot be represented or accepted | Correctness/coverage failure first. Fix mapping, boundaries, corridor constraints or the generator adapter before judging performance. |

No universal speedup or route-count target is preclaimed. For controlled exhaustive cases require preservation of valid movement coverage within the declared route family. For realistic scenes compare useful-route retention and accepted time/objective under matched budgets; report best-known outcomes, not global certificates. The final report should explicitly recommend whether cells plus local search suffice, whether corridor grouping needs revision, or whether a third interaction layer is justified. Lower-bound work remains deferred unless the user later reopens it.

## Implementation and reporting discipline

Keep experimental APIs and benchmark entry points separate from the production default search. Use the existing standalone tests and focused new geometry/route tests; do not run the mod's Java core suite for this spike. Build shared compiler helpers when needed, without duplicating physics. Expand standalone regression checks when changes affect shared orchestration behavior.

Save an experiment manifest, cell/route dumps, per-route counters, accepted witnesses and a compact comparison report under a dedicated cell-route experiment directory. Explain which runs are cold, prescribed, warm validation or exhaustive controls. Persist design decisions and findings after each turn in PRUNING_RESEARCH.md and the relevant experiment report. Commit each completed feature milestone in the isolated branch; do not stage the integration worktree's unrelated changes.

Current status: experimental geometry/routes/forward local-search pipeline implemented and benchmarked in the isolated worktree. The final non-ladder comparison contains 309 capped runs at source checkpoint `3257571`, plus independent replay of 22 witnesses and focused passing tests. Results and next proposals: [experiments/cell-routes/README.md](experiments/cell-routes/README.md). Bounds remain deferred; the remaining route-ordering and interaction-sequence work is explicitly identified in that report.

Latest follow-up: the user proposes route-specific optimistic continuation baselines for forward decision ranking. [ROUTE_BASELINE_GUIDANCE.md](ROUTE_BASELINE_GUIDANCE.md) formalizes conditional whole-route evaluation and short prefix/value-to-go approximations. This is scoped design work reopening the baseline question; it does not change the completed experiment or select a new global B&B implementation.

Latest coordinated refinement: the user welcomes disk controls if they give convexity. [CONVEX_ROUTE_GUIDANCE_PLAN.md](CONVEX_ROUTE_GUIDANCE_PLAN.md) specifies a genuinely convex disaggregated alternative and a three-arm comparison with compact circle/disk models. The earlier circle-first default in [CONTINUOUS_DECISION_RELAXATION.md](CONTINUOUS_DECISION_RELAXATION.md) is superseded. The restricted pilot is specified; conic backend details and full variable-Y contact coverage remain unresolved. User explicitly authorized coordination; no implementation merge or new runtime behavior accompanies this design turn.
