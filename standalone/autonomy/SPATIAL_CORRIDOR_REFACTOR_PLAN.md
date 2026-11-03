# Spatial corridor refactor: preserve support visits and 3D passage choices

2026-11-03. Spatial-refactor implementation contract. Milestone1 (support-aligned refinement and typed region graph) is implemented; route enumeration, progress, fixed guards and DFS integration remain pending. Updated after importing [CORRIDOR_AGENT_HANDOFF](../docs/CORRIDOR_AGENT_HANDOFF.md) through `61355976`, merged as `984b701b` into isolated branch `research/real11-initial-run-ticks`. The earlier geometry diagnostics remain source-specific at `93652150`. This plan specifies the spatial representation and its integration with the **implemented** [DFS/interaction search](DFS_INTERACTION_IMPLEMENTATION_PLAN.md). It supersedes that plan's earlier sections2.1–2.5 where route identity or division of work differs. Preserve the imported runtime protections below rather than reimplementing them. Only committed handoff changes were imported; the source checkout's in-progress retry was left untouched.

For compact-resume checkout/state/commands and the next action, use [CORRIDOR_IMPLEMENTATION_RESUME.md](../docs/CORRIDOR_IMPLEMENTATION_RESUME.md). Sections10–11 below provide the implementation API/call-site checklist, first slice and test matrix; this is not only a conceptual design.

## Implementation status

Milestone1 implemented2026-11-03: `geometry.decompose` refines incident above-support cells at finite footprint edges, retaining residual air and boundary strata and recording parent incidence. `regions.py` canonicalizes exact exposed planar unions and creates explicit support/air nodes with directed open/contact links (graph schema1). Contact links are geometric candidates requiring physical interaction; they are not certified jumps. The legacy corridor enumerator/adapter/DFS still operate under their existing contract until subsequent milestones land.

Verification:29 geometry/region/legacy-route/local-adapter tests and52 focused native DFS/interaction/terminal tests pass, no skips. Stored-geometry diagnostic:33→47 cells,7 support components, with actual rectangle SVG and exact incidence dump in [semantic-regions audit](experiments/linkcraft11-opening/audit/semantic-regions-2026-11-03.json). No cold numerical retry or helper rebuild. Continue milestone2; milestone1 is not the completed refactor.

## 0. Imported baseline and integration requirements

| Already implemented | How the refactor must use/preserve it |
| --- | --- |
| `depth_first.search_goals`, delegated from `find_checkpoint_route` | Extend the existing frames' goal/stage source and route context. Keep arrival ordering, actual-child promotion, siblings/unknowns/refinements and backtracking; do not build another DFS or restore a clock-window barrier. |
| `interactions.InteractionGate` before normal/stepping/ceiling generation | Call its existing `context`, `query` and `required` paths. Preserve the original-state compiled model, matching eligible gates and unknown coverage. |
| Early effective-model dedup and `constraintOwners` | Attach new corridor rows to the actual owning event/completion state; preserve full-proposal screening and removal of orphan approach rows during truncation/promotion. |
| Corrected shared reach envelopes | Named step/grind probes keep unknown Y and intended unclipped X/Z where realized endpoints omit those probes. New spatial filters must not tighten these phases from realized feet height alone. |
| Exact numerical requests and branch validation logs | Keep all fixed-model hashes, solve/scope IDs and separate numerical/corridor/scene outcomes. Same controls or finish tick are insufficient to deduplicate numerical requests. |
| `terminal_goals` and virtual finish | Reuse the descending height-clock and one-sided endpoint rows. Support free overshoot and actual finite B7 collision as distinct alternatives; no artificial floor or goal-imposed opposite-side bounds. |
| `minimum_runs` and DFS wiring | Preserve per-frame dynamically learned latest-jump preparation thresholds, prior-context/contact distinctions, earlier unknowns, exclusions and unpruned control. New route rows/identities must not merge distinct patterns or turn total runs into the local count. |
| Version5 `ordered_goal_dfs` persistence | Preserve one execution owner for each frame, suspended/local generators, accepted constraint variants and incumbent. Add route-stage identity with explicit schema/context migration; even pre-policy version5 states are not automatically compatible. |

Current margins/state-protection helpers remain available, but the implemented DFS does not run their former global peer-retention pass. The existing active heuristic is latest-jump extra-run exclusion. This refactor does not reactivate another exclusion policy or add continuation-time bounds. The imported compiler timeout recovery must also remain intact.

The handoff's source-specific calls8/9 and initial0 calls5–8 remain visible. They include separate free/physical terminal models and corridor repairs. Do not suppress or special-case them to improve call counts. Future reductions require a demonstrated change to the actual fixed constraints/route semantics with old/new request and validation evidence.

## 1. User contract and documentary basis

The purpose of decomposition is to expose different spatial corridors: using different blocks, passing on different sides of obstacles, and taking different vertical passages. Select a small set of those routes, then optimize movement decisions within each.

Latest explicit clarification: **overflying a block and landing on it then running off are different routes even if their X/Z projections agree.** Their vertical/contact itineraries differ. Different jump/run timings and continuous facings within the same itinerary remain local decisions. Different continuous Y samples alone do not create infinitely many route types; crossing a different spatial layer or visiting a physical support boundary does.

Relevant existing docs:

- [Original experiment plan, stage2](CELL_ROUTE_EXPERIMENT_PLAN.md#stage-2-spatial-routes-and-mapping-real-trajectories-to-them): retain raw cell/interface itineraries, normalize incidental subdivisions, preserve meaningful passages, and never union opposite sides back together.
- [Original experiment plan, stage3](CELL_ROUTE_EXPERIMENT_PLAN.md#stage-3-decisions-inside-a-selected-route): reuse existing movement builders and solve the whole history; a portal crossing is not an extra tick or fixed center waypoint.
- [Spatial research, compression and movement annotations](SPATIAL_ROUTE_RESEARCH.md#4-compress-subdivision-artifacts-without-erasing-route-choices): preserve support neighborhoods and multiple internal passages between the same endpoints; geometric connectivity alone permits unrealistic flight.
- [Spatial research, old route-family definition](SPATIAL_ROUTE_RESEARCH.md#defining-a-broad-route-family-operationally) explicitly grouped some landing/overflight variants. That grouping is superseded by the clarification above; preserve its text as historical context.
- [Existing block-route search](BLOCK_ROUTE_SEARCH.md): intermediate supports/order already come from an unordered scene and shared movement/profile/reach helpers. Reuse these mechanisms rather than requiring a fixture support list or inventing another physics model.
- [Cell-route results](experiments/cell-routes/README.md#the-important-counterexample): the prototype missed a known through-support family, and its same-corridor support ambiguity was already diagnosed.
- [Fixed-decision solve contract](cell_routes/ORCHESTRATOR_CONTRACT.md): the orchestrator fixes concrete inputs/interactions/constraints before IPOPT; all original-state facings remain free.

### Route identity examples

| Difference | Route identity |
| --- | --- |
| B1→B2→B3 versus B1→B4→B3 | Different support routes |
| Pass above B2 without contact versus land on B2 and depart | Different vertical/contact routes |
| Same support sequence, opposite passages around a wall | Different passage routes |
| Same X/Z trace, above versus below a platform/ceiling | Different vertical routes |
| Same support/portal itinerary, launch a tick later or add grounded run inputs | Same route, different timed realization |
| Jump versus run off after visiting the same support | Same route if the subsequent spatial/contact itinerary agrees; different if it changes layers/passages or supports |
| Setup self-hop that returns to the current support neighborhood without another significant passage | Local setup alternative; preserve it in the movement program |
| Leave a neighborhood and return through meaningful portals/supports | Explicit route revisit with a distinct occurrence |
| Extra rectangles introduced by an equivalent AABB split | No new semantic route by itself |

The desired automatic spatial path has no global B2/B3/B4/B5 requirement. The current handoff fixture still supplies that support sequence as a reproducible control; keep it while adding the automatic path. A discovered route can select those contacts; a different discovered route can skip or reorder them. Once a route selects a support visit, its inner realization must actually make that contact. An inner candidate that skips it belongs to a different route rather than silently satisfying the selected route.

For example, `support(B1) → air passage above B2 → support(B3)` and `support(B1) → support(B2) → air departure → support(B3)` are different route keys. Either may have many launch times, grounded waits and facing solutions. The separation is the ordered spatial/contact itinerary, not one route for every sampled Y trajectory.

## 2. Specific current failures to replace

1. `geometry.decompose` represents free placement volumes and face patches, but the route graph searches volume adjacency without requiring a usable sequence of support/flight interactions.
2. A large cell above a block can span far beyond the finite support footprint. Physical patches exist, but they are not distinct visit states in route identity. Even refining that volume cannot distinguish touching its lower support face from flying inside it.
3. `routes.enumerate_routes` groups paths by dominant-axis horizontal block-midplane triples. They discard vertical/contact distinctions and introduce irrelevant side restrictions on support blocks.
4. Current automatic/prescribed `projection` construction uses all decomposition cells. Different routes therefore receive the same broad horizontal guard; their signature supplies most of the actual distinction.
5. Current `Corridor.stage_paths` is empty for the inspected Real11 routes. The adapter's ordered forward propagation is consequently disabled there. Its old horizontal-only earliest-stage pass is insufficient for mandatory contact/3D stage order anyway.
6. Whole-portal midpoints depend on distant empty coverage. The B2→B3 diagnostic moved portal anchors5 blocks when empty coverage increased10; appropriate support-region references did not move.
7. The fixed-clock driver adds a support-order list, literal clocks and manually mapped goals/directions rather than consuming a route's inferred stages.

Measured counterexamples are in [corridor comparison](experiments/linkcraft11-opening/audit/corridor-comparison-2026-11-03.json), [default50-route audit](experiments/linkcraft11-opening/audit/automatic-route-defaults-2026-11-03.json), and [decomposition investigation](experiments/linkcraft11-opening/audit/decomposition-investigation-2026-11-03.json). These diagnose the stored geometry; B6's full rightward extent remains unspecified.

## 3. One spatial graph, with explicit regions and transition types

Retain the exact expanded-AABB geometry as the base representation. Refactor the existing spatial graph into a graph with free-volume regions and physical support-surface regions. This is the spatial layer itself, not an additional independent search layer between routing and the existing local solver.

### Data records

| Record | Required contents |
| --- | --- |
| `Cell` / geometric `Portal` | Existing exact bounds, dimension, finite shared opening and raw incidence; IDs local to the decomposition |
| `RouteRegion` | Versioned key; `air_volume`, `support_surface` or `terminal_predicate`; exact box/patch cover or existing virtual-goal spec; parent cell IDs; physical owners/components; neighboring transition IDs. A finite query envelope for a virtual goal is not its physical footprint. |
| `RouteTransition` | Directed key; entry/exit region; kind `open_portal`, `support_arrival`, `support_departure` or `finish`; finite passage/contact geometry or the terminal predicate; physical provenance; underlying raw interfaces; possible movement families; `ruled_out` or `not_ruled_out` with proof/context |
| `RouteStage` | Ordered occurrence; required support visit or passage/layer transition; entry/exit regions; allowed local cell/portal subgraph; acceptance predicate; next compatible stages |
| `Corridor` v2 | Geometry/schema identity; start/goal; ordered semantic key; stages and compatible variants; finite region/portal covers; separate guide points and provenance; no fixed event ticks |
| `RouteProgress` | Set of possible stage/variant occurrences consistent with a prefix; achieved support contacts/passages; scoped pending requirements; no stored numerical state as a splice start |

Place graph/region construction in a focused `cell_routes/regions.py`; keep route records/enumeration in `routes.py`; put shared phase tracing/progress in `cell_routes/route_progress.py`. These are cohesive replacements for logic currently spread across graph, signature, adapter and checkpoint code. Do not add parallel physics, a second route search backend, or a generic plugin framework.

### Support surfaces and local refinement

1. Reuse normalized physical boxes, player dimensions, exposed `ContactPatch` records and standing/landing-footprint helpers. Group patches by physical owner, face plane and connected exposed component; keep different heights/disconnected components distinct. Preserve per-box provenance needed by collision validation and per-physical-block caps.
2. Canonicalize each planar rectangle union without filling concavities/gaps: sweep its sorted X edges, merge covered Z intervals in each strip, then merge adjacent strips with identical interval coverage. Drop internal encoding seams, not actual boundaries. Use exact stored coordinates; no rounded hashes that merge different models.
3. A support surface is a genuine boundary region. `support_arrival` requires the physical contact; entering the air volume above it does not count. `support_departure` exposes the supported boundary to its surrounding free regions. These edges are not free openings through a solid.
4. Locally split above-support free cells at their own finite footprint edges and retain residual air cells. Preserve exact boundary strata. Attach the bottom-face support component to the above-footprint piece, without asserting that every altitude inside it is grounded. Rebuild portals/contact incidences afterward.
5. Keep broad air cells elsewhere. The measured47-region diagnostic is a useful prototype, not a desired fixed count or proof that the production implementation is complete. Do not globally grid the world or split at arbitrary tick/height resolutions.

Raw geometric subdivision IDs may change after refinement. Semantic keys use physical component/finite opening geometry plus schema/context identity. Equivalent scene encodings must retain equivalent route coverage; exact continuation is rejected if its graph/context identity changes.

### Directed movement annotations

Reuse `generate_choices`, normal/run-off scalar replay, stepping/ceiling builders, existing compiled magnitude/friction bounds, `InteractionGate`/`interaction_reachability` and `screen_program`. Extract reusable coarse profile/reach functions from `build_transition_graph` when appropriate; do not import its entire old search policy or assume its defaults match the zero-threshold model. Use the imported gate rather than adding a competing eligibility system. Preserve named step/grind uncertainty and the same eligible gate subset in pre-solve guards and terminal stage checks.

A contact-to-contact transfer retains all supported local method families without putting a particular jump tick/profile ID into the spatial route key. A continuous airborne episode carries its last support/contact and permitted profile context through artificial air portals; crossing a portal cannot reset height, velocity, airborne time or jump availability. Only a selected physical interaction can supply such effects.

Exclude a connection only when existing conservative checks rule it out across the declared relevant family. Unsupported mechanics, incomplete profile generation and capped screens remain unknown. In particular a geometry patch on B6's Y20 top is not a reachable landing just because it exists. The initial-state check can be tighter than later unknown arrivals; later candidates cannot inherit another branch's optimized velocity as a universal assumption.

## 4. Route enumeration and normalization

Refactor `enumerate_routes` to traverse this graph and emit semantic support/passage itineraries. Keep a bounded resumable generator over the same graph; the numerical movement DFS remains inside each emitted route.

1. Root candidates use the actual start support/contact context. A finite-support goal selects its physical surface; a virtual goal selects the existing terminal predicate, with free and physical-contact alternatives retained. Intermediate support arrivals are graph choices, not a scene-ordered list in the automatic path.
2. Each arrival/departure or distinct open passage appends the appropriate stage. Contact-free overflight follows air regions and never visits the support-surface node. Required contact versus overflight is therefore represented structurally even when their 3D volume cells or horizontal projections overlap.
3. Compress only artificial transit chains with no support boundary, junction, distinct opening or meaningful layer change. Merge coplanar patches only when they form the same connected opening; retain a finite patch cover for nonrectangular openings. Keep parallel edges when two passages connect the same outer regions.
4. Inside a convex region, entry-to-exit motion is one regional connection; differences between representative points are not different routes. Contracted connections retain their ordered underlying cells/interfaces for expansion and checking.
5. Form the semantic key from ordered support-component visits and directed passage/layer transitions, including meaningful revisits. Strip artificial subdivision events. Never normalize by X/Z projection alone or a side tag at every block's midpoint.
6. Merge nearby raw realizations only if they have the same semantic key and compatible stage progression. Retain alternatives as graph paths/variant states; do not flatten their cells into a union that admits a new connection between opposite passages. If equivalence is uncertain, retain separate candidates and record the duplicate opportunity rather than erase coverage.
7. Keep same-neighborhood setup loops in the local grammar. Preserve nonconsecutive meaningful returns; no global visited-cell/support deduplication. Search state includes route occurrence/context, not only current cell ID.
8. Use deterministic region-distance/proposal ordering and explicit work limits. A point-to-region geometric estimate is an ordering hint, not an exact transit time or pruning certificate. Remove the old portal-center/dominant-axis ranking dependency and avoid adding arbitrary weighted scores.

Store generator cursors, suspended alternatives, actual exclusions and emitted semantic keys. Report raw walks, unique semantic routes, duplicate encodings, capped/unresolved connections and witness coverage separately. A count of50 geometric signatures must not be presented as50 physical routes.

The outer route queue gives emitted distinct families an initial bounded local evaluation, then allocates further work while retaining untried/unknown routes. Inside a selected route, extend the existing `depth_first.search_goals` frames and backtracking; do not add a replacement traversal engine. Replace the fixed `goal_list`/index mapping with stage-derived goal advancement while retaining the current child promotion, inner repair, refinement and suspended-work machinery. Initial allocation must not exhaust every route's optional refinements before a promising branch can descend.

## 5. Corridors must preserve the selected route in 3D

Replace global projected-region membership plus horizontal midplane triples with stage-specific region/portal paths and physical contact predicates. A `support_arrival` cannot be satisfied by an airborne point above the same footprint. Under/over and opposite obstacle passages remain separate.

### Shared trace and progress engine

Build `route_progress` around existing `horizontal_trace`'s actual Y→X→Z segment construction and `geometry.segment_intervals`/portal incidence:

- Map each axis sweep to the finite 3D cells/interfaces it actually crosses. One input can consume multiple portals; one cell can hold many inputs. Portal crossings have no integer tick cost.
- Require an incidence-consistent ordered walk through ambiguous shared boundaries, retaining possible stage/variant states rather than selecting the lowest cell ID. Touching a plane and returning does not invent a passage.
- Consume physical support stages using the composed program's contact/case events and independent contact validation, with the correct collision phase. Merely being at the same height is insufficient.
- Account for every actual significant support visit, not only those expected by the route. An extra B2 landing cannot be silently ignored to accept an overflight family. Compress repeated supported inputs and allowed same-neighborhood setup returns as specified in the route contract; preserve new support progression and meaningful revisits. Local permitted ceiling/stepping methods still use their owned event predicates.
- Permit a partial prefix to stop inside a stage; do not skip a required support stage to advance to a later volume. Propagate all compatible cursors for alternatives/revisits, not the old horizontal earliest-index shortcut.
- Use the same predicates in conservative prefix screening, guide-stage selection and final membership auditing. Physical scene replay remains a separate requirement.

### Fixed rows before continuous solves

Extend the imported constraint-variant/ownership path, not IPOPT. For each concrete movement program, orchestration fixes its support events, passage alternatives and stage/phase branch before calling the worker. Reuse existing landing/stepping/ceiling/contact rows and obstacle passage builders. Owner completion states must survive `commit_arrival`; a removed future event cannot leave its earlier route approach rows behind. Reuse early partial-model canonicalization and preserve exact request/branch logging when new rows distinguish models.

Derive guards from the selected stage's actual 3D region cover at that program's known scalar Y phases. Generalize `corridor_guards.movement_regions` from horizontal guards to axis-sweep coverage, preserving adjacent connected intervals. An axis movement can traverse several neighboring cells without requiring its endpoints to fit one original rectangle. For stage boundaries within the same phase, retain an actual ordered portal path and its directed plane order; do not assign independent controls or an extra tick to each crossing. Select any genuinely disjunctive guard alternative in the orchestrator using existing reach screening and variant deduplication. Never impose every rectangle in a union simultaneously.

Add route-stage requirements to the fixed context identity alongside the existing solve scope. Preliminary solves enforce only their declared active stage prefix/goal and modeled geometry. Omitted future support/passage requirements cannot return as hidden guards or post-solve failures. Final incumbents require the complete route contract and full supplied-scene replay.

A physically valid trajectory with a different semantic itinerary is recorded as a potential alternate route, not accepted as a witness for the requested family or mislabeled physically infeasible. Discovering that alternate does not seed a sibling with its decisions under the currently deferred decision-transfer policy; it can queue an unseeded spatial family separately.

## 6. Guidance, comparison goals and local decisions

Choose reference anchors from the finite source/destination support surfaces or selected passage openings. Pick/project candidate points inside the actual portal patches and validate their connecting segments in the selected cells. For an air segment, place its guide point between its selected entry/exit regions. Keep the feasible patches; representative coordinates affect proposal order/direction only and do not become numerical waypoints.

This replaces full scene-wide portal midpoint selection. The existing B2→B3 conditioned anchor diagnostic demonstrates the issue, but its simple lift-to-higher-top recipe is not a general movement planner. Obstacles may require another portal path, retained as a route alternative.

The ordered `RouteStage` now supplies the missing context for existing helpers:

- Next `support_arrival` gives a landing `goal_spec`; an open passage gives a finite region/portal goal. Extend the comparison-goal schema explicitly for zero-thickness portals instead of inventing epsilon-width boxes.
- `probe_spatial`/landing validation measure actual achieved arrivals and return the actual selected program. Different spatial routes can have different intermediate goals; do not compare their local times as if they were the same event. Compare completed routes by the true terminal objective.
- Build guide segments from stage-conditioned anchors and reuse `horizontal_segments`/`checkpoint` for a common outgoing direction. Progress is ordered by stage occurrence, not global nearest-point projection.
- `plan_windows` remains available when real local decision ranges/readiness/dependencies are known. Spatial stage selection needs no authored `[10,28,44,55]` clocks. Time comparisons use the shared stage goal; clock/progress diagnostics require compatible context.
- Local choices can vary runs, launch times, facings and contact methods consistent with the selected spatial itinerary. If a new support visit or different passage/layer is required, emit a different route candidate; do not silently broaden the current corridor.

For Real11 follow the imported virtual finish: the descending **height7 clock**, player-center **X<729.7 and Z>=−1095.3**, minimum achieved tick first and maximum Z at that tick second (`candidate_rank`). Use `terminal_goals.height_clock`, `bind_goal` and `satisfied`. There is no new lower X bound, upper Z bound or artificial landing floor. The free-air endpoint may be below Y7 and airborne; the physical B7-contact alternative has its actual finite collision/contact rows. These are distinct contact itineraries under one task objective, not duplicate requests merely because their jump inputs and tick agree. Ordinary finite-support goals remain supported as separate generic tests.

The canonical baseline is `experiments/virtual-finish/fixture.json`, with a200-input search guard. Its runner derives finite horizontal collision-query coverage from a conservative reach/horizon envelope without adding terrain. Do not let that envelope's edges or midpoint become route/goal constraints or local anchor coordinates. Keep the historical state64 and literal-finite-B7 interpretation source-specific; they are not the current Real11 terminal requirement.

## 7. File-level changes and reuse

| Location | Concrete work |
| --- | --- |
| `cell_routes/geometry.py` | Keep AABB subtraction/merging and exact strata; add support-aligned local refinement and regenerate portal/contact incidence with provenance |
| New `cell_routes/regions.py` | Canonical exposed support components; volume/surface route regions; typed directed links; conservative movement annotations using current helpers |
| `cell_routes/routes.py` | `Corridor` v2/stages; graph enumeration, semantic keys and conservative compression; anchors separated from route membership |
| New `cell_routes/route_progress.py` | Shared 3D phase trace, portal/contact consumption and stage/variant cursor |
| `cell_routes/local.py` | Consume stage graph; early candidate conditioning; scoped 3D guards/audits; preserve alternative paths and diagnostic evidence |
| `cell_routes/corridor_guards.py` | Generalize connected axis-sweep guards using the actual selected stage cover and fixed scalar Y |
| `cell_routes/checkpoints.py`, `comparison_goals.py`, `terminal_goals.py` | Goals/directions from ordered route stages; explicit finite portal goal; reuse arrival evaluator and virtual-finish adapter; leave former global peer-retention inactive |
| `interactions.py`, `movement_generation`, `open_movement`, stepping/ceiling builders, `block_search` | Reuse existing early eligibility and physical profile/reach helpers; condition on selected spatial stages before expensive products; retain probe uncertainty and matching scope |
| `constraint_variants`, `movement_composition`, `solve_spec` | Reuse fixed branch assembly and ownership; add route context to identity; preserve stage/contact requirements during truncation |
| `cell_routes/depth_first.py`, `checkpoint_search.py`, `movement_frontier.py` | Extend existing DFS and its schema5 frames with the route queue/stage goals; retain child promotion, accepted variants, unknowns, active/suspended/refinement ownership and resume |
| `cell_routes/minimum_runs.py` and its frame context | Preserve existing latest-jump rule; keep route/contact distinctions and local count semantics when adding new owned rows; update policy/context version only when semantics change |
| Experiment/CLI/report writers | Automatic task input path with no supplied intermediate support order/guide/clocks; keep virtual/finite goals, exact models/validation ledgers, actual region/portal dumps and source-specific controls |

No mod source or continuous-worker change is required. The solver/orchestration fixes are already imported through `61355976`; use their published modules and tests as the baseline. The source checkout's new retry files remain outside this integration. Do not start that retry as a side task or rebuild its active shared Java helpers.

## 8. Milestones and acceptance gates

Each milestone is a feature implementation boundary with its tests, fixtures and docs bundled. This planning turn makes no commit.

1. **Semantic regions.** Implement/refine finite support components and typed surface/volume regions. Pass existing geometry coverage/boundary tests plus equivalent-box-split, overlapping support, covered-top and B1 corner-union controls. Show real region rectangles/portals, not only guide lines. Done when every usable top patch has a distinct surface node and all residual air/boundary space is retained.
2. **Route identity and enumeration.** Replace horizontal signatures with the typed graph/stage key. Small exhaustive fixtures must retain both support branches, both obstacle sides, over/under passages and contact versus overflight. Timing changes stay in one route; meaningful revisits stay distinct; subdivision changes do not invent alternatives. Done when reference routes map automatically without supplied order/waypoints and duplicate raw walks cannot consume the semantic route budget.
3. **Shared route progress and audits.** Implement full Y/X/Z mapping and contact-aware stage predicates. Cover multiple portals in one tick, many ticks in one cell, touches/returns, exact contact-only spaces and incompatible-variant splicing. Done when an airborne trace cannot satisfy a landing route, nor can the wrong obstacle/height passage satisfy another corridor with the same projection.
4. **Fixed model integration.** Extend the imported builders/dedup/ownership paths to compile concrete stage/contact/guard branches before the worker. Cover union guards across adjacent cells, scoped preliminary prefixes, required contact preservation, named step/grind uncertainty, interrupted generation/refinement and physically valid alternate routes. Preserve exact request/branch ledgers and matching eligible gate subsets. Done when returned accepted witnesses pass both selected-route and independent full-scene checks; no route choice is delegated to IPOPT.
5. **Guidance and existing-DFS integration.** Derive stage goals/anchors/directions and extend `depth_first.search_goals`, without rebuilding DFS or restoring its old global retention pass. Cover comparable-goal selection, self-hop controls, unknown route retention, late useful siblings, reoptimization of earlier facings, stage changes at turns and resume parity. Preserve learned local-run minima, earlier unknown/equal-run alternatives and distinct free/physical finish patterns. Terminal tests use earliest virtual height-clock completion then equal-tick Z, plus separate ordinary finite-support goal controls.
6. **Cold discovery and coverage audit.** On the existing generic corpus, compare automatic routing with diagnostic prescribed routes under matched budgets. On stored Real11 geometry, verify automatic representation of the historical valid itinerary without its input/timing/facing seed, then compare with the imported canonical virtual-finish fixture and current local-run policy. Full-map coverage claims require exact B6 bounds, but that does not block stored-geometry regressions. Record support/passage families, first valid completion, time/Z, free/physical final contact, exact requests/validation and pending/unknown work. Keep the handoff's corridor-related calls visible and attribute any savings to actual model changes. Do not raise caps, suppress specific calls or start additional continuation-time pruning.

For the small geometry fixtures, enumerate finite route grammars exhaustively and compare family sets. This is more useful than asserting a particular raw cell count. Run translations and X/Z reflections, including cases where the dominant start-goal axis changes, plus extra empty coverage that must not relocate a local support-conditioned guide. Preserve legitimate stepping/ceiling/run-off/overflight controls. Broaden tests to affected standalone/numerical suites only at implementation boundaries; no mod Gradle gate or Minecraft launch is needed.

## 9. Persistence, migration and limits

Version the corridor/region graph and route enumeration state, and deliberately extend/migrate the existing version5 `ordered_goal_dfs` schema/context. Persist semantic route key, compatible path variants, active occurrence/goal, suspended generators, accepted constraint variants, inner repair progress, `minimumRuns` representatives/exclusions and incumbent. Preserve single frame execution ownership. Reject incompatible old continuations; policy-fingerprint differences matter even within schema5. Do not silently read old midplane signatures as new semantic routes. Historical reports retain their source-era interpretation. A diagnostic import may recover geometry from old guides/witnesses, but cannot infer contact stages or cold discovery from a bare horizontal polyline.

Keep current physical assumptions, normal momentum with zero inertia thresholds, existing margins, fixed-discrete-before-IPOPT boundary, current latest-jump run policy and independent scene validation. Do not reactivate old global peer exclusions. Intentional wall hits, expanded ladder/water search, universal reachable-set computation, additional continuation-time pruning and a new objective-bound project remain deferred. Geometry/route feasibility candidates and best-found local solves are not global infeasibility or optimality certificates.

The refactor is complete when **route identity tracks different support and 3D passage choices, raw tessellation and representative points no longer define those choices, and local search optimizes time/movement within an actual automatically discovered corridor**. Anchor improvement alone is not completion of this contract.

## 10. Concrete implementation interfaces and call-site checklist

Names below are proposed APIs to implement, not claims that the functions already exist. Keep them ordinary serializable records/functions in the modules listed above; do not build another solver framework.

### Geometry and graph construction

- Extract reusable contact/incidence construction from `decompose`: normalized expanded solids → free cuboids/boundary strata → exposed support patches → local support-footprint refinement → final cell/portal/contact incidence. Do not build two independent collision geometry implementations. Retain parent-cell/physical-patch provenance through refinement.
- `regions.build_route_graph(scene, decomposition, task_goal)` returns versioned region/transition records, exposed support components, raw-incidence references and unresolved movement capability annotations. Surface nodes are not positive-volume obstacles or artificial floors.
- Canonical rectangle-union components use physical owner/plane plus exact union geometry. Never replace a concave or disconnected footprint by its bounding rectangle. Zero-width contact strata remain separate supported cases.
- `routes.enumerate_routes(..., route_graph=..., continuation=...)` owns the bounded spatial frontier and semantic route dedup. Preserve the existing explicit route/expansion/depth/visit limits and expose them through the runner; do not silently widen them. Return route count, cursor and omissions separately from raw walk count.
- Store a transfer's last physical interaction and conservative allowed movement families. Ordinary air portals do not start new jumps. Reuse normal/run-off/step/ceiling profile/reach primitives; when no complete method catalogue/context is available, retain `unknown` rather than turn missing generation into a proof. Unknown capability may affect proposal order but is not irreversible exclusion.

### Typed progress and accepted language

`route_progress.advance(route, cursor, phase_segments, contact_events, scope)` returns possible next cursors, achieved semantic events, pending requirements and a three-way result: `compatible`, `ruled_out`, or `unknown`. Cursors include stage occurrence and path variant, so revisiting the same raw region is not mistaken for previous progress.

For each ordinary Y/X/Z segment, obtain ordered parameter intervals from `segment_intervals`, intersect actual finite portal patches, and propagate incidence-consistent transitions through the route's allowed subgraph. Handle shared-face occupancy by retaining compatible alternatives. Consume physical support events at the actual collision phase and require all significant actual support visits to be represented. Consecutive standing inputs do not create new progression visits. No stage can be skipped merely because a later cell also contains the endpoint.

**Named interaction caveat:** `horizontal_trace` reconstructs ordinary Y/X/Z movement from realized endpoints; it does not expose every internal named step/grind collision probe. Do not present that trace as a complete probe trace or reject a valid case from its realized Y alone. Reuse the imported uncertain probe envelopes and case/scene validation. An owned interaction region must cover the relevant probe envelope, or the classifier must retain unknown and avoid a hard exclusion/certified achievement. Verify this on the five-input grind/blip regression. If exact additional scene trace is actually required to resolve a semantic stage, first inspect existing checker diagnostics; extend shared instrumentation only as a separate justified change, with applicable root/module guidance and checks. Do not invent probe physics in Python. A terminal route cannot claim a required unresolved passage was validated merely because physical collision replay passed.

When two raw paths share a semantic key, keep their actual adjacency/variant language. Do not independently union their entry, internal and exit regions, because that can permit an unobserved path assembled from incompatible pieces. Initial compression should be conservative; uncertain equivalence can retain duplicates with diagnostics until a generic regression justifies merging.

### Existing DFS: exact extension points

Add a route-stage goal source to `search_goals`; retain its existing execution loop. A small interface such as `goal_for(cursor)`, `is_final(cursor)`, `advance_goal(cursor, achievement)` replaces its fixed `goal_list` assumptions. In particular inspect/update all of these current sites together:

1. Initial `goal_list` normalization and `frame(index, parents, parent)` creation.
2. `terminal(f)` / `final_frame(f)`, currently based on global list length.
3. `configure`, `eligible(program,f)` and `screen(program,f)` for generation. The adapter/gate must receive the active frame's immutable route-stage view. Do not mutate one shared adapter so resuming another frame inherits the wrong corridor scope.
4. `generate(f)`: direct virtual-finish attempts, normal terminal continuation, persistent generator cursor, `stop_on_goal`, `terminal_goal` and any checkpoint projection.
5. `score(f,...)`: frame-specific adapter, required landing/portal/virtual goal, first-witness policy, accepted variants, arrival extraction and `commit_arrival` promotion.
6. Initial root-stage skipping, choice/child-frame advancement, refinement/suspension/backtrack queues, terminal incumbent detection and return/report `goalType` fields.
7. Schema/context fingerprint, `goal_key` and minimum-run pattern boundaries. Preserve the existing per-frame `minimumRuns`; distinct route/contact patterns cannot share a representative just because their counts match.

Keep current initial-domain0/8 controls explicit where configured; removing `SupportOrder` from the automatic path must not silently remove a separately configured input-domain restriction. Do not hardcode those values into graph/DFS code.

**Outstanding-contact check:** derive only actual remaining physical support visits from the selected route prefix. When those are exhausted and the task is a free virtual finish, do not fall back to `[scene['goalBlock']]` as a mandatory physical B7 landing. The virtual terminal predicate handles that completion. Retain the generic finite-support fallback for tasks that actually require a physical goal. This must have a regression independent of Real11.

### Task entry point and compatibility

The automatic task entry accepts unordered `request` geometry/start/goal, an optional normalized `task_goal` (default ordinary finite-support goal), and explicit existing spatial/search budgets. `find_checkpoint_route` may add `task_goal`/spatial-settings parameters while retaining current callers. Its automatic path builds the graph/routes and passes stage goals into existing DFS. A supplied `Corridor` v2 runs that same path with spatial discovery already done.

The new automatic runner must not require `spatialPoints`, `requiredSupports`, `comparisonTicks` or a checkpoint-to-block list. Preserve optional initial-input-domain settings independently. Keep the canonical supplied virtual-finish fixture as an unchanged matched control; author a new automatic fixture with the same physical inputs/goal/policy and recorded explicit budgets. Do not edit archived fixtures to make them appear automatic.

Legacy prescribed point routes are explicit diagnostics. A bare polyline can establish geometric passage coverage but cannot certify a contact itinerary; require contact annotations or a validated witness when importing mandatory support stages. No hidden legacy production fallback or feature-flag rollout is required.

Target versioning for the new implementation: region graph schema1, semantic corridor schema2, route-stage DFS schema6. These are future schema versions; current schema5 stays the imported baseline until the feature changes its semantics. Include terminal goal, route key, stage language/geometry, physical scope, generation domain, minimum-run policy version and initial roots in compatibility identities. Reject old raw state with an explanatory message; do not guess a lossless migration from a horizontal signature.

## 11. First implementation slice and test matrix

Begin after compaction with milestone1, not another Real11 solve or another design restart. The first slice is:

1. Add rectangle-union canonicalization and exposed support-component extraction in `regions.py`, using existing geometry/provenance.
2. Extract contact/incidence helpers from `geometry.py` and add local support-footprint refinement while preserving all air/boundary regions.
3. Build typed volume/surface regions and open/support-arrival/departure links. Preserve existing public `Cell`/`Portal` usages or update affected callers in the same feature.
4. Add focused generic tests, diagnostic dumps/rectangles and navigation entries. Demonstrate that overflight and support contact can be represented separately before changing enumeration/DFS.
5. Run the existing geometry/route tests plus the new semantic-region tests. Commit this feature with its tests/docs/fixtures, then continue the remaining milestones. Do not end the overall implementation at milestone1 or describe the whole refactor as shipped.

Proposed cohesive test modules are `test_route_regions`, `test_semantic_routes` and `test_route_progress`; extend existing tests when that avoids duplication. They are proposed names until implemented. Minimum required cases:

| Layer | Cases / assertions |
| --- | --- |
| Geometry | Equivalent whole/split/reordered boxes; exposed versus covered top; finite footprint versus broad air; corner unions; exact body-height boundary passage; local refinement preserves coverage and physical ownership |
| Route identity | Alternative supports; same-X/Z contact versus overflight; under versus over; both obstacle sides; parallel passages with common endpoints; equivalent tessellation; same-itinerary timing changes; meaningful revisits and local setup hops |
| Enumeration | No jump reset at air portals; unsupported ascent not called feasible; incomplete catalogue remains unknown; distinct semantic families rather than duplicate raw walks consume route budget; interrupted enumeration resumes without losing a family |
| Progress | Multiple portals in one input; many inputs in one cell; touch-and-return; boundary ambiguity; actual extra contact not ignored; required support not skipped; incompatible-variant splicing rejected; named step/grind uncertainty not falsely excluded |
| Guards / ownership | Connected union across adjacent cells; scoped future omission; selected side/layer enforced before the worker; required contact rows preserved; removed future approach rows do not survive truncation/promotion |
| DFS / policies | Stage-derived goals and direction; one execution owner per frame; child program adopted; earlier facings reoptimized; late useful sibling; route-stage resume parity; local-run minima/unknown/equal-run/final-contact distinctions unchanged |
| Terminal | Virtual downward clock with no fake floor/opposite bounds; physical and free alternatives both retained; ordinary finite-support tasks still require actual contact; time-first and equal-time Z ranking; no accidental physical-B7 outstanding requirement |
| Metamorphic / evidence | X/Z reflections/translations and dominant-axis changes; extra empty coverage not relocating local anchors; known witness classifiable without seeding cold search; exact old/new request and route/scene validation records |

The generic suite gates each milestone. Stored-geometry Real11 coverage and matched-control comparisons follow those gates; unknown B6 extension blocks only full-map claims, not the generic implementation or stored-geometry checks. Numerical retries/caps are not certificates. If a route/scene/model discrepancy is found, preserve its complete request and progress trace and fix the generic representation rather than adding fixture IDs or suppressing calls.
