# Architecture and execution flow

Read [current state](CURRENT_STATE.md) before assuming a planned component already exists. [Code map](CODE_MAP.md) identifies modules; [function index](FUNCTION_INDEX.md) points to definitions. Python packages are imported from the physical repository root.

The current physical automatic backend uses [anchor DFS](../autonomy/ANCHOR_FEASIBILITY_IMPLEMENTATION_PLAN.md): caller-declared required anchors, or one open span when absent. Geometry retains physical contact/passage alternatives; backward soft guide objectives and the existing movement/constraint/native pipeline decide feasibility. Inter-block returns are excluded while repeated current-owner jumps remain generated. Virtual tasks and explicit supplied corridors retain their source-specific event/typed/raw-walk semantics as controls.

## The three layers

1. **Geometry/spatial route:** `block_search.prepare_scene` normalizes supplied physical AABBs and the original state. `cell_routes.geometry.decompose` builds free player-configuration cells, portals and contact patches. `event_routes.build_route_domain` and `build_event_graph` retain raw incidence and support provenance. `AnchorAdapter` chooses required milestones; `anchor_guides` builds backward finite guides and binary approach-edge labels. Virtual-task controls use lazy next-goal queries and `guides.guide_for`. Explicit supplied controls use `corridor_for_points` or typed corridors. A cell is a spatial region, not a tick; a guide point is not a solver wall. The graph alone does not determine dynamics, contact schedule or travel time.
2. **Discrete program:** `movement_generation.generate_choices`, `open_movement`, stepping and ceiling generators compose grounded/air/jump/contact choices. `movement_composition.append_choice` appends choices to an original-state program and traces scalar vertical history. `compile_program` converts it to the existing route/profile/schedule interface. `SupportOrder` is an explicit experimental route restriction, not a universal automatic block order.
3. **Continuous solve and validation:** `compiler.JavaCompiler` obtains movement coefficients using shared Java code. The orchestrator chooses actual rows/contact alternatives and calls `ipopt.solver.solve`. A child worker optimizes continuous facings; parent scalar validation independently checks each witness. Scene, vertical, corridor and eventual executable-angle checks add distinct acceptance layers.

`cell_routes.depth_first` owns comparison frames, goal-based arrival ordering, immediate descent, backtracking and versioned persistence (physical automatic8, virtual automatic7, supplied typed6, supplied legacy5). `interactions.InteractionGate` adds compiled approach/required-contact queries before builders. Earlier disk/SOCP guide experiments under `route_guidance` are separate. No disk control replaces the current fixed-magnitude facing model in the selected DFS work.

## Anchor decisions and solves

The outer automatic state is schema2 for physical tasks. Each frame owns one required-anchor/final goal; optional contacts are internal movement choices. Movement context4 keeps node/parent IDs, completed/last-solved decision depth, queued programs and an exact selected `nodeInFlight`. Constructing a sibling does not dispatch a query. Selected meaningful nodes use guided early-stop feasibility; raw run-holds prepare the next completed decision. Anchor/final queries are mandatory. An internal node omits the pending anchor's acceptance/region predicates while retaining its actual physical history and prior anchors.

`node_feasibility` distinguishes exact oracle rejection, repair exhaustion, suspension/error and feasible evidence. A failed child never assigns a status to an unqueried ancestor. Every descendant starts at the original state with all earlier facings free. Feasible rows/selected contacts and yaw seeds survive; endpoint/velocity/facing equalities are not introduced. Objective/stop/rank/oracle intent and exact validation scope are repair compatibility data (assembly9).

Global hard-jump latches live outside frames and survive backtracking. Extra matching ordinary run alternatives keep their actual programs/ownership and are restored at the original frame with a scoped bypass. The binary turn label selects local-span T/−J/R or T/−J/A consistently. `turn_observability` stores compact referenced geometry/decision/query/replay records, independently of solver allocation. A later moved witness can log a label flip without changing queued keys or adding a pose constraint.

## Main functions and their contracts

| Function / class | Responsibility and important boundary |
| --- | --- |
| `prepare_scene` in `block_search` | Normalize scene, solids, supports and input/search limits. Keep physical boxes distinct from support rectangles. |
| `decompose` in `cell_routes.geometry` | Build player-space arrangement with obstacle-dependent height changes. Preserve free-space unions and openings; artificial cell seams are not physical obstacles. |
| `landmarks`, `corridor_for_points`, `Corridor` in `cell_routes.routes` | Derive directional/topological landmarks and corridor/projection. Optimized coordinates need not pass exactly through guide points. |
| `empty_program`, `append_choice`, `boundary_state` in `movement_composition` | Maintain discrete program and scalar phase history. Boundary scalar state informs next choices; it does not freeze optimized horizontal state. |
| `truncate_program` in `movement_composition` | Project a proposal to a partial horizon. Screens full proposals first; generated row ownership removes uncommitted future prerequisites. |
| `compile_program` in `movement_composition` | Produce route/profiles for existing compilation/constraint machinery; `require_complete` distinguishes partial coverage. |
| `persistent_programs` in `movement_frontier` | Serialize symbolic generation queue, candidates, cursor and exclusions. Its local generator priority uses prefix length; the outer traversal is goal-aware DFS. |
| `screen_program` in `movement_search` | Necessary-condition checks for a discrete proposal; numerical unknown is not proof of death. The open-family eligibility gate and full-proposal screen share the reachability query. |
| `solve_scope`, `solve_identity`, `validation_prefix` in `solve_spec` | Scope geometry/checks, identify exact physical/context problem and align validation. Identity changes when actual model/scope changes. |
| `reachable_envelopes`, `interaction_reachability` in `reachability` | Conservative p/v and collision-phase bounds; answer ruled out or not ruled out. The latter does not prove an interaction feasible. |
| `ConstraintVariants` in `constraint_variants` | Enumerate fixed effective constraints before solving; restartable raw cursor and dedup state. Memoizes equivalent per-obstacle nodes/options and partial products before expensive leaf refinement. |
| `evaluate_program` in `cell_routes.checkpoint_search` | Compile program; validate scalar vertical schedule; scope adapter; obtain coefficients/reach bounds; select fixed alternatives; call contact solver; record physical acceptance. Partial/checkpoint success is not a terminal route. |
| `solve_contact_branches` in `block_contacts` | Inner search for concrete geometry/contact/constraint alternatives of one program. Saves repair state, logs, guesses and accepted candidate. “Repair” does not mean IPOPT chooses discrete decisions. |
| `probe_spatial` in `cell_routes.comparison_goals` | Seek validated arrival at a common landing/region goal, possibly via local choices. Observed arrival is a feasible time measurement, not proof of earliest arrival. |
| `retain_two_goals`, `protections` in `cell_routes.time_progress` | Existing time/progress/state helpers; the current DFS does not run their former global peer-retention pass. |
| `find_checkpoint_route` in `cell_routes.checkpoint_search` | Validates/setup scene, corridor and scope, then delegates outer traversal to `depth_first.search_goals`. |
| `fixed_clock_experiment.run` | Run fixed-clock or virtual-height-goal fixtures, preserving physical collision geometry and independent replay. Virtual mode derives finite query coverage from the horizon without adding goal terrain. CLI writes progress atomically. |
| `ipopt.prepare`, `ipopt.validate` | Normalize fixed request; scalar replay independently validates facings. Arrays have n inputs and n+1 states. |
| `ipopt.solver.solve` | Worker lifecycle, deadline, cancellation, protocol, incumbent retention and independent validation. |
| `ipopt.worker` / `continuation.run` | CasADi/IPOPT phases and optional inertia continuation for supported fixed problems. Current decision research explicitly requests threshold zero. |

## One numerical request

`compile_program` → `route.schedule` → `validate_vertical_schedule` → Java coefficients → scoped reach envelopes → `ConstraintVariants` → fixed walls/contacts/inputs → `solve_contact_branches` → worker → scalar validation → vertical check → `CorridorAdapter.candidate` (scoped scene/corridor checks) → retention or terminal incumbent.

Re-solves use the original horizontal initial state and free earlier yaw variables. Saved witnesses can initialize yaw, not become seam constraints. Multiple numerical requests for one jump schedule can select different hard obstacle-face/tick/contact constraints. Distinct descriptions may also generate identical effective models; early partial-model canonicalization addresses that before expensive work.

## Persistence and identity

The **outer search** selects discrete programs; `searchState` contains generated, deferred, active and terminal work. The **inner repair search** explores fixed constraints for one program; `repairState` includes pending/seen/unresolved/inFlight/completed, accepted results, guesses, constraint generator state and logs. Saving only programs or retained pools loses progress. Saving inner repair state alone loses ungenerated programs.

Resume requires matching model/context identity, including horizon, goal/objective, scope, geometry, inputs, contacts and coefficients. A longer child is a new numerical problem, not an exact continuation of its shorter parent's raw queue. Existing code retains compatible facing guesses and preserves old work under its own identity. IPOPT internal iterations/Hessians are not serialized.

The DFS uses schema5 for legacy supplied comparison frames and schema6 for typed route stages, without a global window barrier. The automatic outer queue uses schema1; route-stage/commit/proposal/allocation policies participate in continuation identity. `acceptedVariants` in inner repair state preserves accepted fixed-constraint alternatives separately from the local best objective. A frame has one execution owner among active, suspended and deferred-refinement queues. Do not call an old checkpoint queue an exact DFS continuation without explicit schema conversion.

## Automatic event guidance

No-corridor `checkpoint_search.find_checkpoint_route` dispatches to `event_search.search_event_routes`. One immutable content-checked `RouteDomain` stores the raw covering graph, contacts, owner provenance, surface metadata and explicit unknown query enclosure. It does not require overlapping-box expansion. `event_routes.next_goals` performs resumable silent closure without treating an overflown support as a required contact, and retains cyclic revisits plus residual language.

For virtual-task controls, `EventGuideAdapter` supplies dynamic next-contact/task requests to the existing `depth_first.search_goals`. It does not inherit `CorridorAdapter` or its box/projection/prefix filters. A compatible actual prefix owns future alternatives before they split. Same-support setup remains in local movement construction; it adds no mandatory comparison. A newly committed child has an atomic active or pending next-goal owner; stalled owners can yield without copying decisions between siblings. Scoring contexts distinguish objective meanings and repair state.

`guides.guide_for` obtains finite local interface anchors and checked geometric witness segments. The witness remains a preference. Physical avoidance/contact/case rows and blockage caps remain hard. A selected exposed support region can impose a finite union of contact footprints at the existing physical landing phase; it imposes no every-input air-cell containment. Required events and full scene/task validation are separate from optional guide classification.

`ConstraintVariants` persists phase scans, masks, chain-cost preparation and products (state2/context7). Shared role/window queries distinguish unwanted interior absence from required contact/prerequisite rejection and preserve named-probe uncertainty. Numeric row aliases retain resource/provenance alternatives. Exact context keys prevent borrowing scoped proofs or repair cursors under a changed objective/model.

Physical automatic state is `event_route_search` schema2 / DFS8, event-stage policy4 and movement context4, with one domain/graph/guide table and declared-anchor/open-span policy. Virtual-task controls use schema1 / DFS7 and event-stage policy3. `discoverySettings` version1 has per-session expansion/materialization quotas and residual cursors. Old automatic `spatialSettings`/cell-walk states require their source-era runner. Supplied typed schema2 corridors/schema6 DFS and legacy schema5 controls remain available. All modes fingerprint shared constraint policy7 and retain allocation normalization; physical meaningful-node service adds versioned movement context4.

The earlier `route_queue`, `SemanticCorridorAdapter`, `RouteGuards` and ordered-cell `route_progress` remain explicit legacy/control code and historical evidence tools. Their raw-walk contract is not used by the new automatic path. Neither backend proves global dynamic reachability or optimal time.
