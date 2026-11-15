# Select and compose existing constraints before IPOPT

Latest discussion, 2026-11-01: [provisional route-aware initial solves](PROVISIONAL_ROUTE_GUIDANCE.md). User emphasizes the value of numerical feedback from earlier decisions to later ones. The recommendation is to preserve important route/obstacle geometry using reasonable existing provisional choices, then refine their timing/method after the solve—not enumerate every downstream alternative first. Fixed decisions within a call remain revisable across calls. This is a proposed middle ground, not a shipped or user-approved policy.

Revised planning record, 2026-11-01. User correctly points out that movement, blockage, hit/avoid, ceiling and stepping constraints are already implemented. The previous plan overstated the missing machinery and proposed unnecessarily rederiving movement guards. This revision supersedes that implementation scope: reuse the existing catalogue and compiler. The remaining work is selecting relevant existing alternatives before a numerical solve and ensuring their composition covers the selected route.

## What already exists and remains authoritative

| Responsibility | Existing implementation to reuse |
| --- | --- |
| Registered normal/ceiling/stepping alternatives | `movement_generation.generate_choices`, `open_movement.normal_choices`, `stepping_generation`, `ceiling_generation` |
| Named stepping conditions and Y/control effects | `case_profiles`, `STEPPING_CASES.md`, existing stepping fixtures/tests |
| Ordinary run/jump/landing profiles | `block_profiles`, `open_movement`, existing route support/exit checks |
| Blockage face chains and transition timing | `blockages.passage_options`, `compile_passages`, `passage_y_valid` |
| Finite contact/hit/avoid constraints | `contact_combinations.compile_contact_events`, `hit_events.compile_hits`, existing avoidance helpers |
| Composition and index shifting | `movement_composition.append_choice`, `compile_program`, `block_search.compile_block_path`, `route.schedule` |
| Corridor axis-segment bounds | Existing cell decomposition/corridor code and corrected `movement_regions` |
| Necessary feasibility checks | Existing interval/entry/passage/contact checks and `screen.contradiction` |
| Saved search work and reference guesses | Existing repair-state compatibility/resumption and validated-incumbent channel |
| Independent verification | Scalar validation and shared finite-scene checker |

Existing selected cases already carry their position constraints, case events, finite contacts and physical schedules into one original-state solve. Reimplementing stepping from the shared resolver's internal A/B branches, introducing a parallel geometry/physics compiler, or rebuilding the movement catalogue is outside this plan. The checker remains the existing independent validation mechanism.

## The actual orchestration gap

Selecting a movement interaction does not automatically select constraints for every other potentially interfering block. The current pilot chooses some additional corridor, optional-floor and blockage alternatives only after a numerical candidate fails validation.

For example, choosing an existing step on block A supplies A's stepping constraints. A nearby block B may still require an avoidance/passsage choice on a particular input interval. Existing builders can express that choice; the orchestrator must select and compose it before IPOPT rather than first discover B through a failed trajectory.

`passage_options` currently receives numerical candidate positions to rank face/timing alternatives. Its geometry/reachability logic and its actual constraint compilation already exist. Decouple that ranking dependency: use the supplied spatial-route guide or a compatible prior guess for ranking, keeping safety screens separate. Similarly, optional-floor opportunities can be identified from the selected Y history, and existing outside-face alternatives can be ranked using guide positions instead of waiting for a solved conflict. Neither requires new collision formulas or a new continuous model.

## Revised implementation steps

1. **Inventory the current generation-to-solve path.** Trace selected-case constraints through `append_choice -> compile_program -> schedule`. Identify exactly which existing alternatives are still added by post-solve `solve_contact_branches`/corridor checks. Preserve existing case contracts and tests. Do not presume every case needs new guards.
2. **Select relevant existing interactions upfront.** Given a spatial route and candidate physical input history, enumerate relevant block/interval opportunities using the existing geometry, Y history and conservative reachability checks. Record each block/interval as selected contact/case, selected avoid/passsage, or conservatively irrelevant. Keep unspecified choices explicit until selected; a guide-distance ranking is not a proof of irrelevance.
3. **Compose the existing alternatives.** Choose face chains/ticks, hit/avoid variants and movement cases using the current generators, then compile through the existing path. Reuse existing contradiction checks to reject inconsistent combinations before IPOPT. Keep earlier facings free and use the full original initial state; guide positions remain ranking/initialization information.
4. **Move the solve boundary.** Only invoke IPOPT after the relevant interaction choices are selected. Retain current completed-candidate scheduling, saved conditional alternatives, reference guesses and fully validated incumbents. Keep existing independent scene replay; a mismatch identifies a specific missing selection or builder bug, which is fixed in its canonical module with a regression.
5. **Benchmark coverage and cost.** Compare the same cold scenes, known valid schedules and held-out geometry. Record post-solve unexpected contacts, omitted interactions, generated complete combinations, constraint counts, solver calls, upstream generation work and best accepted times. Aim to remove routine post-solve geometry repair without multiplying choices unmanageably. Preserve explicit unknown/heuristic-drop reporting.

## Limitations

The primary difficulty is selecting a small, compatible set of existing decisions and assigning them to blocks/ticks. Dense nearby obstacles can still produce many combinations. A fully selected combination may be infeasible; IPOPT unknown is distinct from a physically invalid model-feasible result. Finite generation limits and approximate relevance/ranking can lose coverage, so those drops must be reported honestly.

Existing builders may have particular coverage or composition bugs; investigate observed failures and fix the responsible builder rather than assume the entire catalogue is missing or correct by fiat. Current scope still excludes ladders, water, intentional final wall-hit routes and inertia thresholds; normal momentum carries throughout.

The previously noted expressiveness of arbitrary oblique checkpoint-readiness predicates is a separate checkpoint issue, not a prerequisite for reusing existing movement constraints. Keep this work focused on the existing cases and supported route/constraint representation. Do not change score/readiness semantics or redesign IPOPT as part of this selection/composition work.

## Completion target

Within the supported family, candidate calls contain the selected relevant movement/contact/avoidance constraints up front. Model-feasible candidates should pass existing physical and route verification within stated numerical tolerances; failures identify concrete selection/composition/template defects. Preserve prior six-scene completion coverage and accepted incumbents while reporting total generation-plus-solve cost. No global completeness/optimality or uniformly small-candidate guarantee is inferred from this finite gate.

No runtime code changes in this clarification turn. Next implementation should start by promoting the existing post-solve choice generation into pre-solve orchestration, not by rebuilding movement types, step physics or their constraint formulas. The time/progress frontier remains deferred.
