# Complete standalone file inventory

Every tracked standalone file plus this consolidation is listed by directory. Generated build output, caches and virtualenvs are intentionally excluded. Every production module has an editorial purpose in [code map](CODE_MAP.md); detailed test definitions are in [function index](FUNCTION_INDEX.md), fixtures/testing conventions in [testing](TESTING.md), and documentation status in [document index](DOC_INDEX.md).

Evidence filenames identify different runs/contexts; do not treat reports as executable modules or raw request fixtures. Nearest experiment README/manifest plus embedded source/settings determine exact provenance. This is a navigation snapshot, not permission to overwrite artifacts.

## standalone

| File | Purpose |
| --- | --- |
| [AGENTS.md](../AGENTS.md) | Standalone agent entry point. See documentation index for authority/status. |
| [CONSOLIDATION.md](../CONSOLIDATION.md) | Standalone branch consolidation. See documentation index for authority/status. |
| [README.md](../README.md) | Standalone route search and IPOPT. See documentation index for authority/status. |

## autonomy

| File | Purpose |
| --- | --- |
| [BLOCK_ROUTE_SEARCH.md](../autonomy/BLOCK_ROUTE_SEARCH.md) | Block-route selection prototype. See documentation index for authority/status. |
| [BLOCK_SOLVER_PLAN.md](../autonomy/BLOCK_SOLVER_PLAN.md) | Block solver: continuous-model orchestration. See documentation index for authority/status. |
| [CELL_ROUTE_EXPERIMENT_PLAN.md](../autonomy/CELL_ROUTE_EXPERIMENT_PLAN.md) | Cell-route experiment plan. See documentation index for authority/status. |
| [COMPACTION_HANDOFF.md](../autonomy/COMPACTION_HANDOFF.md) | Orchestration compaction handoff. See documentation index for authority/status. |
| [CONSTRAINT_GENERATION_RUNBOOK.md](../autonomy/CONSTRAINT_GENERATION_RUNBOOK.md) | Reusing the mod's block constraints in route discovery. See documentation index for authority/status. |
| [CONTACT_COMBINATIONS.md](../autonomy/CONTACT_COMBINATIONS.md) | Contact combination implementation. See documentation index for authority/status. |
| [CONTINUOUS_DECISION_RELAXATION.md](../autonomy/CONTINUOUS_DECISION_RELAXATION.md) | Continuous decision relaxation with full-strength controls. See documentation index for authority/status. |
| [CONVEX_ROUTE_GUIDANCE_PLAN.md](../autonomy/CONVEX_ROUTE_GUIDANCE_PLAN.md) | Convex route guidance: scope and first experiment. See documentation index for authority/status. |
| [CORRIDOR_CONSTRAINT_FIX_PLAN.md](../autonomy/CORRIDOR_CONSTRAINT_FIX_PLAN.md) | Corridor and constraint scope fix. See documentation index for authority/status. |
| [DFS_INTERACTION_IMPLEMENTATION_PLAN.md](../autonomy/DFS_INTERACTION_IMPLEMENTATION_PLAN.md) | Consolidated plan: depth-first decision search and early interaction eligibility. See documentation index for authority/status. |
| [DYNAMIC_CONTACT_DECISIONS.md](../autonomy/DYNAMIC_CONTACT_DECISIONS.md) | Ceiling and stepping decisions change the transition law. See documentation index for authority/status. |
| [GENERATION_PERFORMANCE_PLAN.md](../autonomy/GENERATION_PERFORMANCE_PLAN.md) | Schedule-generation performance follow-ups. See documentation index for authority/status. |
| [GENERATION_RELEVANCE_PLAN.md](../autonomy/GENERATION_RELEVANCE_PLAN.md) | Preliminary plan: generate only relevant, distinct interaction models. See documentation index for authority/status. |
| [GENERIC_COMPOSITION.md](../autonomy/GENERIC_COMPOSITION.md) | Generic movement programs. See documentation index for authority/status. |
| [GENERIC_COMPOSITION_PLAN.md](../autonomy/GENERIC_COMPOSITION_PLAN.md) | Generic movement composition. See documentation index for authority/status. |
| [GEOMETRIC_DECISION_GUIDANCE.md](../autonomy/GEOMETRIC_DECISION_GUIDANCE.md) | Use the existing cell route as the first decision guide. See documentation index for authority/status. |
| [HEURISTIC_SOURCE_REVIEW.md](../autonomy/HEURISTIC_SOURCE_REVIEW.md) | Heuristic source review: Baritone and Stratfinder. See documentation index for authority/status. |
| [LADDER_CEILING_PLAN.md](../autonomy/LADDER_CEILING_PLAN.md) | Ladder-ceiling ownership and generation. See documentation index for authority/status. |
| [LADDER_COMPLETION.md](../autonomy/LADDER_COMPLETION.md) | Ladder and vine completion checkpoint. See documentation index for authority/status. |
| [LADDER_COMPOSITION_PLAN.md](../autonomy/LADDER_COMPOSITION_PLAN.md) | Multiple ladders and mixed contact schedules. See documentation index for authority/status. |
| [LADDER_STRATEGY_PLAN.md](../autonomy/LADDER_STRATEGY_PLAN.md) | Ladder and vine strategy search. See documentation index for authority/status. |
| [LONG_LADDER_VALIDATION.md](../autonomy/LONG_LADDER_VALIDATION.md) | Longer climbing-chain validation. See documentation index for authority/status. |
| [MIXED_INTEGER_RESEARCH.md](../autonomy/MIXED_INTEGER_RESEARCH.md) | Generic mixed-integer methods for the standalone solver. See documentation index for authority/status. |
| [MIXED_MOVEMENT_CONTACTS.md](../autonomy/MIXED_MOVEMENT_CONTACTS.md) | Generated contacts across movement units. See documentation index for authority/status. |
| [MOVEMENT_REFACTOR_STATUS.md](../autonomy/MOVEMENT_REFACTOR_STATUS.md) | Ladder and movement-refactor status. See documentation index for authority/status. |
| [MOVEMENT_TYPE_REFACTOR_PLAN.md](../autonomy/MOVEMENT_TYPE_REFACTOR_PLAN.md) | Normal ceiling, ladder ceiling and stepping refactor. See documentation index for authority/status. |
| [MOVEMENT_UNITS.md](../autonomy/MOVEMENT_UNITS.md) | Movement units and airborne composition. See documentation index for authority/status. |
| [NO_THRESHOLD_DECISION_MODEL.md](../autonomy/NO_THRESHOLD_DECISION_MODEL.md) | Convex decision model with normal momentum carry. See documentation index for authority/status. |
| [OBSTACLES_AND_BLOCKAGES.md](../autonomy/OBSTACLES_AND_BLOCKAGES.md) | Obstacles, contacts and directed blockages. See documentation index for authority/status. |
| [OBSTACLE_RELEVANCE.md](../autonomy/OBSTACLE_RELEVANCE.md) | Shared block-interaction reachability. See documentation index for authority/status. |
| [OBSTACLE_SEARCH.md](../autonomy/OBSTACLE_SEARCH.md) | Collision-box obstacle avoidance. See documentation index for authority/status. |
| [ONE_TICK_CONTACT_PLAN.md](../autonomy/ONE_TICK_CONTACT_PLAN.md) | One-input wall and ceiling contacts. See documentation index for authority/status. |
| [ORCHESTRATOR_IMPLEMENTATION_PLAN.md](../autonomy/ORCHESTRATOR_IMPLEMENTATION_PLAN.md) | Consolidated orchestrator implementation plan. See documentation index for authority/status. |
| [PROVISIONAL_ROUTE_GUIDANCE.md](../autonomy/PROVISIONAL_ROUTE_GUIDANCE.md) | Informative initial solves with provisional route choices. See documentation index for authority/status. |
| [PRUNING_IMPROVEMENTS_BACKLOG.md](../autonomy/PRUNING_IMPROVEMENTS_BACKLOG.md) | Deferred pruning and orchestration improvements. See documentation index for authority/status. |
| [PRUNING_RESEARCH.md](../autonomy/PRUNING_RESEARCH.md) | Pruning research and search framework. See documentation index for authority/status. |
| [PRUNING_RESEARCH_HANDOFF.md](../autonomy/PRUNING_RESEARCH_HANDOFF.md) | Current pruning and orchestration handoff. See documentation index for authority/status. |
| [PRUNING_RESEARCH_HISTORY.md](../autonomy/PRUNING_RESEARCH_HISTORY.md) | Historical pruning research handoff (preserved). See documentation index for authority/status. |
| [README.md](../autonomy/README.md) | Run ticks over a block route. See documentation index for authority/status. |
| [REFERENCE_STRATEGY_EVALUATION.md](../autonomy/REFERENCE_STRATEGY_EVALUATION.md) | Choosing a whole-route reference strategy. See documentation index for authority/status. |
| [ROUTE_BASELINE_GUIDANCE.md](../autonomy/ROUTE_BASELINE_GUIDANCE.md) | Route-specific optimistic continuation guidance. See documentation index for authority/status. |
| [ROUTE_MIP_RELAXATION.md](../autonomy/ROUTE_MIP_RELAXATION.md) | A mixed-integer outer model for route guidance. See documentation index for authority/status. |
| [RUN_OFF_OBSTACLES.md](../autonomy/RUN_OFF_OBSTACLES.md) | Run-off source support and obstacle behavior. See documentation index for authority/status. |
| [RUN_TICKS_PLAN.md](../autonomy/RUN_TICKS_PLAN.md) | Run-ticks prototype: status and remaining work. See documentation index for authority/status. |
| [SEARCH_CONTINUATION_FIX_PLAN.md](../autonomy/SEARCH_CONTINUATION_FIX_PLAN.md) | Resume unfinished solves and reject provably dead prefixes. See documentation index for authority/status. |
| [SEARCH_OBJECTIVE.md](../autonomy/SEARCH_OBJECTIVE.md) | Search order: total ticks, then objective. See documentation index for authority/status. |
| [SPATIAL_ROUTE_RESEARCH.md](../autonomy/SPATIAL_ROUTE_RESEARCH.md) | From collision geometry to spatial route families. See documentation index for authority/status. |
| [STATE_PROTECTION_IMPLEMENTATION_PLAN.md](../autonomy/STATE_PROTECTION_IMPLEMENTATION_PLAN.md) | Implementation plan: velocity and schedule protection. See documentation index for authority/status. |
| [STEPPING_CASES.md](../autonomy/STEPPING_CASES.md) | Stepping terminology and cases. See documentation index for authority/status. |
| [STEPPING_IMPLEMENTATION.md](../autonomy/STEPPING_IMPLEMENTATION.md) | Stepping and normal-air ceiling implementation. See documentation index for authority/status. |
| [STEPPING_PLAN.md](../autonomy/STEPPING_PLAN.md) | Case-based stepping plan. See documentation index for authority/status. |
| [STRATFINDER_INVESTIGATION.md](../autonomy/STRATFINDER_INVESTIGATION.md) | Stratfinder investigation: route selection and pruning. See documentation index for authority/status. |
| [TIME_PROGRESS_FRONTIER.md](../autonomy/TIME_PROGRESS_FRONTIER.md) | Time and progress comparisons: two distinct goals. See documentation index for authority/status. |
| [TIME_PROGRESS_IMPLEMENTATION_PLAN.md](../autonomy/TIME_PROGRESS_IMPLEMENTATION_PLAN.md) | Implementation plan: spatial-goal time and fixed-time progress comparisons. See documentation index for authority/status. |
| [UPFRONT_BRANCH_COMPILATION_PLAN.md](../autonomy/UPFRONT_BRANCH_COMPILATION_PLAN.md) | Select and compose existing constraints before IPOPT. See documentation index for authority/status. |
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
| [compiler.gradle](../autonomy/compiler.gradle) | Gradle init tasks for shared standalone coefficient/scene Java compiler and local classpath descriptor. |
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

| File | Purpose |
| --- | --- |
| [ORCHESTRATOR_CONTRACT.md](../autonomy/cell_routes/ORCHESTRATOR_CONTRACT.md) | Fixed-decision continuous-solve contract. See documentation index for authority/status. |
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

## autonomy/experiments/block-route-search

| File | Purpose |
| --- | --- |
| [README.md](../autonomy/experiments/block-route-search/README.md) | Fixed endpoints, variable intermediate supports. See documentation index for authority/status. |
| [momentum-choice-request.json](../autonomy/experiments/block-route-search/momentum-choice-request.json) | Structured experiment data/settings/provenance: momentum choice request. |
| [momentum-choice-result.json](../autonomy/experiments/block-route-search/momentum-choice-result.json) | Structured experiment data/settings/provenance: momentum choice result. |
| [momentum-choice-vertical.json](../autonomy/experiments/block-route-search/momentum-choice-vertical.json) | Structured experiment data/settings/provenance: momentum choice vertical. |
| [route-choice-request.json](../autonomy/experiments/block-route-search/route-choice-request.json) | Structured experiment data/settings/provenance: route choice request. |
| [route-choice-result.json](../autonomy/experiments/block-route-search/route-choice-result.json) | Structured experiment data/settings/provenance: route choice result. |
| [route-choice-vertical.json](../autonomy/experiments/block-route-search/route-choice-vertical.json) | Structured experiment data/settings/provenance: route choice vertical. |
| [summary.json](../autonomy/experiments/block-route-search/summary.json) | Structured experiment data/settings/provenance: summary. |

## autonomy/experiments/blockage-order

| File | Purpose |
| --- | --- |
| [BlockageOrderProbe.java](../autonomy/experiments/blockage-order/BlockageOrderProbe.java) | Probe shared legacy collision-order constraints for signed blockage cases. |
| [README.md](../autonomy/experiments/blockage-order/README.md) | Directed blockage timing checked against Minecraft. See documentation index for authority/status. |
| [integration-reference.json](../autonomy/experiments/blockage-order/integration-reference.json) | Structured experiment data/settings/provenance: integration reference. |
| [results.json](../autonomy/experiments/blockage-order/results.json) | Structured experiment data/settings/provenance: results. |

## autonomy/experiments/cell-routes

| File | Purpose |
| --- | --- |
| [IMPLEMENTATION_NOTES.md](../autonomy/experiments/cell-routes/IMPLEMENTATION_NOTES.md) | Cell-route experiment. See documentation index for authority/status. |
| [README.md](../autonomy/experiments/cell-routes/README.md) | Cell-route experiment results. See documentation index for authority/status. |
| [run_benchmarks.py](../autonomy/experiments/cell-routes/run_benchmarks.py) | Run initial generic non-ladder spatial-route benchmark families. |

## autonomy/experiments/cell-routes/controls

| File | Purpose |
| --- | --- |
| [blip-route.json](../autonomy/experiments/cell-routes/controls/blip-route.json) | Structured experiment data/settings/provenance: blip route. |
| [ceiling-route.json](../autonomy/experiments/cell-routes/controls/ceiling-route.json) | Structured experiment data/settings/provenance: ceiling route. |
| [ceiling.json](../autonomy/experiments/cell-routes/controls/ceiling.json) | Structured experiment data/settings/provenance: ceiling. |
| [five-ladders-route.json](../autonomy/experiments/cell-routes/controls/five-ladders-route.json) | Structured experiment data/settings/provenance: five ladders route. |
| [grind-route.json](../autonomy/experiments/cell-routes/controls/grind-route.json) | Structured experiment data/settings/provenance: grind route. |
| [normal-step-route.json](../autonomy/experiments/cell-routes/controls/normal-step-route.json) | Structured experiment data/settings/provenance: normal step route. |
| [open-route.json](../autonomy/experiments/cell-routes/controls/open-route.json) | Structured experiment data/settings/provenance: open route. |
| [open.json](../autonomy/experiments/cell-routes/controls/open.json) | Structured experiment data/settings/provenance: open. |
| [remote-supports-route.json](../autonomy/experiments/cell-routes/controls/remote-supports-route.json) | Structured experiment data/settings/provenance: remote supports route. |
| [remote-supports.json](../autonomy/experiments/cell-routes/controls/remote-supports.json) | Structured experiment data/settings/provenance: remote supports. |
| [run-up-route.json](../autonomy/experiments/cell-routes/controls/run-up-route.json) | Structured experiment data/settings/provenance: run up route. |
| [run-up.json](../autonomy/experiments/cell-routes/controls/run-up.json) | Structured experiment data/settings/provenance: run up. |
| [same-corridor-supports-route.json](../autonomy/experiments/cell-routes/controls/same-corridor-supports-route.json) | Structured experiment data/settings/provenance: same corridor supports route. |
| [same-corridor-supports.json](../autonomy/experiments/cell-routes/controls/same-corridor-supports.json) | Structured experiment data/settings/provenance: same corridor supports. |
| [wide-neo-route.json](../autonomy/experiments/cell-routes/controls/wide-neo-route.json) | Structured experiment data/settings/provenance: wide neo route. |
| [wide-neo.json](../autonomy/experiments/cell-routes/controls/wide-neo.json) | Structured experiment data/settings/provenance: wide neo. |

## autonomy/experiments/cell-routes/evidence

| File | Purpose |
| --- | --- |
| [grind-forward-profile.txt](../autonomy/experiments/cell-routes/evidence/grind-forward-profile.txt) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [grind-geometry.json](../autonomy/experiments/cell-routes/evidence/grind-geometry.json) | Structured experiment data/settings/provenance: grind geometry. |
| [grind-no-forward-profile.txt](../autonomy/experiments/cell-routes/evidence/grind-no-forward-profile.txt) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [grind-routes.json](../autonomy/experiments/cell-routes/evidence/grind-routes.json) | Structured experiment data/settings/provenance: grind routes. |
| [grind-slice.svg](../autonomy/experiments/cell-routes/evidence/grind-slice.svg) | Rendered diagnostic/trajectory figure from owning experiment evidence. |
| [measurements.json](../autonomy/experiments/cell-routes/evidence/measurements.json) | Structured experiment data/settings/provenance: measurements. |
| [profiles.json](../autonomy/experiments/cell-routes/evidence/profiles.json) | Structured experiment data/settings/provenance: profiles. |
| [same-corridor-supports-forward-profile.txt](../autonomy/experiments/cell-routes/evidence/same-corridor-supports-forward-profile.txt) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [same-corridor-supports-no-forward-profile.txt](../autonomy/experiments/cell-routes/evidence/same-corridor-supports-no-forward-profile.txt) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [tiny-step-exhaustive.json](../autonomy/experiments/cell-routes/evidence/tiny-step-exhaustive.json) | Structured experiment data/settings/provenance: tiny step exhaustive. |
| [validation.json](../autonomy/experiments/cell-routes/evidence/validation.json) | Structured experiment data/settings/provenance: validation. |

## autonomy/experiments/cell-routes/evidence/witnesses

| File | Purpose |
| --- | --- |
| [ceiling-automatic.json](../autonomy/experiments/cell-routes/evidence/witnesses/ceiling-automatic.json) | Structured experiment data/settings/provenance: ceiling automatic. |
| [ceiling-generic.json](../autonomy/experiments/cell-routes/evidence/witnesses/ceiling-generic.json) | Structured experiment data/settings/provenance: ceiling generic. |
| [ceiling-prescribed.json](../autonomy/experiments/cell-routes/evidence/witnesses/ceiling-prescribed.json) | Structured experiment data/settings/provenance: ceiling prescribed. |
| [grind-automatic.json](../autonomy/experiments/cell-routes/evidence/witnesses/grind-automatic.json) | Structured experiment data/settings/provenance: grind automatic. |
| [grind-generic.json](../autonomy/experiments/cell-routes/evidence/witnesses/grind-generic.json) | Structured experiment data/settings/provenance: grind generic. |
| [grind-prescribed.json](../autonomy/experiments/cell-routes/evidence/witnesses/grind-prescribed.json) | Structured experiment data/settings/provenance: grind prescribed. |
| [normal-step-automatic.json](../autonomy/experiments/cell-routes/evidence/witnesses/normal-step-automatic.json) | Structured experiment data/settings/provenance: normal step automatic. |
| [normal-step-generic.json](../autonomy/experiments/cell-routes/evidence/witnesses/normal-step-generic.json) | Structured experiment data/settings/provenance: normal step generic. |
| [normal-step-prescribed.json](../autonomy/experiments/cell-routes/evidence/witnesses/normal-step-prescribed.json) | Structured experiment data/settings/provenance: normal step prescribed. |
| [open-automatic.json](../autonomy/experiments/cell-routes/evidence/witnesses/open-automatic.json) | Structured experiment data/settings/provenance: open automatic. |
| [open-generic.json](../autonomy/experiments/cell-routes/evidence/witnesses/open-generic.json) | Structured experiment data/settings/provenance: open generic. |
| [open-prescribed.json](../autonomy/experiments/cell-routes/evidence/witnesses/open-prescribed.json) | Structured experiment data/settings/provenance: open prescribed. |
| [remote-supports-automatic.json](../autonomy/experiments/cell-routes/evidence/witnesses/remote-supports-automatic.json) | Structured experiment data/settings/provenance: remote supports automatic. |
| [remote-supports-generic.json](../autonomy/experiments/cell-routes/evidence/witnesses/remote-supports-generic.json) | Structured experiment data/settings/provenance: remote supports generic. |
| [remote-supports-prescribed.json](../autonomy/experiments/cell-routes/evidence/witnesses/remote-supports-prescribed.json) | Structured experiment data/settings/provenance: remote supports prescribed. |
| [run-up-automatic.json](../autonomy/experiments/cell-routes/evidence/witnesses/run-up-automatic.json) | Structured experiment data/settings/provenance: run up automatic. |
| [run-up-generic.json](../autonomy/experiments/cell-routes/evidence/witnesses/run-up-generic.json) | Structured experiment data/settings/provenance: run up generic. |
| [run-up-prescribed.json](../autonomy/experiments/cell-routes/evidence/witnesses/run-up-prescribed.json) | Structured experiment data/settings/provenance: run up prescribed. |
| [same-corridor-authored.json](../autonomy/experiments/cell-routes/evidence/witnesses/same-corridor-authored.json) | Structured experiment data/settings/provenance: same corridor authored. |
| [wide-neo-automatic.json](../autonomy/experiments/cell-routes/evidence/witnesses/wide-neo-automatic.json) | Structured experiment data/settings/provenance: wide neo automatic. |
| [wide-neo-generic.json](../autonomy/experiments/cell-routes/evidence/witnesses/wide-neo-generic.json) | Structured experiment data/settings/provenance: wide neo generic. |
| [wide-neo-prescribed.json](../autonomy/experiments/cell-routes/evidence/witnesses/wide-neo-prescribed.json) | Structured experiment data/settings/provenance: wide neo prescribed. |

## autonomy/experiments/checkpoint-guidance

| File | Purpose |
| --- | --- |
| [README.md](../autonomy/experiments/checkpoint-guidance/README.md) | Geometric checkpoint guidance. See documentation index for authority/status. |
| [RESULTS.md](../autonomy/experiments/checkpoint-guidance/RESULTS.md) | Checkpoint guidance results. See documentation index for authority/status. |
| [plot_diagnostic.py](../autonomy/experiments/checkpoint-guidance/plot_diagnostic.py) | Render checkpoint-guidance diagnostic reports. |

## autonomy/experiments/checkpoint-guidance/evidence

| File | Purpose |
| --- | --- |
| [ceiling-ranking.png](../autonomy/experiments/checkpoint-guidance/evidence/ceiling-ranking.png) | Rendered diagnostic/trajectory figure from owning experiment evidence. |
| [ceiling-ranking.svg](../autonomy/experiments/checkpoint-guidance/evidence/ceiling-ranking.svg) | Rendered diagnostic/trajectory figure from owning experiment evidence. |
| [checkpoint-autonomy-tests.log](../autonomy/experiments/checkpoint-guidance/evidence/checkpoint-autonomy-tests.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [checkpoint-guidance-final.json.gz](../autonomy/experiments/checkpoint-guidance/evidence/checkpoint-guidance-final.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [checkpoint-guidance-final.log](../autonomy/experiments/checkpoint-guidance/evidence/checkpoint-guidance-final.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [checkpoint-guidance-greedy-final.json.gz](../autonomy/experiments/checkpoint-guidance/evidence/checkpoint-guidance-greedy-final.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [checkpoint-guidance-greedy-final.log](../autonomy/experiments/checkpoint-guidance/evidence/checkpoint-guidance-greedy-final.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [checkpoint-ipopt-tests.log](../autonomy/experiments/checkpoint-guidance/evidence/checkpoint-ipopt-tests.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [checkpoint-known-ladder-failure.log](../autonomy/experiments/checkpoint-guidance/evidence/checkpoint-known-ladder-failure.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [checkpoint-manifest.json](../autonomy/experiments/checkpoint-guidance/evidence/checkpoint-manifest.json) | Structured experiment data/settings/provenance: checkpoint manifest. |
| [checkpoint-numerical-tests.log](../autonomy/experiments/checkpoint-guidance/evidence/checkpoint-numerical-tests.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [greedy-summary.csv](../autonomy/experiments/checkpoint-guidance/evidence/greedy-summary.csv) | Tabular per-candidate/call/trajectory measurements; column headings define units. |
| [greedy-summary.json](../autonomy/experiments/checkpoint-guidance/evidence/greedy-summary.json) | Structured experiment data/settings/provenance: greedy summary. |
| [summary.csv](../autonomy/experiments/checkpoint-guidance/evidence/summary.csv) | Tabular per-candidate/call/trajectory measurements; column headings define units. |
| [summary.json](../autonomy/experiments/checkpoint-guidance/evidence/summary.json) | Structured experiment data/settings/provenance: summary. |

## autonomy/experiments/checkpoint-guidance/fixtures

| File | Purpose |
| --- | --- |
| [ceiling.json](../autonomy/experiments/checkpoint-guidance/fixtures/ceiling.json) | Reproducible input/model fixture: ceiling.json; owning tests/experiment define domain and expected acceptance. |
| [grind-or-run.json](../autonomy/experiments/checkpoint-guidance/fixtures/grind-or-run.json) | Reproducible input/model fixture: grind-or-run.json; owning tests/experiment define domain and expected acceptance. |
| [multi-support.json](../autonomy/experiments/checkpoint-guidance/fixtures/multi-support.json) | Reproducible input/model fixture: multi-support.json; owning tests/experiment define domain and expected acceptance. |
| [open.json](../autonomy/experiments/checkpoint-guidance/fixtures/open.json) | Reproducible input/model fixture: open.json; owning tests/experiment define domain and expected acceptance. |
| [step-or-jump.json](../autonomy/experiments/checkpoint-guidance/fixtures/step-or-jump.json) | Reproducible input/model fixture: step-or-jump.json; owning tests/experiment define domain and expected acceptance. |
| [turn-blockage.json](../autonomy/experiments/checkpoint-guidance/fixtures/turn-blockage.json) | Reproducible input/model fixture: turn-blockage.json; owning tests/experiment define domain and expected acceptance. |

## autonomy/experiments/contact-combinations

| File | Purpose |
| --- | --- |
| [README.md](../autonomy/experiments/contact-combinations/README.md) | Coupled contact and ladder evidence. See documentation index for authority/status. |
| [airborne-transfer.json](../autonomy/experiments/contact-combinations/airborne-transfer.json) | Structured experiment data/settings/provenance: airborne transfer. |
| [appended-air-ceiling.json](../autonomy/experiments/contact-combinations/appended-air-ceiling.json) | Structured experiment data/settings/provenance: appended air ceiling. |
| [appended-ladder-ceiling.json](../autonomy/experiments/contact-combinations/appended-ladder-ceiling.json) | Structured experiment data/settings/provenance: appended ladder ceiling. |
| [automatic-airborne-transfer.json](../autonomy/experiments/contact-combinations/automatic-airborne-transfer.json) | Structured experiment data/settings/provenance: automatic airborne transfer. |
| [contact-blockage.json](../autonomy/experiments/contact-combinations/contact-blockage.json) | Structured experiment data/settings/provenance: contact blockage. |
| [corner-result.json](../autonomy/experiments/contact-combinations/corner-result.json) | Structured experiment data/settings/provenance: corner result. |
| [final-air-ceiling.json](../autonomy/experiments/contact-combinations/final-air-ceiling.json) | Structured experiment data/settings/provenance: final air ceiling. |
| [five-airborne-ladders.json](../autonomy/experiments/contact-combinations/five-airborne-ladders.json) | Structured experiment data/settings/provenance: five airborne ladders. |
| [five-climbing-ladders.json](../autonomy/experiments/contact-combinations/five-climbing-ladders.json) | Structured experiment data/settings/provenance: five climbing ladders. |
| [five-climbing-search-limit.json](../autonomy/experiments/contact-combinations/five-climbing-search-limit.json) | Structured experiment data/settings/provenance: five climbing search limit. |
| [hanging-vines.json](../autonomy/experiments/contact-combinations/hanging-vines.json) | Structured experiment data/settings/provenance: hanging vines. |
| [ladder-bottom-exit.json](../autonomy/experiments/contact-combinations/ladder-bottom-exit.json) | Structured experiment data/settings/provenance: ladder bottom exit. |
| [ladder-ceiling.json](../autonomy/experiments/contact-combinations/ladder-ceiling.json) | Structured experiment data/settings/provenance: ladder ceiling. |
| [three-climbing-ladders.json](../autonomy/experiments/contact-combinations/three-climbing-ladders.json) | Structured experiment data/settings/provenance: three climbing ladders. |
| [two-ladders.json](../autonomy/experiments/contact-combinations/two-ladders.json) | Structured experiment data/settings/provenance: two ladders. |

## autonomy/experiments/decision-windows

| File | Purpose |
| --- | --- |
| [README.md](../autonomy/experiments/decision-windows/README.md) | Joint decision windows and objective bands. See documentation index for authority/status. |
| [RESULTS.md](../autonomy/experiments/decision-windows/RESULTS.md) | Joint windows: what survives later comparison. See documentation index for authority/status. |
| [analyze_results.py](../autonomy/experiments/decision-windows/analyze_results.py) | Analyze saved decision-window candidate outcomes. |

## autonomy/experiments/decision-windows/evidence

| File | Purpose |
| --- | --- |
| [cold-summary.csv](../autonomy/experiments/decision-windows/evidence/cold-summary.csv) | Tabular per-candidate/call/trajectory measurements; column headings define units. |
| [decision-window-autonomy-tests.log](../autonomy/experiments/decision-windows/evidence/decision-window-autonomy-tests.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [decision-window-diagnostic-final.json.gz](../autonomy/experiments/decision-windows/evidence/decision-window-diagnostic-final.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [decision-window-diagnostic-final.log](../autonomy/experiments/decision-windows/evidence/decision-window-diagnostic-final.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [decision-window-ipopt-tests.log](../autonomy/experiments/decision-windows/evidence/decision-window-ipopt-tests.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [decision-window-numerical-tests.log](../autonomy/experiments/decision-windows/evidence/decision-window-numerical-tests.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [decision-windows-final.json.gz](../autonomy/experiments/decision-windows/evidence/decision-windows-final.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [decision-windows-final.log](../autonomy/experiments/decision-windows/evidence/decision-windows-final.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [horizontal-feature-counterexamples.png](../autonomy/experiments/decision-windows/evidence/horizontal-feature-counterexamples.png) | Rendered diagnostic/trajectory figure from owning experiment evidence. |
| [horizontal-feature-counterexamples.svg](../autonomy/experiments/decision-windows/evidence/horizontal-feature-counterexamples.svg) | Rendered diagnostic/trajectory figure from owning experiment evidence. |
| [manifest.json](../autonomy/experiments/decision-windows/evidence/manifest.json) | Structured experiment data/settings/provenance: manifest. |
| [summary.json](../autonomy/experiments/decision-windows/evidence/summary.json) | Structured experiment data/settings/provenance: summary. |
| [turn-known-prefix-probe.json.gz](../autonomy/experiments/decision-windows/evidence/turn-known-prefix-probe.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |

## autonomy/experiments/decision-windows/fixtures

| File | Purpose |
| --- | --- |
| [ceiling.json](../autonomy/experiments/decision-windows/fixtures/ceiling.json) | Reproducible input/model fixture: ceiling.json; owning tests/experiment define domain and expected acceptance. |
| [grind-or-run.json](../autonomy/experiments/decision-windows/fixtures/grind-or-run.json) | Reproducible input/model fixture: grind-or-run.json; owning tests/experiment define domain and expected acceptance. |
| [multi-support.json](../autonomy/experiments/decision-windows/fixtures/multi-support.json) | Reproducible input/model fixture: multi-support.json; owning tests/experiment define domain and expected acceptance. |
| [open.json](../autonomy/experiments/decision-windows/fixtures/open.json) | Reproducible input/model fixture: open.json; owning tests/experiment define domain and expected acceptance. |
| [step-or-jump.json](../autonomy/experiments/decision-windows/fixtures/step-or-jump.json) | Reproducible input/model fixture: step-or-jump.json; owning tests/experiment define domain and expected acceptance. |
| [turn-blockage.json](../autonomy/experiments/decision-windows/fixtures/turn-blockage.json) | Reproducible input/model fixture: turn-blockage.json; owning tests/experiment define domain and expected acceptance. |

## autonomy/experiments/dfs-interactions

| File | Purpose |
| --- | --- |
| [CALLS.md](../autonomy/experiments/dfs-interactions/CALLS.md) | DFS cold/resume worker calls. See documentation index for authority/status. |
| [README.md](../autonomy/experiments/dfs-interactions/README.md) | Goal-aware DFS and early interaction eligibility. See documentation index for authority/status. |
| [measure_generation.py](../autonomy/experiments/dfs-interactions/measure_generation.py) | Measure current per-block option generation over archived physical contexts, explicitly rebuilding cursors rather than restoring incompatible old ones. |
| [real11-fixture.json](../autonomy/experiments/dfs-interactions/real11-fixture.json) | Structured experiment data/settings/provenance: real11 fixture. |
| [summarize.py](../autonomy/experiments/dfs-interactions/summarize.py) | Reconcile cold/resume frame ownership, worker requests/fingerprints, completed history and independent replays into an evidence summary. |

## autonomy/experiments/dfs-interactions/evidence

| File | Purpose |
| --- | --- |
| [broader-before-probe-fix-tests.log](../autonomy/experiments/dfs-interactions/evidence/broader-before-probe-fix-tests.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [generation-current.json](../autonomy/experiments/dfs-interactions/evidence/generation-current.json) | Structured experiment data/settings/provenance: generation current. |
| [generation.json](../autonomy/experiments/dfs-interactions/evidence/generation.json) | Structured experiment data/settings/provenance: generation. |
| [generation.log](../autonomy/experiments/dfs-interactions/evidence/generation.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [ipopt-tests.log](../autonomy/experiments/dfs-interactions/evidence/ipopt-tests.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [logging-tests.log](../autonomy/experiments/dfs-interactions/evidence/logging-tests.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [manifest.json](../autonomy/experiments/dfs-interactions/evidence/manifest.json) | Structured experiment data/settings/provenance: manifest. |
| [nonladder-tests.log](../autonomy/experiments/dfs-interactions/evidence/nonladder-tests.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [probe-height-tests.log](../autonomy/experiments/dfs-interactions/evidence/probe-height-tests.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [real11-before-stage-scope-fix.json.gz](../autonomy/experiments/dfs-interactions/evidence/real11-before-stage-scope-fix.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [real11-before-stage-scope-fix.log](../autonomy/experiments/dfs-interactions/evidence/real11-before-stage-scope-fix.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [real11-before-step-probe-fix.json.gz](../autonomy/experiments/dfs-interactions/evidence/real11-before-step-probe-fix.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [real11-before-step-probe-fix.log](../autonomy/experiments/dfs-interactions/evidence/real11-before-step-probe-fix.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [real11-cold.json.gz](../autonomy/experiments/dfs-interactions/evidence/real11-cold.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [real11-cold.log](../autonomy/experiments/dfs-interactions/evidence/real11-cold.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [real11-resume.json.gz](../autonomy/experiments/dfs-interactions/evidence/real11-resume.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [real11-resume.log](../autonomy/experiments/dfs-interactions/evidence/real11-resume.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [summary.json](../autonomy/experiments/dfs-interactions/evidence/summary.json) | Structured experiment data/settings/provenance: summary. |
| [validation.json](../autonomy/experiments/dfs-interactions/evidence/validation.json) | Structured experiment data/settings/provenance: validation. |

## autonomy/experiments/failure-fixes

| File | Purpose |
| --- | --- |
| [README.md](../autonomy/experiments/failure-fixes/README.md) | Generic scheduling, incumbent and initialization fixes. See documentation index for authority/status. |

## autonomy/experiments/failure-fixes/evidence

| File | Purpose |
| --- | --- |
| [corridor-repair-coverage-counterexample.json](../autonomy/experiments/failure-fixes/evidence/corridor-repair-coverage-counterexample.json) | Structured experiment data/settings/provenance: corridor repair coverage counterexample. |
| [fix-autonomy-final.log](../autonomy/experiments/failure-fixes/evidence/fix-autonomy-final.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [fix-focused-final.log](../autonomy/experiments/failure-fixes/evidence/fix-focused-final.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [fix-generation.json.gz](../autonomy/experiments/failure-fixes/evidence/fix-generation.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [fix-generation.log](../autonomy/experiments/failure-fixes/evidence/fix-generation.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [fix-ipopt-numerical.log](../autonomy/experiments/failure-fixes/evidence/fix-ipopt-numerical.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [fix-scalar.json.gz](../autonomy/experiments/failure-fixes/evidence/fix-scalar.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [fix-scalar.log](../autonomy/experiments/failure-fixes/evidence/fix-scalar.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [fix-verify.json.gz](../autonomy/experiments/failure-fixes/evidence/fix-verify.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [fix-verify.log](../autonomy/experiments/failure-fixes/evidence/fix-verify.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [fix-warm.json.gz](../autonomy/experiments/failure-fixes/evidence/fix-warm.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [fix-warm.log](../autonomy/experiments/failure-fixes/evidence/fix-warm.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [fix-windows.json.gz](../autonomy/experiments/failure-fixes/evidence/fix-windows.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [fix-windows.log](../autonomy/experiments/failure-fixes/evidence/fix-windows.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [manifest.json](../autonomy/experiments/failure-fixes/evidence/manifest.json) | Structured experiment data/settings/provenance: manifest. |
| [summary.json](../autonomy/experiments/failure-fixes/evidence/summary.json) | Structured experiment data/settings/provenance: summary. |

## autonomy/experiments/failure-rootcause

| File | Purpose |
| --- | --- |
| [README.md](../autonomy/experiments/failure-rootcause/README.md) | Search failure root-cause probes. See documentation index for authority/status. |
| [RESULTS.md](../autonomy/experiments/failure-rootcause/RESULTS.md) | Generic causes of the bounded-search failures. See documentation index for authority/status. |

## autonomy/experiments/failure-rootcause/evidence

| File | Purpose |
| --- | --- |
| [failure-generation-final.json.gz](../autonomy/experiments/failure-rootcause/evidence/failure-generation-final.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [failure-generation-final.json.log](../autonomy/experiments/failure-rootcause/evidence/failure-generation-final.json.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [failure-repair-final.json.gz](../autonomy/experiments/failure-rootcause/evidence/failure-repair-final.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [failure-repair-final.log](../autonomy/experiments/failure-rootcause/evidence/failure-repair-final.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [failure-rootcause-autonomy.log](../autonomy/experiments/failure-rootcause/evidence/failure-rootcause-autonomy.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [failure-rootcause-focused.log](../autonomy/experiments/failure-rootcause/evidence/failure-rootcause-focused.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [failure-scalar-capture.json.gz](../autonomy/experiments/failure-rootcause/evidence/failure-scalar-capture.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [failure-scalar-final.json.gz](../autonomy/experiments/failure-rootcause/evidence/failure-scalar-final.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [failure-scalar-final.log](../autonomy/experiments/failure-rootcause/evidence/failure-scalar-final.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [failure-verify-final.json.gz](../autonomy/experiments/failure-rootcause/evidence/failure-verify-final.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [failure-verify-final.json.log](../autonomy/experiments/failure-rootcause/evidence/failure-verify-final.json.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [failure-warm-final.json.gz](../autonomy/experiments/failure-rootcause/evidence/failure-warm-final.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [failure-warm-final.json.log](../autonomy/experiments/failure-rootcause/evidence/failure-warm-final.json.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [manifest.json](../autonomy/experiments/failure-rootcause/evidence/manifest.json) | Structured experiment data/settings/provenance: manifest. |
| [summary.json](../autonomy/experiments/failure-rootcause/evidence/summary.json) | Structured experiment data/settings/provenance: summary. |

## autonomy/experiments/height-and-stairs

| File | Purpose |
| --- | --- |
| [README.md](../autonomy/experiments/height-and-stairs/README.md) | Different heights and short stair profiles. See documentation index for authority/status. |
| [height-route-request.json](../autonomy/experiments/height-and-stairs/height-route-request.json) | Structured experiment data/settings/provenance: height route request. |
| [height-route-result.json](../autonomy/experiments/height-and-stairs/height-route-result.json) | Structured experiment data/settings/provenance: height route result. |
| [lower-target-request.json](../autonomy/experiments/height-and-stairs/lower-target-request.json) | Structured experiment data/settings/provenance: lower target request. |
| [lower-target-result.json](../autonomy/experiments/height-and-stairs/lower-target-result.json) | Structured experiment data/settings/provenance: lower target result. |
| [staircase-request.json](../autonomy/experiments/height-and-stairs/staircase-request.json) | Structured experiment data/settings/provenance: staircase request. |
| [staircase-result.json](../autonomy/experiments/height-and-stairs/staircase-result.json) | Structured experiment data/settings/provenance: staircase result. |
| [summary.json](../autonomy/experiments/height-and-stairs/summary.json) | Structured experiment data/settings/provenance: summary. |

## autonomy/experiments/interleaved-blockages

| File | Purpose |
| --- | --- |
| [README.md](../autonomy/experiments/interleaved-blockages/README.md) | Interleaved wall and ceiling blockages. See documentation index for authority/status. |
| [request.json](../autonomy/experiments/interleaved-blockages/request.json) | Structured experiment data/settings/provenance: request. |
| [result.json](../autonomy/experiments/interleaved-blockages/result.json) | Structured experiment data/settings/provenance: result. |
| [summary.json](../autonomy/experiments/interleaved-blockages/summary.json) | Structured experiment data/settings/provenance: summary. |

## autonomy/experiments/ladder-climbing-ceiling

| File | Purpose |
| --- | --- |
| [README.md](../autonomy/experiments/ladder-climbing-ceiling/README.md) | Climbing with ceiling contacts and endpoint stops. See documentation index for authority/status. |
| [beginning-stop.json](../autonomy/experiments/ladder-climbing-ceiling/beginning-stop.json) | Structured experiment data/settings/provenance: beginning stop. |
| [both-stops.json](../autonomy/experiments/ladder-climbing-ceiling/both-stops.json) | Structured experiment data/settings/provenance: both stops. |
| [continuous.json](../autonomy/experiments/ladder-climbing-ceiling/continuous.json) | Structured experiment data/settings/provenance: continuous. |
| [end-stop.json](../autonomy/experiments/ladder-climbing-ceiling/end-stop.json) | Structured experiment data/settings/provenance: end stop. |
| [y-heights.tsv](../autonomy/experiments/ladder-climbing-ceiling/y-heights.tsv) | Supporting experiment artifact: y-heights.tsv; read nearest README/manifest for provenance. |

## autonomy/experiments/ladder-schedules

| File | Purpose |
| --- | --- |
| [README.md](../autonomy/experiments/ladder-schedules/README.md) | Ladder schedule height checks. See documentation index for authority/status. |
| [height-evidence.json](../autonomy/experiments/ladder-schedules/height-evidence.json) | Structured experiment data/settings/provenance: height evidence. |

## autonomy/experiments/ladder-search

| File | Purpose |
| --- | --- |
| [README.md](../autonomy/experiments/ladder-search/README.md) | Ladder/vine route search evidence. See documentation index for authority/status. |
| [ladder-prefix-suffix.json](../autonomy/experiments/ladder-search/ladder-prefix-suffix.json) | Structured experiment data/settings/provenance: ladder prefix suffix. |
| [ladder-side-entry.json](../autonomy/experiments/ladder-search/ladder-side-entry.json) | Structured experiment data/settings/provenance: ladder side entry. |
| [ladder-side-landing.json](../autonomy/experiments/ladder-search/ladder-side-landing.json) | Structured experiment data/settings/provenance: ladder side landing. |
| [ladder-thin-top.json](../autonomy/experiments/ladder-search/ladder-thin-top.json) | Structured experiment data/settings/provenance: ladder thin top. |
| [ladder-top.json](../autonomy/experiments/ladder-search/ladder-top.json) | Structured experiment data/settings/provenance: ladder top. |

## autonomy/experiments/last-jump-runs

| File | Purpose |
| --- | --- |
| [README.md](../autonomy/experiments/last-jump-runs/README.md) | Minimum runs before the latest jump. See documentation index for authority/status. |

## autonomy/experiments/last-jump-runs/evidence

| File | Purpose |
| --- | --- |
| [focused-summary.json](../autonomy/experiments/last-jump-runs/evidence/focused-summary.json) | Structured experiment data/settings/provenance: focused summary. |
| [focused-tests.log](../autonomy/experiments/last-jump-runs/evidence/focused-tests.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [generic-focused-summary.json](../autonomy/experiments/last-jump-runs/evidence/generic-focused-summary.json) | Structured experiment data/settings/provenance: generic focused summary. |
| [generic-focused-tests.log](../autonomy/experiments/last-jump-runs/evidence/generic-focused-tests.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |

## autonomy/experiments/linkcraft11-j0-j16

| File | Purpose |
| --- | --- |
| [README.md](../autonomy/experiments/linkcraft11-j0-j16/README.md) | Linkcraft 11, jumps 0 through 16. See documentation index for authority/status. |
| [attempts.csv](../autonomy/experiments/linkcraft11-j0-j16/attempts.csv) | Tabular per-candidate/call/trajectory measurements; column headings define units. |
| [request.json](../autonomy/experiments/linkcraft11-j0-j16/request.json) | Structured experiment data/settings/provenance: request. |
| [solution.json](../autonomy/experiments/linkcraft11-j0-j16/solution.json) | Structured experiment data/settings/provenance: solution. |
| [summary.json](../autonomy/experiments/linkcraft11-j0-j16/summary.json) | Structured experiment data/settings/provenance: summary. |

## autonomy/experiments/linkcraft11-opening

| File | Purpose |
| --- | --- |
| [DeriveSelectedFootprints.java](../autonomy/experiments/linkcraft11-opening/DeriveSelectedFootprints.java) | Demonstrate actual mod neighbor-aware selected-block footprint derivation. |
| [EXECUTION_AUDIT.md](../autonomy/experiments/linkcraft11-opening/EXECUTION_AUDIT.md) | What actually happened in the Real11 opening experiment. See documentation index for authority/status. |
| [INVENTORY.md](../autonomy/experiments/linkcraft11-opening/INVENTORY.md) | Linkcraft Real11 opening support inventory. See documentation index for authority/status. |
| [OTHER_FAILURES.md](../autonomy/experiments/linkcraft11-opening/OTHER_FAILURES.md) | Why the four Real11 searches returned unknown. See documentation index for authority/status. |
| [PLAYER_BOUNDS.md](../autonomy/experiments/linkcraft11-opening/PLAYER_BOUNDS.md) | User-supplied blocks and mod-derived landing bounds. See documentation index for authority/status. |
| [README.md](../autonomy/experiments/linkcraft11-opening/README.md) | Linkcraft Real11 opening: fixed-clock solve and retention counterexample. See documentation index for authority/status. |
| [audit_trace.py](../autonomy/experiments/linkcraft11-opening/audit_trace.py) | Extract initial pilot cells, programs/requests and retention/commit trace. |
| [b1-corner-check.json](../autonomy/experiments/linkcraft11-opening/b1-corner-check.json) | Structured experiment data/settings/provenance: b1 corner check. |
| [b1-corner-regression.log](../autonomy/experiments/linkcraft11-opening/b1-corner-regression.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [bounds-probe-manifest.json](../autonomy/experiments/linkcraft11-opening/bounds-probe-manifest.json) | Structured experiment data/settings/provenance: bounds probe manifest. |
| [control-summary.json](../autonomy/experiments/linkcraft11-opening/control-summary.json) | Structured experiment data/settings/provenance: control summary. |
| [coupling-counterexample.json](../autonomy/experiments/linkcraft11-opening/coupling-counterexample.json) | Structured experiment data/settings/provenance: coupling counterexample. |
| [derived-player-bounds.json](../autonomy/experiments/linkcraft11-opening/derived-player-bounds.json) | Structured experiment data/settings/provenance: derived player bounds. |
| [evidence-manifest.json](../autonomy/experiments/linkcraft11-opening/evidence-manifest.json) | Structured experiment data/settings/provenance: evidence manifest. |
| [measure_setup_crossover.py](../autonomy/experiments/linkcraft11-opening/measure_setup_crossover.py) | Measure common-clock position/velocity and delayed benefit in two saved complete setup-family witnesses. |
| [probe_b2_reachability.py](../autonomy/experiments/linkcraft11-opening/probe_b2_reachability.py) | Diagnose B2 reach/interaction relevance in selected opening contexts. |
| [probe_corridor_failures.py](../autonomy/experiments/linkcraft11-opening/probe_corridor_failures.py) | Inspect corridor rejection contexts and geometry/projection effects. |
| [probe_other_failures.py](../autonomy/experiments/linkcraft11-opening/probe_other_failures.py) | Diagnostic continuation/control probes for the earlier four unknown trials. |
| [replay_solution.py](../autonomy/experiments/linkcraft11-opening/replay_solution.py) | Independently replay the saved complete opening solution. |
| [solution-summary.json](../autonomy/experiments/linkcraft11-opening/solution-summary.json) | Structured experiment data/settings/provenance: solution summary. |
| [solution.json.gz](../autonomy/experiments/linkcraft11-opening/solution.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [summarize_evidence.py](../autonomy/experiments/linkcraft11-opening/summarize_evidence.py) | Summarize original opening experiment evidence and provenance. |
| [summarize_other_failures.py](../autonomy/experiments/linkcraft11-opening/summarize_other_failures.py) | Summarize earlier continuation/repair failure diagnostics. |
| [support-inventory.json](../autonomy/experiments/linkcraft11-opening/support-inventory.json) | Structured experiment data/settings/provenance: support inventory. |
| [trajectory.csv](../autonomy/experiments/linkcraft11-opening/trajectory.csv) | Tabular per-candidate/call/trajectory measurements; column headings define units. |
| [trajectory.png](../autonomy/experiments/linkcraft11-opening/trajectory.png) | Rendered diagnostic/trajectory figure from owning experiment evidence. |
| [trajectory.svg](../autonomy/experiments/linkcraft11-opening/trajectory.svg) | Rendered diagnostic/trajectory figure from owning experiment evidence. |
| [user-blocks-provisional.json](../autonomy/experiments/linkcraft11-opening/user-blocks-provisional.json) | Structured experiment data/settings/provenance: user blocks provisional. |

## autonomy/experiments/linkcraft11-opening/audit

| File | Purpose |
| --- | --- |
| [CANDIDATES.md](../autonomy/experiments/linkcraft11-opening/audit/CANDIDATES.md) | Saved candidate combinations and retention. See documentation index for authority/status. |
| [CELLS.md](../autonomy/experiments/linkcraft11-opening/audit/CELLS.md) | Reconstructed cells. See documentation index for authority/status. |
| [CORRIDOR_FAILURES.md](../autonomy/experiments/linkcraft11-opening/audit/CORRIDOR_FAILURES.md) | Exact Real11 corridor failures. See documentation index for authority/status. |
| [FINAL_REPAIRS.md](../autonomy/experiments/linkcraft11-opening/audit/FINAL_REPAIRS.md) | Winning terminal evaluation: every recorded repair attempt. See documentation index for authority/status. |
| [b2-reachability.json](../autonomy/experiments/linkcraft11-opening/audit/b2-reachability.json) | Structured experiment data/settings/provenance: b2 reachability. |
| [call-accounting.json](../autonomy/experiments/linkcraft11-opening/audit/call-accounting.json) | Structured experiment data/settings/provenance: call accounting. |
| [candidate-ledger.csv](../autonomy/experiments/linkcraft11-opening/audit/candidate-ledger.csv) | Tabular per-candidate/call/trajectory measurements; column headings define units. |
| [candidate-programs.json.gz](../autonomy/experiments/linkcraft11-opening/audit/candidate-programs.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [cells-and-route.json](../autonomy/experiments/linkcraft11-opening/audit/cells-and-route.json) | Structured experiment data/settings/provenance: cells and route. |
| [corridor-failure.png](../autonomy/experiments/linkcraft11-opening/audit/corridor-failure.png) | Rendered diagnostic/trajectory figure from owning experiment evidence. |
| [detailed-evaluations.csv](../autonomy/experiments/linkcraft11-opening/audit/detailed-evaluations.csv) | Tabular per-candidate/call/trajectory measurements; column headings define units. |
| [detailed-repair-branches.json.gz](../autonomy/experiments/linkcraft11-opening/audit/detailed-repair-branches.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [event-aligned-b2.json](../autonomy/experiments/linkcraft11-opening/audit/event-aligned-b2.json) | Structured experiment data/settings/provenance: event aligned b2. |
| [other-failures-summary.json](../autonomy/experiments/linkcraft11-opening/audit/other-failures-summary.json) | Structured experiment data/settings/provenance: other failures summary. |
| [position-velocity-check.json](../autonomy/experiments/linkcraft11-opening/audit/position-velocity-check.json) | Structured experiment data/settings/provenance: position velocity check. |
| [repair-attempts.csv](../autonomy/experiments/linkcraft11-opening/audit/repair-attempts.csv) | Tabular per-candidate/call/trajectory measurements; column headings define units. |
| [route-directions.json](../autonomy/experiments/linkcraft11-opening/audit/route-directions.json) | Structured experiment data/settings/provenance: route directions. |
| [same-clock-check.json](../autonomy/experiments/linkcraft11-opening/audit/same-clock-check.json) | Structured experiment data/settings/provenance: same clock check. |
| [setup-crossover.csv](../autonomy/experiments/linkcraft11-opening/audit/setup-crossover.csv) | Tabular per-candidate/call/trajectory measurements; column headings define units. |
| [setup-crossover.json](../autonomy/experiments/linkcraft11-opening/audit/setup-crossover.json) | Structured experiment data/settings/provenance: setup crossover. |
| [setup-crossover.png](../autonomy/experiments/linkcraft11-opening/audit/setup-crossover.png) | Rendered diagnostic/trajectory figure from owning experiment evidence. |
| [verification.json](../autonomy/experiments/linkcraft11-opening/audit/verification.json) | Structured experiment data/settings/provenance: verification. |
| [windows-and-generation.json](../autonomy/experiments/linkcraft11-opening/audit/windows-and-generation.json) | Structured experiment data/settings/provenance: windows and generation. |
| [winning-cell-visits.json](../autonomy/experiments/linkcraft11-opening/audit/winning-cell-visits.json) | Structured experiment data/settings/provenance: winning cell visits. |
| [winning-fixed-call.json](../autonomy/experiments/linkcraft11-opening/audit/winning-fixed-call.json) | Structured experiment data/settings/provenance: winning fixed call. |

## autonomy/experiments/linkcraft11-opening/evidence

| File | Purpose |
| --- | --- |
| [compiler-used.json](../autonomy/experiments/linkcraft11-opening/evidence/compiler-used.json) | Structured experiment data/settings/provenance: compiler used. |
| [real11-clock64-event-priority.json.gz](../autonomy/experiments/linkcraft11-opening/evidence/real11-clock64-event-priority.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [real11-clock64-event-priority.log](../autonomy/experiments/linkcraft11-opening/evidence/real11-clock64-event-priority.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [real11-clock64-first.json.gz](../autonomy/experiments/linkcraft11-opening/evidence/real11-clock64-first.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [real11-clock64-first.log](../autonomy/experiments/linkcraft11-opening/evidence/real11-clock64-first.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [real11-clock64-near-goals.json.gz](../autonomy/experiments/linkcraft11-opening/evidence/real11-clock64-near-goals.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [real11-clock64-near-goals.log](../autonomy/experiments/linkcraft11-opening/evidence/real11-clock64-near-goals.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [real11-clock64-resumed.json.gz](../autonomy/experiments/linkcraft11-opening/evidence/real11-clock64-resumed.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [real11-clock64-resumed.log](../autonomy/experiments/linkcraft11-opening/evidence/real11-clock64-resumed.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [real11-constructive-probe.json.gz](../autonomy/experiments/linkcraft11-opening/evidence/real11-constructive-probe.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [real11-constructive-probe.log](../autonomy/experiments/linkcraft11-opening/evidence/real11-constructive-probe.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [real11-corridor-failures.json.gz](../autonomy/experiments/linkcraft11-opening/evidence/real11-corridor-failures.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [real11-corridor-probe.json.gz](../autonomy/experiments/linkcraft11-opening/evidence/real11-corridor-probe.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [real11-corridor-probe.log](../autonomy/experiments/linkcraft11-opening/evidence/real11-corridor-probe.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [real11-event-priority-tests.log](../autonomy/experiments/linkcraft11-opening/evidence/real11-event-priority-tests.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [real11-failure-fresh.json.gz](../autonomy/experiments/linkcraft11-opening/evidence/real11-failure-fresh.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [real11-failure-fresh.log](../autonomy/experiments/linkcraft11-opening/evidence/real11-failure-fresh.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [real11-failure-generation.json.gz](../autonomy/experiments/linkcraft11-opening/evidence/real11-failure-generation.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [real11-failure-generation.log](../autonomy/experiments/linkcraft11-opening/evidence/real11-failure-generation.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [real11-failure-numeric.json.gz](../autonomy/experiments/linkcraft11-opening/evidence/real11-failure-numeric.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [real11-failure-numeric.log](../autonomy/experiments/linkcraft11-opening/evidence/real11-failure-numeric.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [real11-failure-priority.json.gz](../autonomy/experiments/linkcraft11-opening/evidence/real11-failure-priority.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [real11-failure-priority.log](../autonomy/experiments/linkcraft11-opening/evidence/real11-failure-priority.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [real11-failure-summary.log](../autonomy/experiments/linkcraft11-opening/evidence/real11-failure-summary.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [real11-final-autonomy.log](../autonomy/experiments/linkcraft11-opening/evidence/real11-final-autonomy.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [real11-final-focused.log](../autonomy/experiments/linkcraft11-opening/evidence/real11-final-focused.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [real11-fixed-clock-contracts.log](../autonomy/experiments/linkcraft11-opening/evidence/real11-fixed-clock-contracts.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [real11-near-goal-contracts.log](../autonomy/experiments/linkcraft11-opening/evidence/real11-near-goal-contracts.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [real11-next-support-probe.json.gz](../autonomy/experiments/linkcraft11-opening/evidence/real11-next-support-probe.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [real11-next-support-probe.log](../autonomy/experiments/linkcraft11-opening/evidence/real11-next-support-probe.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [real11-terminal-variants.json.gz](../autonomy/experiments/linkcraft11-opening/evidence/real11-terminal-variants.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [real11-terminal-variants.log](../autonomy/experiments/linkcraft11-opening/evidence/real11-terminal-variants.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [solution-replay.log](../autonomy/experiments/linkcraft11-opening/evidence/solution-replay.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |

## autonomy/experiments/linkcraft11-opening/evidence/scripts

| File | Purpose |
| --- | --- |
| [probe_real11_corridor.py](../autonomy/experiments/linkcraft11-opening/evidence/scripts/probe_real11_corridor.py) | Historical corridor constraint/signature control; executes at module top level. Inspect before running, never import for discovery. |
| [probe_real11_next_support.py](../autonomy/experiments/linkcraft11-opening/evidence/scripts/probe_real11_next_support.py) | Historical next-support arrival probe; executes at module top level and writes evidence. Check source-era API compatibility. |
| [real11_constructive_probe.py](../autonomy/experiments/linkcraft11-opening/evidence/scripts/real11_constructive_probe.py) | Historical constructive continuation diagnostic using existing choices; seeded control, not cold discovery; top-level execution. |
| [real11_terminal_variants.py](../autonomy/experiments/linkcraft11-opening/evidence/scripts/real11_terminal_variants.py) | Historical terminal jump/run-off variant probes; top-level execution and source-era context, not the current generic entry point. |

## autonomy/experiments/linkcraft11-opening/fixtures

| File | Purpose |
| --- | --- |
| [clock64-event-priority.json](../autonomy/experiments/linkcraft11-opening/fixtures/clock64-event-priority.json) | Reproducible input/model fixture: clock64-event-priority.json; owning tests/experiment define domain and expected acceptance. |
| [clock64-near-goals.json](../autonomy/experiments/linkcraft11-opening/fixtures/clock64-near-goals.json) | Reproducible input/model fixture: clock64-near-goals.json; owning tests/experiment define domain and expected acceptance. |
| [clock64-orchestrator.json](../autonomy/experiments/linkcraft11-opening/fixtures/clock64-orchestrator.json) | Reproducible input/model fixture: clock64-orchestrator.json; owning tests/experiment define domain and expected acceptance. |
| [clock64.json](../autonomy/experiments/linkcraft11-opening/fixtures/clock64.json) | Reproducible input/model fixture: clock64.json; owning tests/experiment define domain and expected acceptance. |

## autonomy/experiments/obstacle-avoidance

| File | Purpose |
| --- | --- |
| [README.md](../autonomy/experiments/obstacle-avoidance/README.md) | Finite obstacle avoidance results. See documentation index for authority/status. |
| [blocked-result.json](../autonomy/experiments/obstacle-avoidance/blocked-result.json) | Structured experiment data/settings/provenance: blocked result. |
| [cross-neo-request.json](../autonomy/experiments/obstacle-avoidance/cross-neo-request.json) | Structured experiment data/settings/provenance: cross neo request. |
| [cross-neo-result.json](../autonomy/experiments/obstacle-avoidance/cross-neo-result.json) | Structured experiment data/settings/provenance: cross neo result. |
| [runoff-side-request.json](../autonomy/experiments/obstacle-avoidance/runoff-side-request.json) | Structured experiment data/settings/provenance: runoff side request. |
| [runoff-side-result.json](../autonomy/experiments/obstacle-avoidance/runoff-side-result.json) | Structured experiment data/settings/provenance: runoff side result. |
| [summary.json](../autonomy/experiments/obstacle-avoidance/summary.json) | Structured experiment data/settings/provenance: summary. |
| [under-ceiling-request.json](../autonomy/experiments/obstacle-avoidance/under-ceiling-request.json) | Structured experiment data/settings/provenance: under ceiling request. |
| [under-ceiling-result.json](../autonomy/experiments/obstacle-avoidance/under-ceiling-result.json) | Structured experiment data/settings/provenance: under ceiling result. |
| [wide-neo-request.json](../autonomy/experiments/obstacle-avoidance/wide-neo-request.json) | Structured experiment data/settings/provenance: wide neo request. |
| [wide-neo-result.json](../autonomy/experiments/obstacle-avoidance/wide-neo-result.json) | Structured experiment data/settings/provenance: wide neo result. |

## autonomy/experiments/one-tick-contacts

| File | Purpose |
| --- | --- |
| [README.md](../autonomy/experiments/one-tick-contacts/README.md) | One-input contact checkpoints. See documentation index for authority/status. |
| [ceiling-request.json](../autonomy/experiments/one-tick-contacts/ceiling-request.json) | Structured experiment data/settings/provenance: ceiling request. |
| [ceiling-result.json](../autonomy/experiments/one-tick-contacts/ceiling-result.json) | Structured experiment data/settings/provenance: ceiling result. |
| [wall-ceiling-request.json](../autonomy/experiments/one-tick-contacts/wall-ceiling-request.json) | Structured experiment data/settings/provenance: wall ceiling request. |
| [wall-ceiling-result.json](../autonomy/experiments/one-tick-contacts/wall-ceiling-result.json) | Structured experiment data/settings/provenance: wall ceiling result. |
| [wall-request.json](../autonomy/experiments/one-tick-contacts/wall-request.json) | Structured experiment data/settings/provenance: wall request. |
| [wall-result.json](../autonomy/experiments/one-tick-contacts/wall-result.json) | Structured experiment data/settings/provenance: wall result. |

## autonomy/experiments/orchestrator-fixes

| File | Purpose |
| --- | --- |
| [LEGACY_RESULTS.md](../autonomy/experiments/orchestrator-fixes/LEGACY_RESULTS.md) | Orchestrator coverage and continuation-state fixes. See documentation index for authority/status. |
| [README.md](../autonomy/experiments/orchestrator-fixes/README.md) | Orchestrator fixes and regression results. See documentation index for authority/status. |
| [audit_candidates.py](../autonomy/experiments/orchestrator-fixes/audit_candidates.py) | Extract/reconcile saved cold reports into exact program/branch/probe/call ledgers; optional independent Java replay. |
| [audit_generation.py](../autonomy/experiments/orchestrator-fixes/audit_generation.py) | Bounded exact-context offline face/gate stream and dedup profiler; no Java/IPOPT calls inside probe. |
| [regressions.py](../autonomy/experiments/orchestrator-fixes/regressions.py) | Seeded fixed-reference terminal solve/resume control and historical dead-fall checks; not cold discovery. |

## autonomy/experiments/orchestrator-fixes/audit

| File | Purpose |
| --- | --- |
| [CANDIDATE_AUDIT.md](../autonomy/experiments/orchestrator-fixes/audit/CANDIDATE_AUDIT.md) | Real11 candidate and numerical-request audit. See documentation index for authority/status. |
| [CANDIDATE_QUESTIONS.md](../autonomy/experiments/orchestrator-fixes/audit/CANDIDATE_QUESTIONS.md) | Real11 candidate questions, answered in order. See documentation index for authority/status. |
| [PRUNING_FOLLOWUP.md](../autonomy/experiments/orchestrator-fixes/audit/PRUNING_FOLLOWUP.md) | Follow-up: what can actually be pruned. See documentation index for authority/status. |
| [WORKER_CALLS.md](../autonomy/experiments/orchestrator-fixes/audit/WORKER_CALLS.md) | Every cold Real11 worker-call slot. See documentation index for authority/status. |
| [b4-prefix-relevance.json](../autonomy/experiments/orchestrator-fixes/audit/b4-prefix-relevance.json) | Structured experiment data/settings/provenance: b4 prefix relevance. |
| [candidate-ledger.json.gz](../autonomy/experiments/orchestrator-fixes/audit/candidate-ledger.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [counts.json](../autonomy/experiments/orchestrator-fixes/audit/counts.json) | Structured experiment data/settings/provenance: counts. |
| [generation-detail.json](../autonomy/experiments/orchestrator-fixes/audit/generation-detail.json) | Structured experiment data/settings/provenance: generation detail. |
| [manifest.json](../autonomy/experiments/orchestrator-fixes/audit/manifest.json) | Structured experiment data/settings/provenance: manifest. |
| [p04-p05-provenance.json.gz](../autonomy/experiments/orchestrator-fixes/audit/p04-p05-provenance.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [program-inclusion.json](../autonomy/experiments/orchestrator-fixes/audit/program-inclusion.json) | Structured experiment data/settings/provenance: program inclusion. |
| [question-evidence.json](../autonomy/experiments/orchestrator-fixes/audit/question-evidence.json) | Structured experiment data/settings/provenance: question evidence. |
| [tests.log](../autonomy/experiments/orchestrator-fixes/audit/tests.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [worker-calls.csv](../autonomy/experiments/orchestrator-fixes/audit/worker-calls.csv) | Tabular per-candidate/call/trajectory measurements; column headings define units. |

## autonomy/experiments/orchestrator-fixes/evidence

| File | Purpose |
| --- | --- |
| [cold-summary.json](../autonomy/experiments/orchestrator-fixes/evidence/cold-summary.json) | Structured experiment data/settings/provenance: cold summary. |
| [fixed-reference-resume.json.gz](../autonomy/experiments/orchestrator-fixes/evidence/fixed-reference-resume.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [implementation-autonomy-numerical-tests.log](../autonomy/experiments/orchestrator-fixes/evidence/implementation-autonomy-numerical-tests.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [implementation-autonomy-tests.log](../autonomy/experiments/orchestrator-fixes/evidence/implementation-autonomy-tests.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [implementation-baseline-ladder.log](../autonomy/experiments/orchestrator-fixes/evidence/implementation-baseline-ladder.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [implementation-contract-tests.log](../autonomy/experiments/orchestrator-fixes/evidence/implementation-contract-tests.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [implementation-final-fast-tests.log](../autonomy/experiments/orchestrator-fixes/evidence/implementation-final-fast-tests.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [implementation-final-focused-tests.log](../autonomy/experiments/orchestrator-fixes/evidence/implementation-final-focused-tests.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [implementation-ipopt-tests.log](../autonomy/experiments/orchestrator-fixes/evidence/implementation-ipopt-tests.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [implementation-manifest.json](../autonomy/experiments/orchestrator-fixes/evidence/implementation-manifest.json) | Structured experiment data/settings/provenance: implementation manifest. |
| [manifest.json](../autonomy/experiments/orchestrator-fixes/evidence/manifest.json) | Structured experiment data/settings/provenance: manifest. |
| [orchestrator-fixed-resume.log](../autonomy/experiments/orchestrator-fixes/evidence/orchestrator-fixed-resume.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [orchestrator-fixes-autonomy.log](../autonomy/experiments/orchestrator-fixes/evidence/orchestrator-fixes-autonomy.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [orchestrator-fixes-focused.log](../autonomy/experiments/orchestrator-fixes/evidence/orchestrator-fixes-focused.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [orchestrator-fixes-resume.json.gz](../autonomy/experiments/orchestrator-fixes/evidence/orchestrator-fixes-resume.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [orchestrator-fixes-resume.log](../autonomy/experiments/orchestrator-fixes/evidence/orchestrator-fixes-resume.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [orchestrator-fixes-warm.json.gz](../autonomy/experiments/orchestrator-fixes/evidence/orchestrator-fixes-warm.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [orchestrator-fixes-warm.log](../autonomy/experiments/orchestrator-fixes/evidence/orchestrator-fixes-warm.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [orchestrator-fixes-windows.json.gz](../autonomy/experiments/orchestrator-fixes/evidence/orchestrator-fixes-windows.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [orchestrator-fixes-windows.log](../autonomy/experiments/orchestrator-fixes/evidence/orchestrator-fixes-windows.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [orchestrator-real11-stable-resume.json.gz](../autonomy/experiments/orchestrator-fixes/evidence/orchestrator-real11-stable-resume.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [orchestrator-real11-stable-resume.log](../autonomy/experiments/orchestrator-fixes/evidence/orchestrator-real11-stable-resume.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [orchestrator-real11-stable-resume2.json.gz](../autonomy/experiments/orchestrator-fixes/evidence/orchestrator-real11-stable-resume2.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [orchestrator-real11-stable-resume2.log](../autonomy/experiments/orchestrator-fixes/evidence/orchestrator-real11-stable-resume2.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [orchestrator-real11-stable.json.gz](../autonomy/experiments/orchestrator-fixes/evidence/orchestrator-real11-stable.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [orchestrator-real11-stable.log](../autonomy/experiments/orchestrator-fixes/evidence/orchestrator-real11-stable.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [orchestrator-two-jump-audit.log](../autonomy/experiments/orchestrator-fixes/evidence/orchestrator-two-jump-audit.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [orchestrator-two-jump-search.log](../autonomy/experiments/orchestrator-fixes/evidence/orchestrator-two-jump-search.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [summary.json](../autonomy/experiments/orchestrator-fixes/evidence/summary.json) | Structured experiment data/settings/provenance: summary. |
| [two-jump-audit.json.gz](../autonomy/experiments/orchestrator-fixes/evidence/two-jump-audit.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [two-jump-search.json.gz](../autonomy/experiments/orchestrator-fixes/evidence/two-jump-search.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |

## autonomy/experiments/route-guidance

| File | Purpose |
| --- | --- |
| [README.md](../autonomy/experiments/route-guidance/README.md) | Route-guidance model comparison. See documentation index for authority/status. |

## autonomy/experiments/route-guidance/evidence

| File | Purpose |
| --- | --- |
| [aggregate.json](../autonomy/experiments/route-guidance/evidence/aggregate.json) | Structured experiment data/settings/provenance: aggregate. |
| [conditional-rankings.svg](../autonomy/experiments/route-guidance/evidence/conditional-rankings.svg) | Rendered diagnostic/trajectory figure from owning experiment evidence. |
| [guidance-autonomy-tests.log](../autonomy/experiments/route-guidance/evidence/guidance-autonomy-tests.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [guidance-autonomy-with-baseline-exclusion.log](../autonomy/experiments/route-guidance/evidence/guidance-autonomy-with-baseline-exclusion.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [guidance-baseline-ladder-test.log](../autonomy/experiments/route-guidance/evidence/guidance-baseline-ladder-test.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [guidance-focused-tests.log](../autonomy/experiments/route-guidance/evidence/guidance-focused-tests.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [guidance-inertia-ablation.json](../autonomy/experiments/route-guidance/evidence/guidance-inertia-ablation.json) | Structured experiment data/settings/provenance: guidance inertia ablation. |
| [guidance-ipopt-tests.log](../autonomy/experiments/route-guidance/evidence/guidance-ipopt-tests.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [measurements.json.gz](../autonomy/experiments/route-guidance/evidence/measurements.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [model-times.svg](../autonomy/experiments/route-guidance/evidence/model-times.svg) | Rendered diagnostic/trajectory figure from owning experiment evidence. |
| [narrow-long-refinement.json](../autonomy/experiments/route-guidance/evidence/narrow-long-refinement.json) | Structured experiment data/settings/provenance: narrow long refinement. |
| [reference-nonuniqueness.svg](../autonomy/experiments/route-guidance/evidence/reference-nonuniqueness.svg) | Rendered diagnostic/trajectory figure from owning experiment evidence. |
| [reference-trajectories.png](../autonomy/experiments/route-guidance/evidence/reference-trajectories.png) | Rendered diagnostic/trajectory figure from owning experiment evidence. |
| [reference-trajectories.svg](../autonomy/experiments/route-guidance/evidence/reference-trajectories.svg) | Rendered diagnostic/trajectory figure from owning experiment evidence. |
| [summaries.json](../autonomy/experiments/route-guidance/evidence/summaries.json) | Structured experiment data/settings/provenance: summaries. |
| [timing-unknown-diagnostic.json](../autonomy/experiments/route-guidance/evidence/timing-unknown-diagnostic.json) | Structured experiment data/settings/provenance: timing unknown diagnostic. |
| [validation.json](../autonomy/experiments/route-guidance/evidence/validation.json) | Structured experiment data/settings/provenance: validation. |

## autonomy/experiments/route-guidance/fixtures

| File | Purpose |
| --- | --- |
| [consecutive-passage.json](../autonomy/experiments/route-guidance/fixtures/consecutive-passage.json) | Reproducible input/model fixture: consecutive-passage.json; owning tests/experiment define domain and expected acceptance. |
| [cross-passage.json](../autonomy/experiments/route-guidance/fixtures/cross-passage.json) | Reproducible input/model fixture: cross-passage.json; owning tests/experiment define domain and expected acceptance. |
| [five-jump-control.json](../autonomy/experiments/route-guidance/fixtures/five-jump-control.json) | Reproducible input/model fixture: five-jump-control.json; owning tests/experiment define domain and expected acceptance. |
| [narrow-fast-entry.json](../autonomy/experiments/route-guidance/fixtures/narrow-fast-entry.json) | Reproducible input/model fixture: narrow-fast-entry.json; owning tests/experiment define domain and expected acceptance. |
| [open.json](../autonomy/experiments/route-guidance/fixtures/open.json) | Reproducible input/model fixture: open.json; owning tests/experiment define domain and expected acceptance. |
| [reverse-objective.json](../autonomy/experiments/route-guidance/fixtures/reverse-objective.json) | Reproducible input/model fixture: reverse-objective.json; owning tests/experiment define domain and expected acceptance. |
| [run-allocation.json](../autonomy/experiments/route-guidance/fixtures/run-allocation.json) | Reproducible input/model fixture: run-allocation.json; owning tests/experiment define domain and expected acceptance. |
| [timing-and-passage.json](../autonomy/experiments/route-guidance/fixtures/timing-and-passage.json) | Reproducible input/model fixture: timing-and-passage.json; owning tests/experiment define domain and expected acceptance. |
| [turning-support.json](../autonomy/experiments/route-guidance/fixtures/turning-support.json) | Reproducible input/model fixture: turning-support.json; owning tests/experiment define domain and expected acceptance. |

## autonomy/experiments/runoff-profiles

| File | Purpose |
| --- | --- |
| [README.md](../autonomy/experiments/runoff-profiles/README.md) | Automatically selected run-off profiles. See documentation index for authority/status. |
| [jump-runoff-request.json](../autonomy/experiments/runoff-profiles/jump-runoff-request.json) | Structured experiment data/settings/provenance: jump runoff request. |
| [jump-runoff-result.json](../autonomy/experiments/runoff-profiles/jump-runoff-result.json) | Structured experiment data/settings/provenance: jump runoff result. |
| [runoff-request.json](../autonomy/experiments/runoff-profiles/runoff-request.json) | Structured experiment data/settings/provenance: runoff request. |
| [runoff-result.json](../autonomy/experiments/runoff-profiles/runoff-result.json) | Structured experiment data/settings/provenance: runoff result. |
| [summary.json](../autonomy/experiments/runoff-profiles/summary.json) | Structured experiment data/settings/provenance: summary. |

## autonomy/experiments/stepping-mechanics

| File | Purpose |
| --- | --- |
| [README.md](../autonomy/experiments/stepping-mechanics/README.md) | Legacy stepping investigation. See documentation index for authority/status. |
| [StepMechanicsProbe.java](../autonomy/experiments/stepping-mechanics/StepMechanicsProbe.java) | Probe shared stepping/collision semantics with scalar and scene evidence. |
| [probe-results.txt](../autonomy/experiments/stepping-mechanics/probe-results.txt) | Captured commands/test/runtime output or investigation notes; source-era evidence. |

## autonomy/experiments/stratfinder-review

| File | Purpose |
| --- | --- |
| [README.md](../autonomy/experiments/stratfinder-review/README.md) | Stratfinder source-review probe. See documentation index for authority/status. |
| [probe-result.txt](../autonomy/experiments/stratfinder-review/probe-result.txt) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [probe.cpp](../autonomy/experiments/stratfinder-review/probe.cpp) | Supporting experiment artifact: probe.cpp; read nearest README/manifest for provenance. |
| [summary.json](../autonomy/experiments/stratfinder-review/summary.json) | Structured experiment data/settings/provenance: summary. |

## autonomy/experiments/ticks-and-objective

| File | Purpose |
| --- | --- |
| [summary.json](../autonomy/experiments/ticks-and-objective/summary.json) | Structured experiment data/settings/provenance: summary. |

## autonomy/experiments/two-goal-comparisons

| File | Purpose |
| --- | --- |
| [README.md](../autonomy/experiments/two-goal-comparisons/README.md) | Separate spatial-time and fixed-clock comparisons. See documentation index for authority/status. |

## autonomy/experiments/two-goal-comparisons/evidence

| File | Purpose |
| --- | --- |
| [manifest.json](../autonomy/experiments/two-goal-comparisons/evidence/manifest.json) | Structured experiment data/settings/provenance: manifest. |
| [summary.json](../autonomy/experiments/two-goal-comparisons/evidence/summary.json) | Structured experiment data/settings/provenance: summary. |
| [two-goal-audit.json.gz](../autonomy/experiments/two-goal-comparisons/evidence/two-goal-audit.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [two-goal-audit.log](../autonomy/experiments/two-goal-comparisons/evidence/two-goal-audit.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [two-goal-autonomy-final.log](../autonomy/experiments/two-goal-comparisons/evidence/two-goal-autonomy-final.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [two-goal-competitive-audit.json.gz](../autonomy/experiments/two-goal-comparisons/evidence/two-goal-competitive-audit.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [two-goal-competitive-audit.log](../autonomy/experiments/two-goal-comparisons/evidence/two-goal-competitive-audit.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [two-goal-contract-final.log](../autonomy/experiments/two-goal-comparisons/evidence/two-goal-contract-final.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [two-goal-focused-final.log](../autonomy/experiments/two-goal-comparisons/evidence/two-goal-focused-final.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [two-goal-search-concurrent-tests.json.gz](../autonomy/experiments/two-goal-comparisons/evidence/two-goal-search-concurrent-tests.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [two-goal-search-concurrent-tests.log](../autonomy/experiments/two-goal-comparisons/evidence/two-goal-search-concurrent-tests.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [two-goal-search.json.gz](../autonomy/experiments/two-goal-comparisons/evidence/two-goal-search.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [two-goal-search.log](../autonomy/experiments/two-goal-comparisons/evidence/two-goal-search.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [two-goal-separated-audit.json.gz](../autonomy/experiments/two-goal-comparisons/evidence/two-goal-separated-audit.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [two-goal-separated-audit.log](../autonomy/experiments/two-goal-comparisons/evidence/two-goal-separated-audit.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [two-goal-separated-search.json.gz](../autonomy/experiments/two-goal-comparisons/evidence/two-goal-separated-search.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [two-goal-separated-search.log](../autonomy/experiments/two-goal-comparisons/evidence/two-goal-separated-search.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |

## autonomy/experiments/two-goal-comparisons/fixtures

| File | Purpose |
| --- | --- |
| [separated-two-jumps-reverse.json](../autonomy/experiments/two-goal-comparisons/fixtures/separated-two-jumps-reverse.json) | Reproducible input/model fixture: separated-two-jumps-reverse.json; owning tests/experiment define domain and expected acceptance. |
| [separated-two-jumps.json](../autonomy/experiments/two-goal-comparisons/fixtures/separated-two-jumps.json) | Reproducible input/model fixture: separated-two-jumps.json; owning tests/experiment define domain and expected acceptance. |

## autonomy/experiments/virtual-finish

| File | Purpose |
| --- | --- |
| [CALLS.md](../autonomy/experiments/virtual-finish/CALLS.md) | Numerical request ledger. See documentation index for authority/status. |
| [PRUNING_AUDIT.md](../autonomy/experiments/virtual-finish/PRUNING_AUDIT.md) | Why these requests survived pruning. See documentation index for authority/status. |
| [PRUNING_AUDIT_CHECKS.json](../autonomy/experiments/virtual-finish/PRUNING_AUDIT_CHECKS.json) | Structured experiment data/settings/provenance: PRUNING AUDIT CHECKS. |
| [README.md](../autonomy/experiments/virtual-finish/README.md) | Real11 virtual finish. See documentation index for authority/status. |
| [fixture.json](../autonomy/experiments/virtual-finish/fixture.json) | Structured experiment data/settings/provenance: fixture. |
| [initial0-fixture.json](../autonomy/experiments/virtual-finish/initial0-fixture.json) | Structured experiment data/settings/provenance: initial0 fixture. |

## autonomy/experiments/virtual-finish/evidence

| File | Purpose |
| --- | --- |
| [cold.json.gz](../autonomy/experiments/virtual-finish/evidence/cold.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [cold.log](../autonomy/experiments/virtual-finish/evidence/cold.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [initial0-cold.json.gz](../autonomy/experiments/virtual-finish/evidence/initial0-cold.json.gz) | Compressed exact result/request/continuation evidence; inspect owning experiment and source fields before reuse. |
| [initial0-cold.log](../autonomy/experiments/virtual-finish/evidence/initial0-cold.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [manifest.json](../autonomy/experiments/virtual-finish/evidence/manifest.json) | Structured experiment data/settings/provenance: manifest. |
| [nonladder-tests.log](../autonomy/experiments/virtual-finish/evidence/nonladder-tests.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |
| [summary.json](../autonomy/experiments/virtual-finish/evidence/summary.json) | Structured experiment data/settings/provenance: summary. |
| [terminal-resume-tests.log](../autonomy/experiments/virtual-finish/evidence/terminal-resume-tests.log) | Captured commands/test/runtime output or investigation notes; source-era evidence. |

## autonomy/fixtures

| File | Purpose |
| --- | --- |
| [airborne-ladder-transfer.json](../autonomy/fixtures/airborne-ladder-transfer.json) | Reproducible input/model fixture: airborne-ladder-transfer.json; owning tests/experiment define domain and expected acceptance. |
| [appended-air-ceiling.json](../autonomy/fixtures/appended-air-ceiling.json) | Reproducible input/model fixture: appended-air-ceiling.json; owning tests/experiment define domain and expected acceptance. |
| [appended-ladder-ceiling.json](../autonomy/fixtures/appended-ladder-ceiling.json) | Reproducible input/model fixture: appended-ladder-ceiling.json; owning tests/experiment define domain and expected acceptance. |
| [backtrack.json](../autonomy/fixtures/backtrack.json) | Reproducible input/model fixture: backtrack.json; owning tests/experiment define domain and expected acceptance. |
| [blocks-flat-two-jumps.json](../autonomy/fixtures/blocks-flat-two-jumps.json) | Reproducible input/model fixture: blocks-flat-two-jumps.json; owning tests/experiment define domain and expected acceptance. |
| [blocks-height-route.json](../autonomy/fixtures/blocks-height-route.json) | Reproducible input/model fixture: blocks-height-route.json; owning tests/experiment define domain and expected acceptance. |
| [blocks-jump-runoff.json](../autonomy/fixtures/blocks-jump-runoff.json) | Reproducible input/model fixture: blocks-jump-runoff.json; owning tests/experiment define domain and expected acceptance. |
| [blocks-lower-target.json](../autonomy/fixtures/blocks-lower-target.json) | Reproducible input/model fixture: blocks-lower-target.json; owning tests/experiment define domain and expected acceptance. |
| [blocks-momentum-choice.json](../autonomy/fixtures/blocks-momentum-choice.json) | Reproducible input/model fixture: blocks-momentum-choice.json; owning tests/experiment define domain and expected acceptance. |
| [blocks-required-run.json](../autonomy/fixtures/blocks-required-run.json) | Reproducible input/model fixture: blocks-required-run.json; owning tests/experiment define domain and expected acceptance. |
| [blocks-route-choice.json](../autonomy/fixtures/blocks-route-choice.json) | Reproducible input/model fixture: blocks-route-choice.json; owning tests/experiment define domain and expected acceptance. |
| [blocks-runoff.json](../autonomy/fixtures/blocks-runoff.json) | Reproducible input/model fixture: blocks-runoff.json; owning tests/experiment define domain and expected acceptance. |
| [blocks-staircase.json](../autonomy/fixtures/blocks-staircase.json) | Reproducible input/model fixture: blocks-staircase.json; owning tests/experiment define domain and expected acceptance. |
| [ceiling-bonk.json](../autonomy/fixtures/ceiling-bonk.json) | Reproducible input/model fixture: ceiling-bonk.json; owning tests/experiment define domain and expected acceptance. |
| [contact-wide-neo.json](../autonomy/fixtures/contact-wide-neo.json) | Reproducible input/model fixture: contact-wide-neo.json; owning tests/experiment define domain and expected acceptance. |
| [final-air-ceiling.json](../autonomy/fixtures/final-air-ceiling.json) | Reproducible input/model fixture: final-air-ceiling.json; owning tests/experiment define domain and expected acceptance. |
| [five-airborne-ladders.json](../autonomy/fixtures/five-airborne-ladders.json) | Reproducible input/model fixture: five-airborne-ladders.json; owning tests/experiment define domain and expected acceptance. |
| [five-climbing-ladders.json](../autonomy/fixtures/five-climbing-ladders.json) | Reproducible input/model fixture: five-climbing-ladders.json; owning tests/experiment define domain and expected acceptance. |
| [generic-grind-blip.json](../autonomy/fixtures/generic-grind-blip.json) | Reproducible input/model fixture: generic-grind-blip.json; owning tests/experiment define domain and expected acceptance. |
| [generic-repeat-ceiling.json](../autonomy/fixtures/generic-repeat-ceiling.json) | Reproducible input/model fixture: generic-repeat-ceiling.json; owning tests/experiment define domain and expected acceptance. |
| [hanging-vines.json](../autonomy/fixtures/hanging-vines.json) | Reproducible input/model fixture: hanging-vines.json; owning tests/experiment define domain and expected acceptance. |
| [ladder-bottom-exit.json](../autonomy/fixtures/ladder-bottom-exit.json) | Reproducible input/model fixture: ladder-bottom-exit.json; owning tests/experiment define domain and expected acceptance. |
| [ladder-ceiling.json](../autonomy/fixtures/ladder-ceiling.json) | Reproducible input/model fixture: ladder-ceiling.json; owning tests/experiment define domain and expected acceptance. |
| [ladder-climbing-ceiling.json](../autonomy/fixtures/ladder-climbing-ceiling.json) | Reproducible input/model fixture: ladder-climbing-ceiling.json; owning tests/experiment define domain and expected acceptance. |
| [ladder-prefix-suffix.json](../autonomy/fixtures/ladder-prefix-suffix.json) | Reproducible input/model fixture: ladder-prefix-suffix.json; owning tests/experiment define domain and expected acceptance. |
| [ladder-schedule-generator.json](../autonomy/fixtures/ladder-schedule-generator.json) | Reproducible input/model fixture: ladder-schedule-generator.json; owning tests/experiment define domain and expected acceptance. |
| [ladder-side-entry.json](../autonomy/fixtures/ladder-side-entry.json) | Reproducible input/model fixture: ladder-side-entry.json; owning tests/experiment define domain and expected acceptance. |
| [ladder-side-landing.json](../autonomy/fixtures/ladder-side-landing.json) | Reproducible input/model fixture: ladder-side-landing.json; owning tests/experiment define domain and expected acceptance. |
| [ladder-thin-top.json](../autonomy/fixtures/ladder-thin-top.json) | Reproducible input/model fixture: ladder-thin-top.json; owning tests/experiment define domain and expected acceptance. |
| [ladder-top.json](../autonomy/fixtures/ladder-top.json) | Reproducible input/model fixture: ladder-top.json; owning tests/experiment define domain and expected acceptance. |
| [linkcraft11-j0-j16.json](../autonomy/fixtures/linkcraft11-j0-j16.json) | Reproducible input/model fixture: linkcraft11-j0-j16.json; owning tests/experiment define domain and expected acceptance. |
| [linkcraft11-j5-j9.json](../autonomy/fixtures/linkcraft11-j5-j9.json) | Reproducible input/model fixture: linkcraft11-j5-j9.json; owning tests/experiment define domain and expected acceptance. |
| [obstacle-blocked.json](../autonomy/fixtures/obstacle-blocked.json) | Reproducible input/model fixture: obstacle-blocked.json; owning tests/experiment define domain and expected acceptance. |
| [obstacle-cross-neo.json](../autonomy/fixtures/obstacle-cross-neo.json) | Reproducible input/model fixture: obstacle-cross-neo.json; owning tests/experiment define domain and expected acceptance. |
| [obstacle-interleaved.json](../autonomy/fixtures/obstacle-interleaved.json) | Reproducible input/model fixture: obstacle-interleaved.json; owning tests/experiment define domain and expected acceptance. |
| [obstacle-runoff-side.json](../autonomy/fixtures/obstacle-runoff-side.json) | Reproducible input/model fixture: obstacle-runoff-side.json; owning tests/experiment define domain and expected acceptance. |
| [obstacle-under-ceiling.json](../autonomy/fixtures/obstacle-under-ceiling.json) | Reproducible input/model fixture: obstacle-under-ceiling.json; owning tests/experiment define domain and expected acceptance. |
| [obstacle-wide-neo.json](../autonomy/fixtures/obstacle-wide-neo.json) | Reproducible input/model fixture: obstacle-wide-neo.json; owning tests/experiment define domain and expected acceptance. |
| [one-required-run-tick.json](../autonomy/fixtures/one-required-run-tick.json) | Reproducible input/model fixture: one-required-run-tick.json; owning tests/experiment define domain and expected acceptance. |
| [three-climbing-ladders.json](../autonomy/fixtures/three-climbing-ladders.json) | Reproducible input/model fixture: three-climbing-ladders.json; owning tests/experiment define domain and expected acceptance. |
| [turning-two-jumps.json](../autonomy/fixtures/turning-two-jumps.json) | Reproducible input/model fixture: turning-two-jumps.json; owning tests/experiment define domain and expected acceptance. |
| [two-jumps.json](../autonomy/fixtures/two-jumps.json) | Reproducible input/model fixture: two-jumps.json; owning tests/experiment define domain and expected acceptance. |
| [two-ladders.json](../autonomy/fixtures/two-ladders.json) | Reproducible input/model fixture: two-ladders.json; owning tests/experiment define domain and expected acceptance. |
| [wall-ceiling-reversal.json](../autonomy/fixtures/wall-ceiling-reversal.json) | Reproducible input/model fixture: wall-ceiling-reversal.json; owning tests/experiment define domain and expected acceptance. |
| [wall-reversal.json](../autonomy/fixtures/wall-reversal.json) | Reproducible input/model fixture: wall-reversal.json; owning tests/experiment define domain and expected acceptance. |
| [xyz-contact-corner.json](../autonomy/fixtures/xyz-contact-corner.json) | Reproducible input/model fixture: xyz-contact-corner.json; owning tests/experiment define domain and expected acceptance. |

## autonomy/fixtures/stepping

| File | Purpose |
| --- | --- |
| [blip-wall.json](../autonomy/fixtures/stepping/blip-wall.json) | Reproducible input/model fixture: blip-wall.json; owning tests/experiment define domain and expected acceptance. |
| [blip.json](../autonomy/fixtures/stepping/blip.json) | Reproducible input/model fixture: blip.json; owning tests/experiment define domain and expected acceptance. |
| [blip_up-jump.json](../autonomy/fixtures/stepping/blip_up-jump.json) | Reproducible input/model fixture: blip_up-jump.json; owning tests/experiment define domain and expected acceptance. |
| [blip_up.json](../autonomy/fixtures/stepping/blip_up.json) | Reproducible input/model fixture: blip_up.json; owning tests/experiment define domain and expected acceptance. |
| [corner_blip_up.json](../autonomy/fixtures/stepping/corner_blip_up.json) | Reproducible input/model fixture: corner_blip_up.json; owning tests/experiment define domain and expected acceptance. |
| [corner_jump_cancel.json](../autonomy/fixtures/stepping/corner_jump_cancel.json) | Reproducible input/model fixture: corner_jump_cancel.json; owning tests/experiment define domain and expected acceptance. |
| [grind.json](../autonomy/fixtures/stepping/grind.json) | Reproducible input/model fixture: grind.json; owning tests/experiment define domain and expected acceptance. |
| [normal_step.json](../autonomy/fixtures/stepping/normal_step.json) | Reproducible input/model fixture: normal_step.json; owning tests/experiment define domain and expected acceptance. |
| [slab_boost.json](../autonomy/fixtures/stepping/slab_boost.json) | Reproducible input/model fixture: slab_boost.json; owning tests/experiment define domain and expected acceptance. |

## autonomy/java

| File | Purpose |
| --- | --- |
| [CompileRunTicksPhysics.java](../autonomy/java/CompileRunTicksPhysics.java) | JSON bridge to existing movement coefficient compiler, scene and geometry operations; reused by Python orchestration. |

## autonomy/route_guidance

| File | Purpose |
| --- | --- |
| [README.md](../autonomy/route_guidance/README.md) | Experimental route guidance. See documentation index for authority/status. |
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
| [requirements-plot.txt](../autonomy/route_guidance/requirements-plot.txt) | Pinned optional/runtime dependencies; role determined by package and include chain. |
| [requirements.txt](../autonomy/route_guidance/requirements.txt) | Pinned optional/runtime dependencies; role determined by package and include chain. |

## autonomy/tests

| File | Purpose |
| --- | --- |
| [test_acceptance.py](../autonomy/tests/test_acceptance.py) | Tests: numerical row tolerance cannot make a nonoverlapping landing valid; compiler failure is reported without searching more ticks. |
| [test_block_contacts.py](../autonomy/tests/test_block_contacts.py) | Tests: runoff checks the departure floor on its first input; no jump first movement uses its own step eligibility; ordinary profile must avoid an unplanned first movement stair cancel (additional cases in file). |
| [test_block_profiles.py](../autonomy/tests/test_block_profiles.py) | Tests: runoff profiles use the same vertical model without a jump impulse; runoff cannot rise and respects its simulation horizon; half block stair has a takeoff and one airborne input (additional cases in file). |
| [test_block_route_witnesses.py](../autonomy/tests/test_block_route_witnesses.py) | Tests: saved interleaving replays with per block caps and shared collision checks; saved obstacle routes pass numerical and shared swept collision checks; saved runoffs validate no jump physics and actual source exits (additional cases in file). |
| [test_block_search.py](../autonomy/tests/test_block_search.py) | Tests: unordered supports and fixed inputs; unimplemented geometry and duplicate ids are rejected; repeated visits compile distinct events without losing support identity (additional cases in file). |
| [test_candidate_audit.py](../autonomy/tests/test_candidate_audit.py) | Tests: saved cold calls reconcile without counting resume snapshots twice; missing validation record keeps every possible alignment; missing call records are not reported as ipopt failures. |
| [test_ceiling_contacts.py](../autonomy/tests/test_ceiling_contacts.py) | Tests: existing y model regenerates single hit and shorter landing; same input ceiling and wall hit use one coupled movement schedule; geometry discovers bonk and graph bound includes shorter profile. |
| [test_cell_experiment.py](../autonomy/tests/test_cell_experiment.py) | Tests: retry budget counts actual worker calls; prescribed points do not remove remote free space; one input step family exhausts without losing complete programs (additional cases in file). |
| [test_cell_geometry.py](../autonomy/tests/test_cell_geometry.py) | Tests: random points and disjoint interiors match obstacle union; contact boundary between floor and exact body height ceiling survives; partial portals and physical contact provenance (additional cases in file). |
| [test_cell_local.py](../autonomy/tests/test_cell_local.py) | Tests: early filter removes remote support before choice product; ladder region filter runs before schedule generation; contact filter preserves original solid indices (additional cases in file). |
| [test_cell_routes.py](../autonomy/tests/test_cell_routes.py) | Tests: support corner keeps both free regions without filling the obstacle; both obstacle sides and overflight are distinct proposals; prescribed spatial points hide timing and preserve crossing side (additional cases in file). |
| [test_checkpoint_guidance.py](../autonomy/tests/test_checkpoint_guidance.py) | Tests: shared checkpoint has horizontal direction and no y objective; comparison respects ordered gate crossings on a revisit; checkpoint rejects a state past the next turn (additional cases in file). |
| [test_climbable_search.py](../autonomy/tests/test_climbable_search.py) | Tests: run allocations are incremental and bounded; invalid cells settings and conflicting metadata are rejected; ladder events retime keys hits membership and y together (additional cases in file). |
| [test_comparison_goals.py](../autonomy/tests/test_comparison_goals.py) | Tests: goal validation uses real blocks and separate region semantics; landing does not mean airborne or grounded above the block; region arrival cannot use a gate crossed later (additional cases in file). |
| [test_contact_combinations.py](../autonomy/tests/test_contact_combinations.py) | Tests: selected hit can touch its avoidance face without erasing other clearance; every same tick axis subset and two tick pair is generated; free gap is independent but full ladder sequence is combined (additional cases in file). |
| [test_contacts.py](../autonomy/tests/test_contacts.py) | Tests: full route keeps all jumps runoff step and run cost; reduced jump must enter target on first movement; step landing is after horizontal movement with no early landing (additional cases in file). |
| [test_corridor_guards.py](../autonomy/tests/test_corridor_guards.py) | Tests: projection failure logs exact phase and detached replay data; signature failure logs expected and actual crossing; crossing adjacent rectangles is preserved in both axes and directions (additional cases in file). |
| [test_decision_windows.py](../autonomy/tests/test_decision_windows.py) | Tests: fixture domains include the known physical controls; joint cluster runs to the next input not one state earlier; dependencies merge the intervening decisions (additional cases in file). |
| [test_depth_first.py](../autonomy/tests/test_depth_first.py) | Tests: common goal arrival wins and completes before later sibling; deadline preserves incumbent and unvisited sibling without redispatch; in flight state and identity survive a mid solve stop (additional cases in file). |
| [test_failure_diagnostic.py](../autonomy/tests/test_failure_diagnostic.py) | Tests: instrumented baseline preserves generator outputs and counts; completed candidate priority is separate from partial length; tick fair pruning preserves multiple elapsed buckets (additional cases in file). |
| [test_fixed_clock_goal.py](../autonomy/tests/test_fixed_clock_goal.py) | Tests: priority proposes the actual intermediate contact before an air gap; unknown spatial goal is rejected before compilation; explicit clock preserves direction without goal region (additional cases in file). |
| [test_full_route_witness.py](../autonomy/tests/test_full_route_witness.py) | Tests: full linkcraft 0 to 16 solution is independently valid. |
| [test_generic_composition.py](../autonomy/tests/test_generic_composition.py) | Tests: repeated supported units do not stop replay at first landing; unbounded repeated event types share the same append contract; grind can end in air then choose another movement type (additional cases in file). |
| [test_hanging_vines.py](../autonomy/tests/test_hanging_vines.py) | Tests: falling region does not require a wall or hide real wall choices; all four horizontal entry faces remain available; wall free generation keeps all sneak masks without phantom climbing (additional cases in file). |
| [test_hit_events.py](../autonomy/tests/test_hit_events.py) | Tests: relative hit and all avoidance rows follow inserted runs; generation preserves fixed height and reports candidate limit; geometry discovers reversal for x z and both signs. |
| [test_integration.py](../autonomy/tests/test_integration.py) | Tests: obstacles can interleave without finishing one block first; full obstacle geometry finds cross and wide neos and checks both models; source runoff can leave the near side and stays clear while y overlaps (additional cases in file). |
| [test_ladder_bridge.py](../autonomy/tests/test_ladder_bridge.py) | Tests: columns preserve holes and signed front side channels; straight line ranking evaluates progress and caps; shared compiler realizes sneak and repeated wall sprint loss (additional cases in file). |
| [test_ladder_ceiling.py](../autonomy/tests/test_ladder_ceiling.py) | Tests: ownership is idempotent finite and does not rewrite controls or physics; only climbing successors are accepted and stops stay inside climbing; actual clip and unclipped endpoint stops produce distinct heights (additional cases in file). |
| [test_ladder_composition.py](../autonomy/tests/test_ladder_composition.py) | Tests: expired generation budget retains unknown status; horizontal vertical strips merge without diagonal or hole filling; airborne transfer preserves y and has only one launch (additional cases in file). |
| [test_ladder_generation_speed.py](../autonomy/tests/test_ladder_generation_speed.py) | Tests: open trace skips the extra floor prediction; landing keeps exact downward clip prediction; invalid occupied height stops at the first failure (additional cases in file). |
| [test_ladder_schedules.py](../autonomy/tests/test_ladder_schedules.py) | Tests: four stop choices omit first final or both hits; two stops are not adjacent and short windows remain valid; invalid climb windows are rejected (additional cases in file). |
| [test_linkcraft.py](../autonomy/tests/test_linkcraft.py) | Tests: extracts without any recorded facing field; missing geometry is not invented; grounded height change is not silently retimed. |
| [test_long_ladder_chains.py](../autonomy/tests/test_long_ladder_chains.py) | Tests: saved cold three and prescribed five climbs revalidate full scene; fresh solves start inside first ladder without turning back to its centre. |
| [test_minimum_runs.py](../autonomy/tests/test_minimum_runs.py) | Generic dispatch/resume/unknown and archived Real11 regressions for local last-jump run pruning. |
| [test_movement_composition.py](../autonomy/tests/test_movement_composition.py) | Tests: direct transfer seam preserves both regions and entry hit; endpoint stops keep their unit and change actual y carry; units retime together with runs hits and sneak (additional cases in file). |
| [test_movement_contacts.py](../autonomy/tests/test_movement_contacts.py) | Tests: ceiling regenerates exit time instead of requiring old exit to work; contact assisted entry is generated even without a baseline schedule; repeated ceiling clips during climbing are generated as one sequence (additional cases in file). |
| [test_movement_entry.py](../autonomy/tests/test_movement_entry.py) | Tests: initial membership needs no provisional jump or fall; expired budget retains every unknown entry as unprobed; unknown numerical results are never infeasibility (additional cases in file). |
| [test_movement_generation.py](../autonomy/tests/test_movement_generation.py) | Tests: normal uses existing jump and runoff physics; composer uses registered generators and emits exact five grammars; front entry hit has no forced falling phase (additional cases in file). |
| [test_movement_units.py](../autonomy/tests/test_movement_units.py) | Tests: exactly five phase sequences and top rule; all endpoint variants remain owned by climbing; exhaustive hit masks allow one contiguous climb and endpoint stops (additional cases in file). |
| [test_obstacles.py](../autonomy/tests/test_obstacles.py) | Tests: collision boxes derive supports and survive normalization without mutation; multipart supports remain separate with their actual gap; geometry requires vertical bounds coverage and consistent supports (additional cases in file). |
| [test_orchestrator_continuation.py](../autonomy/tests/test_orchestrator_continuation.py) | Tests: diagonal distance bound rejects box relaxation false positive; all acceleration directions and swept phases remain in the envelope; required and unwanted interactions use the same query (additional cases in file). |
| [test_repair_resume.py](../autonomy/tests/test_repair_resume.py) | Tests: extended program keeps guess without copying exact old problem work; valid saved incumbent does not need another numerical request; worker interruption restores the inflight branch (additional cases in file). |
| [test_route_guidance.py](../autonomy/tests/test_route_guidance.py) | Tests: phase flow preserves every small allocation and horizon; supports use pre landing and run inputs without pinning jump; consecutive passage uses correct same or successive states (additional cases in file). |
| [test_runoff.py](../autonomy/tests/test_runoff.py) | Tests: departure is grounded without jump boost then air; zero delay after a jump checks source support at the preceding state; added runs retime preceding support and current exit together (additional cases in file). |
| [test_screen.py](../autonomy/tests/test_screen.py) | Tests: unreachable wall is rejected; per axis zeroing and turns do not reject a known witness; ladder cap cannot claim reaching a further contact (additional cases in file). |
| [test_search.py](../autonomy/tests/test_search.py) | Tests: equal run allocations remain available for objective optimization; increments the first failing jump and backtracks at support limit; unknown run backtracks without claiming physical infeasibility (additional cases in file). |
| [test_search_fixes.py](../autonomy/tests/test_search_fixes.py) | Tests: completed profiles survive partial queue and expansion caps; outer incumbent survives invalid polish and repair continues; invalid seed is never retained (additional cases in file). |
| [test_shared_contacts.py](../autonomy/tests/test_shared_contacts.py) | Tests: hit cancels sprint with carried air factor; raw wall clip and outward following movement; finite face and nearer wall reject selected hit (additional cases in file). |
| [test_stepping_cases.py](../autonomy/tests/test_stepping_cases.py) | Tests: legacy prefix without landing phase keeps pre landing timing; legacy boost profile is adapted to its registered owner; scalar named outcomes and rejected controls (additional cases in file). |
| [test_terminal_goals.py](../autonomy/tests/test_terminal_goals.py) | Unit and native regression checks for discovered virtual landing-height goals and actual physical replay. |
| [test_time_progress.py](../autonomy/tests/test_time_progress.py) | Tests: fast and far are different and tradeoffs survive; similar candidates are not strictly frontier pruned; only marked inferiority is discarded (additional cases in file). |

## docs

| File | Purpose |
| --- | --- |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Architecture and execution flow. See documentation index for authority/status. |
| [CODE_MAP.md](CODE_MAP.md) | Production code map. See documentation index for authority/status. |
| [CORRIDOR_AGENT_HANDOFF.md](CORRIDOR_AGENT_HANDOFF.md) | Handoff: changes since the standalone AGENTS consolidation. See documentation index for authority/status. |
| [CURRENT_STATE.md](CURRENT_STATE.md) | Current standalone state. See documentation index for authority/status. |
| [DECISIONS_AND_PLANS.md](DECISIONS_AND_PLANS.md) | Decisions and plan authority. See documentation index for authority/status. |
| [DEVELOPMENT.md](DEVELOPMENT.md) | Writing code and fixes. See documentation index for authority/status. |
| [DOC_INDEX.md](DOC_INDEX.md) | Documentation index. See documentation index for authority/status. |
| [FILE_INDEX.md](FILE_INDEX.md) | Complete standalone file inventory. See documentation index for authority/status. |
| [FUNCTION_INDEX.md](FUNCTION_INDEX.md) | Function and test definition index. See documentation index for authority/status. |
| [MOD_REFERENCES.md](MOD_REFERENCES.md) | Reusing the broader Parkour Calculator. See documentation index for authority/status. |
| [README.md](README.md) | Standalone documentation guide. See documentation index for authority/status. |
| [REAL11_GUIDE.md](REAL11_GUIDE.md) | Linkcraft Real11 opening: agent guide. See documentation index for authority/status. |
| [RESULTS_AND_DEBUGGING.md](RESULTS_AND_DEBUGGING.md) | Results, terminology and debugging. See documentation index for authority/status. |
| [RUNBOOK.md](RUNBOOK.md) | Standalone runbook. See documentation index for authority/status. |
| [TESTING.md](TESTING.md) | Tests and fixtures. See documentation index for authority/status. |

## ipopt

| File | Purpose |
| --- | --- |
| [AUTONOMOUS_AGENT_HANDOFF.md](../ipopt/AUTONOMOUS_AGENT_HANDOFF.md) | Standalone IPOPT handoff for the autonomous-solve agent. See documentation index for authority/status. |
| [FINDINGS.md](../ipopt/FINDINGS.md) | Standalone IPOPT findings. See documentation index for authority/status. |
| [MOD_INTEGRATION_PLAN.md](../ipopt/MOD_INTEGRATION_PLAN.md) | Minecraft and mod integration plan. See documentation index for authority/status. |
| [MOVEMENT_PORT.md](../ipopt/MOVEMENT_PORT.md) | Movement port status and limitations. See documentation index for authority/status. |
| [README.md](../ipopt/README.md) | Standalone continuous IPOPT solver. See documentation index for authority/status. |
| [RECENT_REPLAY_HANDOFF.md](../ipopt/RECENT_REPLAY_HANDOFF.md) | Replay and movement handoff, updated 2026-10-28. See documentation index for authority/status. |
| [REPLAY.md](../ipopt/REPLAY.md) | Local Minecraft movement replay. See documentation index for authority/status. |
| [RUN_TICKS_PHYSICS_HANDOFF.md](../ipopt/RUN_TICKS_PHYSICS_HANDOFF.md) | Block-sequence and minimum-run-ticks physics handoff. See documentation index for authority/status. |
| [__init__.py](../ipopt/__init__.py) | Public prepare, validate, solve and capture-attachment API. |
| [capture.py](../ipopt/capture.py) | Attach prescribed vertical/ladder evidence from recorded captures. |
| [continuation.py](../ipopt/continuation.py) | Continuous/inertia phase strategy, incumbent preservation and continuation state. |
| [export.gradle](../ipopt/export.gradle) | Gradle init tasks for capture compilation, standalone export and local executable replay. |
| [ladder.py](../ipopt/ladder.py) | Float-promoted caps, prescribed ladder/vertical step and stored-carry equations. |
| [model.py](../ipopt/model.py) | Strict fixed-problem schema/fingerprint and independent scalar horizontal/vertical constraint validation. |
| [requirements.txt](../ipopt/requirements.txt) | Pinned optional/runtime dependencies; role determined by package and include chain. |
| [solve.py](../ipopt/solve.py) | Fixed-request CLI, JSON/gzip IO, runtime options, atomic output and exit statuses. |
| [solver.py](../ipopt/solver.py) | Parent worker subprocess lifecycle/protocol, deadlines/cancellation and independently validated incumbents. |
| [worker.py](../ipopt/worker.py) | Native CasADi/IPOPT model, solve phases, callback monitor and worker protocol entry point. |

## ipopt/fixtures

| File | Purpose |
| --- | --- |
| [real11-200-ladders.json.gz](../ipopt/fixtures/real11-200-ladders.json.gz) | Reproducible input/model fixture: real11-200-ladders.json.gz; owning tests/experiment define domain and expected acceptance. |
| [real11-200.json.gz](../ipopt/fixtures/real11-200.json.gz) | Reproducible input/model fixture: real11-200.json.gz; owning tests/experiment define domain and expected acceptance. |
| [real11-400-ladders.json.gz](../ipopt/fixtures/real11-400-ladders.json.gz) | Reproducible input/model fixture: real11-400-ladders.json.gz; owning tests/experiment define domain and expected acceptance. |
| [real11-400-warm-yaws.json.gz](../ipopt/fixtures/real11-400-warm-yaws.json.gz) | Reproducible input/model fixture: real11-400-warm-yaws.json.gz; owning tests/experiment define domain and expected acceptance. |
| [real11-400.json.gz](../ipopt/fixtures/real11-400.json.gz) | Reproducible input/model fixture: real11-400.json.gz; owning tests/experiment define domain and expected acceptance. |
| [real11-ladder-evidence.json.gz](../ipopt/fixtures/real11-ladder-evidence.json.gz) | Reproducible input/model fixture: real11-ladder-evidence.json.gz; owning tests/experiment define domain and expected acceptance. |

## ipopt/java

| File | Purpose |
| --- | --- |
| [CompileStandaloneIpoptPhysics.java](../ipopt/java/CompileStandaloneIpoptPhysics.java) | Compile effective-input fixed problems with shared Java movement/input policies. |
| [ExportStandaloneIpopt.java](../ipopt/java/ExportStandaloneIpopt.java) | Read captured TAS spans and export fixed IPOPT requests with exact source/state/settings. |
| [ReplayStandaloneIpopt.java](../ipopt/java/ReplayStandaloneIpopt.java) | Replay/repair solved facings as executable local TAS inputs and export only validated results. |

## ipopt/tests

| File | Purpose |
| --- | --- |
| [test_capture.py](../ipopt/tests/test_capture.py) | Tests: climb signatures include entry and idle ladder states; vertical reference replays capture exactly; mid climb start preserves incoming velocity and previous hit (additional cases in file). |
| [test_continuation.py](../ipopt/tests/test_continuation.py) | Tests: failed phase cannot displace valid path for polishing; fallback is explored after successful incumbent polish; incumbent tracks minimization and rejects invalid paths (additional cases in file). |
| [test_integration.py](../ipopt/tests/test_integration.py) | Tests: zero threshold axis extremum recovers with declared fallback; known optimum with relative position and facing rows; real11 200 and 400 with all rows and contacts (additional cases in file). |
| [test_ladder_model.py](../ipopt/tests/test_ladder_model.py) | Tests: entry is uncapped and climb is carried to next movement; missing wall does not grant climb carry; cap precedes wall reach and cuts stored velocity (additional cases in file). |
| [test_model.py](../ipopt/tests/test_model.py) | Tests: continuous carry and inertia are distinct; contact resets only normal velocity; subtolerance missed contact never resets velocity (additional cases in file). |
| [test_movement.py](../ipopt/tests/test_movement.py) | Tests: boundary touch cannot claim the compiled wall hit; ground changes cannot silently keep old acceleration; collision assumptions must match declared schedule (additional cases in file). |
| [test_solver.py](../ipopt/tests/test_solver.py) | Tests: timeout stops worker that never reads large request; active cancellation discards candidate; timeout retains validated incumbent (additional cases in file). |
