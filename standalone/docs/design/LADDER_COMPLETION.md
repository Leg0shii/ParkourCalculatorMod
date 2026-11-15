# Ladder and vine completion checkpoint

The agreed ladder movement milestone is complete within the current continuous-model orchestration scope. The closeout regression passed. Stepping is the next movement type. Replay/export and in-game acceptance belong to the other layer.

## Completed behavior and evidence

| Requirement | Implementation and evidence |
| --- | --- |
| Rectangular adjacent cells form one region | `ladder_geometry.py`; horizontal/vertical merging and exact membership tests in `test_ladder_composition.py` |
| Optional climbing and falling; exactly five phase sequences | Registered `ladder_climb` and `ladder_fall` generators; exhaustive short masks and grammar tests in `test_movement_units.py` and `test_movement_generation.py` |
| Beginning/end stops belong to climbing | Four endpoint variants; the final eligible hit is omitted, not merely the exit-producing input; independent Y/carry and landing-time checks in `test_ladder_schedules.py` and `test_movement_composition.py` |
| Arbitrary falling Sneak/no-Sneak arrangements | All 256 eight-input masks, interspersed equal-count masks, first-clamped-input priority and actual Y effects |
| Correct component caps and sprint history | Existing shared compiler/scalar model; `test_ladder_bridge.py`, numerical ladder tests, missing/extra/mistimed contact mutations and scene acceptance |
| Immediate hit on entry | Caps use pre-input membership; refresh uses resulting membership and an actual horizontal clip; scalar and fresh IPOPT tests in `test_movement_entry.py` |
| Front/side entry and delayed entry alternatives | One numerical probe implementation for the first and appended region; original-state solve, bounded delays and explicit unresolved times |
| Top, side and bottom exits | Top ends climbing; side/bottom can end in either phase; real bottom-exit scalar/NLP/scene witness |
| Consecutive airborne interactions | Repeated open-prefix extension, one complete input/Y/contact history, no intermediate X/Z/VXZ pins; accepted five-fall and prescribed five-climb chains |
| Walls, ceilings and independent contacts | Actual finite faces, Y-X-Z stage order, before/after avoidance, combined contacts, connecting-air/ladder/final-air generation and acceptance |
| Falling through hanging vines without a climb wall | New explicit `fall_only` movement context, four entry sides, ordinary caps/Sneak and no invented wall/contact; cold 17-input accepted witness in `experiments/contact-combinations/hanging-vines.json` |
| Common generation and inserted run ticks | Unit choices precede Y replay, masks/grammar are validated, relative ladder anchors retime with the original complete plan |
| Honest success/failure contract | IPOPT success must pass independent scalar and shared scene validation; numerical failures remain unknown; work limits do not establish infeasibility or a global minimum |

The falling context's axis/plane is only a seed/objective reference. Its finite wall parts are empty. It adds no wall-avoidance plane and cannot generate a primary climb hit. Separately selected real contacts retain their actual climbing-refresh behavior. Real climb-wall alternatives remain available; a falling strategy is no longer conditional on one existing.

## Completion boundary

This closes the agreed mechanics and unit/compiler interface, not every possible Minecraft route or an exhaustive search theorem. Already deferred: same-region re-entry, changing climb-wall orientation during one interaction, rare diagonal-only transfers, route/input pruning, exact-Y caching and optional resumable generation. Cold five-climb discovery reliability remains a search-performance issue; the supplied five-climb structure is numerically accepted.

The automatic first ladder approach currently uses the existing grounded jump launch. Open airborne appends do not add a jump. Normal run-off support transitions remain supported separately; automatically enumerating a no-jump first-ladder launch is a further normal/ladder route-composition extension. Likewise, vertical entry from above is outside the agreed front/side entry family. These do not change the implemented climbing/falling equations or their acceptance requirements.

No ladder arithmetic should be duplicated for stepping. Preserve the existing ladder regression cases when adding the next type.

## Verification

Before this closeout: 257 orchestration tests and 51 numerical-model tests passed. The closeout adds hanging-vine discovery, four-face entry, no-phantom-hit, Sneak, real-contact refresh, repeated append and saved scalar/scene checks. Final closeout coverage: all 265 orchestration tests passed across two batches, 50 completed cases and the 215-case continuation in 661.037 seconds. An optional Python diagnostic traceback timer crashed in its native stack dumper during the first process; continuation without that timer completed successfully. All eight new hanging-vine tests passed, including fresh cold discovery and saved scalar/shared-scene revalidation. No numerical-worker or shared Java arithmetic changed. The feature and initial plans are saved in `78c8f13`; stepping can proceed from STEPPING_PLAN.md.
