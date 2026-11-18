# Architecture and execution flow

Read [current state](CURRENT_STATE.md) before assuming a planned component already exists. The [execution reference](ANCHOR_SEARCH_EXECUTION_REFERENCE.md) supplies current combination/pruning/IPOPT tables and the reasons behind each policy. [Code map](CODE_MAP.md) identifies modules; [function index](FUNCTION_INDEX.md) points to definitions. The production package is installed from `src/parkour_solver`; imports and worker launch are independent of the working directory. See [layout](LAYOUT.md) for domain ownership and [runbook](RUNBOOK.md) for entrypoints.

The current physical automatic backend uses [anchor DFS](design/ANCHOR_POLICY_DECISIONS.md): caller-declared required anchors, or one open span when absent. Geometry retains physical contact/passage alternatives; backward soft guide objectives and the existing movement/constraint/native pipeline decide feasibility. Inter-block returns are excluded while repeated current-owner jumps remain generated. Virtual tasks and explicit supplied corridors retain their source-specific event/typed/raw-walk semantics as controls.

## The three layers

1. **Geometry/spatial route:** `scene.normalize.prepare_scene` normalizes supplied physical AABBs and the original state. `spatial.cells.decompose` builds free player-configuration cells, portals and contact patches. `event_routes.build_route_domain` and `build_event_graph` retain raw incidence and support provenance. `AnchorAdapter` chooses required milestones; `anchor_guides` builds backward finite guides and binary approach-edge labels. Virtual-task controls use lazy next-goal queries and `guides.guide_for`. Explicit supplied controls use `corridor_for_points` or typed corridors. A cell is a spatial region, not a tick; a guide point is not a solver wall. The graph alone does not determine dynamics, contact schedule or travel time.
2. **Discrete program:** `movement.generation.generate_choices`, `movement.normal`, stepping and ceiling generators compose grounded/air/jump/contact choices. `movement.program.append_choice` appends choices to an original-state program and traces scalar vertical history. `compile_program` converts it to the existing route/profile/schedule interface. `SupportOrder` in `search.compat.support_order` is an explicit supplied-order restriction, not a universal automatic block order.
3. **Continuous solve and validation:** `bridge.compiler.JavaCompiler` obtains movement coefficients using shared Java code. The orchestrator chooses actual rows/contact alternatives and calls `numerics.solver.solve`. A child worker optimizes continuous facings; parent scalar validation independently checks each witness. Scene, vertical, corridor and eventual executable-angle checks add distinct acceptance layers.

`search.dfs` owns comparison frames, goal-based arrival ordering, immediate descent, backtracking and versioned persistence (physical automatic8, virtual automatic7, supplied typed6, supplied legacy5). `reachability.service.InteractionGate` shares fixed-program preparation, canonical compiler payloads and exact-context envelopes/screens; conditioned case prerequisites run before wall/corner fan-out. `movement.generation.propose_choices` owns common composition and stable consumed raw ordinals. Earlier disk/SOCP guide experiments under `research.route_guidance` are separate. No disk control replaces the current fixed-magnitude facing model in the selected DFS work.

## Anchor decisions and solves

The outer automatic state is schema2 for physical tasks. Each frame owns one required-anchor/final goal; optional contacts are internal movement choices. Movement context4 keeps node/parent IDs, completed/last-solved decision depth, queued programs and an exact selected `nodeInFlight`. Constructing a sibling does not dispatch a query. Selected meaningful nodes use guided early-stop feasibility; raw run-holds prepare the next completed decision. Anchor/final queries are mandatory. An internal node omits the pending anchor's acceptance/region predicates while retaining its actual physical history and prior anchors.

`node_feasibility` distinguishes exact oracle rejection, repair exhaustion, suspension/error and feasible evidence. A failed child never assigns a status to an unqueried ancestor. Every descendant starts at the original state with all earlier facings free. Feasible rows/selected contacts and yaw seeds survive; endpoint/velocity/facing equalities are not introduced. Objective/stop/rank/oracle intent and exact validation scope are repair compatibility data (assembly10).

Global hard-jump latches live outside frames and survive backtracking. Policy3 excludes stepping in the actual hard transfer and counts one transfer jump after the last source contact; preceding same-source setup hops remain eligible and remain included in J. This policy version is fingerprinted across exact continuations. Slower comparable hard-anchor alternatives defer only without more J or fewer R (ordinary) / fewer A (turnaround). They keep their actual programs/ownership and restore one at a time on explicit failed-continuation recovery. `search.comparison` owns the predicates; terminal ranking remains minT then objective. Conditional flight minima consider ceilings under the declared no-stepping hard-transfer assumption, and combine with phase-correct horizontal time using max. Checks apply only to new/selected work against one incumbent. The binary turn label selects local-span T/−J/R or T/−J/A consistently. `observability.turns` stores compact referenced geometry/decision/query/replay records, independently of solver allocation. A later moved witness can log a label flip without changing queued keys or adding a pose constraint.

## Main functions and their contracts

| Function / class | Responsibility and important boundary |
| --- | --- |
| `prepare_scene` in `scene.normalize` | Normalize scene, solids, supports and input/search limits. Keep physical boxes distinct from support rectangles. |
| `decompose` in `spatial.cells` | Build player-space arrangement with obstacle-dependent height changes. Preserve free-space unions and openings; artificial cell seams are not physical obstacles. |
| `landmarks`, `corridor_for_points`, `Corridor` in `spatial.compat.corridors` | Derive directional/topological landmarks and corridor/projection. Optimized coordinates need not pass exactly through guide points. |
| `empty_program`, `append_choice`, `boundary_state` in `movement.program` | Maintain discrete program and scalar phase history. Boundary scalar state informs next choices; it does not freeze optimized horizontal state. |
| `truncate_program` in `movement.program` | Project a proposal to a partial horizon. Generated row ownership removes uncommitted future prerequisites. Automatic anchor proposals check their owned arrival independently; a rejected unused tail does not erase it. Legacy checkpoint projections retain full-first validation. |
| `compile_program` in `movement.program` | Produce route/profiles for existing compilation/constraint machinery; `require_complete` distinguishes partial coverage. |
| `persistent_programs` in `search.frontier` | Serialize symbolic generation queue, candidates, cursor and exclusions. Uses the injected shared span T/J/R-or-A key in anchor mode, with legacy processing-key fallback; the outer traversal is goal-aware DFS. |
| `screen_program` in `search.movement` | Necessary-condition checks for a discrete proposal; numerical unknown is not proof of death. The open-family eligibility gate and full-proposal screen share the reachability query. |
| `solve_scope`, `solve_identity`, `validation_prefix` in `search.identity` | Scope geometry/checks, identify exact physical/context problem and align validation. Identity changes when actual model/scope changes. |
| `reachable_envelopes`, `interaction_reachability` in `reachability.intervals` | Conservative p/v and collision-phase bounds; answer ruled out or not ruled out. The latter does not prove an interaction feasible. |
| `ConstraintVariants` in `constraints.variants` | Choose concrete phase covers and propagate accumulated rows before preparing downstream domains; one shared builder serves upfront generation and collision repair, with a durable lazy frontier. |
| `evaluate_program` in `search.engine` | Compile program; validate scalar vertical schedule; scope adapter; obtain coefficients/reach bounds; select fixed alternatives; call contact solver; record physical acceptance. Partial/checkpoint success is not a terminal route. |
| `solve_contact_branches` in `search.repair` | Inner search for concrete geometry/contact/constraint alternatives of one program. Saves repair state, lazy collision-repair sources, logs, guesses and accepted candidate. “Repair” does not mean IPOPT chooses discrete decisions. |
| `probe_spatial` in `search.goals` | Seek validated arrival at a common landing/region goal, possibly via local choices. Observed arrival is a feasible time measurement, not proof of earliest arrival. |
| `retain_two_goals`, `protections` in `search.compat.time_progress` | Existing time/progress/state helpers; the current DFS does not run their former global peer-retention pass. |
| `find_checkpoint_route` in `search.engine` | Validates/setup scene, corridor and scope, then delegates outer traversal to `depth_first.search_goals`. |
| `cli.fixed_clock.run` | Run fixed-clock or virtual-height-goal fixtures, preserving physical collision geometry and independent replay. Virtual mode derives finite query coverage from the horizon without adding goal terrain. CLI writes progress atomically. |
| `numerics.model.prepare`, `validation.numerical.validate` | Normalize fixed request; scalar replay independently validates facings. Arrays have n inputs and n+1 states. |
| `numerics.solver.solve` | Worker lifecycle, deadline, cancellation, protocol, incumbent retention and independent validation. |
| `numerics.worker` / `continuation.run` | CasADi/IPOPT phases and optional inertia continuation for supported fixed problems. Current decision research explicitly requests threshold zero. |

## One numerical request

`ConstraintVariants` builds meaningful collision-phase covers within selected landing
segments. It conditions conservative envelopes on each concrete choice before
preparing downstream domains; all applicable obstacles still share the full
numerical model. Known support-entry geometry, already-owned clear-side bridges
and named-probe uncertainty remain distinct. Upfront and collision repair use the
same builder. Switch counts are descriptive; local repair/retention caps are
removed. See [the avoidance contract](design/AVOIDANCE_PASSAGES.md).

Physical anchor search gives its DFS the remaining session deadline and global
solver-call allowance. It does not impose service-turn or per-program time/call
quotas: `score_seconds`, `score_call_limit` and `final_call_limit` apply only to
non-anchor search. Each numerical request still uses `candidate_seconds`.
Progress callbacks, cancellation, generation limits and external continuation
remain active. Repair checks the budget before taking a pending branch, including
after generation, so unstarted work remains pending rather than becoming an
unknown numerical result. See the [run-budget change](../research/anchor-run-budget/README.md).

`compile_program` → `scene.route.schedule` → `validate_vertical_schedule` → Java coefficients → scoped reach envelopes → `ConstraintVariants` → fixed walls/contacts/inputs → `solve_contact_branches` → worker → scalar validation → vertical check → `CorridorAdapter.candidate` (scoped scene/corridor checks) → retention or terminal incumbent.

Re-solves use the original horizontal initial state and free earlier yaw variables. Saved witnesses can initialize yaw, not become seam constraints. Multiple numerical requests for one jump schedule can select different hard obstacle-face/tick/contact constraints. Distinct descriptions may also generate identical effective models; early partial-model canonicalization addresses that before expensive work.

## Persistence and identity

The **outer search** selects discrete programs; `searchState` contains generated, deferred, active and terminal work. The **inner repair search** explores fixed constraints for one program; `repairState` includes pending/seen/unresolved/inFlight/completed, accepted results, guesses, constraint generator state and logs. Saving only programs or retained pools loses progress. Saving inner repair state alone loses ungenerated programs.

Resume requires matching model/context identity, including horizon, goal/objective, scope, geometry, inputs, contacts and coefficients. A longer child is a new numerical problem, not an exact continuation of its shorter parent's raw queue. Existing code retains compatible facing guesses and preserves old work under its own identity. IPOPT internal iterations/Hessians are not serialized.

The DFS uses schema5 for legacy supplied comparison frames and schema6 for typed route stages, without a global window barrier. The generic event/virtual outer state uses schema1; physical anchor mode uses schema2/DFS8/movement context4/repair assembly10; route-stage/commit/proposal/allocation policies participate in continuation identity. `acceptedVariants` in inner repair state preserves accepted fixed-constraint alternatives separately from the local best objective. A frame has one execution owner among active, suspended and deferred-refinement queues. Do not call an old checkpoint queue an exact DFS continuation without explicit schema conversion.

## Automatic event guidance

No-corridor `checkpoint_search.find_checkpoint_route` dispatches to `event_search.search_event_routes`. One immutable content-checked `RouteDomain` stores the raw covering graph, contacts, owner provenance, surface metadata and explicit unknown query enclosure. It does not require overlapping-box expansion. `event_routes.next_goals` performs resumable silent closure without treating an overflown support as a required contact, and can represent cyclic revisits plus residual language in its generic domain. Current physical `AnchorAdapter` generation still excludes departed-owner returns; graph representation is not active revisit generation.

For virtual-task controls, `EventGuideAdapter` supplies dynamic next-contact/task requests to the existing `depth_first.search_goals`. It does not inherit `CorridorAdapter` or its box/projection/prefix filters. A compatible actual prefix owns future alternatives before they split. Same-support setup remains in local movement construction; it adds no mandatory comparison. A newly committed child has an atomic active or pending next-goal owner; stalled owners can yield without copying decisions between siblings. Scoring contexts distinguish objective meanings and repair state.

`guides.guide_for` obtains finite local interface anchors and checked geometric witness segments. The witness remains a preference. Physical avoidance/contact/case rows and blockage caps remain hard. A selected exposed support region can impose a finite union of contact footprints at the existing physical landing phase; it imposes no every-input air-cell containment. Required events and full scene/task validation are separate from optional guide classification.

`constraints/encounters.py` prepares one encounter per obstacle cluster (`spatial/footprints.py`: touching solids, sides are the maximal free rectangles around the members active at the player's height) and landing segment; `ConstraintVariants` persists its lazy frontier of conditioned alternatives (constraint policy9/state4). Shared role/window queries distinguish unwanted interior absence from required contact/prerequisite rejection and preserve named-probe uncertainty. Numeric row aliases retain resource/provenance alternatives. Exact context keys prevent borrowing scoped proofs or repair cursors under a changed objective/model.

Physical automatic state is `event_route_search` schema2 / DFS8, event-stage policy4 and movement context4, with one domain/graph/guide table and declared-anchor/open-span policy. Virtual-task controls use schema1 / DFS7 and event-stage policy3. `discoverySettings` version1 has per-session expansion/materialization quotas and residual cursors. Old automatic `spatialSettings`/cell-walk states require their source-era runner. Supplied typed schema2 corridors/schema6 DFS and legacy schema5 controls remain available. All modes fingerprint shared constraint policy8/state3 and retain allocation normalization; physical meaningful-node service adds versioned movement context4.

The earlier `route_queue`, `SemanticCorridorAdapter`, `RouteGuards` and ordered-cell `route_progress` remain explicit legacy/control code and historical evidence tools. Their raw-walk contract is not used by the new automatic path. Neither backend proves global dynamic reachability or optimal time.
