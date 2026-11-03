# Production code map

Snapshot at the current DFS implementation source, including request-audit logging. Every production Python module is listed below, along with Java/build adapters and experiment scripts. Tests/data/docs have an exhaustive [file index](FILE_INDEX.md). [Architecture](ARCHITECTURE.md) explains interactions and important function contracts; [function index](FUNCTION_INDEX.md) supplies definitions and source lines.

## autonomy

| Module | Purpose |
| --- | --- |
| [__init__.py](../autonomy/__init__.py) | Public autonomous route-search exports. |
| [block_contacts.py](../autonomy/block_contacts.py) | Floor interaction checks, avoidance/contact branches, vertical schedule validation and resumable fixed-program numerical search. |
| [block_profiles.py](../autonomy/block_profiles.py) | Ordinary flight, run-off and stair/jump scalar profiles; compile prescribed open-air support routes. |
| [block_search.py](../autonomy/block_search.py) | Normalize supplied block scenes, build transition graph and discover/compile block paths; dispatch movement families. |
| [blockages.py](../autonomy/blockages.py) | Signed face chains, gate timelines, caps, ordering and hard passage constraints in legacy collision order. |
| [case_profiles.py](../autonomy/case_profiles.py) | Geometry and scalar-profile constraints for named stepping/corner cases and landing alternatives. |
| [ceiling_contacts.py](../autonomy/ceiling_contacts.py) | Compatibility import surface for ceiling contact primitives. |
| [ceiling_generation.py](../autonomy/ceiling_generation.py) | Open ceiling choices and generator registered with generic movement discovery. |
| [ceiling_primitives.py](../autonomy/ceiling_primitives.py) | Ceiling contact/opportunity detection, sequences and scalar profiles. |
| [climbable_search.py](../autonomy/climbable_search.py) | Existing climbable-block route discovery, entry probes, timing allocations and exit reach screens. |
| [compiler.py](../autonomy/compiler.py) | Context-managed Java coefficient/scene compiler bridge, process protocol and request helpers. |
| [constraint_variants.py](../autonomy/constraint_variants.py) | Restartable pre-solve obstacle/floor constraint alternatives; cursors, reach masks and effective-row dedup. |
| [contact_combinations.py](../autonomy/contact_combinations.py) | Merge, validate and compile heterogeneous contact events; enumerate combinations. |
| [contact_profiles.py](../autonomy/contact_profiles.py) | Contact-derived scalar profiles and opportunity generation. |
| [hit_events.py](../autonomy/hit_events.py) | Compile/validate horizontal hit events, options and initial guesses. |
| [interactions.py](../autonomy/interactions.py) | Shared block/tick/phase eligibility and compiled prefix/approach gate before open builders; uncertain named probes preserve coverage. |
| [ladder_ceiling.py](../autonomy/ladder_ceiling.py) | Assign ceiling contacts to ladder units and predict revised ceiling sequences. |
| [ladder_episodes.py](../autonomy/ladder_episodes.py) | Compose multiple ladder episodes with original-state continuity. |
| [ladder_events.py](../autonomy/ladder_events.py) | Validate and compile prescribed ladder event fields. |
| [ladder_geometry.py](../autonomy/ladder_geometry.py) | Ladder columns, rectangular occupancy regions, entry/exit channels and schedule bounds. |
| [ladder_ranking.py](../autonomy/ladder_ranking.py) | Plan/score/rank ladder schedules with geometric guidance. |
| [ladder_schedules.py](../autonomy/ladder_schedules.py) | Existing ladder schedule/context/sneak-mask enumeration and request preparation. |
| [ladder_transfers.py](../autonomy/ladder_transfers.py) | Append ladder episodes/transfers and finish or enumerate multi-episode programs. |
| [linear_reachability.py](../autonomy/linear_reachability.py) | Optional coupled position/velocity LP exclusion using an outer polygon of accelerations; numerical tolerance, not rational certificate. |
| [linkcraft.py](../autonomy/linkcraft.py) | Extract a route section from a captured Linkcraft TAS; command-line adapter. |
| [mixed_ladder.py](../autonomy/mixed_ladder.py) | Compose ladder and other contact schedules in existing schedule machinery. |
| [movement_composition.py](../autonomy/movement_composition.py) | Original-state program records, append/truncate, scalar trace conversion, physical/search keys and compile to route/profiles. |
| [movement_contacts.py](../autonomy/movement_contacts.py) | Interleave contact families and seed mixed-schedule programs. |
| [movement_entry.py](../autonomy/movement_entry.py) | Build/probe numerical problems for entries into movement regions. |
| [movement_frontier.py](../autonomy/movement_frontier.py) | Persistent symbolic program frontier and cursors; local symbolic queue inside goal-aware DFS, with owned-row projection and full-proposal screening. |
| [movement_generation.py](../autonomy/movement_generation.py) | Movement type registry and normal/ladder generators; materialize choices into units. |
| [movement_problem.py](../autonomy/movement_problem.py) | Waypoint-based initial guesses and raw horizontal/vertical candidate assembly. |
| [movement_search.py](../autonomy/movement_search.py) | Movement settings, program iteration/search and generic necessary-condition screening. |
| [movement_units.py](../autonomy/movement_units.py) | Movement unit/program records, compilation, overlays, shifting and exit compatibility. |
| [obstacle_geometry.py](../autonomy/obstacle_geometry.py) | Normalize collision boxes, player expansion and independent swept geometry checks. |
| [open_ladder.py](../autonomy/open_ladder.py) | Compose open ladder event choices for program discovery. |
| [open_movement.py](../autonomy/open_movement.py) | Scalar replay, approach/descent/event helpers and open normal choices. |
| [prefix_viability.py](../autonomy/prefix_viability.py) | Conservative first-floor dead-fall rejection when mandatory future interactions remain. |
| [ranking.py](../autonomy/ranking.py) | Normalize objectives, rank candidates and create objective/passage initial guesses. |
| [reachability.py](../autonomy/reachability.py) | Conservative swept-phase position/velocity envelopes and shared block-interaction eligibility. |
| [repair_state.py](../autonomy/repair_state.py) | Exact repair context, compatibility and branch identity helpers. |
| [route.py](../autonomy/route.py) | Route schema, support-center bounds, schedule construction and yaw initial guess. |
| [screen.py](../autonomy/screen.py) | Necessary interval constraint contradictions before numerical work. |
| [search.py](../autonomy/search.py) | Original route/allocation search with worker solving and independent acceptance. |
| [solve.py](../autonomy/solve.py) | CLI for prescribed route/run-allocation solve. |
| [solve_blocks.py](../autonomy/solve_blocks.py) | CLI for autonomous block-scene solve. |
| [solve_spec.py](../autonomy/solve_spec.py) | Solve scope, physical/context digests, motion metadata, processing key and validation projection. |
| [stepping_generation.py](../autonomy/stepping_generation.py) | Named stepping profiles and registered open stepping choices. |
| [terminal_goals.py](../autonomy/terminal_goals.py) | Generic one-sided horizontal finish bounds at a discovered descending height clock; no artificial terrain. |
| [vertical_profiles.py](../autonomy/vertical_profiles.py) | Vertical trace flags and existing ladder schedule simulation. |

## autonomy/cell_routes

| Module | Purpose |
| --- | --- |
| [__init__.py](../autonomy/cell_routes/__init__.py) | Cell-route experiment package marker. |
| [checkpoint_experiment.py](../autonomy/cell_routes/checkpoint_experiment.py) | Initial checkpoint-guidance fixture runner and independent witness replay. |
| [checkpoint_search.py](../autonomy/cell_routes/checkpoint_search.py) | Scene/corridor setup and scoped fixed-program evaluation; delegates outer traversal to depth_first. |
| [checkpoints.py](../autonomy/cell_routes/checkpoints.py) | Geometric direction segments, checkpoint rows/stage checks and prefix/decision retention helpers. |
| [comparison_audit.py](../autonomy/cell_routes/comparison_audit.py) | Complete continuation audit of retained and discarded comparison-pool entries. |
| [comparison_goals.py](../autonomy/cell_routes/comparison_goals.py) | Common spatial-goal identity, arrival/landing checks and auxiliary arrival probes. |
| [corridor_guards.py](../autonomy/cell_routes/corridor_guards.py) | Merge projected corridor geometry into broad movement-region guards without guide-point clipping. |
| [decision_windows.py](../autonomy/cell_routes/decision_windows.py) | Cluster decision opportunities, construct comparison windows and retain/score candidate bands. |
| [depth_first.py](../autonomy/cell_routes/depth_first.py) | Goal-based DFS frames, arrival ranking, actual decision promotion, saved siblings/refinements and version5 continuation. |
| [experiment.py](../autonomy/cell_routes/experiment.py) | Cell-route benchmark arms, witness verification and compact experiment reports. |
| [failure_diagnostic.py](../autonomy/cell_routes/failure_diagnostic.py) | Generation and warm-control failure diagnostics, candidate audit hooks and CLI. |
| [fix_validation.py](../autonomy/cell_routes/fix_validation.py) | Focused generic failure-fix validation runner. |
| [fixed_clock_experiment.py](../autonomy/cell_routes/fixed_clock_experiment.py) | Current fixed-terminal-clock fixture runner with support-order policy, atomic progress and resume CLI. |
| [generation_diagnostic.py](../autonomy/cell_routes/generation_diagnostic.py) | Physical/execution keys, generator/queue instrumentation and report saving. |
| [geometry.py](../autonomy/cell_routes/geometry.py) | AABB subtraction/merging, cells/portals/contact patches, free-space decomposition and segment coverage/SVG. |
| [local.py](../autonomy/cell_routes/local.py) | Search work budgets/counters, counting compiler and scoped corridor/scene acceptance adapter. |
| [minimum_runs.py](../autonomy/cell_routes/minimum_runs.py) | Latest-jump ordinary ground-hold pattern normalization, validated feasible representatives and heuristic extra-run exclusions. |
| [repair_diagnostic.py](../autonomy/cell_routes/repair_diagnostic.py) | Diagnostic of repair queue allocation and failures. |
| [repair_resume_experiment.py](../autonomy/cell_routes/repair_resume_experiment.py) | Experiment preserving and resuming inner repair work. |
| [routes.py](../autonomy/cell_routes/routes.py) | Route enumeration, landmarks/signatures, guide polyline/corridor projection and horizontal traces. |
| [scalar_seed_diagnostic.py](../autonomy/cell_routes/scalar_seed_diagnostic.py) | Synthetic and fixture tests of scalar seed/fallback validity. |
| [support_order.py](../autonomy/cell_routes/support_order.py) | Fixture policy enforcing a supplied required support order on generated programs. |
| [time_progress.py](../autonomy/cell_routes/time_progress.py) | Time/progress comparison, same-witness state protections and retained/deferred frontier. |
| [two_goal_experiment.py](../autonomy/cell_routes/two_goal_experiment.py) | Two-goal comparisons and competitive continuation experiments. |
| [window_diagnostic.py](../autonomy/cell_routes/window_diagnostic.py) | Control and window-comparison diagnostics. |
| [window_experiment.py](../autonomy/cell_routes/window_experiment.py) | Decision-window benchmark runner and atomic plain/gzip report writer. |

## autonomy/route_guidance

| Module | Purpose |
| --- | --- |
| [__init__.py](../autonomy/route_guidance/__init__.py) | Experimental route guidance public exports. |
| [compact.py](../autonomy/route_guidance/compact.py) | Earlier compact circle/disk nonlinear relaxation models and witness containment audit. |
| [conic.py](../autonomy/route_guidance/conic.py) | Earlier disaggregated perspective disk SOCP assembly and Clarabel backend. |
| [experiment.py](../autonomy/route_guidance/experiment.py) | Compare relaxed models with physically refined fixed decisions; similarity/ranking experiments. |
| [graph.py](../autonomy/route_guidance/graph.py) | Time-expanded phase graph, control/carry domains, guards and discrete assignments. |
| [guidance.py](../autonomy/route_guidance/guidance.py) | Earlier relaxed candidate ranking and bounded guidance search; not current DFS implementation. |
| [inertia_ablation.py](../autonomy/route_guidance/inertia_ablation.py) | Compare threshold/carry variants in historical relaxation experiments. |
| [physical.py](../autonomy/route_guidance/physical.py) | Fix/round assignments, build actual fixed problems, refine and lift witnesses into relaxed model. |
| [replay.py](../autonomy/route_guidance/replay.py) | Physical replay diagnostics for route-guidance experiment reports. |
| [report.py](../autonomy/route_guidance/report.py) | Read/aggregate experiment outputs and optional publication plots. |

## ipopt

| Module | Purpose |
| --- | --- |
| [__init__.py](../ipopt/__init__.py) | Public prepare, validate, solve and capture-attachment API. |
| [capture.py](../ipopt/capture.py) | Attach prescribed vertical/ladder evidence from recorded captures. |
| [continuation.py](../ipopt/continuation.py) | Continuous/inertia phase strategy, incumbent preservation and continuation state. |
| [ladder.py](../ipopt/ladder.py) | Float-promoted caps, prescribed ladder/vertical step and stored-carry equations. |
| [model.py](../ipopt/model.py) | Strict fixed-problem schema/fingerprint and independent scalar horizontal/vertical constraint validation. |
| [solve.py](../ipopt/solve.py) | Fixed-request CLI, JSON/gzip IO, runtime options, atomic output and exit statuses. |
| [solver.py](../ipopt/solver.py) | Parent worker subprocess lifecycle/protocol, deadlines/cancellation and independently validated incumbents. |
| [worker.py](../ipopt/worker.py) | Native CasADi/IPOPT model, solve phases, callback monitor and worker protocol entry point. |

## Java, build adapters and diagnostic scripts

These reuse current mod sources. Scripts are evidence tools, not alternative production solvers. Some overwrite their default output; inspect CLI first.

| File | Purpose |
| --- | --- |
| [autonomy/compiler.gradle](../autonomy/compiler.gradle) | Gradle init tasks for shared standalone coefficient/scene Java compiler and local classpath descriptor. |
| [autonomy/experiments/blockage-order/BlockageOrderProbe.java](../autonomy/experiments/blockage-order/BlockageOrderProbe.java) | Probe shared legacy collision-order constraints for signed blockage cases. |
| [autonomy/experiments/cell-routes/run_benchmarks.py](../autonomy/experiments/cell-routes/run_benchmarks.py) | Run initial generic non-ladder spatial-route benchmark families. |
| [autonomy/experiments/checkpoint-guidance/plot_diagnostic.py](../autonomy/experiments/checkpoint-guidance/plot_diagnostic.py) | Render checkpoint-guidance diagnostic reports. |
| [autonomy/experiments/decision-windows/analyze_results.py](../autonomy/experiments/decision-windows/analyze_results.py) | Analyze saved decision-window candidate outcomes. |
| [autonomy/experiments/dfs-interactions/measure_generation.py](../autonomy/experiments/dfs-interactions/measure_generation.py) | Measure current per-block option generation over archived physical contexts, explicitly rebuilding cursors rather than restoring incompatible old ones. |
| [autonomy/experiments/dfs-interactions/summarize.py](../autonomy/experiments/dfs-interactions/summarize.py) | Reconcile cold/resume frame ownership, worker requests/fingerprints, completed history and independent replays into an evidence summary. |
| [autonomy/experiments/linkcraft11-opening/DeriveSelectedFootprints.java](../autonomy/experiments/linkcraft11-opening/DeriveSelectedFootprints.java) | Demonstrate actual mod neighbor-aware selected-block footprint derivation. |
| [autonomy/experiments/linkcraft11-opening/audit_trace.py](../autonomy/experiments/linkcraft11-opening/audit_trace.py) | Extract initial pilot cells, programs/requests and retention/commit trace. |
| [autonomy/experiments/linkcraft11-opening/evidence/scripts/probe_real11_corridor.py](../autonomy/experiments/linkcraft11-opening/evidence/scripts/probe_real11_corridor.py) | Historical corridor constraint/signature control; executes at module top level. Inspect before running, never import for discovery. |
| [autonomy/experiments/linkcraft11-opening/evidence/scripts/probe_real11_next_support.py](../autonomy/experiments/linkcraft11-opening/evidence/scripts/probe_real11_next_support.py) | Historical next-support arrival probe; executes at module top level and writes evidence. Check source-era API compatibility. |
| [autonomy/experiments/linkcraft11-opening/evidence/scripts/real11_constructive_probe.py](../autonomy/experiments/linkcraft11-opening/evidence/scripts/real11_constructive_probe.py) | Historical constructive continuation diagnostic using existing choices; seeded control, not cold discovery; top-level execution. |
| [autonomy/experiments/linkcraft11-opening/evidence/scripts/real11_terminal_variants.py](../autonomy/experiments/linkcraft11-opening/evidence/scripts/real11_terminal_variants.py) | Historical terminal jump/run-off variant probes; top-level execution and source-era context, not the current generic entry point. |
| [autonomy/experiments/linkcraft11-opening/measure_setup_crossover.py](../autonomy/experiments/linkcraft11-opening/measure_setup_crossover.py) | Measure common-clock position/velocity and delayed benefit in two saved complete setup-family witnesses. |
| [autonomy/experiments/linkcraft11-opening/probe_b2_reachability.py](../autonomy/experiments/linkcraft11-opening/probe_b2_reachability.py) | Diagnose B2 reach/interaction relevance in selected opening contexts. |
| [autonomy/experiments/linkcraft11-opening/probe_corridor_failures.py](../autonomy/experiments/linkcraft11-opening/probe_corridor_failures.py) | Inspect corridor rejection contexts and geometry/projection effects. |
| [autonomy/experiments/linkcraft11-opening/probe_other_failures.py](../autonomy/experiments/linkcraft11-opening/probe_other_failures.py) | Diagnostic continuation/control probes for the earlier four unknown trials. |
| [autonomy/experiments/linkcraft11-opening/replay_solution.py](../autonomy/experiments/linkcraft11-opening/replay_solution.py) | Independently replay the saved complete opening solution. |
| [autonomy/experiments/linkcraft11-opening/summarize_evidence.py](../autonomy/experiments/linkcraft11-opening/summarize_evidence.py) | Summarize original opening experiment evidence and provenance. |
| [autonomy/experiments/linkcraft11-opening/summarize_other_failures.py](../autonomy/experiments/linkcraft11-opening/summarize_other_failures.py) | Summarize earlier continuation/repair failure diagnostics. |
| [autonomy/experiments/orchestrator-fixes/audit_candidates.py](../autonomy/experiments/orchestrator-fixes/audit_candidates.py) | Extract/reconcile saved cold reports into exact program/branch/probe/call ledgers; optional independent Java replay. |
| [autonomy/experiments/orchestrator-fixes/audit_generation.py](../autonomy/experiments/orchestrator-fixes/audit_generation.py) | Bounded exact-context offline face/gate stream and dedup profiler; no Java/IPOPT calls inside probe. |
| [autonomy/experiments/orchestrator-fixes/regressions.py](../autonomy/experiments/orchestrator-fixes/regressions.py) | Seeded fixed-reference terminal solve/resume control and historical dead-fall checks; not cold discovery. |
| [autonomy/experiments/stepping-mechanics/StepMechanicsProbe.java](../autonomy/experiments/stepping-mechanics/StepMechanicsProbe.java) | Probe shared stepping/collision semantics with scalar and scene evidence. |
| [autonomy/java/CompileRunTicksPhysics.java](../autonomy/java/CompileRunTicksPhysics.java) | JSON bridge to existing movement coefficient compiler, scene and geometry operations; reused by Python orchestration. |
| [ipopt/export.gradle](../ipopt/export.gradle) | Gradle init tasks for capture compilation, standalone export and local executable replay. |
| [ipopt/java/CompileStandaloneIpoptPhysics.java](../ipopt/java/CompileStandaloneIpoptPhysics.java) | Compile effective-input fixed problems with shared Java movement/input policies. |
| [ipopt/java/ExportStandaloneIpopt.java](../ipopt/java/ExportStandaloneIpopt.java) | Read captured TAS spans and export fixed IPOPT requests with exact source/state/settings. |
| [ipopt/java/ReplayStandaloneIpopt.java](../ipopt/java/ReplayStandaloneIpopt.java) | Replay/repair solved facings as executable local TAS inputs and export only validated results. |

Maintain this map when public modules change. Index generation uses Python AST and `git ls-files --cached --others --exclude-standard standalone`; ignore build output, virtualenvs and caches. Purposes are editorial, not inferred dependency contracts.
