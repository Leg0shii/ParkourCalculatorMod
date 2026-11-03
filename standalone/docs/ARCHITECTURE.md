# Architecture and execution flow

Read [current state](CURRENT_STATE.md) before assuming a planned component already exists. [Code map](CODE_MAP.md) identifies modules; [function index](FUNCTION_INDEX.md) points to definitions. Python packages are imported from the physical repository root.

## The three layers

1. **Geometry/spatial route:** `block_search.prepare_scene` normalizes supplied physical AABBs and the original state. `cell_routes.geometry.decompose` builds free player-configuration cells, portals and contact patches. `routes.enumerate_routes` or `corridor_for_points` supplies a broad spatial corridor. A cell is a spatial region, not a tick; a guide point is not a solver wall. The graph alone does not determine dynamics, contact schedule or travel time.
2. **Discrete program:** `movement_generation.generate_choices`, `open_movement`, stepping and ceiling generators compose grounded/air/jump/contact choices. `movement_composition.append_choice` appends choices to an original-state program and traces scalar vertical history. `compile_program` converts it to the existing route/profile/schedule interface. `SupportOrder` is an explicit experimental route restriction, not a universal automatic block order.
3. **Continuous solve and validation:** `compiler.JavaCompiler` obtains movement coefficients using shared Java code. The orchestrator chooses actual rows/contact alternatives and calls `ipopt.solver.solve`. A child worker optimizes continuous facings; parent scalar validation independently checks each witness. Scene, vertical, corridor and eventual executable-angle checks add distinct acceptance layers.

`cell_routes.depth_first` owns comparison frames, goal-based arrival ordering, immediate descent, backtracking and version5 persistence. `interactions.InteractionGate` adds compiled approach/required-contact queries before builders. Earlier disk/SOCP guide experiments under `route_guidance` are separate. No disk control replaces the current fixed-magnitude facing model in the selected DFS work.

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
| `retain_two_goals`, `protections` in `cell_routes.time_progress` | Time/progress retention and schedule/velocity guards. Ordering and exclusion evidence are separate. |
| `find_checkpoint_route` in `cell_routes.checkpoint_search` | Validates/setup scene, corridor and scope, then delegates outer traversal to `depth_first.search_goals`. |
| `fixed_clock_experiment.run` | Apply fixture/support order/terminal clock, run search, replay full witnesses. CLI writes progress atomically. |
| `ipopt.prepare`, `ipopt.validate` | Normalize fixed request; scalar replay independently validates facings. Arrays have n inputs and n+1 states. |
| `ipopt.solver.solve` | Worker lifecycle, deadline, cancellation, protocol, incumbent retention and independent validation. |
| `ipopt.worker` / `continuation.run` | CasADi/IPOPT phases and optional inertia continuation for supported fixed problems. Current decision research explicitly requests threshold zero. |

## One numerical request

`compile_program` → `route.schedule` → `validate_vertical_schedule` → Java coefficients → scoped reach envelopes → `ConstraintVariants` → fixed walls/contacts/inputs → `solve_contact_branches` → worker → scalar validation → vertical check → `CorridorAdapter.candidate` (scoped scene/corridor checks) → retention or terminal incumbent.

Re-solves use the original horizontal initial state and free earlier yaw variables. Saved witnesses can initialize yaw, not become seam constraints. Multiple numerical requests for one jump schedule can select different hard obstacle-face/tick/contact constraints. Distinct descriptions may also generate identical effective models; early partial-model canonicalization addresses that before expensive work.

## Persistence and identity

The **outer search** selects discrete programs; `searchState` contains generated, deferred, active and terminal work. The **inner repair search** explores fixed constraints for one program; `repairState` includes pending/seen/unresolved/inFlight/completed, accepted results, guesses, constraint generator state and logs. Saving only programs or retained pools loses progress. Saving inner repair state alone loses ungenerated programs.

Resume requires matching model/context identity, including horizon, goal/objective, scope, geometry, inputs, contacts and coefficients. A longer child is a new numerical problem, not an exact continuation of its shorter parent's raw queue. Existing code retains compatible facing guesses and preserves old work under its own identity. IPOPT internal iterations/Hessians are not serialized.

The current DFS implementation uses version5 comparison frames/backtracking without a global window barrier. `acceptedVariants` in inner repair state preserves accepted fixed-constraint alternatives separately from the local best objective. A frame has one execution owner among active, suspended and deferred-refinement queues. Do not call an old checkpoint queue an exact DFS continuation without explicit schema conversion.
