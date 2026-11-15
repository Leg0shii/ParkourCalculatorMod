# Movement units and airborne composition

Latest extension, 2026-10-30: [GENERIC_COMPOSITION.md](GENERIC_COMPOSITION.md) documents the open choice contract and shared queue for normal, stepping, ceiling and ladder phases. Units can hand off at their actual event state, including airborne and unsupported grounded states. The new path removes the one-case support-leg limit; the older support-profile adapter remains for compatibility.

2026-10-30 extension: registered `stepping` and normal-air `ceiling` units now survive profile compilation, air-gap overlays and run retiming. Named case anchors, jump inputs and grounded-input masks are retained and checked against the scalar profile. Ladder-owned intervals keep their existing units and ceiling behavior; the ladder-ceiling refactor is deferred. See [STEPPING_IMPLEMENTATION.md](STEPPING_IMPLEMENTATION.md). Earlier checkpoint statements below saying stepping is absent describe the preceding ladder milestone.

2026-10-29. This implements the latest ladder decisions in [LADDER_COMPOSITION_PLAN.md](LADDER_COMPOSITION_PLAN.md). Continuous numerical acceptance remains this layer's responsibility; replay/export belongs to the other integration worktree.

## Implemented interface

`movement_units.py` registers `normal`, `stepping`, `ceiling`, `ladder_climb` and `ladder_fall`. A unit has an input interval `[start,end)`, a type and its relevant relative anchors: region, occupied-state interval, hit inputs, Sneak inputs, planned climb-hit window and omitted endpoint hits. `compile_units` materializes occupied states and input masks. `validate_ladder_events` builds and checks the per-leg program, and ladder constraint compilation consumes its occupied-state mask. These units are part of the orchestrator/compiler contract, rather than display labels.

`route.movementUnits` holds relative programs per support-to-support leg. `schedule(...).movementUnits` exposes the absolute timeline, including inserted grounded normal/run units. Every duration, contact, occupied-state interval, stop and Sneak anchor shifts with run insertion. The global key/ground/contact history still goes through the existing shared movement compiler. Types do not replace its physics.

Within one region, the exact allowed phase grammar is:

| Sequence | Permitted exits |
| --- | --- |
| `ladder_fall` | side, bottom |
| `ladder_climb` | top, side, bottom |
| `ladder_fall -> ladder_climb` | top, side, bottom |
| `ladder_climb -> ladder_fall` | side, bottom |
| `ladder_fall -> ladder_climb -> ladder_fall` | side, bottom |

There is at most one climbing unit. Its zero, one or two omitted endpoint hits remain internal to that unit. An end stop omits the final hit whose produced state would still be inside the region. It does not omit merely the exit-producing input. The exit-producing input belongs to the final climb unit when its planned hit window reaches that last eligible hit, including an end-stop variant. Actual Y replay, not this ownership label, determines its movement and carry.

An entering input can belong to climbing immediately. Its caps still depend on actual pre-input occupancy; an input starting outside is not capped early. Its wall hit establishes climbing carry only if its resulting state is inside. No falling unit or extra tick is inserted to establish that carry. Other selected horizontal contacts count too: an extra wall hit cannot secretly create a second climbing interval after falling.

Falling retains arbitrary interspersed Sneak/no-Sneak masks. Existing endpoint-stop Sneak and first-clamped-input Sneak alternatives remain available. The former remains climbing-owned, while the latter preserves the previously requested clamp/Sneak alternative. Neither adds an external falling phase. No new Y/clamp or sprint equations were introduced.

`exit_matches` checks feet Y against the region's half-open interval `[minY,maxY)`: side is inside the interval, top is at/above maxY, bottom is strictly below minY. A bottom exit does not invent an X/Z departure requirement while Y is outside; ordinary finite-wall/body collision checks remain active. If Y later intersects the region again, the normal exit-corridor constraints apply.

## Open prefixes and repeated extension

`generate_ladder_schedules` accepts `finishAtExit: true`. Its terminal state is the actual airborne exit, its scalar target floor is absent, and its Y/VY/contact history remains available. Explicit early/final landings are rejected for this terminal mode. The existing landed mode still requires a genuine descending floor clip.

`compose_ladder_episodes(source_y, None, episodes, ...)` composes an airborne prefix. A numeric target height instead requests a completed landing. The composer replays all episodes and normal gaps from the original Y/VY/ground/jump state. It retains the complete Sneak/contact history, validates phase grammar and exit height, and generates one coherent timeline. A direct transfer can share the previous exit-producing input with the next entry, without adding a fictitious free tick or dropping its pre-input occupancy.

`append_ladder_programs` appends another region to any accumulated prefix. It proposes front/side entry channels and timing/phase/Sneak alternatives, retimes them after the previous exit, and recomposes the entire prefix. The previous episode's departure corridor can face the new region. It never requires the prefix to land on the previous wall or the final goal before considering another ladder.

`iter_chain_programs` maintains a queue ordered by committed elapsed inputs. Each prefix can finish on the real target support or append any unused region. Repeated extension replaces the old A/B transfer template. There is no two-region structural limit. `maxInteractions` is configurable through the total 200-input model horizon; its default remains 12. The compatibility `transfer_programs` function now delegates to the same append/composer implementation.

The route orchestrator interleaves completed chain proposals with continuous solves. Each accepted route recompiles and solves all prior facings, incoming momentum, contacts and connecting movement from the original route initial state. No intermediate numerical X/Z endpoint is frozen or treated as an executable checkpoint. Elapsed ticks rank before objective among accepted complete routes.

Production airborne extension now probes entries through the same full-history numerical/scene path as the first region. The probe validates an approach witness, while later phase/Sneak/delay choices still require a complete solve. Low-level pure generation remains available with explicitly unprobed entries. An open node is not an accepted complete route.

## Work limits and unresolved coverage

The bounded search is not an optimality/infeasibility proof. Entry/skeleton/mask/ranking/solver caps, numerical unknowns and dropped queue entries remain reported. Defaults for airborne expansion are 16 extension programs, 64 expanded prefixes and 256 queued prefixes. Configure them using `maxTransferPrograms`, `maxTransferPrefixes` and `maxTransferQueuedPrefixes`. Generation retains entry/exit/climbing diversity before filling remaining slots.

Initial transfer windows sample distinct exit states, rather than allowing multiple carried-VY variants at one state to consume all four default windows. Other VY/Sneak/stop alternatives can still be dropped by those work caps. Automatic appended entries use the configured entry horizon and remaining flight duration, report clipping and unresolved times, and do not claim to have proved their earliest feasible entry. The whole flight remains bounded to 200 inputs.

Same-region re-entry and changing the chosen climb-wall orientation inside a region remain deferred. Rectangle regions are sufficient for the requested scope. Route-specific rarity/pruning rules, globally exhaustive schedule dominance and a global time-optimality proof remain later work. Automatic mixed contacts now expand initial open episodes, every appended approach/ladder and final air interval; see [MIXED_MOVEMENT_CONTACTS.md](MIXED_MOVEMENT_CONTACTS.md). These alternatives remain bounded, rather than exhaustive. `stepping` and `water` have not been added; the existing stair/step profile path remains in place until that follow-up.

## Measured evidence

- New five-region falling-only episode contract: 58 inputs, one launch, no intermediate ground contact; fresh IPOPT solve and shared full-scene acceptance. This covers optional climbing and arbitrary-length composition. It is not a cold geometry-only discovery of all five regions.
- New bottom-exit contract: exits below Y=20 at state 15 with Y=19.970787077218812, lands at Y=18 at state 21; fresh IPOPT and shared-scene acceptance. Caps apply while inside, then ordinary descent continues below the region.
- Existing cold two-region discovery still finds the 28-input airborne transfer. A focused rerun after transfer-window fairness correction completed in about 50 seconds under the existing fixture budgets.
- Separate repeated calls to the actual append generator produced five climbing prefixes with entry/exit anchors `(1,6)`, `(11,25)`, `(30,43)`, `(48,62)`, `(67,81)`. Those narrow-region climbing skeletons are scalar/generation evidence only. Attempted full numerical solves returned unknown; do not describe them as accepted movement or infer infeasibility.

Saved new contracts and accepted candidates live beside the earlier witnesses in [experiments/contact-combinations](experiments/contact-combinations/README.md). `test_movement_units.py` and `test_movement_composition.py` cover the five phase sequences, all short hit masks, endpoint stops and their Y/carry changes, real bottom descent, open prefixes, direct transfer seams, run retiming, full-history append, queue composition/cancellation, fresh numerical solves and saved scalar/scene revalidation. The five-prefix queue test isolates queue traversal with predetermined scalar prefixes; the append test and numerical acceptance tests exercise the real implementations separately.

Earlier checkpoint verification: 27 focused unit/composition/legacy-witness tests passed in 18.487 seconds, including real repeated append across five climbing regions and fresh numerical/scene acceptance for the five falling regions and bottom exit. All 51 numerical tests passed in 24.973 seconds. The full 215-test orchestration sweep completed in 460.748 seconds; five subcase errors came from old saved routes without the newly introduced unit metadata. A compatibility fallback reconstructs those units, and all five witness methods pass in the final focused gate. Three additional tests added during that sweep are covered by the focused gate. The full sweep was not repeated after that narrowly scoped metadata fix. Both feature commits leave the numerical worker and shared movement arithmetic unchanged.

## Common generation interface, 2026-10-29

`movement_generation.GENERATORS` dispatches candidate generation by movement type. Each choice supplies a typed input interval plus its relative inputs, occupancy/contact requirements and duration. Normal support choices carry the existing jump/run-off vertical profiles and source/target-height conditions. Ladder choices carry occupied-state intervals, contiguous climbing contacts, planned hit windows and omitted endpoint hits. Sneak domains are supplied by the owning type. The composer chooses the five permitted per-region sequences before scalar simulation; it no longer identifies new unit choices after replaying a whole hit mask.

Generated units are retained through schedule materialization, episode retiming and common compilation. Their semantic masks are checked against the selected timeline so stale or altered metadata cannot hide a restart of climbing. Legacy supplied contracts without generated units still use the existing parser. Stair profiles remain on their existing path pending the deferred stepping type; water is deferred.

## Consistent entry handling, 2026-10-29

`movement_entry.build_entry_problem` extends an original-state route context through either an initial approach or any accumulated airborne prefix to an occupied terminal entry state. It retains previous supports/run ticks, jump and Sneak inputs, occupancy, finite hit/avoid constraints, Y collisions and carried velocity. It does not fabricate an exit, ground contact or landing support. `compile_contact_events` accepts an explicit open `end` as well as a real landing endpoint. Normal simulation and finite Y-X-Z stage checks remain shared.

`probe_region_entries` scans each front/side channel in increasing entry time with hit/no-hit and approach-ceiling alternatives. Initial membership at state zero is checked directly. Successful probes record their first validated state and earlier unknowns. Failed numerical attempts remain unknown. Generation prioritizes validated windows and preserves deferred/unresolved alternatives in diagnostics, so no minimum proof follows from the ordering or work limits. Appended probes have a bounded slice of the remaining budget; delayed windows and all complete routes still receive the original final acceptance. Shared waypoint construction is used by probes and complete solves.

Final verification for the common generator/entry refactor: all 257 orchestration tests pass across two batches, all 51 numerical-model tests pass, and the focused entry gate passes 11 tests. The latter includes an entry after four prior climbs, an approach ceiling, launch run retiming and rejection of a worker success that fails independent scalar validation. Numerical-worker arithmetic and shared Java movement code are unchanged. See MOVEMENT_REFACTOR_STATUS.md for saved commits, coverage and deferred work.
