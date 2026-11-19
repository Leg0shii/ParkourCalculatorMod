# Production function index

Generated from the current installed-source tree by `python -m tools.docs_index`.
Line numbers are a snapshot; symbol names are the stable lookup keys. Private helpers remain private.

## bridge/compiler.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `coefficient_request` | [6](../src/parkour_solver/bridge/compiler.py#L6) | function |
| `JavaCompiler` | [17](../src/parkour_solver/bridge/compiler.py#L17) | class |
| `JavaCompiler.__init__` | [18](../src/parkour_solver/bridge/compiler.py#L18) | function |
| `JavaCompiler._start` | [26](../src/parkour_solver/bridge/compiler.py#L26) | function |
| `JavaCompiler._errors` | [33](../src/parkour_solver/bridge/compiler.py#L33) | function |
| `JavaCompiler.compile` | [41](../src/parkour_solver/bridge/compiler.py#L41) | function |
| `JavaCompiler.derive_footprints` | [44](../src/parkour_solver/bridge/compiler.py#L44) | function |
| `JavaCompiler.derive_standing_footprints` | [47](../src/parkour_solver/bridge/compiler.py#L47) | function |
| `JavaCompiler.check_scene` | [50](../src/parkour_solver/bridge/compiler.py#L50) | function |
| `JavaCompiler._exchange` | [56](../src/parkour_solver/bridge/compiler.py#L56) | function |
| `JavaCompiler.close` | [85](../src/parkour_solver/bridge/compiler.py#L85) | function |
| `JavaCompiler.__enter__` | [97](../src/parkour_solver/bridge/compiler.py#L97) | function |
| `JavaCompiler.__exit__` | [100](../src/parkour_solver/bridge/compiler.py#L100) | function |

## bridge/counting.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `CountingCompiler` | [6](../src/parkour_solver/bridge/counting.py#L6) | class |
| `CountingCompiler.__init__` | [7](../src/parkour_solver/bridge/counting.py#L7) | function |
| `CountingCompiler.__getattr__` | [12](../src/parkour_solver/bridge/counting.py#L12) | function |
| `CountingCompiler.summary` | [28](../src/parkour_solver/bridge/counting.py#L28) | function |

## cli/blocks.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `main` | [13](../src/parkour_solver/cli/blocks.py#L13) | function |

## cli/fixed_clock.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `run` | [26](../src/parkour_solver/cli/fixed_clock.py#L26) | function |
| `main` | [84](../src/parkour_solver/cli/fixed_clock.py#L84) | function |

## cli/numerical.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `read_json` | [18](../src/parkour_solver/cli/numerical.py#L18) | function |
| `write_json` | [25](../src/parkour_solver/cli/numerical.py#L25) | function |
| `main` | [40](../src/parkour_solver/cli/numerical.py#L40) | function |

## cli/ordered_route.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `main` | [13](../src/parkour_solver/cli/ordered_route.py#L13) | function |

## cli/solve.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `main` | [28](../src/parkour_solver/cli/solve.py#L28) | function |

## constraints/avoidance.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `consecutive` | [9](../src/parkour_solver/constraints/avoidance.py#L9) | function |
| `phase_states` | [13](../src/parkour_solver/constraints/avoidance.py#L13) | function |
| `outside` | [22](../src/parkour_solver/constraints/avoidance.py#L22) | function |
| `passage_kind` | [26](../src/parkour_solver/constraints/avoidance.py#L26) | function |
| `blockage_counts` | [31](../src/parkour_solver/constraints/avoidance.py#L31) | function |
| `block_key` | [36](../src/parkour_solver/constraints/avoidance.py#L36) | function |
| `blockage_usage` | [40](../src/parkour_solver/constraints/avoidance.py#L40) | function |
| `gate_timeline` | [49](../src/parkour_solver/constraints/avoidance.py#L49) | function |
| `intervals` | [81](../src/parkour_solver/constraints/avoidance.py#L81) | function |
| `validate_passages` | [95](../src/parkour_solver/constraints/avoidance.py#L95) | function |
| `face_clearance` | [122](../src/parkour_solver/constraints/avoidance.py#L122) | function |
| `compile_passages` | [135](../src/parkour_solver/constraints/avoidance.py#L135) | function |
| `passage_y_valid` | [158](../src/parkour_solver/constraints/avoidance.py#L158) | function |

## constraints/contacts.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `exposed_tops` | [8](../src/parkour_solver/constraints/contacts.py#L8) | function |
| `floor_checks` | [17](../src/parkour_solver/constraints/contacts.py#L17) | function |
| `contact_conflicts` | [62](../src/parkour_solver/constraints/contacts.py#L62) | function |
| `avoid_contact_rows` | [73](../src/parkour_solver/constraints/contacts.py#L73) | function |

## constraints/encounters.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `canonical_rows` | [20](../src/parkour_solver/constraints/encounters.py#L20) | function |
| `rows_entailed` | [31](../src/parkour_solver/constraints/encounters.py#L31) | function |
| `landing_segments` | [46](../src/parkour_solver/constraints/encounters.py#L46) | function |
| `region` | [57](../src/parkour_solver/constraints/encounters.py#L57) | function |
| `same` | [61](../src/parkour_solver/constraints/encounters.py#L61) | function |
| `satisfied` | [69](../src/parkour_solver/constraints/encounters.py#L69) | function |
| `reaches` | [74](../src/parkour_solver/constraints/encounters.py#L74) | function |
| `side_dict` | [79](../src/parkour_solver/constraints/encounters.py#L79) | function |
| `vertical_side` | [89](../src/parkour_solver/constraints/encounters.py#L89) | function |
| `support_region` | [96](../src/parkour_solver/constraints/encounters.py#L96) | function |
| `prepare_encounter` | [111](../src/parkour_solver/constraints/encounters.py#L111) | function |
| `json_key` | [210](../src/parkour_solver/constraints/encounters.py#L210) | function |
| `_continuation` | [214](../src/parkour_solver/constraints/encounters.py#L214) | function |
| `describe` | [233](../src/parkour_solver/constraints/encounters.py#L233) | function |
| `encounter_option` | [266](../src/parkour_solver/constraints/encounters.py#L266) | function |

## constraints/hit_events.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `validate_hits` | [9](../src/parkour_solver/constraints/hit_events.py#L9) | function |
| `compile_hits` | [64](../src/parkour_solver/constraints/hit_events.py#L64) | function |
| `wall_hit_options` | [109](../src/parkour_solver/constraints/hit_events.py#L109) | function |
| `float_height` | [144](../src/parkour_solver/constraints/hit_events.py#L144) | function |
| `hit_seed` | [149](../src/parkour_solver/constraints/hit_events.py#L149) | function |

## constraints/route_guards.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `physical_events` | [15](../src/parkour_solver/constraints/route_guards.py#L15) | function |
| `canonical_rows` | [26](../src/parkour_solver/constraints/route_guards.py#L26) | function |
| `RouteGuards` | [36](../src/parkour_solver/constraints/route_guards.py#L36) | class |
| `RouteGuards.__init__` | [37](../src/parkour_solver/constraints/route_guards.py#L37) | function |
| `RouteGuards.rows` | [49](../src/parkour_solver/constraints/route_guards.py#L49) | function |
| `RouteGuards.phase_options` | [66](../src/parkour_solver/constraints/route_guards.py#L66) | function |
| `RouteGuards.option` | [165](../src/parkour_solver/constraints/route_guards.py#L165) | function |

## constraints/variants.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `ConstraintVariants` | [19](../src/parkour_solver/constraints/variants.py#L19) | class |
| `ConstraintVariants.for_plan` | [21](../src/parkour_solver/constraints/variants.py#L21) | function |
| `ConstraintVariants.__init__` | [48](../src/parkour_solver/constraints/variants.py#L48) | function |
| `ConstraintVariants.metric` | [100](../src/parkour_solver/constraints/variants.py#L100) | function |
| `ConstraintVariants.coordinate` | [104](../src/parkour_solver/constraints/variants.py#L104) | function |
| `ConstraintVariants.check` | [109](../src/parkour_solver/constraints/variants.py#L109) | function |
| `ConstraintVariants.face_row` | [117](../src/parkour_solver/constraints/variants.py#L117) | function |
| `ConstraintVariants.effective_key` | [122](../src/parkour_solver/constraints/variants.py#L122) | function |
| `ConstraintVariants.analyze` | [134](../src/parkour_solver/constraints/variants.py#L134) | function |
| `ConstraintVariants._screen` | [152](../src/parkour_solver/constraints/variants.py#L152) | function |
| `ConstraintVariants.excluded` | [155](../src/parkour_solver/constraints/variants.py#L155) | function |
| `ConstraintVariants.prepare_item` | [162](../src/parkour_solver/constraints/variants.py#L162) | function |
| `ConstraintVariants.option` | [184](../src/parkour_solver/constraints/variants.py#L184) | function |
| `ConstraintVariants.entailed` | [196](../src/parkour_solver/constraints/variants.py#L196) | function |
| `ConstraintVariants.__iter__` | [199](../src/parkour_solver/constraints/variants.py#L199) | function |
| `ConstraintVariants.__next__` | [202](../src/parkour_solver/constraints/variants.py#L202) | function |
| `ConstraintVariants.choose_item` | [210](../src/parkour_solver/constraints/variants.py#L210) | function |
| `ConstraintVariants._next_variant` | [238](../src/parkour_solver/constraints/variants.py#L238) | function |

## io/capture.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `cell` | [15](../src/parkour_solver/io/capture.py#L15) | function |
| `attach_capture` | [19](../src/parkour_solver/io/capture.py#L19) | function |

## io/checkpoint_store.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `StoredValue` | [21](../src/parkour_solver/io/checkpoint_store.py#L21) | class |
| `StoredValue.__init__` | [22](../src/parkour_solver/io/checkpoint_store.py#L22) | function |
| `_json` | [26](../src/parkour_solver/io/checkpoint_store.py#L26) | function |
| `_atomic_json` | [30](../src/parkour_solver/io/checkpoint_store.py#L30) | function |
| `CheckpointStore` | [37](../src/parkour_solver/io/checkpoint_store.py#L37) | class |
| `CheckpointStore.__init__` | [44](../src/parkour_solver/io/checkpoint_store.py#L44) | function |
| `CheckpointStore.__enter__` | [62](../src/parkour_solver/io/checkpoint_store.py#L62) | function |
| `CheckpointStore.__exit__` | [65](../src/parkour_solver/io/checkpoint_store.py#L65) | function |
| `CheckpointStore.close` | [68](../src/parkour_solver/io/checkpoint_store.py#L68) | function |
| `CheckpointStore.save` | [71](../src/parkour_solver/io/checkpoint_store.py#L71) | function |
| `CheckpointStore.archive` | [76](../src/parkour_solver/io/checkpoint_store.py#L76) | function |
| `CheckpointStore.publish` | [138](../src/parkour_solver/io/checkpoint_store.py#L138) | function |
| `CheckpointStore.patch` | [145](../src/parkour_solver/io/checkpoint_store.py#L145) | function |
| `CheckpointStore.compact` | [159](../src/parkour_solver/io/checkpoint_store.py#L159) | function |
| `read_report` | [194](../src/parkour_solver/io/checkpoint_store.py#L194) | function |
| `main` | [250](../src/parkour_solver/io/checkpoint_store.py#L250) | function |

## io/linkcraft.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `extract_section` | [11](../src/parkour_solver/io/linkcraft.py#L11) | function |
| `main` | [175](../src/parkour_solver/io/linkcraft.py#L175) | function |

## io/provenance.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `source_hashes` | [6](../src/parkour_solver/io/provenance.py#L6) | function |
| `source_revision` | [12](../src/parkour_solver/io/provenance.py#L12) | function |

## io/reports.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `save` | [6](../src/parkour_solver/io/reports.py#L6) | function |
| `brief` | [15](../src/parkour_solver/io/reports.py#L15) | function |

## io/search_checkpoint.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `identity_paths` | [8](../src/parkour_solver/io/search_checkpoint.py#L8) | function |
| `SearchCheckpoint` | [24](../src/parkour_solver/io/search_checkpoint.py#L24) | class |
| `SearchCheckpoint.__init__` | [25](../src/parkour_solver/io/search_checkpoint.py#L25) | function |
| `SearchCheckpoint.state` | [31](../src/parkour_solver/io/search_checkpoint.py#L31) | function |
| `SearchCheckpoint.__call__` | [41](../src/parkour_solver/io/search_checkpoint.py#L41) | function |
| `SearchCheckpoint.begin_repair` | [52](../src/parkour_solver/io/search_checkpoint.py#L52) | function |
| `SearchCheckpoint.repair_progress` | [55](../src/parkour_solver/io/search_checkpoint.py#L55) | function |
| `SearchCheckpoint.finish` | [67](../src/parkour_solver/io/search_checkpoint.py#L67) | function |

## movement/cases.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `region_rows` | [16](../src/parkour_solver/movement/cases.py#L16) | function |
| `outside_row` | [22](../src/parkour_solver/movement/cases.py#L22) | function |
| `tangent_rows` | [28](../src/parkour_solver/movement/cases.py#L28) | function |
| `_faces` | [34](../src/parkour_solver/movement/cases.py#L34) | function |
| `_height` | [39](../src/parkour_solver/movement/cases.py#L39) | function |
| `exposed_side` | [45](../src/parkour_solver/movement/cases.py#L45) | function |
| `convex_corner` | [56](../src/parkour_solver/movement/cases.py#L56) | function |
| `top_choices` | [67](../src/parkour_solver/movement/cases.py#L67) | function |
| `corner_rows` | [86](../src/parkour_solver/movement/cases.py#L86) | function |
| `case_profile` | [94](../src/parkour_solver/movement/cases.py#L94) | function |
| `landing_wall_options` | [126](../src/parkour_solver/movement/cases.py#L126) | function |
| `entry_possible` | [143](../src/parkour_solver/movement/cases.py#L143) | function |

## movement/ceiling_generation.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `grind_options` | [11](../src/parkour_solver/movement/ceiling_generation.py#L11) | function |
| `ceiling_profiles` | [39](../src/parkour_solver/movement/ceiling_generation.py#L39) | function |
| `CeilingGenerator` | [56](../src/parkour_solver/movement/ceiling_generation.py#L56) | class |
| `CeilingGenerator.generate` | [57](../src/parkour_solver/movement/ceiling_generation.py#L57) | function |
| `open_ceiling_choices` | [85](../src/parkour_solver/movement/ceiling_generation.py#L85) | function |

## movement/ceiling_profiles.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `ceiling_options` | [8](../src/parkour_solver/movement/ceiling_profiles.py#L8) | function |
| `ceiling_contact` | [17](../src/parkour_solver/movement/ceiling_profiles.py#L17) | function |
| `ceiling_opportunities` | [22](../src/parkour_solver/movement/ceiling_profiles.py#L22) | function |
| `ceiling_sequence` | [29](../src/parkour_solver/movement/ceiling_profiles.py#L29) | function |
| `ceiling_profile` | [52](../src/parkour_solver/movement/ceiling_profiles.py#L52) | function |

## movement/contact_combinations.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `merge_contacts` | [11](../src/parkour_solver/movement/contact_combinations.py#L11) | function |
| `validate_contact_events` | [29](../src/parkour_solver/movement/contact_combinations.py#L29) | function |
| `contact_clearance` | [86](../src/parkour_solver/movement/contact_combinations.py#L86) | function |
| `expanded_face` | [90](../src/parkour_solver/movement/contact_combinations.py#L90) | function |
| `scene_contact_options` | [97](../src/parkour_solver/movement/contact_combinations.py#L97) | function |
| `contact_event` | [105](../src/parkour_solver/movement/contact_combinations.py#L105) | function |
| `region_choices` | [111](../src/parkour_solver/movement/contact_combinations.py#L111) | function |
| `reachable_option` | [121](../src/parkour_solver/movement/contact_combinations.py#L121) | function |
| `bounding_region` | [130](../src/parkour_solver/movement/contact_combinations.py#L130) | function |
| `wall_heights` | [134](../src/parkour_solver/movement/contact_combinations.py#L134) | function |
| `region_offset` | [139](../src/parkour_solver/movement/contact_combinations.py#L139) | function |
| `finite_rows` | [144](../src/parkour_solver/movement/contact_combinations.py#L144) | function |
| `compile_contact_events` | [150](../src/parkour_solver/movement/contact_combinations.py#L150) | function |
| `contact_combinations` | [181](../src/parkour_solver/movement/contact_combinations.py#L181) | function |

## movement/contact_profiles.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `contact_profile` | [8](../src/parkour_solver/movement/contact_profiles.py#L8) | function |
| `contact_opportunities` | [51](../src/parkour_solver/movement/contact_profiles.py#L51) | function |
| `generate_contact_profiles` | [66](../src/parkour_solver/movement/contact_profiles.py#L66) | function |

## movement/contacts.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `contact_signature` | [14](../src/parkour_solver/movement/contacts.py#L14) | function |
| `interleave_families` | [20](../src/parkour_solver/movement/contacts.py#L20) | function |
| `contact_variants` | [36](../src/parkour_solver/movement/contacts.py#L36) | function |
| `mixed_schedule_pool` | [118](../src/parkour_solver/movement/contacts.py#L118) | function |
| `program_seed` | [167](../src/parkour_solver/movement/contacts.py#L167) | function |

## movement/generation.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `register_movement_type` | [10](../src/parkour_solver/movement/generation.py#L10) | function |
| `generate_choices` | [17](../src/parkour_solver/movement/generation.py#L17) | function |
| `propose_choices` | [31](../src/parkour_solver/movement/generation.py#L31) | function |
| `assess_proposal` | [56](../src/parkour_solver/movement/generation.py#L56) | function |
| `_unit` | [67](../src/parkour_solver/movement/generation.py#L67) | function |
| `NormalGenerator` | [75](../src/parkour_solver/movement/generation.py#L75) | class |
| `NormalGenerator.sneak_inputs` | [76](../src/parkour_solver/movement/generation.py#L76) | function |
| `NormalGenerator.generate` | [79](../src/parkour_solver/movement/generation.py#L79) | function |
| `LadderFallGenerator` | [103](../src/parkour_solver/movement/generation.py#L103) | class |
| `LadderFallGenerator.sneak_inputs` | [104](../src/parkour_solver/movement/generation.py#L104) | function |
| `LadderFallGenerator.generate` | [107](../src/parkour_solver/movement/generation.py#L107) | function |
| `LadderClimbGenerator` | [121](../src/parkour_solver/movement/generation.py#L121) | class |
| `LadderClimbGenerator.sneak_inputs` | [122](../src/parkour_solver/movement/generation.py#L122) | function |
| `LadderClimbGenerator._choice` | [125](../src/parkour_solver/movement/generation.py#L125) | function |
| `LadderClimbGenerator.generate` | [147](../src/parkour_solver/movement/generation.py#L147) | function |
| `generate_unit_programs` | [177](../src/parkour_solver/movement/generation.py#L177) | function |
| `materialize_generated_units` | [202](../src/parkour_solver/movement/generation.py#L202) | function |
| `sneak_domain` | [214](../src/parkour_solver/movement/generation.py#L214) | function |

## movement/ladder/ceiling.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `_unit_at` | [7](../src/parkour_solver/movement/ladder/ceiling.py#L7) | function |
| `own_ceiling_contacts` | [11](../src/parkour_solver/movement/ladder/ceiling.py#L11) | function |
| `own_schedule_ceilings` | [48](../src/parkour_solver/movement/ladder/ceiling.py#L48) | function |
| `predict_ceiling_sequences` | [62](../src/parkour_solver/movement/ladder/ceiling.py#L62) | function |

## movement/ladder/contacts.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `climb_contacts` | [11](../src/parkour_solver/movement/ladder/contacts.py#L11) | function |
| `with_contacts` | [27](../src/parkour_solver/movement/ladder/contacts.py#L27) | function |
| `mixed_ladder_schedules` | [69](../src/parkour_solver/movement/ladder/contacts.py#L69) | function |

## movement/ladder/episodes.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `compose_ladder_episodes` | [11](../src/parkour_solver/movement/ladder/episodes.py#L11) | function |

## movement/ladder/events.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `validate_ladder_events` | [5](../src/parkour_solver/movement/ladder/events.py#L5) | function |
| `compile_ladder_events` | [56](../src/parkour_solver/movement/ladder/events.py#L56) | function |

## movement/ladder/generation.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `ladder_choices` | [9](../src/parkour_solver/movement/ladder/generation.py#L9) | function |
| `ladder_event_choice` | [86](../src/parkour_solver/movement/ladder/generation.py#L86) | function |

## movement/ladder/schedules.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `climb_variants` | [13](../src/parkour_solver/movement/ladder/schedules.py#L13) | function |
| `sneak_patterns` | [28](../src/parkour_solver/movement/ladder/schedules.py#L28) | function |
| `prepare_ladder_request` | [64](../src/parkour_solver/movement/ladder/schedules.py#L64) | function |
| `_round_robin` | [142](../src/parkour_solver/movement/ladder/schedules.py#L142) | function |
| `_context_skeletons` | [154](../src/parkour_solver/movement/ladder/schedules.py#L154) | function |
| `_mask_work` | [166](../src/parkour_solver/movement/ladder/schedules.py#L166) | function |
| `generate_ladder_schedules` | [236](../src/parkour_solver/movement/ladder/schedules.py#L236) | function |

## movement/ladder/settings.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `ladder_settings` | [6](../src/parkour_solver/movement/ladder/settings.py#L6) | function |

## movement/ladder/transfers.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `episode_from_event` | [13](../src/parkour_solver/movement/ladder/transfers.py#L13) | function |
| `open_program` | [29](../src/parkour_solver/movement/ladder/transfers.py#L29) | function |
| `recompose` | [38](../src/parkour_solver/movement/ladder/transfers.py#L38) | function |
| `append_ladder_programs` | [47](../src/parkour_solver/movement/ladder/transfers.py#L47) | function |
| `finish_programs` | [134](../src/parkour_solver/movement/ladder/transfers.py#L134) | function |
| `iter_chain_programs` | [159](../src/parkour_solver/movement/ladder/transfers.py#L159) | function |
| `chain_programs` | [222](../src/parkour_solver/movement/ladder/transfers.py#L222) | function |
| `transfer_programs` | [229](../src/parkour_solver/movement/ladder/transfers.py#L229) | function |

## movement/normal.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `replay` | [8](../src/parkour_solver/movement/normal.py#L8) | function |
| `approaches` | [18](../src/parkour_solver/movement/normal.py#L18) | function |
| `first_descent` | [27](../src/parkour_solver/movement/normal.py#L27) | function |
| `event_choice` | [34](../src/parkour_solver/movement/normal.py#L34) | function |
| `named_choice` | [46](../src/parkour_solver/movement/normal.py#L46) | function |
| `feet_allowed` | [57](../src/parkour_solver/movement/normal.py#L57) | function |
| `normal_choices` | [62](../src/parkour_solver/movement/normal.py#L62) | function |

## movement/profiles.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `normal_jump_height` | [10](../src/parkour_solver/movement/profiles.py#L10) | function |
| `ordinary_jump_profile` | [14](../src/parkour_solver/movement/profiles.py#L14) | function |
| `runoff_profile` | [18](../src/parkour_solver/movement/profiles.py#L18) | function |
| `_flight_profile` | [27](../src/parkour_solver/movement/profiles.py#L27) | function |
| `stair_jump_profile` | [56](../src/parkour_solver/movement/profiles.py#L56) | function |
| `compile_open_air_route` | [78](../src/parkour_solver/movement/profiles.py#L78) | function |

## movement/program.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `scalar_initial` | [12](../src/parkour_solver/movement/program.py#L12) | function |
| `empty_program` | [22](../src/parkour_solver/movement/program.py#L22) | function |
| `boundary_state` | [37](../src/parkour_solver/movement/program.py#L37) | function |
| `profile_from_trace` | [47](../src/parkour_solver/movement/program.py#L47) | function |
| `movement_choice` | [64](../src/parkour_solver/movement/program.py#L64) | function |
| `_contacts` | [81](../src/parkour_solver/movement/program.py#L81) | function |
| `_shift_event` | [86](../src/parkour_solver/movement/program.py#L86) | function |
| `_events` | [103](../src/parkour_solver/movement/program.py#L103) | function |
| `append_choice` | [129](../src/parkour_solver/movement/program.py#L129) | function |
| `truncate_program` | [215](../src/parkour_solver/movement/program.py#L215) | function |
| `program_physics_key` | [264](../src/parkour_solver/movement/program.py#L264) | function |
| `program_search_key` | [273](../src/parkour_solver/movement/program.py#L273) | function |
| `compile_program` | [281](../src/parkour_solver/movement/program.py#L281) | function |

## movement/settings.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `movement_settings` | [6](../src/parkour_solver/movement/settings.py#L6) | function |

## movement/stepping.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `box_for` | [10](../src/parkour_solver/movement/stepping.py#L10) | function |
| `stepping_profiles` | [14](../src/parkour_solver/movement/stepping.py#L14) | function |
| `_flight_cases` | [73](../src/parkour_solver/movement/stepping.py#L73) | function |
| `SteppingGenerator` | [105](../src/parkour_solver/movement/stepping.py#L105) | class |
| `SteppingGenerator.generate` | [106](../src/parkour_solver/movement/stepping.py#L106) | function |
| `_sequence_allowed` | [138](../src/parkour_solver/movement/stepping.py#L138) | function |
| `open_stepping_choices` | [149](../src/parkour_solver/movement/stepping.py#L149) | function |
| `_open_flight_cases` | [211](../src/parkour_solver/movement/stepping.py#L211) | function |

## movement/units.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `movement_unit` | [10](../src/parkour_solver/movement/units.py#L10) | function |
| `ladder_units` | [18](../src/parkour_solver/movement/units.py#L18) | function |
| `movement_program` | [52](../src/parkour_solver/movement/units.py#L52) | function |
| `compile_units` | [103](../src/parkour_solver/movement/units.py#L103) | function |
| `shifted_unit` | [132](../src/parkour_solver/movement/units.py#L132) | function |
| `profile_program` | [146](../src/parkour_solver/movement/units.py#L146) | function |
| `overlay_program` | [171](../src/parkour_solver/movement/units.py#L171) | function |
| `exit_matches` | [202](../src/parkour_solver/movement/units.py#L202) | function |

## movement/vertical_profiles.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `_flags` | [8](../src/parkour_solver/movement/vertical_profiles.py#L8) | function |
| `trace_ladder_schedule` | [16](../src/parkour_solver/movement/vertical_profiles.py#L16) | function |

## numerics/builders.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `waypoint_seed` | [6](../src/parkour_solver/numerics/builders.py#L6) | function |
| `_raw_candidate` | [23](../src/parkour_solver/numerics/builders.py#L23) | function |
| `_vertical` | [34](../src/parkour_solver/numerics/builders.py#L34) | function |
| `ladder_waypoints` | [46](../src/parkour_solver/numerics/builders.py#L46) | function |

## numerics/constraints.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `reduce_walls` | [4](../src/parkour_solver/numerics/constraints.py#L4) | function |

## numerics/continuation.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `Incumbent` | [9](../src/parkour_solver/numerics/continuation.py#L9) | class |
| `Incumbent.__init__` | [10](../src/parkour_solver/numerics/continuation.py#L10) | function |
| `Incumbent.consider` | [15](../src/parkour_solver/numerics/continuation.py#L15) | function |
| `Incumbent.pattern` | [28](../src/parkour_solver/numerics/continuation.py#L28) | function |
| `run` | [34](../src/parkour_solver/numerics/continuation.py#L34) | function |

## numerics/model.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `prepare` | [15](../src/parkour_solver/numerics/model.py#L15) | function |
| `fingerprint` | [195](../src/parkour_solver/numerics/model.py#L195) | function |

## numerics/solver.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `_command` | [18](../src/parkour_solver/numerics/solver.py#L18) | function |
| `solve` | [22](../src/parkour_solver/numerics/solver.py#L22) | function |

## numerics/worker.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `send` | [20](../src/parkour_solver/numerics/worker.py#L20) | function |
| `forward` | [25](../src/parkour_solver/numerics/worker.py#L25) | function |
| `Monitor` | [56](../src/parkour_solver/numerics/worker.py#L56) | class |
| `Monitor.__init__` | [57](../src/parkour_solver/numerics/worker.py#L57) | function |
| `Monitor.get_n_in` | [68](../src/parkour_solver/numerics/worker.py#L68) | function |
| `Monitor.get_n_out` | [71](../src/parkour_solver/numerics/worker.py#L71) | function |
| `Monitor.get_name_in` | [74](../src/parkour_solver/numerics/worker.py#L74) | function |
| `Monitor.get_name_out` | [77](../src/parkour_solver/numerics/worker.py#L77) | function |
| `Monitor.get_sparsity_in` | [80](../src/parkour_solver/numerics/worker.py#L80) | function |
| `Monitor.record` | [88](../src/parkour_solver/numerics/worker.py#L88) | function |
| `Monitor.eval` | [105](../src/parkour_solver/numerics/worker.py#L105) | function |
| `fixed_phase` | [112](../src/parkour_solver/numerics/worker.py#L112) | function |
| `phase` | [241](../src/parkour_solver/numerics/worker.py#L241) | function |
| `main` | [290](../src/parkour_solver/numerics/worker.py#L290) | function |

## observability/turns.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `record` | [10](../src/parkour_solver/observability/turns.py#L10) | function |
| `replay` | [32](../src/parkour_solver/observability/turns.py#L32) | function |
| `summary` | [42](../src/parkour_solver/observability/turns.py#L42) | function |

## physics/angles.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `wrap` | [5](../src/parkour_solver/physics/angles.py#L5) | function |

## physics/vertical.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `f32` | [5](../src/parkour_solver/physics/vertical.py#L5) | function |
| `clamp` | [16](../src/parkour_solver/physics/vertical.py#L16) | function |
| `branch` | [20](../src/parkour_solver/physics/vertical.py#L20) | function |
| `vertical_move` | [24](../src/parkour_solver/physics/vertical.py#L24) | function |
| `vertical_carry` | [36](../src/parkour_solver/physics/vertical.py#L36) | function |
| `vertical_step` | [42](../src/parkour_solver/physics/vertical.py#L42) | function |

## reachability/intervals.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `travel_radii` | [6](../src/parkour_solver/reachability/intervals.py#L6) | function |
| `motion_bound` | [15](../src/parkour_solver/reachability/intervals.py#L15) | function |
| `reachable_envelopes` | [23](../src/parkour_solver/reachability/intervals.py#L23) | function |
| `interaction_reachability` | [132](../src/parkour_solver/reachability/intervals.py#L132) | function |

## reachability/linear.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `linear_exclusion` | [11](../src/parkour_solver/reachability/linear.py#L11) | function |
| `_cost` | [48](../src/parkour_solver/reachability/linear.py#L48) | function |
| `_check` | [67](../src/parkour_solver/reachability/linear.py#L67) | function |

## reachability/screen.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `condition_envelopes` | [6](../src/parkour_solver/reachability/screen.py#L6) | function |
| `contradiction` | [41](../src/parkour_solver/reachability/screen.py#L41) | function |

## reachability/service.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `interaction_context_key` | [10](../src/parkour_solver/reachability/service.py#L10) | function |
| `query_interaction` | [25](../src/parkour_solver/reachability/service.py#L25) | function |
| `normalize_alternatives` | [67](../src/parkour_solver/reachability/service.py#L67) | function |
| `block_interaction` | [84](../src/parkour_solver/reachability/service.py#L84) | function |
| `interaction_allowed` | [89](../src/parkour_solver/reachability/service.py#L89) | function |
| `bounds_allowed` | [93](../src/parkour_solver/reachability/service.py#L93) | function |
| `InteractionGate` | [101](../src/parkour_solver/reachability/service.py#L101) | class |
| `InteractionGate.__init__` | [103](../src/parkour_solver/reachability/service.py#L103) | function |
| `InteractionGate._cached` | [112](../src/parkour_solver/reachability/service.py#L112) | function |
| `InteractionGate.prepare` | [125](../src/parkour_solver/reachability/service.py#L125) | function |
| `InteractionGate.analyze_model` | [142](../src/parkour_solver/reachability/service.py#L142) | function |
| `InteractionGate.analyze` | [160](../src/parkour_solver/reachability/service.py#L160) | function |
| `InteractionGate.condition` | [166](../src/parkour_solver/reachability/service.py#L166) | function |
| `InteractionGate.envelopes` | [174](../src/parkour_solver/reachability/service.py#L174) | function |
| `InteractionGate.sequence` | [178](../src/parkour_solver/reachability/service.py#L178) | function |
| `InteractionGate.check_program` | [191](../src/parkour_solver/reachability/service.py#L191) | function |
| `InteractionGate.query` | [216](../src/parkour_solver/reachability/service.py#L216) | function |
| `InteractionGate.required` | [257](../src/parkour_solver/reachability/service.py#L257) | function |
| `InteractionGate.context` | [278](../src/parkour_solver/reachability/service.py#L278) | function |
| `InteractionGate.summary` | [281](../src/parkour_solver/reachability/service.py#L281) | function |

## reachability/viability.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `prefix_viability` | [8](../src/parkour_solver/reachability/viability.py#L8) | function |

## scene/geometry.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `thin` | [14](../src/parkour_solver/scene/geometry.py#L14) | function |
| `box_bounds` | [19](../src/parkour_solver/scene/geometry.py#L19) | function |
| `normalize_geometry` | [29](../src/parkour_solver/scene/geometry.py#L29) | function |
| `expanded_box` | [96](../src/parkour_solver/scene/geometry.py#L96) | function |

## scene/ladders.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `ladder_columns` | [5](../src/parkour_solver/scene/ladders.py#L5) | function |
| `_rectangular_regions` | [28](../src/parkour_solver/scene/ladders.py#L28) | function |
| `ladder_regions` | [52](../src/parkour_solver/scene/ladders.py#L52) | function |
| `region_rectangles` | [78](../src/parkour_solver/scene/ladders.py#L78) | function |
| `region_contains` | [83](../src/parkour_solver/scene/ladders.py#L83) | function |
| `region_axis` | [88](../src/parkour_solver/scene/ladders.py#L88) | function |
| `climb_walls` | [93](../src/parkour_solver/scene/ladders.py#L93) | function |
| `movement_contexts` | [113](../src/parkour_solver/scene/ladders.py#L113) | function |
| `entry_channels` | [121](../src/parkour_solver/scene/ladders.py#L121) | function |
| `outside_row` | [136](../src/parkour_solver/scene/ladders.py#L136) | function |
| `schedule_constraints` | [141](../src/parkour_solver/scene/ladders.py#L141) | function |
| `exit_faces` | [197](../src/parkour_solver/scene/ladders.py#L197) | function |

## scene/normalize.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `prepare_scene` | [9](../src/parkour_solver/scene/normalize.py#L9) | function |

## scene/objectives.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `objective_spec` | [4](../src/parkour_solver/scene/objectives.py#L4) | function |

## scene/route.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `prepare_route` | [12](../src/parkour_solver/scene/route.py#L12) | function |
| `supports` | [149](../src/parkour_solver/scene/route.py#L149) | function |
| `center_bounds` | [160](../src/parkour_solver/scene/route.py#L160) | function |
| `row_tick` | [174](../src/parkour_solver/scene/route.py#L174) | function |
| `schedule` | [178](../src/parkour_solver/scene/route.py#L178) | function |
| `initial_guess` | [340](../src/parkour_solver/scene/route.py#L340) | function |

## schema.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `number` | [7](../src/parkour_solver/schema.py#L7) | function |
| `integer` | [13](../src/parkour_solver/schema.py#L13) | function |

## search/anchors/adapter.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `AnchorAdapter` | [11](../src/parkour_solver/search/anchors/adapter.py#L11) | class |
| `AnchorAdapter.__init__` | [12](../src/parkour_solver/search/anchors/adapter.py#L12) | function |
| `AnchorAdapter.initial_cursor` | [18](../src/parkour_solver/search/anchors/adapter.py#L18) | function |
| `AnchorAdapter.next_goals` | [27](../src/parkour_solver/search/anchors/adapter.py#L27) | function |
| `AnchorAdapter.for_goal` | [51](../src/parkour_solver/search/anchors/adapter.py#L51) | function |
| `AnchorAdapter.for_node` | [65](../src/parkour_solver/search/anchors/adapter.py#L65) | function |
| `AnchorAdapter.advance` | [69](../src/parkour_solver/search/anchors/adapter.py#L69) | function |
| `AnchorAdapter.context` | [75](../src/parkour_solver/search/anchors/adapter.py#L75) | function |
| `AnchorAdapter.program_allowed` | [84](../src/parkour_solver/search/anchors/adapter.py#L84) | function |
| `AnchorAdapter.candidate` | [88](../src/parkour_solver/search/anchors/adapter.py#L88) | function |
| `AnchorAdapter.summary` | [98](../src/parkour_solver/search/anchors/adapter.py#L98) | function |

## search/anchors/policy.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `settings` | [12](../src/parkour_solver/search/anchors/policy.py#L12) | function |
| `semantic_settings` | [44](../src/parkour_solver/search/anchors/policy.py#L44) | function |
| `owner` | [49](../src/parkour_solver/search/anchors/policy.py#L49) | function |
| `visits` | [54](../src/parkour_solver/search/anchors/policy.py#L54) | function |
| `feet_touches` | [85](../src/parkour_solver/search/anchors/policy.py#L85) | function |
| `visit_state` | [97](../src/parkour_solver/search/anchors/policy.py#L97) | function |
| `audit` | [120](../src/parkour_solver/search/anchors/policy.py#L120) | function |
| `local_counts` | [138](../src/parkour_solver/search/anchors/policy.py#L138) | function |
| `priority` | [143](../src/parkour_solver/search/anchors/policy.py#L143) | function |
| `within_caps` | [147](../src/parkour_solver/search/anchors/policy.py#L147) | function |
| `transfer_structure` | [161](../src/parkour_solver/search/anchors/policy.py#L161) | function |
| `single_jump_structure` | [200](../src/parkour_solver/search/anchors/policy.py#L200) | function |

## search/application.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `run` | [8](../src/parkour_solver/search/application.py#L8) | function |

## search/bounds/flight.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `state_covers` | [21](../src/parkour_solver/search/bounds/flight.py#L21) | function |
| `transfer_bound` | [32](../src/parkour_solver/search/bounds/flight.py#L32) | function |
| `transfer_bounds` | [87](../src/parkour_solver/search/bounds/flight.py#L87) | function |

## search/bounds/remaining.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `cover_distance` | [9](../src/parkour_solver/search/bounds/remaining.py#L9) | function |
| `_vertical_remaining` | [14](../src/parkour_solver/search/bounds/remaining.py#L14) | function |
| `remaining_bound` | [46](../src/parkour_solver/search/bounds/remaining.py#L46) | function |

## search/climbable.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `selected_edges` | [19](../src/parkour_solver/search/climbable.py#L19) | function |
| `ordinary_paths` | [23](../src/parkour_solver/search/climbable.py#L23) | function |
| `run_allocations` | [40](../src/parkour_solver/search/climbable.py#L40) | function |
| `_prefix` | [58](../src/parkour_solver/search/climbable.py#L58) | function |
| `entry_context` | [82](../src/parkour_solver/search/climbable.py#L82) | function |
| `probe_entries` | [87](../src/parkour_solver/search/climbable.py#L87) | function |
| `post_exit_reachable` | [97](../src/parkour_solver/search/climbable.py#L97) | function |
| `first_hit_bounds` | [108](../src/parkour_solver/search/climbable.py#L108) | function |
| `find_climbable_blocks` | [144](../src/parkour_solver/search/climbable.py#L144) | function |

## search/comparison.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `comparison_label` | [11](../src/parkour_solver/search/comparison.py#L11) | function |
| `priority_key` | [38](../src/parkour_solver/search/comparison.py#L38) | function |
| `_count_protections` | [44](../src/parkour_solver/search/comparison.py#L44) | function |
| `measurements` | [53](../src/parkour_solver/search/comparison.py#L53) | function |
| `_clock_protections` | [61](../src/parkour_solver/search/comparison.py#L61) | function |
| `_clock_decision` | [90](../src/parkour_solver/search/comparison.py#L90) | function |
| `compare_candidates` | [107](../src/parkour_solver/search/comparison.py#L107) | function |

## search/compat/corridor_queue.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `collision_request` | [20](../src/parkour_solver/search/compat/corridor_queue.py#L20) | function |
| `search_routes` | [41](../src/parkour_solver/search/compat/corridor_queue.py#L41) | function |

## search/compat/decision_windows.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `plan_windows` | [9](../src/parkour_solver/search/compat/decision_windows.py#L9) | function |
| `decision_events` | [72](../src/parkour_solver/search/compat/decision_windows.py#L72) | function |
| `DecisionPolicy` | [92](../src/parkour_solver/search/compat/decision_windows.py#L92) | class |
| `DecisionPolicy.__init__` | [93](../src/parkour_solver/search/compat/decision_windows.py#L93) | function |
| `DecisionPolicy.audit` | [98](../src/parkour_solver/search/compat/decision_windows.py#L98) | function |
| `DecisionPolicy.__call__` | [105](../src/parkour_solver/search/compat/decision_windows.py#L105) | function |
| `retain_candidates` | [109](../src/parkour_solver/search/compat/decision_windows.py#L109) | function |
| `score_features` | [130](../src/parkour_solver/search/compat/decision_windows.py#L130) | function |

## search/compat/minimum_runs.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `_shift` | [12](../src/parkour_solver/search/compat/minimum_runs.py#L12) | function |
| `latest_jump` | [21](../src/parkour_solver/search/compat/minimum_runs.py#L21) | function |
| `remember` | [77](../src/parkour_solver/search/compat/minimum_runs.py#L77) | function |
| `exclusion` | [84](../src/parkour_solver/search/compat/minimum_runs.py#L84) | function |

## search/compat/semantic_adapter.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `SemanticCorridorAdapter` | [11](../src/parkour_solver/search/compat/semantic_adapter.py#L11) | class |
| `SemanticCorridorAdapter.__init__` | [12](../src/parkour_solver/search/compat/semantic_adapter.py#L12) | function |
| `SemanticCorridorAdapter.goals` | [21](../src/parkour_solver/search/compat/semantic_adapter.py#L21) | function |
| `SemanticCorridorAdapter.for_goal` | [35](../src/parkour_solver/search/compat/semantic_adapter.py#L35) | function |
| `SemanticCorridorAdapter.outgoing_objective` | [67](../src/parkour_solver/search/compat/semantic_adapter.py#L67) | function |
| `SemanticCorridorAdapter.bind_program` | [79](../src/parkour_solver/search/compat/semantic_adapter.py#L79) | function |
| `SemanticCorridorAdapter.context` | [84](../src/parkour_solver/search/compat/semantic_adapter.py#L84) | function |
| `SemanticCorridorAdapter.program_states` | [100](../src/parkour_solver/search/compat/semantic_adapter.py#L100) | function |
| `SemanticCorridorAdapter.program_allowed` | [128](../src/parkour_solver/search/compat/semantic_adapter.py#L128) | function |
| `SemanticCorridorAdapter.scoped` | [132](../src/parkour_solver/search/compat/semantic_adapter.py#L132) | function |
| `SemanticCorridorAdapter.eligible` | [137](../src/parkour_solver/search/compat/semantic_adapter.py#L137) | function |
| `SemanticCorridorAdapter._omitted` | [141](../src/parkour_solver/search/compat/semantic_adapter.py#L141) | function |
| `SemanticCorridorAdapter.constraint_guards` | [146](../src/parkour_solver/search/compat/semantic_adapter.py#L146) | function |
| `SemanticCorridorAdapter.candidate` | [154](../src/parkour_solver/search/compat/semantic_adapter.py#L154) | function |
| `SemanticCorridorAdapter.summary` | [185](../src/parkour_solver/search/compat/semantic_adapter.py#L185) | function |

## search/compat/support_order.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `SupportOrder` | [4](../src/parkour_solver/search/compat/support_order.py#L4) | class |
| `SupportOrder.__init__` | [5](../src/parkour_solver/search/compat/support_order.py#L5) | function |
| `SupportOrder.audit` | [23](../src/parkour_solver/search/compat/support_order.py#L23) | function |
| `SupportOrder.__call__` | [55](../src/parkour_solver/search/compat/support_order.py#L55) | function |
| `SupportOrder.remaining` | [57](../src/parkour_solver/search/compat/support_order.py#L57) | function |
| `SupportOrder.priority` | [60](../src/parkour_solver/search/compat/support_order.py#L60) | function |

## search/compat/time_progress.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `protections` | [9](../src/parkour_solver/search/compat/time_progress.py#L9) | function |
| `clearly_worse` | [14](../src/parkour_solver/search/compat/time_progress.py#L14) | function |
| `retain_two_goals` | [19](../src/parkour_solver/search/compat/time_progress.py#L19) | function |
| `clock_result` | [68](../src/parkour_solver/search/compat/time_progress.py#L68) | function |

## search/completion.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `generation_exhausted` | [4](../src/parkour_solver/search/completion.py#L4) | function |

## search/deferrals.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `remember_peer` | [7](../src/parkour_solver/search/deferrals.py#L7) | function |
| `assess` | [16](../src/parkour_solver/search/deferrals.py#L16) | function |

## search/dfs.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `candidate_key` | [20](../src/parkour_solver/search/dfs.py#L20) | function |
| `arrival_key` | [25](../src/parkour_solver/search/dfs.py#L25) | function |
| `decision_family` | [29](../src/parkour_solver/search/dfs.py#L29) | function |
| `commit_arrival` | [40](../src/parkour_solver/search/dfs.py#L40) | function |
| `search_goals` | [65](../src/parkour_solver/search/dfs.py#L65) | function |

## search/domain.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `InitialJumpDomain` | [1](../src/parkour_solver/search/domain.py#L1) | class |
| `InitialJumpDomain.__init__` | [3](../src/parkour_solver/search/domain.py#L3) | function |
| `InitialJumpDomain.__call__` | [10](../src/parkour_solver/search/domain.py#L10) | function |

## search/engine.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `ScopedWork` | [25](../src/parkour_solver/search/engine.py#L25) | class |
| `ScopedWork.__init__` | [26](../src/parkour_solver/search/engine.py#L26) | function |
| `ScopedWork.deadline` | [31](../src/parkour_solver/search/engine.py#L31) | function |
| `ScopedWork.termination` | [35](../src/parkour_solver/search/engine.py#L35) | function |
| `ScopedWork.check` | [38](../src/parkour_solver/search/engine.py#L38) | function |
| `ScopedWork.proposal` | [42](../src/parkour_solver/search/engine.py#L42) | function |
| `ScopedWork.before_solve` | [46](../src/parkour_solver/search/engine.py#L46) | function |
| `ScopedWork.accepted` | [51](../src/parkour_solver/search/engine.py#L51) | function |
| `evaluate_program` | [55](../src/parkour_solver/search/engine.py#L55) | function |
| `_key` | [194](../src/parkour_solver/search/engine.py#L194) | function |
| `find_checkpoint_route` | [198](../src/parkour_solver/search/engine.py#L198) | function |

## search/entry.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `build_entry_problem` | [13](../src/parkour_solver/search/entry.py#L13) | function |
| `entry_contact_options` | [88](../src/parkour_solver/search/entry.py#L88) | function |
| `probe_region_entries` | [113](../src/parkour_solver/search/entry.py#L113) | function |

## search/events/adapter.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `EventReference` | [17](../src/parkour_solver/search/events/adapter.py#L17) | class |
| `EventReference.to_dict` | [22](../src/parkour_solver/search/events/adapter.py#L22) | function |
| `EventGuideAdapter` | [26](../src/parkour_solver/search/events/adapter.py#L26) | class |
| `EventGuideAdapter.__init__` | [29](../src/parkour_solver/search/events/adapter.py#L29) | function |
| `EventGuideAdapter.initial_cursor` | [43](../src/parkour_solver/search/events/adapter.py#L43) | function |
| `EventGuideAdapter.next_goals` | [46](../src/parkour_solver/search/events/adapter.py#L46) | function |
| `EventGuideAdapter.for_goal` | [104](../src/parkour_solver/search/events/adapter.py#L104) | function |
| `EventGuideAdapter.advance` | [122](../src/parkour_solver/search/events/adapter.py#L122) | function |
| `EventGuideAdapter.context` | [125](../src/parkour_solver/search/events/adapter.py#L125) | function |
| `EventGuideAdapter.program_allowed` | [132](../src/parkour_solver/search/events/adapter.py#L132) | function |
| `EventGuideAdapter.scoped` | [135](../src/parkour_solver/search/events/adapter.py#L135) | function |
| `EventGuideAdapter.bind_program` | [139](../src/parkour_solver/search/events/adapter.py#L139) | function |
| `EventGuideAdapter.eligible` | [143](../src/parkour_solver/search/events/adapter.py#L143) | function |
| `EventGuideAdapter.candidate` | [146](../src/parkour_solver/search/events/adapter.py#L146) | function |
| `EventGuideAdapter.summary` | [178](../src/parkour_solver/search/events/adapter.py#L178) | function |

## search/events/engine.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `discovery_settings` | [19](../src/parkour_solver/search/events/engine.py#L19) | function |
| `search_event_routes` | [32](../src/parkour_solver/search/events/engine.py#L32) | function |

## search/feasibility.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `policy_spec` | [9](../src/parkour_solver/search/feasibility.py#L9) | function |
| `node_identity` | [26](../src/parkour_solver/search/feasibility.py#L26) | function |
| `outcome` | [33](../src/parkour_solver/search/feasibility.py#L33) | function |
| `meaningful_choice` | [60](../src/parkour_solver/search/feasibility.py#L60) | function |
| `solve_due` | [67](../src/parkour_solver/search/feasibility.py#L67) | function |
| `unresolved_only` | [71](../src/parkour_solver/search/feasibility.py#L71) | function |
| `restore_unknown_nodes` | [77](../src/parkour_solver/search/feasibility.py#L77) | function |

## search/frontier.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `persistent_programs` | [11](../src/parkour_solver/search/frontier.py#L11) | function |

## search/goals.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `goal_spec` | [9](../src/parkour_solver/search/goals.py#L9) | function |
| `goal_key` | [39](../src/parkour_solver/search/goals.py#L39) | function |
| `region_arrival` | [44](../src/parkour_solver/search/goals.py#L44) | function |
| `landing_satisfied` | [57](../src/parkour_solver/search/goals.py#L57) | function |
| `_landing_prefixes` | [62](../src/parkour_solver/search/goals.py#L62) | function |

## search/identity.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `digest` | [9](../src/parkour_solver/search/identity.py#L9) | function |
| `motion_record` | [13](../src/parkour_solver/search/identity.py#L13) | function |
| `processing_key` | [22](../src/parkour_solver/search/identity.py#L22) | function |
| `solve_scope` | [26](../src/parkour_solver/search/identity.py#L26) | function |
| `solve_identity` | [52](../src/parkour_solver/search/identity.py#L52) | function |
| `scoped_geometry` | [60](../src/parkour_solver/search/identity.py#L60) | function |
| `full_witness` | [65](../src/parkour_solver/search/identity.py#L65) | function |
| `validation_prefix` | [70](../src/parkour_solver/search/identity.py#L70) | function |

## search/ladder_ranking.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `schedule_plan` | [11](../src/parkour_solver/search/ladder_ranking.py#L11) | function |
| `straight_line_score` | [22](../src/parkour_solver/search/ladder_ranking.py#L22) | function |
| `rank_ladder_schedules` | [35](../src/parkour_solver/search/ladder_ranking.py#L35) | function |

## search/movement.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `iter_movement_programs` | [14](../src/parkour_solver/search/movement.py#L14) | function |
| `find_movement_blocks` | [211](../src/parkour_solver/search/movement.py#L211) | function |
| `screen_program` | [277](../src/parkour_solver/search/movement.py#L277) | function |

## search/ordered_route.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `search_allocations` | [14](../src/parkour_solver/search/ordered_route.py#L14) | function |
| `find_route` | [128](../src/parkour_solver/search/ordered_route.py#L128) | function |

## search/ranking.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `candidate_rank` | [8](../src/parkour_solver/search/ranking.py#L8) | function |
| `objective_seed` | [14](../src/parkour_solver/search/ranking.py#L14) | function |
| `passage_seed` | [29](../src/parkour_solver/search/ranking.py#L29) | function |

## search/repair.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `solve_contact_branches` | [23](../src/parkour_solver/search/repair.py#L23) | function |

## search/repair_state.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `_rows` | [8](../src/parkour_solver/search/repair_state.py#L8) | function |
| `context` | [13](../src/parkour_solver/search/repair_state.py#L13) | function |
| `compatible` | [21](../src/parkour_solver/search/repair_state.py#L21) | function |
| `branch_key` | [52](../src/parkour_solver/search/repair_state.py#L52) | function |

## search/support_profiles.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `minimum_distance` | [19](../src/parkour_solver/search/support_profiles.py#L19) | function |
| `build_transition_graph` | [25](../src/parkour_solver/search/support_profiles.py#L25) | function |
| `compile_block_path` | [205](../src/parkour_solver/search/support_profiles.py#L205) | function |
| `search_block_paths` | [271](../src/parkour_solver/search/support_profiles.py#L271) | function |
| `_find_blocks_normal` | [403](../src/parkour_solver/search/support_profiles.py#L403) | function |
| `find_blocks` | [475](../src/parkour_solver/search/support_profiles.py#L475) | function |

## search/virtual_goals.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `prepare_goal` | [10](../src/parkour_solver/search/virtual_goals.py#L10) | function |
| `height_clock` | [27](../src/parkour_solver/search/virtual_goals.py#L27) | function |
| `goal_rows` | [39](../src/parkour_solver/search/virtual_goals.py#L39) | function |
| `bind_goal` | [45](../src/parkour_solver/search/virtual_goals.py#L45) | function |
| `satisfied` | [59](../src/parkour_solver/search/virtual_goals.py#L59) | function |
| `finish_choices` | [69](../src/parkour_solver/search/virtual_goals.py#L69) | function |

## search/work.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `SearchWork` | [10](../src/parkour_solver/search/work.py#L10) | class |
| `SearchWork.__init__` | [11](../src/parkour_solver/search/work.py#L11) | function |
| `SearchWork.check` | [24](../src/parkour_solver/search/work.py#L24) | function |
| `SearchWork.program` | [32](../src/parkour_solver/search/work.py#L32) | function |
| `SearchWork.proposal` | [43](../src/parkour_solver/search/work.py#L43) | function |
| `SearchWork.before_solve` | [48](../src/parkour_solver/search/work.py#L48) | function |
| `SearchWork.accepted` | [52](../src/parkour_solver/search/work.py#L52) | function |
| `SearchWork.summary` | [57](../src/parkour_solver/search/work.py#L57) | function |

## spatial/anchor_guides.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `nearest` | [9](../src/parkour_solver/spatial/anchor_guides.py#L9) | function |
| `landing_cover` | [16](../src/parkour_solver/spatial/anchor_guides.py#L16) | function |
| `terminal_point` | [23](../src/parkour_solver/spatial/anchor_guides.py#L23) | function |
| `unit` | [40](../src/parkour_solver/spatial/anchor_guides.py#L40) | function |
| `build_guides` | [45](../src/parkour_solver/spatial/anchor_guides.py#L45) | function |
| `classify` | [79](../src/parkour_solver/spatial/anchor_guides.py#L79) | function |
| `onward_objective` | [91](../src/parkour_solver/spatial/anchor_guides.py#L91) | function |

## spatial/cells.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `dimension` | [9](../src/parkour_solver/spatial/cells.py#L9) | function |
| `contains` | [13](../src/parkour_solver/spatial/cells.py#L13) | function |
| `intersection` | [17](../src/parkour_solver/spatial/cells.py#L17) | function |
| `subtract_open` | [22](../src/parkour_solver/spatial/cells.py#L22) | function |
| `merge_boxes` | [44](../src/parkour_solver/spatial/cells.py#L44) | function |
| `union_subtract` | [70](../src/parkour_solver/spatial/cells.py#L70) | function |
| `Cell` | [82](../src/parkour_solver/spatial/cells.py#L82) | class |
| `Portal` | [89](../src/parkour_solver/spatial/cells.py#L89) | class |
| `ContactPatch` | [97](../src/parkour_solver/spatial/cells.py#L97) | class |
| `Decomposition` | [108](../src/parkour_solver/spatial/cells.py#L108) | class |
| `Decomposition.locate` | [117](../src/parkour_solver/spatial/cells.py#L117) | function |
| `Decomposition.neighbors` | [120](../src/parkour_solver/spatial/cells.py#L120) | function |
| `Decomposition.to_dict` | [128](../src/parkour_solver/spatial/cells.py#L128) | function |
| `cell_portals` | [132](../src/parkour_solver/spatial/cells.py#L132) | function |
| `contact_patches` | [144](../src/parkour_solver/spatial/cells.py#L144) | function |
| `refine_support_cells` | [160](../src/parkour_solver/spatial/cells.py#L160) | function |
| `decompose` | [198](../src/parkour_solver/spatial/cells.py#L198) | function |
| `box_segment_interval` | [250](../src/parkour_solver/spatial/cells.py#L250) | function |
| `segment_intervals` | [264](../src/parkour_solver/spatial/cells.py#L264) | function |
| `segment_covered` | [276](../src/parkour_solver/spatial/cells.py#L276) | function |

## spatial/checkpoints.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `horizontal_segments` | [9](../src/parkour_solver/spatial/checkpoints.py#L9) | function |
| `Checkpoint` | [25](../src/parkour_solver/spatial/checkpoints.py#L25) | class |
| `Checkpoint.objective` | [36](../src/parkour_solver/spatial/checkpoints.py#L36) | function |
| `Checkpoint.to_dict` | [39](../src/parkour_solver/spatial/checkpoints.py#L39) | function |
| `Checkpoint.rows` | [42](../src/parkour_solver/spatial/checkpoints.py#L42) | function |
| `Checkpoint.stage` | [49](../src/parkour_solver/spatial/checkpoints.py#L49) | function |
| `checkpoint` | [63](../src/parkour_solver/spatial/checkpoints.py#L63) | function |
| `retain_prefix` | [91](../src/parkour_solver/spatial/checkpoints.py#L91) | function |
| `retain_unresolved` | [110](../src/parkour_solver/spatial/checkpoints.py#L110) | function |
| `retain_decisions` | [121](../src/parkour_solver/spatial/checkpoints.py#L121) | function |

## spatial/compat/corridor_guards.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `movement_regions` | [6](../src/parkour_solver/spatial/compat/corridor_guards.py#L6) | function |

## spatial/compat/corridors.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `midpoint` | [12](../src/parkour_solver/spatial/compat/corridors.py#L12) | function |
| `goal_point` | [16](../src/parkour_solver/spatial/compat/corridors.py#L16) | function |
| `goal_region` | [22](../src/parkour_solver/spatial/compat/corridors.py#L22) | function |
| `distance_to_box` | [29](../src/parkour_solver/spatial/compat/corridors.py#L29) | function |
| `landmarks` | [33](../src/parkour_solver/spatial/compat/corridors.py#L33) | function |
| `crossings` | [48](../src/parkour_solver/spatial/compat/corridors.py#L48) | function |
| `signature` | [70](../src/parkour_solver/spatial/compat/corridors.py#L70) | function |
| `projected_boxes` | [74](../src/parkour_solver/spatial/compat/corridors.py#L74) | function |
| `anchor_box` | [79](../src/parkour_solver/spatial/compat/corridors.py#L79) | function |
| `walk_projection` | [86](../src/parkour_solver/spatial/compat/corridors.py#L86) | function |
| `prescribed_projection` | [98](../src/parkour_solver/spatial/compat/corridors.py#L98) | function |
| `Corridor` | [103](../src/parkour_solver/spatial/compat/corridors.py#L103) | class |
| `Corridor.to_dict` | [121](../src/parkour_solver/spatial/compat/corridors.py#L121) | function |
| `corridor_for_points` | [129](../src/parkour_solver/spatial/compat/corridors.py#L129) | function |
| `enumerate_routes` | [160](../src/parkour_solver/spatial/compat/corridors.py#L160) | function |
| `conditioned_anchor` | [240](../src/parkour_solver/spatial/compat/corridors.py#L240) | function |
| `semantic_events` | [247](../src/parkour_solver/spatial/compat/corridors.py#L247) | function |
| `corridor_from_walk` | [274](../src/parkour_solver/spatial/compat/corridors.py#L274) | function |
| `enumerate_semantic_routes` | [318](../src/parkour_solver/spatial/compat/corridors.py#L318) | function |
| `horizontal_trace` | [444](../src/parkour_solver/spatial/compat/corridors.py#L444) | function |
| `projected_segment_covered` | [460](../src/parkour_solver/spatial/compat/corridors.py#L460) | function |
| `audit_candidate` | [485](../src/parkour_solver/spatial/compat/corridors.py#L485) | function |

## spatial/compat/progress.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `initial_cursor` | [10](../src/parkour_solver/spatial/compat/progress.py#L10) | function |
| `phase_trace` | [19](../src/parkour_solver/spatial/compat/progress.py#L19) | function |
| `_phase` | [26](../src/parkour_solver/spatial/compat/progress.py#L26) | function |
| `_contact` | [87](../src/parkour_solver/spatial/compat/progress.py#L87) | function |
| `advance` | [115](../src/parkour_solver/spatial/compat/progress.py#L115) | function |

## spatial/event_routes.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `_json` | [22](../src/parkour_solver/spatial/event_routes.py#L22) | function |
| `SurfaceGroup` | [27](../src/parkour_solver/spatial/event_routes.py#L27) | class |
| `surface_groups` | [36](../src/parkour_solver/spatial/event_routes.py#L36) | function |
| `RouteDomain` | [76](../src/parkour_solver/spatial/event_routes.py#L76) | class |
| `RouteDomain.id` | [85](../src/parkour_solver/spatial/event_routes.py#L85) | function |
| `RouteDomain.to_dict` | [88](../src/parkour_solver/spatial/event_routes.py#L88) | function |
| `RouteDomain.from_dict` | [92](../src/parkour_solver/spatial/event_routes.py#L92) | function |
| `build_route_domain` | [102](../src/parkour_solver/spatial/event_routes.py#L102) | function |
| `support_episodes` | [135](../src/parkour_solver/spatial/event_routes.py#L135) | function |
| `build_event_graph` | [172](../src/parkour_solver/spatial/event_routes.py#L172) | function |
| `initial_cursor` | [201](../src/parkour_solver/spatial/event_routes.py#L201) | function |
| `next_goals` | [218](../src/parkour_solver/spatial/event_routes.py#L218) | function |
| `semantic_prefix` | [282](../src/parkour_solver/spatial/event_routes.py#L282) | function |
| `advance_cursor` | [295](../src/parkour_solver/spatial/event_routes.py#L295) | function |
| `observed_route` | [307](../src/parkour_solver/spatial/event_routes.py#L307) | function |

## spatial/guides.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `task_reference` | [12](../src/parkour_solver/spatial/guides.py#L12) | function |
| `segment_in_cover` | [25](../src/parkour_solver/spatial/guides.py#L25) | function |
| `guide_for` | [35](../src/parkour_solver/spatial/guides.py#L35) | function |
| `outgoing_objective` | [109](../src/parkour_solver/spatial/guides.py#L109) | function |

## spatial/regions.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `geometry_key` | [19](../src/parkour_solver/spatial/regions.py#L19) | function |
| `canonical_cover` | [25](../src/parkour_solver/spatial/regions.py#L25) | function |
| `SupportComponent` | [59](../src/parkour_solver/spatial/regions.py#L59) | class |
| `support_components` | [69](../src/parkour_solver/spatial/regions.py#L69) | function |
| `RouteRegion` | [98](../src/parkour_solver/spatial/regions.py#L98) | class |
| `RouteTransition` | [110](../src/parkour_solver/spatial/regions.py#L110) | class |
| `RouteGraph` | [124](../src/parkour_solver/spatial/regions.py#L124) | class |
| `RouteGraph.to_dict` | [135](../src/parkour_solver/spatial/regions.py#L135) | function |
| `RouteGraph.neighbors` | [138](../src/parkour_solver/spatial/regions.py#L138) | function |
| `free_transit_cover` | [145](../src/parkour_solver/spatial/regions.py#L145) | function |
| `build_route_graph` | [200](../src/parkour_solver/spatial/regions.py#L200) | function |
| `build_geometry_graph` | [210](../src/parkour_solver/spatial/regions.py#L210) | function |
| `_incidence_graph` | [222](../src/parkour_solver/spatial/regions.py#L222) | function |
| `annotate_normal_proposals` | [314](../src/parkour_solver/spatial/regions.py#L314) | function |

## spatial/surfaces.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `SourceSolid` | [30](../src/parkour_solver/spatial/surfaces.py#L30) | class |
| `SourceFace` | [38](../src/parkour_solver/spatial/surfaces.py#L38) | class |
| `FaceContribution` | [47](../src/parkour_solver/spatial/surfaces.py#L47) | class |
| `MergedFace` | [53](../src/parkour_solver/spatial/surfaces.py#L53) | class |
| `FaceCatalog` | [64](../src/parkour_solver/spatial/surfaces.py#L64) | class |
| `FaceCatalog.__post_init__` | [79](../src/parkour_solver/spatial/surfaces.py#L79) | function |
| `FaceCatalog.__deepcopy__` | [95](../src/parkour_solver/spatial/surfaces.py#L95) | function |
| `FaceCatalog.face` | [98](../src/parkour_solver/spatial/surfaces.py#L98) | function |
| `FaceCatalog.faces_for_block` | [101](../src/parkour_solver/spatial/surfaces.py#L101) | function |
| `FaceCatalog.faces_for_solid` | [104](../src/parkour_solver/spatial/surfaces.py#L104) | function |
| `FaceCatalog.contributors_at` | [109](../src/parkour_solver/spatial/surfaces.py#L109) | function |
| `FaceCatalog.contributors_over` | [117](../src/parkour_solver/spatial/surfaces.py#L117) | function |
| `FaceCatalog.cover_for_sources` | [127](../src/parkour_solver/spatial/surfaces.py#L127) | function |
| `FaceCatalog.statistics` | [142](../src/parkour_solver/spatial/surfaces.py#L142) | function |
| `FaceCatalog._payload` | [150](../src/parkour_solver/spatial/surfaces.py#L150) | function |
| `FaceCatalog.to_dict` | [162](../src/parkour_solver/spatial/surfaces.py#L162) | function |
| `FaceCatalog.from_dict` | [167](../src/parkour_solver/spatial/surfaces.py#L167) | function |
| `_coordinate` | [191](../src/parkour_solver/spatial/surfaces.py#L191) | function |
| `_coordinates` | [202](../src/parkour_solver/spatial/surfaces.py#L202) | function |
| `_contains_point` | [208](../src/parkour_solver/spatial/surfaces.py#L208) | function |
| `_sources` | [219](../src/parkour_solver/spatial/surfaces.py#L219) | function |
| `_embed` | [252](../src/parkour_solver/spatial/surfaces.py#L252) | function |
| `_project` | [256](../src/parkour_solver/spatial/surfaces.py#L256) | function |
| `_cover` | [260](../src/parkour_solver/spatial/surfaces.py#L260) | function |
| `_overlap` | [264](../src/parkour_solver/spatial/surfaces.py#L264) | function |
| `_adjacent` | [271](../src/parkour_solver/spatial/surfaces.py#L271) | function |
| `_check` | [276](../src/parkour_solver/spatial/surfaces.py#L276) | function |
| `_source_faces` | [281](../src/parkour_solver/spatial/surfaces.py#L281) | function |
| `_components` | [314](../src/parkour_solver/spatial/surfaces.py#L314) | function |
| `_merged_faces` | [338](../src/parkour_solver/spatial/surfaces.py#L338) | function |
| `merge_faces` | [355](../src/parkour_solver/spatial/surfaces.py#L355) | function |
| `Cluster` | [375](../src/parkour_solver/spatial/surfaces.py#L375) | class |
| `Side` | [383](../src/parkour_solver/spatial/surfaces.py#L383) | class |
| `Side.key` | [389](../src/parkour_solver/spatial/surfaces.py#L389) | function |
| `nested` | [393](../src/parkour_solver/spatial/surfaces.py#L393) | function |
| `intersects` | [400](../src/parkour_solver/spatial/surfaces.py#L400) | function |
| `free_rectangles` | [404](../src/parkour_solver/spatial/surfaces.py#L404) | function |
| `Surface` | [432](../src/parkour_solver/spatial/surfaces.py#L432) | class |
| `SceneGeometry` | [444](../src/parkour_solver/spatial/surfaces.py#L444) | class |
| `SceneGeometry.__init__` | [447](../src/parkour_solver/spatial/surfaces.py#L447) | function |
| `SceneGeometry.clusters` | [455](../src/parkour_solver/spatial/surfaces.py#L455) | function |
| `SceneGeometry.cluster` | [458](../src/parkour_solver/spatial/surfaces.py#L458) | function |
| `SceneGeometry._cluster_map` | [461](../src/parkour_solver/spatial/surfaces.py#L461) | function |
| `SceneGeometry.active` | [483](../src/parkour_solver/spatial/surfaces.py#L483) | function |
| `SceneGeometry.sides` | [486](../src/parkour_solver/spatial/surfaces.py#L486) | function |
| `SceneGeometry.player_surfaces` | [506](../src/parkour_solver/spatial/surfaces.py#L506) | function |
| `SceneGeometry.contact_options` | [550](../src/parkour_solver/spatial/surfaces.py#L550) | function |
| `ContactOption` | [591](../src/parkour_solver/spatial/surfaces.py#L591) | class |
| `_maximal_rectangles` | [599](../src/parkour_solver/spatial/surfaces.py#L599) | function |
| `_erode` | [625](../src/parkour_solver/spatial/surfaces.py#L625) | function |
| `_scene_geometry` | [632](../src/parkour_solver/spatial/surfaces.py#L632) | function |
| `scene_geometry` | [636](../src/parkour_solver/spatial/surfaces.py#L636) | function |

## validation/numerical.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `validate` | [8](../src/parkour_solver/validation/numerical.py#L8) | function |

## validation/scene.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `check_geometry` | [5](../src/parkour_solver/validation/scene.py#L5) | function |

## validation/scoped_scene.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `CorridorAdapter` | [15](../src/parkour_solver/validation/scoped_scene.py#L15) | class |
| `CorridorAdapter.__init__` | [16](../src/parkour_solver/validation/scoped_scene.py#L16) | function |
| `CorridorAdapter.box_allowed` | [25](../src/parkour_solver/validation/scoped_scene.py#L25) | function |
| `CorridorAdapter.region_allowed` | [30](../src/parkour_solver/validation/scoped_scene.py#L30) | function |
| `CorridorAdapter.context` | [38](../src/parkour_solver/validation/scoped_scene.py#L38) | function |
| `CorridorAdapter.prefix` | [47](../src/parkour_solver/validation/scoped_scene.py#L47) | function |
| `CorridorAdapter.row` | [111](../src/parkour_solver/validation/scoped_scene.py#L111) | function |
| `CorridorAdapter.scoped` | [114](../src/parkour_solver/validation/scoped_scene.py#L114) | function |
| `CorridorAdapter.eligible` | [128](../src/parkour_solver/validation/scoped_scene.py#L128) | function |
| `CorridorAdapter.failure_diagnostics` | [139](../src/parkour_solver/validation/scoped_scene.py#L139) | function |
| `CorridorAdapter.candidate` | [164](../src/parkour_solver/validation/scoped_scene.py#L164) | function |
| `CorridorAdapter.summary` | [210](../src/parkour_solver/validation/scoped_scene.py#L210) | function |

## validation/vertical.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `validate_vertical_schedule` | [6](../src/parkour_solver/validation/vertical.py#L6) | function |

## validation/witness.py

| Symbol | Line | Kind |
| --- | ---: | --- |
| `replay_witness` | [10](../src/parkour_solver/validation/witness.py#L10) | function |
| `witnesses` | [42](../src/parkour_solver/validation/witness.py#L42) | function |
