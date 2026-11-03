# Function and test definition index

Source snapshot: current DFS implementation source, including request-audit logging. Line numbers are navigation hints; use `rg` after edits. This is a locator, not a replacement for [function contracts](ARCHITECTURE.md). Includes every top-level Python function/class and class method, plus test methods. Nested callbacks remain in their enclosing function. Java entry points are in the [code map](CODE_MAP.md).

## [autonomy/block_contacts.py](../autonomy/block_contacts.py)

- `floor_checks` — def, line 16.
- `contact_conflicts` — def, line 59.
- `avoid_contact_rows` — def, line 69.
- `validate_vertical_schedule` — def, line 81.
- `solve_contact_branches` — def, line 120.

## [autonomy/block_profiles.py](../autonomy/block_profiles.py)

- `normal_jump_height` — def, line 10.
- `ordinary_jump_profile` — def, line 14.
- `runoff_profile` — def, line 18.
- `_flight_profile` — def, line 27.
- `stair_jump_profile` — def, line 54.
- `compile_open_air_route` — def, line 76.

## [autonomy/block_search.py](../autonomy/block_search.py)

- `prepare_scene` — def, line 15.
- `minimum_distance` — def, line 112.
- `build_transition_graph` — def, line 118.
- `compile_block_path` — def, line 301.
- `search_block_paths` — def, line 367.
- `_find_blocks_normal` — def, line 502.
- `find_blocks` — def, line 582.

## [autonomy/blockages.py](../autonomy/blockages.py)

- `consecutive` — def, line 15.
- `faces` — def, line 19.
- `outside` — def, line 25.
- `passage_kind` — def, line 29.
- `blockage_counts` — def, line 34.
- `block_key` — def, line 39.
- `blockage_usage` — def, line 43.
- `within_caps` — def, line 52.
- `gate_timeline` — def, line 56.
- `intervals` — def, line 74.
- `face_chains` — def, line 88.
- `validate_passages` — def, line 95.
- `face_clearance` — def, line 126.
- `compile_passages` — def, line 139.
- `passage_y_valid` — def, line 162.
- `passage_options` — def, line 173.

## [autonomy/case_profiles.py](../autonomy/case_profiles.py)

- `region_rows` — def, line 12.
- `outside_row` — def, line 18.
- `tangent_rows` — def, line 24.
- `corner_rows` — def, line 30.
- `case_profile` — def, line 38.
- `landing_wall_options` — def, line 70.
- `entry_possible` — def, line 82.

## [autonomy/ceiling_generation.py](../autonomy/ceiling_generation.py)

- `ceiling_profiles` — def, line 10.
- `CeilingGenerator` — class, line 37.
- `CeilingGenerator.generate` — def, line 38.
- `open_ceiling_choices` — def, line 66.

## [autonomy/ceiling_primitives.py](../autonomy/ceiling_primitives.py)

- `ceiling_contact` — def, line 9.
- `ceiling_opportunities` — def, line 17.
- `ceiling_sequence` — def, line 23.
- `ceiling_profile` — def, line 47.

## [autonomy/cell_routes/checkpoint_experiment.py](../autonomy/cell_routes/checkpoint_experiment.py)

- `replay_witness` — def, line 20.
- `brief` — def, line 52.
- `run_fixture` — def, line 63.
- `main` — def, line 76.

## [autonomy/cell_routes/checkpoint_search.py](../autonomy/cell_routes/checkpoint_search.py)

- `ScopedWork` — class, line 23.
- `ScopedWork.__init__` — def, line 24.
- `ScopedWork.deadline` — def, line 29.
- `ScopedWork.termination` — def, line 33.
- `ScopedWork.check` — def, line 36.
- `ScopedWork.proposal` — def, line 40.
- `ScopedWork.before_solve` — def, line 44.
- `ScopedWork.accepted` — def, line 49.
- `_motion_bound` — def, line 53.
- `evaluate_program` — def, line 61.
- `_key` — def, line 163.
- `find_checkpoint_route` — def, line 167.

## [autonomy/cell_routes/checkpoints.py](../autonomy/cell_routes/checkpoints.py)

- `horizontal_segments` — def, line 9.
- `Checkpoint` — class, line 25.
- `Checkpoint.objective` — def, line 36.
- `Checkpoint.to_dict` — def, line 39.
- `Checkpoint.rows` — def, line 42.
- `Checkpoint.stage` — def, line 49.
- `checkpoint` — def, line 63.
- `retain_prefix` — def, line 91.
- `retain_unresolved` — def, line 110.
- `retain_decisions` — def, line 121.

## [autonomy/cell_routes/comparison_audit.py](../autonomy/cell_routes/comparison_audit.py)

- `audit_pool` — def, line 14.
- `source_entries` — def, line 43.
- `run_full_audit` — def, line 67.

## [autonomy/cell_routes/comparison_goals.py](../autonomy/cell_routes/comparison_goals.py)

- `goal_spec` — def, line 14.
- `goal_key` — def, line 41.
- `region_arrival` — def, line 47.
- `landing_satisfied` — def, line 61.
- `_landing_prefixes` — def, line 66.
- `probe_spatial` — def, line 82.

## [autonomy/cell_routes/corridor_guards.py](../autonomy/cell_routes/corridor_guards.py)

- `movement_regions` — def, line 4.

## [autonomy/cell_routes/decision_windows.py](../autonomy/cell_routes/decision_windows.py)

- `plan_windows` — def, line 9.
- `decision_events` — def, line 72.
- `DecisionPolicy` — class, line 92.
- `DecisionPolicy.__init__` — def, line 93.
- `DecisionPolicy.audit` — def, line 98.
- `DecisionPolicy.__call__` — def, line 105.
- `retain_candidates` — def, line 109.
- `score_features` — def, line 130.
- `fair_programs` — def, line 143.

## [autonomy/cell_routes/depth_first.py](../autonomy/cell_routes/depth_first.py)

- `candidate_key` — def, line 17.
- `arrival_key` — def, line 22.
- `decision_family` — def, line 27.
- `commit_arrival` — def, line 38.
- `search_goals` — def, line 58.

## [autonomy/cell_routes/experiment.py](../autonomy/cell_routes/experiment.py)

- `content_hash` — def, line 31.
- `verify_witness` — def, line 35.
- `witness_corridor` — def, line 73.
- `generation_totals` — def, line 94.
- `run_arm` — def, line 103.
- `compact` — def, line 195.
- `main` — def, line 213.

## [autonomy/cell_routes/failure_diagnostic.py](../autonomy/cell_routes/failure_diagnostic.py)

- `read` — def, line 33.
- `generation_scenes` — def, line 37.
- `run_generation` — def, line 49.
- `CandidateAudit` — class, line 107.
- `CandidateAudit.__init__` — def, line 108.
- `CandidateAudit.__call__` — def, line 118.
- `warm_controls` — def, line 143.
- `run_warm` — def, line 160.
- `verify_generation` — def, line 237.
- `main` — def, line 282.

## [autonomy/cell_routes/fix_validation.py](../autonomy/cell_routes/fix_validation.py)

- `main` — def, line 10.

## [autonomy/cell_routes/fixed_clock_experiment.py](../autonomy/cell_routes/fixed_clock_experiment.py)

- `run` — def, line 23.
- `main` — def, line 93.

## [autonomy/cell_routes/generation_diagnostic.py](../autonomy/cell_routes/generation_diagnostic.py)

- `physical_key` — def, line 11.
- `execution_key` — def, line 15.
- `KeyAudit` — class, line 21.
- `KeyAudit.__init__` — def, line 22.
- `KeyAudit.__call__` — def, line 27.
- `QueueAudit` — class, line 42.
- `QueueAudit.__init__` — def, line 43.
- `QueueAudit.complete` — def, line 48.
- `QueueAudit.heapify` — def, line 52.
- `QueueAudit.heappush` — def, line 55.
- `QueueAudit.heappop` — def, line 60.
- `QueueAudit.nsmallest` — def, line 75.
- `QueueAudit.summary` — def, line 98.
- `instrumented_generator` — def, line 103.
- `save` — def, line 121.

## [autonomy/cell_routes/geometry.py](../autonomy/cell_routes/geometry.py)

- `dimension` — def, line 9.
- `contains` — def, line 13.
- `intersection` — def, line 17.
- `subtract_open` — def, line 22.
- `merge_boxes` — def, line 44.
- `union_subtract` — def, line 70.
- `Cell` — class, line 82.
- `Portal` — class, line 89.
- `ContactPatch` — class, line 97.
- `Decomposition` — class, line 108.
- `Decomposition.locate` — def, line 116.
- `Decomposition.neighbors` — def, line 119.
- `Decomposition.to_dict` — def, line 127.
- `decompose` — def, line 131.
- `segment_intervals` — def, line 193.
- `segment_covered` — def, line 215.
- `diagnostic_svg` — def, line 229.

## [autonomy/cell_routes/local.py](../autonomy/cell_routes/local.py)

- `SearchWork` — class, line 13.
- `SearchWork.__init__` — def, line 14.
- `SearchWork.check` — def, line 27.
- `SearchWork.program` — def, line 35.
- `SearchWork.proposal` — def, line 46.
- `SearchWork.before_solve` — def, line 51.
- `SearchWork.accepted` — def, line 55.
- `SearchWork.summary` — def, line 60.
- `CountingCompiler` — class, line 67.
- `CountingCompiler.__init__` — def, line 68.
- `CountingCompiler.__getattr__` — def, line 73.
- `CountingCompiler.summary` — def, line 89.
- `CorridorAdapter` — class, line 93.
- `CorridorAdapter.__init__` — def, line 94.
- `CorridorAdapter.box_allowed` — def, line 103.
- `CorridorAdapter.region_allowed` — def, line 108.
- `CorridorAdapter.context` — def, line 116.
- `CorridorAdapter.prefix` — def, line 125.
- `CorridorAdapter.row` — def, line 188.
- `CorridorAdapter.scoped` — def, line 191.
- `CorridorAdapter.eligible` — def, line 205.
- `CorridorAdapter.failure_diagnostics` — def, line 216.
- `CorridorAdapter.candidate` — def, line 241.
- `CorridorAdapter.summary` — def, line 287.

## [autonomy/cell_routes/minimum_runs.py](../autonomy/cell_routes/minimum_runs.py)

- `_shift` — def, line 11.
- `latest_jump` — def, line 20.
- `remember` — def, line 73.
- `exclusion` — def, line 80.

## [autonomy/cell_routes/repair_diagnostic.py](../autonomy/cell_routes/repair_diagnostic.py)

- `main` — def, line 21.

## [autonomy/cell_routes/repair_resume_experiment.py](../autonomy/cell_routes/repair_resume_experiment.py)

- `main` — def, line 20.

## [autonomy/cell_routes/routes.py](../autonomy/cell_routes/routes.py)

- `midpoint` — def, line 12.
- `goal_point` — def, line 16.
- `goal_region` — def, line 22.
- `distance_to_box` — def, line 29.
- `landmarks` — def, line 33.
- `crossings` — def, line 48.
- `signature` — def, line 70.
- `projected_boxes` — def, line 74.
- `anchor_box` — def, line 79.
- `walk_projection` — def, line 86.
- `prescribed_projection` — def, line 98.
- `Corridor` — class, line 103.
- `Corridor.to_dict` — def, line 115.
- `corridor_for_points` — def, line 119.
- `enumerate_routes` — def, line 150.
- `horizontal_trace` — def, line 224.
- `projected_segment_covered` — def, line 240.
- `audit_candidate` — def, line 265.

## [autonomy/cell_routes/scalar_seed_diagnostic.py](../autonomy/cell_routes/scalar_seed_diagnostic.py)

- `synthetic` — def, line 14.
- `main` — def, line 27.

## [autonomy/cell_routes/support_order.py](../autonomy/cell_routes/support_order.py)

- `SupportOrder` — class, line 1.
- `SupportOrder.__init__` — def, line 2.
- `SupportOrder.audit` — def, line 20.
- `SupportOrder.__call__` — def, line 50.
- `SupportOrder.remaining` — def, line 52.
- `SupportOrder.priority` — def, line 55.

## [autonomy/cell_routes/time_progress.py](../autonomy/cell_routes/time_progress.py)

- `generation_exhausted` — def, line 11.
- `_measurements` — def, line 18.
- `protections` — def, line 26.
- `clearly_worse` — def, line 54.
- `retain_two_goals` — def, line 71.
- `clock_result` — def, line 113.

## [autonomy/cell_routes/two_goal_experiment.py](../autonomy/cell_routes/two_goal_experiment.py)

- `witnesses` — def, line 18.
- `competitive_followup` — def, line 28.
- `main` — def, line 42.

## [autonomy/cell_routes/window_diagnostic.py](../autonomy/cell_routes/window_diagnostic.py)

- `load` — def, line 23.
- `controls` — def, line 27.
- `compare` — def, line 42.
- `main` — def, line 62.

## [autonomy/cell_routes/window_experiment.py](../autonomy/cell_routes/window_experiment.py)

- `run_fixture` — def, line 16.
- `save` — def, line 23.
- `main` — def, line 31.

## [autonomy/climbable_search.py](../autonomy/climbable_search.py)

- `selected_edges` — def, line 16.
- `ladder_settings` — def, line 20.
- `ordinary_paths` — def, line 44.
- `run_allocations` — def, line 61.
- `_prefix` — def, line 79.
- `entry_context` — def, line 103.
- `probe_entries` — def, line 108.
- `post_exit_reachable` — def, line 118.
- `first_hit_bounds` — def, line 132.
- `find_climbable_blocks` — def, line 168.

## [autonomy/compiler.py](../autonomy/compiler.py)

- `JavaCompiler` — class, line 6.
- `JavaCompiler.__init__` — def, line 7.
- `JavaCompiler._start` — def, line 15.
- `JavaCompiler._errors` — def, line 22.
- `JavaCompiler.compile` — def, line 30.
- `JavaCompiler.derive_footprints` — def, line 39.
- `JavaCompiler.derive_standing_footprints` — def, line 42.
- `JavaCompiler.check_scene` — def, line 45.
- `JavaCompiler._exchange` — def, line 51.
- `JavaCompiler.close` — def, line 80.
- `JavaCompiler.__enter__` — def, line 92.
- `JavaCompiler.__exit__` — def, line 95.

## [autonomy/constraint_variants.py](../autonomy/constraint_variants.py)

- `ConstraintVariants` — class, line 13.
- `ConstraintVariants.__init__` — def, line 14.
- `ConstraintVariants.excluded` — def, line 148.
- `ConstraintVariants.face_row` — def, line 151.
- `ConstraintVariants.check` — def, line 156.
- `ConstraintVariants.effective_key` — def, line 160.
- `ConstraintVariants.segment_rows` — def, line 172.
- `ConstraintVariants.option` — def, line 177.
- `ConstraintVariants.__iter__` — def, line 257.
- `ConstraintVariants.entailed` — def, line 260.
- `ConstraintVariants.__next__` — def, line 274.

## [autonomy/contact_combinations.py](../autonomy/contact_combinations.py)

- `merge_contacts` — def, line 9.
- `validate_contact_events` — def, line 29.
- `compile_contact_events` — def, line 71.
- `contact_combinations` — def, line 108.

## [autonomy/contact_profiles.py](../autonomy/contact_profiles.py)

- `contact_profile` — def, line 10.
- `contact_opportunities` — def, line 51.
- `generate_contact_profiles` — def, line 69.

## [autonomy/experiments/cell-routes/run_benchmarks.py](../autonomy/experiments/cell-routes/run_benchmarks.py)

- `main` — def, line 24.

## [autonomy/experiments/checkpoint-guidance/plot_diagnostic.py](../autonomy/experiments/checkpoint-guidance/plot_diagnostic.py)

- `main` — def, line 10.

## [autonomy/experiments/decision-windows/analyze_results.py](../autonomy/experiments/decision-windows/analyze_results.py)

- `read` — def, line 12.
- `main` — def, line 16.

## [autonomy/experiments/dfs-interactions/measure_generation.py](../autonomy/experiments/dfs-interactions/measure_generation.py)

- `main` — def, line 10.

## [autonomy/experiments/dfs-interactions/summarize.py](../autonomy/experiments/dfs-interactions/summarize.py)

- `read` — def, line 8.
- `records` — def, line 13.
- `calls` — def, line 27.
- `first_full_calls` — def, line 40.
- `run_summary` — def, line 56.
- `main` — def, line 89.

## [autonomy/experiments/linkcraft11-opening/audit_trace.py](../autonomy/experiments/linkcraft11-opening/audit_trace.py)

- `read` — def, line 18.
- `save` — def, line 23.
- `csv_file` — def, line 32.
- `ranges` — def, line 39.
- `main` — def, line 47.

## [autonomy/experiments/linkcraft11-opening/evidence/scripts/probe_real11_corridor.py](../autonomy/experiments/linkcraft11-opening/evidence/scripts/probe_real11_corridor.py)

- `SceneOnly` — class, line 19.
- `SceneOnly.candidate` — def, line 20.

## [autonomy/experiments/linkcraft11-opening/evidence/scripts/real11_constructive_probe.py](../autonomy/experiments/linkcraft11-opening/evidence/scripts/real11_constructive_probe.py)

- `choice` — def, line 21.

## [autonomy/experiments/linkcraft11-opening/measure_setup_crossover.py](../autonomy/experiments/linkcraft11-opening/measure_setup_crossover.py)

- `main` — def, line 10.

## [autonomy/experiments/linkcraft11-opening/probe_b2_reachability.py](../autonomy/experiments/linkcraft11-opening/probe_b2_reachability.py)

- `read` — def, line 17.
- `main` — def, line 22.

## [autonomy/experiments/linkcraft11-opening/probe_corridor_failures.py](../autonomy/experiments/linkcraft11-opening/probe_corridor_failures.py)

- `read` — def, line 23.
- `main` — def, line 28.

## [autonomy/experiments/linkcraft11-opening/probe_other_failures.py](../autonomy/experiments/linkcraft11-opening/probe_other_failures.py)

- `read` — def, line 28.
- `summary` — def, line 33.
- `main` — def, line 41.

## [autonomy/experiments/linkcraft11-opening/replay_solution.py](../autonomy/experiments/linkcraft11-opening/replay_solution.py)

- `main` — def, line 11.

## [autonomy/experiments/linkcraft11-opening/summarize_evidence.py](../autonomy/experiments/linkcraft11-opening/summarize_evidence.py)

- `read` — def, line 15.
- `write` — def, line 20.
- `main` — def, line 24.

## [autonomy/experiments/linkcraft11-opening/summarize_other_failures.py](../autonomy/experiments/linkcraft11-opening/summarize_other_failures.py)

- `read` — def, line 14.
- `main` — def, line 19.

## [autonomy/experiments/orchestrator-fixes/audit_candidates.py](../autonomy/experiments/orchestrator-fixes/audit_candidates.py)

- `read` — def, line 21.
- `digest` — def, line 26.
- `records` — def, line 30.
- `spans` — def, line 35.
- `summary_program` — def, line 45.
- `extra_rows` — def, line 61.
- `align_contacts` — def, line 68.
- `contact_outcome` — def, line 112.
- `base_route` — def, line 123.
- `row_key` — def, line 131.
- `canonical_rows` — def, line 135.
- `extract` — def, line 139.
- `replay` — def, line 209.
- `metrics` — def, line 222.
- `cell` — def, line 243.
- `table` — def, line 247.
- `support_text` — def, line 252.
- `render` — def, line 257.
- `worker_ledger` — def, line 438.
- `render_calls` — def, line 468.
- `main` — def, line 478.

## [autonomy/experiments/orchestrator-fixes/audit_generation.py](../autonomy/experiments/orchestrator-fixes/audit_generation.py)

- `effective_rows` — def, line 21.
- `source_for` — def, line 31.
- `run` — def, line 52.

## [autonomy/experiments/orchestrator-fixes/regressions.py](../autonomy/experiments/orchestrator-fixes/regressions.py)

- `read` — def, line 26.
- `fallen_prefixes` — def, line 31.
- `main` — def, line 58.

## [autonomy/hit_events.py](../autonomy/hit_events.py)

- `validate_hits` — def, line 8.
- `compile_hits` — def, line 55.
- `wall_hit_options` — def, line 104.
- `float_height` — def, line 139.
- `hit_seed` — def, line 144.

## [autonomy/interactions.py](../autonomy/interactions.py)

- `block_interaction` — def, line 5.
- `interaction_allowed` — def, line 10.
- `InteractionGate` — class, line 15.
- `InteractionGate.__init__` — def, line 16.
- `InteractionGate.envelopes` — def, line 22.
- `InteractionGate.query` — def, line 45.
- `InteractionGate.required` — def, line 72.
- `InteractionGate.context` — def, line 86.
- `InteractionGate.summary` — def, line 89.

## [autonomy/ladder_ceiling.py](../autonomy/ladder_ceiling.py)

- `_unit_at` — def, line 7.
- `own_ceiling_contacts` — def, line 11.
- `own_schedule_ceilings` — def, line 47.
- `predict_ceiling_sequences` — def, line 61.

## [autonomy/ladder_episodes.py](../autonomy/ladder_episodes.py)

- `compose_ladder_episodes` — def, line 11.

## [autonomy/ladder_events.py](../autonomy/ladder_events.py)

- `validate_ladder_events` — def, line 5.
- `compile_ladder_events` — def, line 56.

## [autonomy/ladder_geometry.py](../autonomy/ladder_geometry.py)

- `ladder_columns` — def, line 5.
- `_rectangular_regions` — def, line 28.
- `ladder_regions` — def, line 52.
- `region_rectangles` — def, line 78.
- `region_contains` — def, line 83.
- `region_axis` — def, line 88.
- `climb_walls` — def, line 93.
- `movement_contexts` — def, line 113.
- `entry_channels` — def, line 121.
- `outside_row` — def, line 136.
- `schedule_constraints` — def, line 141.
- `exit_faces` — def, line 198.

## [autonomy/ladder_ranking.py](../autonomy/ladder_ranking.py)

- `schedule_plan` — def, line 11.
- `straight_line_score` — def, line 22.
- `rank_ladder_schedules` — def, line 35.

## [autonomy/ladder_schedules.py](../autonomy/ladder_schedules.py)

- `climb_variants` — def, line 13.
- `sneak_patterns` — def, line 28.
- `prepare_ladder_request` — def, line 64.
- `_round_robin` — def, line 142.
- `_context_skeletons` — def, line 154.
- `_mask_work` — def, line 166.
- `generate_ladder_schedules` — def, line 236.

## [autonomy/ladder_transfers.py](../autonomy/ladder_transfers.py)

- `episode_from_event` — def, line 13.
- `open_program` — def, line 29.
- `recompose` — def, line 38.
- `append_ladder_programs` — def, line 47.
- `finish_programs` — def, line 134.
- `iter_chain_programs` — def, line 159.
- `chain_programs` — def, line 222.
- `transfer_programs` — def, line 229.

## [autonomy/linear_reachability.py](../autonomy/linear_reachability.py)

- `linear_exclusion` — def, line 6.
- `_check` — def, line 26.

## [autonomy/linkcraft.py](../autonomy/linkcraft.py)

- `extract_section` — def, line 11.
- `main` — def, line 175.

## [autonomy/mixed_ladder.py](../autonomy/mixed_ladder.py)

- `climb_contacts` — def, line 11.
- `with_contacts` — def, line 25.
- `mixed_ladder_schedules` — def, line 67.

## [autonomy/movement_composition.py](../autonomy/movement_composition.py)

- `scalar_initial` — def, line 12.
- `empty_program` — def, line 22.
- `boundary_state` — def, line 37.
- `profile_from_trace` — def, line 47.
- `movement_choice` — def, line 61.
- `_contacts` — def, line 78.
- `_shift_event` — def, line 83.
- `_events` — def, line 100.
- `append_choice` — def, line 126.
- `truncate_program` — def, line 206.
- `program_physics_key` — def, line 252.
- `program_search_key` — def, line 262.
- `compile_program` — def, line 270.

## [autonomy/movement_contacts.py](../autonomy/movement_contacts.py)

- `contact_signature` — def, line 15.
- `interleave_families` — def, line 20.
- `contact_variants` — def, line 36.
- `mixed_schedule_pool` — def, line 117.
- `program_seed` — def, line 166.

## [autonomy/movement_entry.py](../autonomy/movement_entry.py)

- `build_entry_problem` — def, line 12.
- `entry_contact_options` — def, line 87.
- `probe_region_entries` — def, line 111.

## [autonomy/movement_frontier.py](../autonomy/movement_frontier.py)

- `persistent_programs` — def, line 10.

## [autonomy/movement_generation.py](../autonomy/movement_generation.py)

- `register_movement_type` — def, line 10.
- `generate_choices` — def, line 17.
- `_unit` — def, line 31.
- `NormalGenerator` — class, line 39.
- `NormalGenerator.sneak_inputs` — def, line 40.
- `NormalGenerator.generate` — def, line 43.
- `LadderFallGenerator` — class, line 67.
- `LadderFallGenerator.sneak_inputs` — def, line 68.
- `LadderFallGenerator.generate` — def, line 71.
- `LadderClimbGenerator` — class, line 85.
- `LadderClimbGenerator.sneak_inputs` — def, line 86.
- `LadderClimbGenerator._choice` — def, line 89.
- `LadderClimbGenerator.generate` — def, line 111.
- `generate_unit_programs` — def, line 141.
- `materialize_generated_units` — def, line 166.
- `sneak_domain` — def, line 178.

## [autonomy/movement_problem.py](../autonomy/movement_problem.py)

- `waypoint_seed` — def, line 6.
- `_raw_candidate` — def, line 23.
- `_vertical` — def, line 34.
- `ladder_waypoints` — def, line 46.

## [autonomy/movement_search.py](../autonomy/movement_search.py)

- `movement_settings` — def, line 13.
- `iter_movement_programs` — def, line 30.
- `find_movement_blocks` — def, line 224.
- `screen_program` — def, line 294.

## [autonomy/movement_units.py](../autonomy/movement_units.py)

- `movement_unit` — def, line 10.
- `ladder_units` — def, line 18.
- `movement_program` — def, line 52.
- `compile_units` — def, line 103.
- `shifted_unit` — def, line 132.
- `profile_program` — def, line 146.
- `overlay_program` — def, line 171.
- `exit_matches` — def, line 202.

## [autonomy/obstacle_geometry.py](../autonomy/obstacle_geometry.py)

- `box_bounds` — def, line 11.
- `normalize_geometry` — def, line 21.
- `expanded_box` — def, line 88.
- `check_geometry` — def, line 93.

## [autonomy/open_ladder.py](../autonomy/open_ladder.py)

- `ladder_choices` — def, line 9.
- `ladder_event_choice` — def, line 86.

## [autonomy/open_movement.py](../autonomy/open_movement.py)

- `replay` — def, line 8.
- `approaches` — def, line 18.
- `first_descent` — def, line 27.
- `event_choice` — def, line 34.
- `named_choice` — def, line 45.
- `normal_choices` — def, line 54.

## [autonomy/prefix_viability.py](../autonomy/prefix_viability.py)

- `prefix_viability` — def, line 8.

## [autonomy/ranking.py](../autonomy/ranking.py)

- `objective_spec` — def, line 7.
- `candidate_rank` — def, line 16.
- `objective_seed` — def, line 22.
- `passage_seed` — def, line 37.

## [autonomy/reachability.py](../autonomy/reachability.py)

- `reachable_envelopes` — def, line 6.
- `interaction_reachability` — def, line 119.

## [autonomy/repair_state.py](../autonomy/repair_state.py)

- `_rows` — def, line 8.
- `context` — def, line 13.
- `compatible` — def, line 21.
- `branch_key` — def, line 52.

## [autonomy/route.py](../autonomy/route.py)

- `prepare_route` — def, line 12.
- `supports` — def, line 149.
- `center_bounds` — def, line 160.
- `schedule` — def, line 174.
- `initial_guess` — def, line 337.

## [autonomy/route_guidance/compact.py](../autonomy/route_guidance/compact.py)

- `_run_compact` — def, line 10.
- `solve_compact` — def, line 145.
- `audit_compact_witness` — def, line 149.

## [autonomy/route_guidance/conic.py](../autonomy/route_guidance/conic.py)

- `linear` — def, line 9.
- `ConeProgram` — class, line 16.
- `ConeProgram.__init__` — def, line 17.
- `ConeProgram.variable` — def, line 21.
- `ConeProgram.vector` — def, line 30.
- `ConeProgram.eq` — def, line 33.
- `ConeProgram.le` — def, line 36.
- `ConeProgram.scaled_bounds` — def, line 39.
- `ConeProgram.data` — def, line 44.
- `ConeProgram.violation` — def, line 65.
- `ConeProgram.upper_value` — def, line 74.
- `build_conic` — def, line 96.
- `solve_conic` — def, line 194.

## [autonomy/route_guidance/experiment.py](../autonomy/route_guidance/experiment.py)

- `_resample` — def, line 22.
- `similarity` — def, line 31.
- `control_stats` — def, line 39.
- `_ranking` — def, line 61.
- `run_case` — def, line 92.
- `summary` — def, line 151.
- `main` — def, line 173.

## [autonomy/route_guidance/graph.py](../autonomy/route_guidance/graph.py)

- `carry_vertices` — def, line 11.
- `_row` — def, line 21.
- `_support_rows` — def, line 25.
- `build_family` — def, line 31.
- `edge_path` — def, line 203.
- `decisions` — def, line 216.
- `fixed_values` — def, line 226.
- `assignments` — def, line 239.

## [autonomy/route_guidance/guidance.py](../autonomy/route_guidance/guidance.py)

- `relaxation` — def, line 12.
- `rank_candidates` — def, line 18.
- `guide_route` — def, line 30.

## [autonomy/route_guidance/inertia_ablation.py](../autonomy/route_guidance/inertia_ablation.py)

- `main` — def, line 12.

## [autonomy/route_guidance/physical.py](../autonomy/route_guidance/physical.py)

- `rounded_assignment` — def, line 15.
- `make_problem` — def, line 40.
- `accept_candidate` — def, line 75.
- `refine` — def, line 95.
- `_hull_weights` — def, line 119.
- `lift_witness` — def, line 132.

## [autonomy/route_guidance/replay.py](../autonomy/route_guidance/replay.py)

- `replay` — def, line 14.
- `main` — def, line 65.

## [autonomy/route_guidance/report.py](../autonomy/route_guidance/report.py)

- `read_report` — def, line 10.
- `aggregate` — def, line 15.
- `plots` — def, line 66.
- `main` — def, line 156.

## [autonomy/screen.py](../autonomy/screen.py)

- `contradiction` — def, line 4.

## [autonomy/search.py](../autonomy/search.py)

- `search_allocations` — def, line 12.
- `find_route` — def, line 126.

## [autonomy/solve.py](../autonomy/solve.py)

- `main` — def, line 13.

## [autonomy/solve_blocks.py](../autonomy/solve_blocks.py)

- `main` — def, line 13.

## [autonomy/solve_spec.py](../autonomy/solve_spec.py)

- `digest` — def, line 8.
- `motion_record` — def, line 12.
- `processing_key` — def, line 24.
- `solve_scope` — def, line 31.
- `solve_identity` — def, line 57.
- `scoped_geometry` — def, line 65.
- `full_witness` — def, line 70.
- `validation_prefix` — def, line 75.

## [autonomy/stepping_generation.py](../autonomy/stepping_generation.py)

- `box_for` — def, line 9.
- `stepping_profiles` — def, line 13.
- `SteppingGenerator` — class, line 88.
- `SteppingGenerator.generate` — def, line 89.
- `open_stepping_choices` — def, line 121.

## [autonomy/terminal_goals.py](../autonomy/terminal_goals.py)

- `prepare_goal` — def, line 10.
- `height_clock` — def, line 27.
- `goal_rows` — def, line 39.
- `bind_goal` — def, line 45.
- `satisfied` — def, line 59.
- `finish_choices` — def, line 69.

## [autonomy/tests/test_acceptance.py](../autonomy/tests/test_acceptance.py)

- `NoMovementCompiler` — class, line 8.
- `NoMovementCompiler.compile` — def, line 9.
- `AcceptanceTests` — class, line 14.
- `AcceptanceTests.test_numerical_row_tolerance_cannot_make_a_nonoverlapping_landing_valid` — def, line 15.
- `AcceptanceTests.test_compiler_failure_is_reported_without_searching_more_ticks` — def, line 29.

## [autonomy/tests/test_block_contacts.py](../autonomy/tests/test_block_contacts.py)

- `descending_scene` — def, line 10.
- `FloorContactTests` — class, line 18.
- `FloorContactTests.test_runoff_checks_the_departure_floor_on_its_first_input` — def, line 19.
- `FloorContactTests.test_no_jump_first_movement_uses_its_own_step_eligibility` — def, line 26.
- `FloorContactTests.test_ordinary_profile_must_avoid_an_unplanned_first_movement_stair_cancel` — def, line 34.
- `FloorContactTests.test_descending_profile_must_miss_the_departure_floor_before_the_lower_target` — def, line 47.
- `FloorContactTests.test_higher_intermediate_floor_is_checked_even_if_it_is_not_in_the_route` — def, line 64.
- `FloorContactTests.test_equal_height_landing_does_not_forbid_the_selected_floor` — def, line 72.

## [autonomy/tests/test_block_profiles.py](../autonomy/tests/test_block_profiles.py)

- `BlockProfileTests` — class, line 14.
- `BlockProfileTests.test_runoff_profiles_use_the_same_vertical_model_without_a_jump_impulse` — def, line 15.
- `BlockProfileTests.test_runoff_cannot_rise_and_respects_its_simulation_horizon` — def, line 37.
- `BlockProfileTests.test_half_block_stair_has_a_takeoff_and_one_airborne_input` — def, line 42.
- `BlockProfileTests.test_stair_profile_is_only_generated_when_the_existing_step_model_accepts_it` — def, line 60.
- `BlockProfileTests.test_ordinary_height_tiers_and_existing_vertical_acceptance` — def, line 65.
- `BlockProfileTests.test_high_target_is_not_given_a_fabricated_profile` — def, line 89.
- `BlockProfileTests.test_short_budget_is_unknown_and_can_be_extended` — def, line 98.
- `BlockProfileTests.test_geometry_and_heights_generate_schedule_without_capture` — def, line 102.
- `BlockProfileTests.test_surroundings_and_contact_profiles_are_not_silently_ignored` — def, line 116.
- `BlockProfileTests.test_disconnected_support_union_is_not_accepted_as_one_support` — def, line 129.

## [autonomy/tests/test_block_route_witnesses.py](../autonomy/tests/test_block_route_witnesses.py)

- `BlockRouteWitnessTests` — class, line 16.
- `BlockRouteWitnessTests.test_saved_interleaving_replays_with_per_block_caps_and_shared_collision_checks` — def, line 18.
- `BlockRouteWitnessTests.test_saved_obstacle_routes_pass_numerical_and_shared_swept_collision_checks` — def, line 42.
- `BlockRouteWitnessTests.test_saved_runoffs_validate_no_jump_physics_and_actual_source_exits` — def, line 75.
- `BlockRouteWitnessTests.test_saved_height_and_stair_routes_include_valid_retimed_y_contacts` — def, line 105.
- `BlockRouteWitnessTests.test_saved_routes_validate_horizontally_vertically_and_on_actual_supports` — def, line 127.

## [autonomy/tests/test_block_search.py](../autonomy/tests/test_block_search.py)

- `scene` — def, line 11.
- `Coefficients` — class, line 22.
- `Coefficients.compile` — def, line 23.
- `Coefficients.derive_footprints` — def, line 27.
- `accepted` — def, line 32.
- `graph` — def, line 37.
- `BlockSceneTests` — class, line 41.
- `BlockSceneTests.test_unordered_supports_and_fixed_inputs` — def, line 42.
- `BlockSceneTests.test_unimplemented_geometry_and_duplicate_ids_are_rejected` — def, line 55.
- `BlockSceneTests.test_repeated_visits_compile_distinct_events_without_losing_support_identity` — def, line 70.
- `TransitionGraphTests` — class, line 81.
- `TransitionGraphTests.test_runoff_has_a_smaller_candidate_radius_than_the_jump_to_the_same_height` — def, line 82.
- `TransitionGraphTests.test_equal_duration_profiles_keep_distinct_jump_and_no_jump_coefficients` — def, line 93.
- `TransitionGraphTests.test_stair_and_ordinary_profiles_remain_separate_options_for_the_same_blocks` — def, line 113.
- `TransitionGraphTests.test_reach_bound_removes_remote_support_without_merging_blocks` — def, line 131.
- `TransitionGraphTests.test_high_initial_momentum_expands_the_graph_bound` — def, line 141.
- `TransitionGraphTests.test_graph_compilation_errors_and_timeouts_have_different_verdicts` — def, line 149.
- `TransitionGraphTests.test_free_facing_witnesses_with_inertia_survive_the_optimistic_edge_test` — def, line 163.
- `BlockPathSearchTests` — class, line 182.
- `BlockPathSearchTests.test_same_block_route_keeps_different_contact_profiles` — def, line 183.
- `BlockPathSearchTests.test_different_arrivals_at_the_same_block_are_not_deduplicated` — def, line 199.
- `BlockPathSearchTests.test_unknown_prefix_is_extended_and_full_prefix_can_recover` — def, line 214.
- `BlockPathSearchTests.test_same_block_jump_is_available_for_momentum` — def, line 221.
- `BlockPathSearchTests.test_graph_disconnection_and_hop_budget_do_not_call_the_solver` — def, line 228.
- `BlockPathSearchTests.test_candidate_and_pool_limits_are_reported_as_limits` — def, line 237.
- `BlockPathSearchTests.test_error_is_not_a_route_miss_and_cancellation_keeps_an_incumbent` — def, line 248.
- `BlockPathSearchTests.test_fewer_total_ticks_beat_a_zero_run_route` — def, line 266.
- `BlockPathSearchTests.test_equal_total_ticks_compare_objective_even_when_run_counts_differ` — def, line 284.
- `BlockPathSearchTests.test_unknown_shorter_route_prevents_a_minimum_tick_claim` — def, line 306.

## [autonomy/tests/test_candidate_audit.py](../autonomy/tests/test_candidate_audit.py)

- `CandidateAuditTests` — class, line 11.
- `CandidateAuditTests.test_saved_cold_calls_reconcile_without_counting_resume_snapshots_twice` — def, line 12.
- `CandidateAuditTests.test_missing_validation_record_keeps_every_possible_alignment` — def, line 30.
- `CandidateAuditTests.test_missing_call_records_are_not_reported_as_ipopt_failures` — def, line 37.

## [autonomy/tests/test_ceiling_contacts.py](../autonomy/tests/test_ceiling_contacts.py)

- `CeilingProfileTests` — class, line 19.
- `CeilingProfileTests.test_existing_y_model_regenerates_single_hit_and_shorter_landing` — def, line 20.
- `CeilingContactIntegrationTests` — class, line 39.
- `CeilingContactIntegrationTests.test_same_input_ceiling_and_wall_hit_use_one_coupled_movement_schedule` — def, line 40.
- `CeilingContactIntegrationTests.test_geometry_discovers_bonk_and_graph_bound_includes_shorter_profile` — def, line 55.

## [autonomy/tests/test_cell_experiment.py](../autonomy/tests/test_cell_experiment.py)

- `CellExperimentTests` — class, line 18.
- `CellExperimentTests.test_retry_budget_counts_actual_worker_calls` — def, line 19.
- `CellExperimentTests.test_prescribed_points_do_not_remove_remote_free_space` — def, line 34.
- `CellExperimentTests.test_one_input_step_family_exhausts_without_losing_complete_programs` — def, line 44.
- `CellExperimentTests.test_deadline_retains_active_prefix_generation_records` — def, line 56.
- `CellExperimentTests.test_prescribed_route_endpoints_are_checked` — def, line 72.
- `CellNumericalTests` — class, line 82.
- `CellNumericalTests.setUp` — def, line 83.
- `CellNumericalTests.test_automatic_blockage_routes_are_repaired_and_physically_checked` — def, line 87.
- `CellNumericalTests.test_goal_region_preserves_faster_grind_landing_before_an_interior_gate` — def, line 97.
- `CellNumericalTests.test_all_arms_preserve_original_state_acceptance_and_terminal_runoff` — def, line 112.

## [autonomy/tests/test_cell_geometry.py](../autonomy/tests/test_cell_geometry.py)

- `scene_for` — def, line 10.
- `CellGeometryTests` — class, line 15.
- `CellGeometryTests.test_random_points_and_disjoint_interiors_match_obstacle_union` — def, line 16.
- `CellGeometryTests.test_contact_boundary_between_floor_and_exact_body_height_ceiling_survives` — def, line 30.
- `CellGeometryTests.test_partial_portals_and_physical_contact_provenance` — def, line 41.
- `CellGeometryTests.test_split_and_reordered_solids_preserve_free_space_on_faces_and_volume` — def, line 51.
- `CellGeometryTests.test_segment_crosses_many_cells_and_rejects_interior_crossing` — def, line 62.
- `CellGeometryTests.test_corner_only_free_connection_has_an_incidence_route` — def, line 68.
- `CellGeometryTests.test_geometry_limit_and_deadline_do_not_silently_truncate` — def, line 83.

## [autonomy/tests/test_cell_local.py](../autonomy/tests/test_cell_local.py)

- `CellLocalTests` — class, line 15.
- `CellLocalTests.test_early_filter_removes_remote_support_before_choice_product` — def, line 16.
- `CellLocalTests.test_ladder_region_filter_runs_before_schedule_generation` — def, line 34.
- `CellLocalTests.test_contact_filter_preserves_original_solid_indices` — def, line 42.
- `CellLocalTests.test_wrong_side_gets_a_timed_constraint_without_changing_initial_state` — def, line 51.
- `CellLocalTests.test_forward_domains_reject_reverse_cell_order_but_allow_declared_returns` — def, line 64.
- `CellLocalTests.test_numerical_budget_counts_every_call_and_preserves_accepted_state` — def, line 81.

## [autonomy/tests/test_cell_routes.py](../autonomy/tests/test_cell_routes.py)

- `side_scene` — def, line 8.
- `CellRouteTests` — class, line 19.
- `CellRouteTests.test_support_corner_keeps_both_free_regions_without_filling_the_obstacle` — def, line 20.
- `CellRouteTests.test_both_obstacle_sides_and_overflight_are_distinct_proposals` — def, line 36.
- `CellRouteTests.test_prescribed_spatial_points_hide_timing_and_preserve_crossing_side` — def, line 47.
- `CellRouteTests.test_phase_trace_sees_multiple_crossings_in_one_tick` — def, line 57.
- `CellRouteTests.test_returns_are_kept_in_signature_and_explicitly_limited` — def, line 68.
- `CellRouteTests.test_goal_is_a_landing_region_even_when_its_center_is_blocked` — def, line 79.
- `CellRouteTests.test_touching_a_gate_then_returning_does_not_create_crossings` — def, line 92.
- `CellRouteTests.test_blocked_prescribed_trace_and_budget_expiry_are_explicit` — def, line 98.

## [autonomy/tests/test_checkpoint_guidance.py](../autonomy/tests/test_checkpoint_guidance.py)

- `flat_scene` — def, line 29.
- `programs` — def, line 37.
- `CheckpointTests` — class, line 42.
- `CheckpointTests.test_shared_checkpoint_has_horizontal_direction_and_no_y_objective` — def, line 43.
- `CheckpointTests.test_comparison_respects_ordered_gate_crossings_on_a_revisit` — def, line 54.
- `CheckpointTests.test_checkpoint_rejects_a_state_past_the_next_turn` — def, line 65.
- `CheckpointTests.test_reverse_direction_uses_the_same_signed_horizontal_objective` — def, line 72.
- `CheckpointTests.test_cancellation_before_geometry_needs_no_compiler` — def, line 79.
- `CheckpointTests.test_partial_corridor_accepts_a_signature_prefix_and_full_audit_does_not` — def, line 88.
- `CheckpointTests.test_open_program_has_no_fabricated_terminal_goal_constraint` — def, line 96.
- `CheckpointTests.test_different_method_lengths_are_cut_at_the_same_absolute_tick` — def, line 108.
- `CheckpointTests.test_truncating_preserves_past_contact_and_drops_future_contact` — def, line 117.
- `CheckpointTests.test_retention_keeps_constraints_and_warm_yaw_without_pinning_states` — def, line 129.
- `CheckpointTests.test_scoped_call_limit_does_not_exhaust_other_candidates` — def, line 142.
- `CheckpointTests.test_no_threshold_vertical_replay_carries_small_apex_velocity` — def, line 153.
- `CheckpointNumericalTests` — class, line 165.
- `CheckpointNumericalTests.setUp` — def, line 166.
- `CheckpointNumericalTests.test_repair_does_not_replace_a_supplied_seed_with_a_rejected_proposal` — def, line 170.
- `CheckpointNumericalTests.test_known_post_turn_seed_survives_polishing_and_replays` — def, line 195.
- `CheckpointNumericalTests.test_common_tick_scores_distinct_y_with_normal_momentum_carry` — def, line 213.
- `CheckpointNumericalTests.test_prefixes_continue_to_an_accepted_goal_without_freezing_the_start` — def, line 240.
- `CheckpointNumericalTests.test_a_later_constraint_can_reoptimize_facings_before_the_previous_checkpoint` — def, line 261.
- `CheckpointNumericalTests.test_turn_route_reaches_the_goal_and_replays_without_a_clock_barrier` — def, line 289.

## [autonomy/tests/test_climbable_search.py](../autonomy/tests/test_climbable_search.py)

- `ClimbableContractTests` — class, line 23.
- `ClimbableContractTests.test_run_allocations_are_incremental_and_bounded` — def, line 24.
- `ClimbableContractTests.test_invalid_cells_settings_and_conflicting_metadata_are_rejected` — def, line 30.
- `ClimbableContractTests.test_ladder_events_retime_keys_hits_membership_and_y_together` — def, line 45.
- `ClimbableContractTests.test_finite_wall_must_overlap_the_actual_climb_height` — def, line 64.
- `SavedLadderEvidenceTests` — class, line 74.
- `SavedLadderEvidenceTests.setUp` — def, line 75.
- `SavedLadderEvidenceTests.check` — def, line 79.
- `SavedLadderEvidenceTests.test_saved_candidates_revalidate_scalar_y_and_whole_scene_contracts` — def, line 85.
- `SavedLadderEvidenceTests.test_shared_acceptance_rejects_missing_extra_and_mistimed_hits_occupancy_and_carry` — def, line 100.
- `ClimbableIntegrationTests` — class, line 128.
- `ClimbableIntegrationTests.test_cold_geometry_scenes_find_top_side_and_composed_routes` — def, line 129.

## [autonomy/tests/test_comparison_goals.py](../autonomy/tests/test_comparison_goals.py)

- `ComparisonGoalTests` — class, line 22.
- `ComparisonGoalTests.test_goal_validation_uses_real_blocks_and_separate_region_semantics` — def, line 23.
- `ComparisonGoalTests.test_landing_does_not_mean_airborne_or_grounded_above_the_block` — def, line 29.
- `ComparisonGoalTests.test_region_arrival_cannot_use_a_gate_crossed_later` — def, line 36.
- `ComparisonGoalTests.test_terminal_before_clock_is_unknown_without_held_endpoint` — def, line 47.
- `ComparisonGoalNumericalTests` — class, line 58.
- `ComparisonGoalNumericalTests.setUp` — def, line 59.
- `ComparisonGoalNumericalTests.test_landing_time_and_same_clock_progress_are_distinct` — def, line 63.
- `ComparisonGoalNumericalTests.test_full_regression_continues_a_discarded_actual_faster_route` — def, line 99.
- `ComparisonGoalNumericalTests.test_region_time_is_an_evaluator_on_existing_candidate_not_generation_target` — def, line 126.

## [autonomy/tests/test_contact_combinations.py](../autonomy/tests/test_contact_combinations.py)

- `event` — def, line 19.
- `CombinationTests` — class, line 28.
- `CombinationTests.test_selected_hit_can_touch_its_avoidance_face_without_erasing_other_clearance` — def, line 29.
- `CombinationTests.test_every_same_tick_axis_subset_and_two_tick_pair_is_generated` — def, line 42.
- `CombinationTests.test_free_gap_is_independent_but_full_ladder_sequence_is_combined` — def, line 51.
- `CombinationTests.test_different_blocks_and_normal_signs_do_not_filter_combination_types` — def, line 57.
- `CombinationTests.test_conflicting_plane_rejected_and_compatible_duplicate_merged` — def, line 63.
- `CombinationTests.test_events_and_before_after_avoidance_follow_run_insertion` — def, line 71.
- `CombinationTests.test_contact_on_landing_input_and_actual_step_have_explicit_representation` — def, line 88.
- `MixedVerticalTests` — class, line 100.
- `MixedVerticalTests.test_same_tick_ceiling_climb_refresh_and_no_climb_give_different_carry` — def, line 101.
- `MixedVerticalTests.test_repeated_ceiling_climb_sequence_and_final_omission` — def, line 111.
- `MixedVerticalTests.test_no_ladder_refresh_on_exit_despite_same_horizontal_hit` — def, line 121.
- `MixedVerticalTests.test_profile_regenerates_airtime_for_wall_and_ceiling_combinations` — def, line 127.
- `CombinationSceneTests` — class, line 139.
- `CombinationSceneTests.setUp` — def, line 140.
- `CombinationSceneTests.test_adjacent_ladder_parts_use_exact_finite_face_alternatives` — def, line 144.
- `CombinationSceneTests.test_all_same_tick_axis_combinations_and_signs_against_shared_scene` — def, line 161.
- `CombinationSceneTests.test_selected_step_is_checked_as_step_not_a_fictitious_downward_clip` — def, line 194.
- `CombinationSceneTests.test_step_can_retain_x_z_contacts_from_the_selected_step_path` — def, line 205.
- `StepCombinationIntegrationTests` — class, line 224.
- `StepCombinationIntegrationTests.test_ipopt_step_and_two_wall_contacts_share_one_movement` — def, line 225.
- `StepCombinationIntegrationTests.test_ipopt_ceiling_x_z_ladder_refresh_then_ceiling_stop_and_land` — def, line 251.

## [autonomy/tests/test_contacts.py](../autonomy/tests/test_contacts.py)

- `ContactScheduleTests` — class, line 12.
- `ContactScheduleTests.test_full_route_keeps_all_jumps_runoff_step_and_run_cost` — def, line 13.
- `ContactScheduleTests.test_reduced_jump_must_enter_target_on_first_movement` — def, line 30.
- `ContactScheduleTests.test_step_landing_is_after_horizontal_movement_with_no_early_landing` — def, line 41.
- `ContactScheduleTests.test_run_support_is_not_limited_to_the_landing_rectangle` — def, line 50.

## [autonomy/tests/test_corridor_guards.py](../autonomy/tests/test_corridor_guards.py)

- `adapter` — def, line 11.
- `satisfies` — def, line 18.
- `CorridorGuardTests` — class, line 24.
- `CorridorGuardTests.test_projection_failure_logs_exact_phase_and_detached_replay_data` — def, line 25.
- `CorridorGuardTests.test_signature_failure_logs_expected_and_actual_crossing` — def, line 45.
- `CorridorGuardTests.test_crossing_adjacent_rectangles_is_preserved_in_both_axes_and_directions` — def, line 59.
- `CorridorGuardTests.test_regions_cover_exactly_the_axis_movements_inside_union` — def, line 76.
- `CorridorGuardTests.test_real_gap_is_not_bridged_by_a_region` — def, line 90.

## [autonomy/tests/test_decision_windows.py](../autonomy/tests/test_decision_windows.py)

- `decision` — def, line 19.
- `DecisionWindowTests` — class, line 23.
- `DecisionWindowTests.test_fixture_domains_include_the_known_physical_controls` — def, line 24.
- `DecisionWindowTests.test_joint_cluster_runs_to_the_next_input_not_one_state_earlier` — def, line 36.
- `DecisionWindowTests.test_dependencies_merge_the_intervening_decisions` — def, line 43.
- `DecisionWindowTests.test_incompatible_post_turn_boundary_absorbs_the_next_decision` — def, line 49.
- `DecisionWindowTests.test_earliest_onset_across_a_timing_range_is_the_shared_horizon` — def, line 55.
- `DecisionWindowTests.test_rejects_bad_domains_and_dependencies` — def, line 59.
- `DecisionWindowTests.test_band_retention_is_translation_invariant_and_adaptive` — def, line 65.
- `DecisionWindowTests.test_band_drops_caps_and_unknowns_have_separate_accounting` — def, line 76.
- `DecisionWindowTests.test_band_does_not_fill_unused_score_slots_with_unknowns` — def, line 85.
- `DecisionWindowTests.test_policy_disallows_new_jump_choices_in_the_clear_continuation` — def, line 92.
- `DecisionWindowTests.test_fair_generation_gives_each_parent_a_turn_and_bounded_quota` — def, line 104.
- `DecisionWindowNumericalTests` — class, line 122.
- `DecisionWindowNumericalTests.test_joint_decision_policy_and_original_state_replay_under_dfs` — def, line 123.

## [autonomy/tests/test_depth_first.py](../autonomy/tests/test_depth_first.py)

- `program` — def, line 19.
- `DepthFirstTests` — class, line 29.
- `DepthFirstTests.run_search` — def, line 30.
- `DepthFirstTests.test_common_goal_arrival_wins_and_completes_before_later_sibling` — def, line 79.
- `DepthFirstTests.test_deadline_preserves_incumbent_and_unvisited_sibling_without_redispatch` — def, line 88.
- `DepthFirstTests.test_in_flight_state_and_identity_survive_a_mid_solve_stop` — def, line 98.
- `DepthFirstTests.test_other_valid_constraint_variants_are_not_lost_to_local_objective_ranking` — def, line 111.
- `DepthFirstTests.test_spatial_decision_goals_do_not_require_clock_windows` — def, line 120.
- `DepthFirstTests.test_old_checkpoint_state_is_not_silently_loaded_as_dfs` — def, line 126.
- `InteractionOwnershipTests` — class, line 132.
- `InteractionOwnershipTests.test_uncommitted_future_contact_cannot_leave_early_approach_rows` — def, line 133.
- `InteractionOwnershipTests.test_shared_gate_distinguishes_swept_phase_and_keeps_unknown` — def, line 153.
- `InteractionOwnershipTests.test_realized_grind_height_is_not_the_collision_probe_height` — def, line 162.
- `VariantCoverageTests` — class, line 178.
- `VariantCoverageTests.test_distinct_options_cover_small_exhaustible_clear_trajectories` — def, line 179.
- `BuilderGateTests` — class, line 223.
- `BuilderGateTests.test_all_open_families_gate_before_creating_hard_block_rows` — def, line 224.
- `CompilerRecoveryTests` — class, line 249.
- `CompilerRecoveryTests.test_timed_out_bridge_can_restart_for_independent_validation` — def, line 250.
- `HistoricalInteractionTests` — class, line 260.
- `HistoricalInteractionTests.test_real11_b4_blip_is_excluded_before_its_wall_options_are_built` — def, line 261.

## [autonomy/tests/test_failure_diagnostic.py](../autonomy/tests/test_failure_diagnostic.py)

- `FailureDiagnosticTests` — class, line 10.
- `FailureDiagnosticTests.test_instrumented_baseline_preserves_generator_outputs_and_counts` — def, line 11.
- `FailureDiagnosticTests.test_completed_candidate_priority_is_separate_from_partial_length` — def, line 24.
- `FailureDiagnosticTests.test_tick_fair_pruning_preserves_multiple_elapsed_buckets` — def, line 34.
- `FailureDiagnosticTests.test_canonical_key_keeps_different_interaction_budgets_separate` — def, line 41.
- `FailureDiagnosticTests.test_canonicalization_removes_only_metadata_in_the_key_audit` — def, line 49.

## [autonomy/tests/test_fixed_clock_goal.py](../autonomy/tests/test_fixed_clock_goal.py)

- `FixedClockTests` — class, line 19.
- `FixedClockTests.test_priority_proposes_the_actual_intermediate_contact_before_an_air_gap` — def, line 20.
- `FixedClockTests.test_unknown_spatial_goal_is_rejected_before_compilation` — def, line 35.
- `FixedClockTests.test_explicit_clock_preserves_direction_without_goal_region` — def, line 41.
- `FixedClockTests.test_clock_generation_continues_after_goal_tag` — def, line 48.
- `FixedClockNumericalTests` — class, line 62.
- `FixedClockNumericalTests.test_terminal_clock_can_overshoot_goal_and_finish_airborne` — def, line 63.
- `FixedClockNumericalTests.test_unreachable_floor_gates_are_excluded_from_terminal_stage_checks` — def, line 91.
- `SupportOrderTests` — class, line 126.
- `SupportOrderTests.test_route_order_preserves_timings_and_does_not_require_terminal_landing` — def, line 127.

## [autonomy/tests/test_full_route_witness.py](../autonomy/tests/test_full_route_witness.py)

- `FullRouteWitnessTests` — class, line 11.
- `FullRouteWitnessTests.test_full_linkcraft_0_to_16_solution_is_independently_valid` — def, line 12.

## [autonomy/tests/test_generic_composition.py](../autonomy/tests/test_generic_composition.py)

- `initial` — def, line 10.
- `GenericCompositionTests` — class, line 14.
- `GenericCompositionTests.test_repeated_supported_units_do_not_stop_replay_at_first_landing` — def, line 15.
- `GenericCompositionTests.test_unbounded_repeated_event_types_share_the_same_append_contract` — def, line 27.
- `GenericCompositionTests.test_grind_can_end_in_air_then_choose_another_movement_type` — def, line 40.
- `GenericCompositionTests.test_wrong_boundary_and_horizon_are_rejected_without_mutating_prefix` — def, line 57.
- `GenericOpenGenerationTests` — class, line 70.
- `GenericOpenGenerationTests.test_all_stepping_cases_end_at_their_event_instead_of_a_forced_landing_tail` — def, line 71.
- `GenericOpenGenerationTests.test_boundary_entry_hit_refreshes_climbing_immediately_and_refines_ownership` — def, line 92.
- `GenericOpenGenerationTests.test_five_existing_ladder_episodes_use_the_generic_composer` — def, line 120.
- `GenericQueueTests` — class, line 136.
- `GenericQueueTests.test_new_registered_types_and_cost_order_need_no_pair_specific_queue_logic` — def, line 137.
- `GenericQueueTests.test_cancellation_deadlines_and_limits_preserve_unknown_coverage` — def, line 167.
- `select` — def, line 196.
- `GenericNumericalTests` — class, line 207.
- `GenericNumericalTests.setUp` — def, line 208.
- `GenericNumericalTests.solve` — def, line 212.
- `GenericNumericalTests.test_cold_shared_queue_discovers_grind_then_airborne_blip` — def, line 225.
- `GenericNumericalTests.test_repeated_ceiling_type_with_blip_and_ground_movement_between` — def, line 238.
- `GenericNumericalTests.test_two_airborne_ladders_continue_into_blip_without_a_support_reset` — def, line 256.
- `GenericNumericalTests.test_five_airborne_ladders_share_the_same_generic_route_compilation` — def, line 278.

## [autonomy/tests/test_hanging_vines.py](../autonomy/tests/test_hanging_vines.py)

- `falling_context` — def, line 22.
- `HangingVineTests` — class, line 27.
- `HangingVineTests.test_falling_region_does_not_require_a_wall_or_hide_real_wall_choices` — def, line 28.
- `HangingVineTests.test_all_four_horizontal_entry_faces_remain_available` — def, line 37.
- `HangingVineTests.test_wall_free_generation_keeps_all_sneak_masks_without_phantom_climbing` — def, line 43.
- `HangingVineTests.test_a_separately_selected_real_contact_can_still_refresh_climbing` — def, line 58.
- `HangingVineTests.test_falling_constraints_do_not_create_a_wall_plane_or_collision` — def, line 69.
- `HangingVineTests.test_append_can_add_a_wall_free_falling_region_to_an_airborne_prefix` — def, line 81.
- `HangingVineIntegrationTests` — class, line 97.
- `HangingVineIntegrationTests.test_cold_discovery_solves_hanging_vines_without_a_climb_wall` — def, line 98.
- `HangingVineIntegrationTests.test_saved_wall_free_candidate_revalidates_scalar_physics_and_scene` — def, line 116.

## [autonomy/tests/test_hit_events.py](../autonomy/tests/test_hit_events.py)

- `HitEventTests` — class, line 17.
- `HitEventTests.test_relative_hit_and_all_avoidance_rows_follow_inserted_runs` — def, line 18.
- `HitEventTests.test_generation_preserves_fixed_height_and_reports_candidate_limit` — def, line 37.
- `WallReversalIntegrationTests` — class, line 51.
- `WallReversalIntegrationTests.test_geometry_discovers_reversal_for_x_z_and_both_signs` — def, line 52.

## [autonomy/tests/test_integration.py](../autonomy/tests/test_integration.py)

- `IntegrationTests` — class, line 23.
- `IntegrationTests.test_obstacles_can_interleave_without_finishing_one_block_first` — def, line 24.
- `IntegrationTests.test_full_obstacle_geometry_finds_cross_and_wide_neos_and_checks_both_models` — def, line 43.
- `IntegrationTests.test_source_runoff_can_leave_the_near_side_and_stays_clear_while_y_overlaps` — def, line 69.
- `IntegrationTests.test_ceiling_can_be_passed_under_and_a_sealed_wall_is_never_accepted` — def, line 86.
- `IntegrationTests.test_shared_scene_bridge_detects_swept_walls_and_releases_below_the_bottom` — def, line 101.
- `IntegrationTests.test_runoff_competes_as_a_shorter_profile_in_the_same_route_search` — def, line 114.
- `IntegrationTests.test_a_jump_can_be_followed_by_a_zero_delay_runoff` — def, line 131.
- `IntegrationTests.test_small_drop_can_land_on_the_first_runoff_input` — def, line 147.
- `IntegrationTests.test_grounded_jump_after_leaving_the_edge_uses_only_the_scheduled_inputs` — def, line 162.
- `IntegrationTests.test_configured_objective_is_optimized_after_fixing_the_tick_count` — def, line 187.
- `IntegrationTests.test_staircase_uses_two_short_profiles_and_searches_the_run_between_them` — def, line 206.
- `IntegrationTests.test_ordinary_half_block_jump_survives_when_the_short_stair_profile_cannot_reach` — def, line 224.
- `IntegrationTests.test_different_heights_generate_edge_specific_airtimes` — def, line 237.
- `IntegrationTests.test_lower_target_cannot_be_accepted_through_an_earlier_floor` — def, line 249.
- `IntegrationTests.test_selected_block_path_can_require_an_added_ground_input` — def, line 263.
- `IntegrationTests.test_only_endpoints_are_fixed_and_remote_supports_are_pruned` — def, line 273.
- `IntegrationTests.test_route_pool_matches_exhaustive_small_family_with_coupled_momentum` — def, line 293.
- `IntegrationTests.test_blocks_without_supplied_airtimes_feed_the_existing_run_search` — def, line 317.
- `IntegrationTests.test_java_compiles_ground_jump_and_air_with_the_existing_model` — def, line 330.
- `IntegrationTests.test_java_reuses_the_mod_footprint_deriver` — def, line 340.
- `IntegrationTests.test_known_routes_zero_one_and_backtracking` — def, line 354.
- `IntegrationTests.test_linkcraft_section_keeps_runoff_and_finds_without_recorded_facings` — def, line 375.

## [autonomy/tests/test_ladder_bridge.py](../autonomy/tests/test_ladder_bridge.py)

- `LadderGeometryTests` — class, line 14.
- `LadderGeometryTests.test_columns_preserve_holes_and_signed_front_side_channels` — def, line 15.
- `LadderGeometryTests.test_straight_line_ranking_evaluates_progress_and_caps` — def, line 23.
- `LadderBridgeTests` — class, line 34.
- `LadderBridgeTests.setUp` — def, line 35.
- `LadderBridgeTests.test_shared_compiler_realizes_sneak_and_repeated_wall_sprint_loss` — def, line 39.
- `LadderBridgeTests.test_scene_checks_occupancy_sneak_and_actual_climb_velocity` — def, line 49.
- `LadderBridgeTests.test_first_clamped_sneak_can_preserve_progress_better_than_late_sneak` — def, line 72.
- `LadderBridgeTests.test_coplanar_climb_parts_require_the_declared_face_plane_and_overlap` — def, line 85.
- `LadderBridgeTests.test_ranked_pool_preserves_backups_and_does_not_change_schedules` — def, line 100.

## [autonomy/tests/test_ladder_ceiling.py](../autonomy/tests/test_ladder_ceiling.py)

- `climbing_case` — def, line 25.
- `complete_case` — def, line 47.
- `LadderCeilingTests` — class, line 55.
- `LadderCeilingTests.test_ownership_is_idempotent_finite_and_does_not_rewrite_controls_or_physics` — def, line 56.
- `LadderCeilingTests.test_only_climbing_successors_are_accepted_and_stops_stay_inside_climbing` — def, line 74.
- `LadderCeilingTests.test_actual_clip_and_unclipped_endpoint_stops_produce_distinct_heights` — def, line 88.
- `LadderCeilingTests.test_repeated_prediction_includes_prefixes_and_rejects_last_climb_input` — def, line 109.
- `LadderCeilingTests.test_neutral_ceiling_provider_preserves_provenance_and_existing_normal_contacts` — def, line 123.
- `LadderCeilingTests.test_regeneration_owns_the_changed_exit_and_rejects_direct_fall_successor` — def, line 133.
- `LadderCeilingTests.test_composed_entry_ceiling_transfers_ownership_without_duplicate_contact_or_effect` — def, line 152.
- `LadderCeilingTests.test_second_ladder_contact_effects_and_schedule_offsets_agree_after_retiming` — def, line 183.
- `LadderCeilingNumericalTests` — class, line 211.
- `LadderCeilingNumericalTests.setUp` — def, line 212.
- `LadderCeilingNumericalTests.test_saved_examples_match_fixture_and_pass_scalar_and_shared_scene_acceptance` — def, line 217.
- `LadderCeilingNumericalTests.test_fresh_joint_solves_for_climbing_and_each_endpoint_stop_combination` — def, line 236.

## [autonomy/tests/test_ladder_composition.py](../autonomy/tests/test_ladder_composition.py)

- `airborne_case` — def, line 24.
- `LadderRegionTests` — class, line 45.
- `LadderRegionTests.test_expired_generation_budget_retains_unknown_status` — def, line 46.
- `LadderRegionTests.test_horizontal_vertical_strips_merge_without_diagonal_or_hole_filling` — def, line 53.
- `LadderRegionTests.test_airborne_transfer_preserves_y_and_has_only_one_launch` — def, line 69.
- `LadderRegionTests.test_repeated_ladder_ceiling_events_regenerate_the_complete_schedule` — def, line 98.
- `LadderRegionTests.test_generator_marks_unprobed_transfer_entries_without_claiming_validation` — def, line 115.
- `LadderCompositionIntegrationTests` — class, line 128.
- `LadderCompositionIntegrationTests.test_direct_airborne_transfer_solves_without_a_fictitious_landing` — def, line 129.
- `LadderCompositionIntegrationTests.test_geometry_only_ladder_ceiling_and_two_ladder_routes` — def, line 145.
- `CompositionEvidenceTests` — class, line 170.
- `CompositionEvidenceTests.test_saved_multiple_ladder_ceiling_transfer_and_contact_blockage_contracts` — def, line 171.

## [autonomy/tests/test_ladder_generation_speed.py](../autonomy/tests/test_ladder_generation_speed.py)

- `LadderGenerationSpeedTests` — class, line 12.
- `LadderGenerationSpeedTests.test_open_trace_skips_the_extra_floor_prediction` — def, line 13.
- `LadderGenerationSpeedTests.test_landing_keeps_exact_downward_clip_prediction` — def, line 21.
- `LadderGenerationSpeedTests.test_invalid_occupied_height_stops_at_the_first_failure` — def, line 30.
- `LadderGenerationSpeedTests.test_invalid_initial_occupancy_preserves_the_incoming_carry` — def, line 39.
- `LadderGenerationSpeedTests.test_real_top_and_bottom_exits_are_not_rejected` — def, line 46.
- `LadderGenerationSpeedTests.test_random_valid_traces_are_identical_and_invalid_traces_stop_at_first_bad_state` — def, line 53.
- `LadderGenerationSpeedTests.test_generator_emits_identical_candidates_with_full_or_short_circuit_validation` — def, line 72.

## [autonomy/tests/test_ladder_schedules.py](../autonomy/tests/test_ladder_schedules.py)

- `reference_y` — def, line 14.
- `request` — def, line 39.
- `ClimbPatternTests` — class, line 47.
- `ClimbPatternTests.test_four_stop_choices_omit_first_final_or_both_hits` — def, line 48.
- `ClimbPatternTests.test_two_stops_are_not_adjacent_and_short_windows_remain_valid` — def, line 60.
- `ClimbPatternTests.test_invalid_climb_windows_are_rejected` — def, line 67.
- `ClimbPatternTests.test_all_256_sneak_masks_include_every_four_sneak_arrangement` — def, line 72.
- `ClimbPatternTests.test_cap_priority_changes_order_without_losing_masks` — def, line 81.
- `ClimbPatternTests.test_invalid_sneak_domains_and_returned_masks_are_independent` — def, line 89.
- `LadderHeightTests` — class, line 99.
- `LadderHeightTests.trace` — def, line 100.
- `LadderHeightTests.test_final_inside_hit_stop_lowers_exit_height_on_the_following_input` — def, line 105.
- `LadderHeightTests.test_hit_ending_outside_does_not_refresh_or_change_y` — def, line 118.
- `LadderHeightTests.test_a_stop_can_preserve_more_upward_velocity_than_a_first_hit` — def, line 126.
- `LadderHeightTests.test_a_hit_at_exact_point_two_vertical_movement_has_no_extra_y_effect` — def, line 134.
- `LadderHeightTests.test_beginning_stop_allows_a_capped_fall_instead_of_the_next_climb` — def, line 140.
- `LadderHeightTests.test_beginning_stop_with_sneak_arrests_fall_but_does_not_invent_a_hit` — def, line 150.
- `LadderHeightTests.test_both_stops_preserve_the_middle_hits_and_both_height_changes` — def, line 160.
- `LadderHeightTests.test_fall_cap_applies_at_start_not_on_an_uncapped_entering_input` — def, line 169.
- `LadderHeightTests.test_enter_and_hit_establishes_carry_for_the_following_input` — def, line 174.
- `LadderHeightTests.test_sneak_during_fall_zeroes_movement_not_the_stored_carry` — def, line 179.
- `LadderHeightTests.test_sneak_outside_does_not_arrest_descent` — def, line 185.
- `LadderHeightTests.test_sneak_does_not_stop_positive_climb_carry_or_continued_hits` — def, line 190.
- `LadderHeightTests.test_interspersed_sneaks_change_height_even_with_equal_sneak_counts` — def, line 199.
- `LadderHeightTests.test_incoming_y_threshold_is_strict_and_precedes_ladder_rules` — def, line 209.
- `LadderHeightTests.test_floor_arrival_without_clipping_is_not_a_landing_yet` — def, line 215.
- `LadderHeightTests.test_ground_jump_order_and_invalid_air_jump_are_preserved` — def, line 223.
- `LadderHeightTests.test_state_seams_preserve_ladder_carry_sneak_and_previous_hit` — def, line 232.
- `LadderHeightTests.test_random_traces_match_independent_minecraft_order_equations` — def, line 243.
- `LadderHeightTests.test_traces_cross_check_the_existing_independent_scalar_validator` — def, line 256.
- `LadderGeneratorTests` — class, line 281.
- `LadderGeneratorTests.test_first_hit_reach_bound_preserves_every_remaining_small_family_witness` — def, line 282.
- `LadderGeneratorTests.test_malformed_first_hit_bounds_are_rejected` — def, line 299.
- `LadderGeneratorTests.test_generated_end_stop_lowers_exit_height_and_lands_one_input_earlier` — def, line 306.
- `LadderGeneratorTests.test_front_side_and_all_six_entry_times_are_preserved` — def, line 320.
- `LadderGeneratorTests.test_exit_step_cannot_be_a_climb_hit_and_first_cap_uses_entry_state` — def, line 332.
- `LadderGeneratorTests.test_small_family_matches_exhaustive_hit_and_sneak_enumeration` — def, line 341.
- `LadderGeneratorTests.test_larger_contact_family_matches_exhaustive_windows_and_sneaks` — def, line 363.
- `LadderGeneratorTests.test_generator_preserves_all_eight_tick_nonclimb_sneak_prefixes` — def, line 387.
- `LadderGeneratorTests.test_top_exit_requires_actual_y_crossing_and_records_the_stop_consequence` — def, line 398.
- `LadderGeneratorTests.test_timing_buckets_keep_post_exit_time_and_cover_every_schedule_once` — def, line 409.
- `LadderGeneratorTests.test_caps_are_explicit_and_do_not_claim_complete_generation` — def, line 426.
- `LadderGeneratorTests.test_input_output_and_separate_candidates_do_not_alias` — def, line 440.
- `LadderGeneratorTests.test_bad_requests_and_nonfinite_physics_are_rejected` — def, line 453.
- `LadderGeneratorTests.test_cancelled_generation_retains_explicit_status` — def, line 466.

## [autonomy/tests/test_linkcraft.py](../autonomy/tests/test_linkcraft.py)

- `capture` — def, line 6.
- `LinkcraftExtractionTests` — class, line 20.
- `LinkcraftExtractionTests.test_extracts_without_any_recorded_facing_field` — def, line 21.
- `LinkcraftExtractionTests.test_missing_geometry_is_not_invented` — def, line 31.
- `LinkcraftExtractionTests.test_grounded_height_change_is_not_silently_retimed` — def, line 37.

## [autonomy/tests/test_long_ladder_chains.py](../autonomy/tests/test_long_ladder_chains.py)

- `LongLadderChainTests` — class, line 19.
- `LongLadderChainTests.test_saved_cold_three_and_prescribed_five_climbs_revalidate_full_scene` — def, line 20.
- `LongLadderChainTests.test_fresh_solves_start_inside_first_ladder_without_turning_back_to_its_centre` — def, line 40.

## [autonomy/tests/test_minimum_runs.py](../autonomy/tests/test_minimum_runs.py)

- `flight` — def, line 17.
- `MinimumRunTests` — class, line 26.
- `MinimumRunTests.setUp` — def, line 27.
- `MinimumRunTests.test_same_last_jump_discards_more_runs_but_keeps_equal_and_fewer` — def, line 29.
- `MinimumRunTests.test_equal_run_timing_variants_and_distinct_obstacle_choices_survive` — def, line 46.
- `MinimumRunTests.test_interacting_or_unclassified_ground_preparation_is_retained` — def, line 56.
- `MinimumRunTests.test_changed_prior_decisions_do_not_share_a_representative` — def, line 65.
- `ArchivedMinimumRunTests` — class, line 73.
- `ArchivedMinimumRunTests.setUpClass` — def, line 75.
- `ArchivedMinimumRunTests.test_real11_last_jump_three_runs_excludes_four_not_total_fifteen` — def, line 86.
- `ArchivedMinimumRunTests.test_real11_zero_run_last_jump_excludes_all_delayed_prefix_calls` — def, line 94.
- `ArchivedMinimumRunTests.test_real11_initial_histories_and_terminal_contacts_stay_distinct` — def, line 100.
- `MinimumRunDfsTests` — class, line 107.
- `MinimumRunDfsTests.search` — def, line 108.
- `MinimumRunDfsTests.test_prunes_before_dispatch_and_can_replay_exclusions_as_a_control` — def, line 132.
- `MinimumRunDfsTests.test_unknown_earlier_timing_is_preserved_and_does_not_establish_minimum` — def, line 137.
- `MinimumRunDfsTests.test_feasible_representative_survives_resume_and_policy_mismatch_is_rejected` — def, line 142.

## [autonomy/tests/test_movement_composition.py](../autonomy/tests/test_movement_composition.py)

- `MovementCompositionTests` — class, line 29.
- `MovementCompositionTests.test_direct_transfer_seam_preserves_both_regions_and_entry_hit` — def, line 30.
- `MovementCompositionTests.test_endpoint_stops_keep_their_unit_and_change_actual_y_carry` — def, line 42.
- `MovementCompositionTests.test_units_retime_together_with_runs_hits_and_sneak` — def, line 62.
- `MovementCompositionTests.test_actual_extra_wall_contact_cannot_restart_climbing` — def, line 79.
- `MovementCompositionTests.test_legacy_saved_route_without_units_reconstructs_the_same_program` — def, line 88.
- `MovementCompositionTests.test_chain_cancellation_and_prefix_cap_are_explicit` — def, line 97.
- `MovementCompositionTests.test_chain_queue_can_extend_through_five_airborne_prefixes` — def, line 120.
- `MovementCompositionTests.test_append_reoptimizes_a_complete_prefix_instead_of_requiring_a_floor` — def, line 139.
- `MovementCompositionTests.test_real_append_generator_repeats_across_five_climbing_regions` — def, line 155.
- `MovementCompositionIntegrationTests` — class, line 181.
- `MovementCompositionIntegrationTests.test_five_airborne_ladders_and_bottom_exit_solve_from_episode_contracts` — def, line 182.

## [autonomy/tests/test_movement_contacts.py](../autonomy/tests/test_movement_contacts.py)

- `small_request` — def, line 25.
- `MovementContactTests` — class, line 32.
- `MovementContactTests.test_ceiling_regenerates_exit_time_instead_of_requiring_old_exit_to_work` — def, line 33.
- `MovementContactTests.test_contact_assisted_entry_is_generated_even_without_a_baseline_schedule` — def, line 48.
- `MovementContactTests.test_repeated_ceiling_clips_during_climbing_are_generated_as_one_sequence` — def, line 61.
- `MovementContactTests.test_small_shared_mask_budget_can_emit_a_valid_regenerated_schedule` — def, line 71.
- `MovementContactTests.test_same_consecutive_and_independent_contacts_preserve_all_axis_types` — def, line 80.
- `MovementContactTests.test_final_air_seed_preserves_original_state_and_never_inserts_another_jump` — def, line 94.
- `MovementContactTests.test_final_air_contact_recomputes_landing_without_moving_earlier_anchors` — def, line 103.
- `MovementContactTests.test_existing_contacts_are_retained_and_conflicting_normals_are_not_emitted` — def, line 115.
- `MovementContactTests.test_disabled_cancelled_and_deadline_generation_are_explicit` — def, line 123.
- `GeneratedMixedContactIntegrationTests` — class, line 138.
- `GeneratedMixedContactIntegrationTests.test_generated_contacts_on_approach_second_ladder_and_final_air_solve_jointly` — def, line 139.

## [autonomy/tests/test_movement_entry.py](../autonomy/tests/test_movement_entry.py)

- `MovementEntryTests` — class, line 24.
- `MovementEntryTests.test_initial_membership_needs_no_provisional_jump_or_fall` — def, line 25.
- `MovementEntryTests.test_expired_budget_retains_every_unknown_entry_as_unprobed` — def, line 36.
- `MovementEntryTests.test_unknown_numerical_results_are_never_infeasibility` — def, line 45.
- `MovementEntryTests.test_worker_success_must_also_pass_independent_scalar_validation` — def, line 55.
- `MovementEntryTests.test_appended_entry_discovers_ceilings_from_the_carried_airborne_trace` — def, line 67.
- `MovementEntryTests.test_append_uses_shared_probe_and_keeps_delayed_entries` — def, line 77.
- `MovementEntryIntegrationTests` — class, line 98.
- `MovementEntryIntegrationTests.setUp` — def, line 99.
- `MovementEntryIntegrationTests.root` — def, line 104.
- `MovementEntryIntegrationTests.accept` — def, line 114.
- `MovementEntryIntegrationTests.test_initial_hit_on_entry_starts_climbing_without_preentry_cap` — def, line 124.
- `MovementEntryIntegrationTests.test_appended_entry_solves_whole_history_and_retains_run_retiming` — def, line 134.
- `MovementEntryIntegrationTests.test_entry_after_four_climbs_keeps_every_prior_episode_free` — def, line 155.
- `MovementEntryIntegrationTests.test_append_entry_ceiling_uses_its_global_tick_and_original_y_history` — def, line 175.
- `MovementEntryIntegrationTests.test_shared_probe_validates_a_later_region_and_reports_earlier_unknowns` — def, line 192.

## [autonomy/tests/test_movement_generation.py](../autonomy/tests/test_movement_generation.py)

- `context` — def, line 11.
- `MovementGenerationTests` — class, line 15.
- `MovementGenerationTests.test_normal_uses_existing_jump_and_runoff_physics` — def, line 16.
- `MovementGenerationTests.test_composer_uses_registered_generators_and_emits_exact_five_grammars` — def, line 26.
- `MovementGenerationTests.test_front_entry_hit_has_no_forced_falling_phase` — def, line 40.
- `MovementGenerationTests.test_all_four_stop_variants_are_internal_and_omit_last_refresh_hit` — def, line 47.
- `MovementGenerationTests.test_top_ends_climbing_and_bottom_can_end_in_either_phase` — def, line 58.
- `MovementGenerationTests.test_forced_contacts_are_owned_by_climbing_and_cannot_restart_it` — def, line 64.
- `MovementGenerationTests.test_sneak_domain_keeps_falling_and_endpoint_stop_and_first_clamp` — def, line 74.
- `MovementGenerationTests.test_generated_units_are_compiled_and_invalid_edits_are_rejected` — def, line 83.
- `MovementGenerationTests.test_unknown_type_is_rejected` — def, line 97.

## [autonomy/tests/test_movement_units.py](../autonomy/tests/test_movement_units.py)

- `schedule` — def, line 12.
- `MovementUnitTests` — class, line 18.
- `MovementUnitTests.test_exactly_five_phase_sequences_and_top_rule` — def, line 19.
- `MovementUnitTests.test_all_endpoint_variants_remain_owned_by_climbing` — def, line 32.
- `MovementUnitTests.test_exhaustive_hit_masks_allow_one_contiguous_climb_and_endpoint_stops` — def, line 43.
- `MovementUnitTests.test_open_bottom_exit_uses_real_fall_cap_and_no_landing` — def, line 56.
- `MovementUnitTests.test_open_prefix_does_not_require_a_reachable_provisional_floor` — def, line 76.
- `MovementUnitTests.test_open_terminal_rejects_a_real_floor_contact_instead_of_labeling_it_airborne` — def, line 87.
- `MovementUnitTests.test_unit_materialization_matches_five_phase_occupancy_and_contacts` — def, line 97.
- `MovementUnitTests.test_two_episode_composition_carries_units_and_shared_masks` — def, line 110.

## [autonomy/tests/test_obstacles.py](../autonomy/tests/test_obstacles.py)

- `scene` — def, line 20.
- `passage_case` — def, line 24.
- `ObstacleGeometryTests` — class, line 35.
- `ObstacleGeometryTests.test_collision_boxes_derive_supports_and_survive_normalization_without_mutation` — def, line 36.
- `ObstacleGeometryTests.test_multipart_supports_remain_separate_with_their_actual_gap` — def, line 46.
- `ObstacleGeometryTests.test_geometry_requires_vertical_bounds_coverage_and_consistent_supports` — def, line 55.
- `ObstacleGeometryTests.test_jump_height_split_removes_high_landing_edges_but_preserves_the_solid` — def, line 74.
- `ObstacleGeometryTests.test_body_clearance_uses_the_head_and_the_actual_bottom` — def, line 83.
- `BlockageTests` — class, line 92.
- `BlockageTests.test_default_caps_and_tightening_are_per_physical_block` — def, line 93.
- `BlockageTests.test_horizontal_vertical_and_combined_limits_all_apply` — def, line 105.
- `BlockageTests.test_multipart_blocks_share_the_cap_across_separate_passages_and_legs` — def, line 120.
- `BlockageTests.test_generator_keeps_mixed_three_blockages_and_respects_all_three_caps` — def, line 129.
- `BlockageTests.test_interleaved_blocks_keep_independent_intervals_on_one_retimed_timeline` — def, line 138.
- `BlockageTests.test_refining_one_part_reserves_the_other_parts_budget_without_resetting_the_block` — def, line 162.
- `BlockageTests.test_later_passage_seed_keeps_the_earlier_prefix_guess_and_adds_no_facing_pins` — def, line 173.
- `BlockageTests.test_all_six_orders_match_the_minecraft_corner_probe` — def, line 196.
- `BlockageTests.test_wide_neo_generates_both_sides_and_height_prunes_every_vertical_face` — def, line 215.
- `BlockageTests.test_reachable_top_and_suspended_bottom_keep_vertical_and_horizontal_candidates` — def, line 227.
- `BlockageTests.test_gate_intervals_retime_with_runs_and_do_not_make_one_face_a_permanent_wall` — def, line 236.
- `BlockageTests.test_y_gate_stops_constraining_x_after_the_body_clears_the_bottom` — def, line 258.
- `BlockageTests.test_caps_are_visible_and_never_silently_expand_the_blockage_family` — def, line 271.

## [autonomy/tests/test_orchestrator_continuation.py](../autonomy/tests/test_orchestrator_continuation.py)

- `InteractionReachabilityTests` — class, line 22.
- `InteractionReachabilityTests.test_diagonal_distance_bound_rejects_box_relaxation_false_positive` — def, line 23.
- `InteractionReachabilityTests.test_all_acceleration_directions_and_swept_phases_remain_in_the_envelope` — def, line 27.
- `InteractionReachabilityTests.test_required_and_unwanted_interactions_use_the_same_query` — def, line 52.
- `InteractionReachabilityTests.test_linear_relaxation_rules_out_velocity_position_coupling` — def, line 61.
- `InteractionReachabilityTests.test_selected_clips_do_not_exclude_their_feasible_controls` — def, line 70.
- `ScopeAndViabilityTests` — class, line 89.
- `ScopeAndViabilityTests.test_complete_scope_cannot_silently_omit_a_block` — def, line 90.
- `ScopeAndViabilityTests.test_fall_below_every_floor_is_dead_only_when_a_contact_is_required` — def, line 97.
- `ScopeAndViabilityTests.test_unsupported_first_event_family_is_not_proof_of_death` — def, line 107.
- `PersistentGenerationTests` — class, line 113.
- `PersistentGenerationTests.test_route_side_still_allows_clearing_an_obstacle_vertically` — def, line 114.
- `PersistentGenerationTests.test_choice_and_queue_sizes_do_not_erase_a_late_candidate_after_reload` — def, line 133.
- `PersistentGenerationTests.test_interrupted_screen_keeps_the_unprocessed_choice` — def, line 155.
- `PersistentGenerationTests.test_constraint_generation_cursor_survives_a_new_facing_guess` — def, line 166.
- `StateProtectionTests` — class, line 184.
- `StateProtectionTests.check_protected` — def, line 185.
- `StateProtectionTests.test_forward_velocity_and_speed_are_independent_guards` — def, line 191.
- `StateProtectionTests.test_different_schedules_are_not_clock_compared_even_with_equal_counts` — def, line 195.
- `StateProtectionTests.test_missing_velocity_keeps_candidate_unknown_rather_than_inferior` — def, line 201.
- `StateProtectionTests.test_queue_key_counts_effective_grounded_inputs` — def, line 206.
- `OuterResumeTests` — class, line 211.
- `OuterResumeTests.test_split_search_does_not_dispatch_the_completed_terminal_again` — def, line 212.
- `ScopedNumericalTests` — class, line 250.
- `ScopedNumericalTests.test_omitted_future_obstacle_does_not_reject_preliminary_guidance` — def, line 251.

## [autonomy/tests/test_repair_resume.py](../autonomy/tests/test_repair_resume.py)

- `route` — def, line 16.
- `best` — def, line 21.
- `outcome` — def, line 26.
- `RepairResumeTests` — class, line 33.
- `RepairResumeTests.test_extended_program_keeps_guess_without_copying_exact_old_problem_work` — def, line 34.
- `RepairResumeTests.test_valid_saved_incumbent_does_not_need_another_numerical_request` — def, line 45.
- `RepairResumeTests.test_worker_interruption_restores_the_inflight_branch` — def, line 52.
- `RepairResumeTests.test_unknown_retry_is_explicit_and_not_a_completed_result` — def, line 66.
- `RepairResumeTests.invoke` — def, line 74.
- `RepairResumeTests.test_corridor_diagnostics_survive_branch_result_without_new_replay_calls` — def, line 80.
- `RepairResumeTests.test_pending_guards_and_facing_guesses_resume_without_repeating_root` — def, line 109.
- `RepairResumeTests.test_unknown_numeric_branch_is_stored_and_revalidated_on_resume` — def, line 129.
- `RepairResumeTests.test_changed_start_or_corridor_discards_incompatible_state` — def, line 138.
- `RepairResumeTests.test_changed_constraints_cannot_reuse_prior_acceptance` — def, line 154.
- `RepairResumeTests.test_cancellation_keeps_queue_serializable_for_resume` — def, line 161.
- `RepairResumeTests.test_compatible_extension_preserves_symbolic_state_and_has_distinct_search_identity` — def, line 169.

## [autonomy/tests/test_route_guidance.py](../autonomy/tests/test_route_guidance.py)

- `Coefficients` — class, line 19.
- `Coefficients.derive_footprints` — def, line 20.
- `Coefficients.compile` — def, line 25.
- `ordinary` — def, line 30.
- `GuidanceGraphTests` — class, line 37.
- `GuidanceGraphTests.test_phase_flow_preserves_every_small_allocation_and_horizon` — def, line 38.
- `GuidanceGraphTests.test_supports_use_pre_landing_and_run_inputs_without_pinning_jump` — def, line 50.
- `GuidanceGraphTests.test_consecutive_passage_uses_correct_same_or_successive_states` — def, line 60.
- `GuidanceGraphTests.test_closed_inertia_hull_contains_both_threshold_branches` — def, line 71.
- `GuidanceGraphTests.test_decision_fixes_keep_parent_domains_and_reject_unknowns` — def, line 80.
- `GuidanceGraphTests.test_scope_and_horizon_errors_are_explicit` — def, line 92.
- `GuidanceNumericalTests` — class, line 105.
- `GuidanceNumericalTests.test_dual_projection_and_box_residual_correction_cover_known_optimum` — def, line 106.
- `GuidanceNumericalTests.test_fixed_history_disk_matches_analytic_affine_oracle` — def, line 119.
- `GuidanceNumericalTests.test_real_witness_lifts_cover_thresholds_and_random_free_facings` — def, line 135.
- `GuidanceNumericalTests.test_conditional_bounds_nest_and_compact_circle_is_in_disk` — def, line 161.
- `GuidancePhysicalTests` — class, line 183.
- `GuidancePhysicalTests.setUp` — def, line 184.
- `GuidancePhysicalTests.test_edge_coefficients_match_complete_authoritative_schedules` — def, line 189.
- `GuidancePhysicalTests.test_guidance_produces_independently_checked_whole_initial_state_path` — def, line 203.
- `GuidancePhysicalTests.test_retaining_two_timings_recovers_the_narrow_support_ranking_counterexample` — def, line 220.

## [autonomy/tests/test_runoff.py](../autonomy/tests/test_runoff.py)

- `runoff_route` — def, line 6.
- `RunOffTests` — class, line 17.
- `RunOffTests.test_departure_is_grounded_without_jump_boost_then_air` — def, line 18.
- `RunOffTests.test_zero_delay_after_a_jump_checks_source_support_at_the_preceding_state` — def, line 30.
- `RunOffTests.test_added_runs_retime_preceding_support_and_current_exit_together` — def, line 46.
- `RunOffTests.test_authored_regions_are_not_expanded_twice` — def, line 54.
- `RunOffTests.test_diagonal_policy_only_changes_guesses_not_facing_constraints` — def, line 61.

## [autonomy/tests/test_screen.py](../autonomy/tests/test_screen.py)

- `ScreenTests` — class, line 8.
- `ScreenTests.test_unreachable_wall_is_rejected` — def, line 9.
- `ScreenTests.test_per_axis_zeroing_and_turns_do_not_reject_a_known_witness` — def, line 14.
- `ScreenTests.test_ladder_cap_cannot_claim_reaching_a_further_contact` — def, line 32.
- `ScreenTests.test_contact_cap_screen_keeps_random_valid_witnesses` — def, line 40.

## [autonomy/tests/test_search.py](../autonomy/tests/test_search.py)

- `route` — def, line 9.
- `outcome` — def, line 16.
- `SearchTests` — class, line 20.
- `SearchTests.test_equal_run_allocations_remain_available_for_objective_optimization` — def, line 21.
- `SearchTests.test_increments_the_first_failing_jump_and_backtracks_at_support_limit` — def, line 34.
- `SearchTests.test_unknown_run_backtracks_without_claiming_physical_infeasibility` — def, line 54.
- `SearchTests.test_improve_can_trade_a_prior_tick_for_fewer_later_ticks` — def, line 66.
- `SearchTests.test_zero_run_witness_attains_nonnegative_lower_bound` — def, line 80.
- `SearchTests.test_limits_and_cancel_do_not_leave_a_partial_route` — def, line 85.
- `SearchTests.test_error_stops_instead_of_becoming_an_impossible_schedule` — def, line 96.
- `ScheduleTests` — class, line 102.
- `ScheduleTests.test_immediate_continuation_and_landing_collision_phase` — def, line 103.
- `ScheduleTests.test_run_ticks_shift_all_later_events_and_require_ground_support` — def, line 113.
- `ScheduleTests.test_candidates_have_independent_arrays_and_source_is_unchanged` — def, line 124.
- `ScheduleTests.test_initial_overlap_and_invalid_airtimes_are_rejected` — def, line 135.
- `ScheduleTests.test_unsupported_physics_is_not_silently_ignored` — def, line 145.

## [autonomy/tests/test_search_fixes.py](../autonomy/tests/test_search_fixes.py)

- `SearchFixTests` — class, line 11.
- `SearchFixTests.test_completed_profiles_survive_partial_queue_and_expansion_caps` — def, line 12.
- `SearchFixTests.test_outer_incumbent_survives_invalid_polish_and_repair_continues` — def, line 35.
- `SearchFixTests.test_invalid_seed_is_never_retained` — def, line 38.
- `SearchFixTests.test_outer_incumbent_survives_later_solver_error` — def, line 42.
- `SearchFixTests.test_outer_incumbent_survives_later_cancellation` — def, line 45.
- `SearchFixTests.exercise_incumbent` — def, line 48.
- `SearchFixTests.test_only_complete_plans_publish_outer_incumbents` — def, line 76.
- `SearchFixTests.test_custom_projection_controls_fallback_direction` — def, line 96.

## [autonomy/tests/test_shared_contacts.py](../autonomy/tests/test_shared_contacts.py)

- `SharedContactTests` — class, line 11.
- `SharedContactTests.setUp` — def, line 12.
- `SharedContactTests.test_hit_cancels_sprint_with_carried_air_factor` — def, line 16.
- `SharedContactTests.test_raw_wall_clip_and_outward_following_movement` — def, line 29.
- `SharedContactTests.test_finite_face_and_nearer_wall_reject_selected_hit` — def, line 45.
- `SharedContactTests.test_paired_contacts_use_new_x_for_z_and_repeated_hit_is_rejected` — def, line 59.

## [autonomy/tests/test_stepping_cases.py](../autonomy/tests/test_stepping_cases.py)

- `fixture` — def, line 23.
- `generated` — def, line 27.
- `SteppingCaseTests` — class, line 38.
- `SteppingCaseTests.test_legacy_prefix_without_landing_phase_keeps_pre_landing_timing` — def, line 39.
- `SteppingCaseTests.test_legacy_boost_profile_is_adapted_to_its_registered_owner` — def, line 49.
- `SteppingCaseTests.test_scalar_named_outcomes_and_rejected_controls` — def, line 58.
- `SteppingCaseTests.test_height_windows_and_free_hit_tick_are_required` — def, line 85.
- `SteppingCaseTests.test_generated_units_retime_all_case_controls_constraints_and_scene_contacts` — def, line 97.
- `SteppingCaseTests.test_elevated_ground_hands_off_to_jump_or_ground_then_air` — def, line 125.
- `SteppingCaseTests.test_ladder_ownership_survives_typed_air_overlay` — def, line 139.
- `SteppingCaseTests.test_corner_constraints_have_x_approach_and_consecutive_z_escape` — def, line 153.
- `SteppingCaseTests.test_grind_and_corner_cancel_share_y_without_claiming_a_wall_hit` — def, line 164.
- `SteppingNumericalTests` — class, line 177.
- `SteppingNumericalTests.test_fresh_original_state_solves_for_every_case_and_both_blip_wall_choices` — def, line 178.
- `SteppingNumericalTests.test_z_facing_corner_controls_do_not_inherit_x_facing_vertical_cancellation` — def, line 212.
- `SteppingNumericalTests.test_normal_step_supports_both_horizontal_axes` — def, line 240.
- `SteppingNumericalTests.test_grind_continues_through_normal_air_to_a_lower_floor` — def, line 254.
- `SteppingNumericalTests.test_cold_discovery_covers_the_catalogue_and_allows_composite_height_gain` — def, line 273.
- `SteppingNumericalTests.test_cold_geometry_discovers_normal_steps_and_exposes_generation_caps` — def, line 290.

## [autonomy/tests/test_terminal_goals.py](../autonomy/tests/test_terminal_goals.py)

- `TerminalGoalTests` — class, line 13.
- `TerminalGoalTests.setUp` — def, line 14.
- `TerminalGoalTests.program` — def, line 19.
- `TerminalGoalTests.test_downward_height_clock_is_not_a_fixed_tick_or_apex` — def, line 23.
- `TerminalGoalTests.test_goal_rows_are_one_sided_center_bounds_with_no_fake_landing` — def, line 28.
- `TerminalGoalTests.test_unknown_or_vertical_terminal_rows_are_rejected` — def, line 49.
- `TerminalGoalNumericalTests` — class, line 56.
- `TerminalGoalNumericalTests.test_generic_height_goal_can_finish_airborne_beyond_the_physical_block` — def, line 57.

## [autonomy/tests/test_time_progress.py](../autonomy/tests/test_time_progress.py)

- `label` — def, line 7.
- `TimeProgressTests` — class, line 15.
- `TimeProgressTests.retained` — def, line 16.
- `TimeProgressTests.test_fast_and_far_are_different_and_tradeoffs_survive` — def, line 20.
- `TimeProgressTests.test_similar_candidates_are_not_strictly_frontier_pruned` — def, line 24.
- `TimeProgressTests.test_only_marked_inferiority_is_discarded` — def, line 28.
- `TimeProgressTests.test_unrelated_fastest_and_farthest_do_not_dominate_middle` — def, line 33.
- `TimeProgressTests.test_distance_at_different_clocks_is_not_compared` — def, line 37.
- `TimeProgressTests.test_different_spatial_goals_are_not_compared` — def, line 41.
- `TimeProgressTests.test_unknown_measurement_does_not_mean_bad_candidate` — def, line 45.
- `TimeProgressTests.test_full_reference_keeps_would_be_discards_despite_cap` — def, line 49.
- `TimeProgressTests.test_hard_cap_is_reported_separately` — def, line 56.
- `TimeProgressTests.test_translation_does_not_change_classification` — def, line 64.
- `ComparisonAuditTests` — class, line 71.
- `ComparisonAuditTests.test_numerical_error_cannot_be_reported_as_completed_reference` — def, line 72.
- `ComparisonAuditTests.test_competitive_followup_targets_incomplete_omissions_and_keeps_physics` — def, line 88.
- `ComparisonAuditTests.test_reference_exhaustion_requires_uncapped_parent_generation` — def, line 105.
- `ComparisonAuditTests.test_retaining_decisions_does_not_commit_clock_optimum_guards_or_states` — def, line 113.
- `ComparisonAuditTests.test_completed_candidate_in_window_is_already_final_evaluated` — def, line 128.
- `ComparisonAuditTests.test_regression_really_continues_discarded_candidate_that_wins` — def, line 134.
- `ComparisonAuditTests.test_unknown_reference_cannot_claim_full_audit` — def, line 148.

## [autonomy/vertical_profiles.py](../autonomy/vertical_profiles.py)

- `_flags` — def, line 8.
- `trace_ladder_schedule` — def, line 16.

## [ipopt/capture.py](../ipopt/capture.py)

- `cell` — def, line 13.
- `attach_capture` — def, line 17.

## [ipopt/continuation.py](../ipopt/continuation.py)

- `Incumbent` — class, line 9.
- `Incumbent.__init__` — def, line 10.
- `Incumbent.consider` — def, line 15.
- `Incumbent.pattern` — def, line 28.
- `run` — def, line 34.

## [ipopt/ladder.py](../ipopt/ladder.py)

- `f32` — def, line 5.
- `clamp` — def, line 16.
- `branch` — def, line 20.
- `vertical_move` — def, line 24.
- `vertical_carry` — def, line 36.
- `vertical_step` — def, line 42.

## [ipopt/model.py](../ipopt/model.py)

- `number` — def, line 15.
- `integer` — def, line 21.
- `prepare` — def, line 27.
- `fingerprint` — def, line 202.
- `wrap` — def, line 207.
- `validate` — def, line 216.

## [ipopt/solve.py](../ipopt/solve.py)

- `read_json` — def, line 18.
- `write_json` — def, line 25.
- `main` — def, line 40.

## [ipopt/solver.py](../ipopt/solver.py)

- `_command` — def, line 16.
- `solve` — def, line 20.

## [ipopt/tests/test_capture.py](../ipopt/tests/test_capture.py)

- `read` — def, line 16.
- `CaptureTests` — class, line 21.
- `CaptureTests.test_climb_signatures_include_entry_and_idle_ladder_states` — def, line 22.
- `CaptureTests.test_vertical_reference_replays_capture_exactly` — def, line 35.
- `CaptureTests.test_mid_climb_start_preserves_incoming_velocity_and_previous_hit` — def, line 51.
- `CaptureTests.test_profile_range_and_initial_state_mismatches_are_rejected` — def, line 62.
- `CaptureTests.test_vertical_reference_failure_cannot_be_accepted_as_feasible` — def, line 74.

## [ipopt/tests/test_continuation.py](../ipopt/tests/test_continuation.py)

- `ContinuationTests` — class, line 8.
- `ContinuationTests.request` — def, line 9.
- `ContinuationTests.test_failed_phase_cannot_displace_valid_path_for_polishing` — def, line 15.
- `ContinuationTests.test_fallback_is_explored_after_successful_incumbent_polish` — def, line 36.
- `ContinuationTests.test_incumbent_tracks_minimization_and_rejects_invalid_paths` — def, line 52.
- `ContinuationTests.test_zero_threshold_uses_fallback_without_inertia_inference` — def, line 65.
- `ContinuationTests.test_zero_threshold_failed_fallback_preserves_valid_incumbent` — def, line 85.
- `ContinuationTests.test_explicit_pattern_is_preserved` — def, line 98.

## [ipopt/tests/test_integration.py](../ipopt/tests/test_integration.py)

- `IntegrationTests` — class, line 20.
- `IntegrationTests.test_zero_threshold_axis_extremum_recovers_with_declared_fallback` — def, line 21.
- `IntegrationTests.test_known_optimum_with_relative_position_and_facing_rows` — def, line 34.
- `IntegrationTests.test_real11_200_and_400_with_all_rows_and_contacts` — def, line 44.
- `IntegrationTests.test_inconsistent_rows_return_unknown` — def, line 63.
- `IntegrationTests.test_explicit_fixed_inertia_pattern` — def, line 71.
- `IntegrationTests.test_real11_warm_start_recovers_objective_and_polishes_valid_seed` — def, line 79.
- `IntegrationTests.test_ladder_branch_changes_preserve_optimization_and_explicit_patterns` — def, line 104.
- `IntegrationTests.test_real11_ladders_and_vertical_replay` — def, line 116.
- `IntegrationTests.test_cli_attaches_ladder_capture` — def, line 138.

## [ipopt/tests/test_ladder_model.py](../ipopt/tests/test_ladder_model.py)

- `vertical` — def, line 9.
- `LadderModelTests` — class, line 15.
- `LadderModelTests.test_entry_is_uncapped_and_climb_is_carried_to_next_movement` — def, line 16.
- `LadderModelTests.test_missing_wall_does_not_grant_climb_carry` — def, line 31.
- `LadderModelTests.test_cap_precedes_wall_reach_and_cuts_stored_velocity` — def, line 40.
- `LadderModelTests.test_cap_is_after_inertia_and_acceleration` — def, line 51.
- `LadderModelTests.test_explicit_cap_branches_and_continuous_boundary` — def, line 61.
- `LadderModelTests.test_sneak_floor_jump_and_ceiling_order` — def, line 70.
- `LadderModelTests.test_prescribed_step_and_reference_are_checked` — def, line 86.
- `LadderModelTests.test_resume_preserves_climb_carry_and_previous_collision` — def, line 101.
- `LadderModelTests.test_malformed_ladder_and_vertical_fields_are_rejected` — def, line 116.

## [ipopt/tests/test_model.py](../ipopt/tests/test_model.py)

- `problem` — def, line 8.
- `ModelTests` — class, line 15.
- `ModelTests.test_continuous_carry_and_inertia_are_distinct` — def, line 16.
- `ModelTests.test_contact_resets_only_normal_velocity` — def, line 26.
- `ModelTests.test_subtolerance_missed_contact_never_resets_velocity` — def, line 36.
- `ModelTests.test_contact_from_wrong_side_is_rejected` — def, line 45.
- `ModelTests.test_fixed_pattern_is_checked_against_automatic_threshold` — def, line 53.
- `ModelTests.test_facing_relative_and_cross_axis_rows` — def, line 62.
- `ModelTests.test_sum_of_world_positions_keeps_both_offsets` — def, line 74.
- `ModelTests.test_custom_motion_objective_uses_displacement` — def, line 80.
- `ModelTests.test_nonfinite_and_wrong_size_candidates_are_rejected` — def, line 86.
- `ModelTests.test_malformed_requests_fail_before_starting_worker` — def, line 92.
- `ModelTests.test_prepare_copies_request_and_hash_excludes_execution_settings` — def, line 102.

## [ipopt/tests/test_movement.py](../ipopt/tests/test_movement.py)

- `movement` — def, line 8.
- `MovementTests` — class, line 15.
- `MovementTests.test_boundary_touch_cannot_claim_the_compiled_wall_hit` — def, line 16.
- `MovementTests.test_ground_changes_cannot_silently_keep_old_acceleration` — def, line 30.
- `MovementTests.test_collision_assumptions_must_match_declared_schedule` — def, line 41.
- `MovementTests.test_malformed_movement_metadata_is_rejected` — def, line 48.

## [ipopt/tests/test_solver.py](../ipopt/tests/test_solver.py)

- `ProcessTests` — class, line 15.
- `ProcessTests.run_worker` — def, line 16.
- `ProcessTests.test_timeout_stops_worker_that_never_reads_large_request` — def, line 20.
- `ProcessTests.test_active_cancellation_discards_candidate` — def, line 30.
- `ProcessTests.test_timeout_retains_validated_incumbent` — def, line 42.
- `ProcessTests.test_worker_success_does_not_override_failed_facing_constraint` — def, line 50.
- `ProcessTests.test_malformed_output_and_missing_runtime_are_errors` — def, line 58.
- `ProcessTests.test_first_feasible_stop_and_callback` — def, line 69.
- `ProcessTests.test_valid_seed_survives_timeout_without_worker_candidates` — def, line 81.
- `ProcessTests.test_valid_seed_satisfies_feasibility_without_starting_worker` — def, line 89.
- `ProcessTests.test_valid_seed_survives_runtime_error_and_weaker_worker_candidate` — def, line 96.
- `ProcessTests.test_cli_writes_structured_error_atomically` — def, line 107.

## [ipopt/worker.py](../ipopt/worker.py)

- `send` — def, line 18.
- `forward` — def, line 23.
- `Monitor` — class, line 54.
- `Monitor.__init__` — def, line 55.
- `Monitor.get_n_in` — def, line 65.
- `Monitor.get_n_out` — def, line 68.
- `Monitor.get_name_in` — def, line 71.
- `Monitor.get_name_out` — def, line 74.
- `Monitor.get_sparsity_in` — def, line 77.
- `Monitor.record` — def, line 85.
- `Monitor.eval` — def, line 98.
- `fixed_phase` — def, line 105.
- `phase` — def, line 226.
- `main` — def, line 275.
