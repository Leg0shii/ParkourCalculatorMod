# Finite obstacle avoidance results

2026-10-26. Cold searches from actual collision boxes, an initial state and fixed endpoint IDs. No air time, facing sequence, blockage gates or per-tick positional table is supplied. Inputs are sprint-jump/sprint-45 with free facings. The shared Java scene helper validates the accepted continuous trajectories against all supplied solids.

| Fixture | Result | Added runs | Total inputs | Search seconds |
| --- | --- | --- | ---: | ---: |
| cross-neo | X->Z cross neo, gate 6 | 0 | 12 | 1.714 |
| wide-neo | X->Z->X wide neo, gates 4 and 9 | 0 | 12 | 15.316 |
| runoff-side | Leave the source's nearby Z edge | 1 | 5 | .219 |
| under-ceiling | Clear the underside at the correct movement stage | 0 | 12 | .168 |
| blocked | No accepted passage within the declared family | n/a | n/a | .154 |

The cross fixture caps passage complexity at one blockage; the wide fixture permits two. Both walls are too tall for the selected jump to clear vertically, so both Y faces are pruned. The cross fixture retains five passage timings. The wide fixture retains 34 timings after height/reach filtering, with two additional between-gate reach contradictions recorded. Its branch budget evaluates 24 alternatives. A successful result is a validated incumbent, not proof that every local numerical branch converged.

The side run-off starts near a long support's side with carried velocity. One ground input leaves that Z edge, followed by four run-off inputs. It does not need to travel to the support's far X edge. Its actual source volume remains an obstacle throughout the fall while the body overlaps in Y. All source-exit and subsequent clearance checks pass.

The ceiling case demonstrates the old/new-coordinate distinction: X enters the ceiling's horizontal footprint only after the Y stage has lowered the player's head below its bottom. Checking only the input's old feet height would misclassify that movement. This case does not prescribe or hit the ceiling, so its ordinary 12-input Y profile remains unchanged.

The blocked control has neither a reachable top, a passage below nor a reachable left/right route within this one-jump family. Its initial horizontal solve intersects the wall, no passage candidate survives, and no result is accepted. The reported status is unknown, not a claim about every Minecraft strategy.

Each `*-request.json` is the accepted full numerical request with generated horizontal rows and Y contacts. Each `*-result.json` preserves the original scene, candidate graph, selected profile/route, passage metadata, exact compiler footprints and search diagnostics. `summary.json` records fixture hashes, horizontal/full numerical hashes and independent validation results. All four accepted requests have zero scalar violation, correct ground flags, strict support/exit membership and successful shared swept-collision checks.

Saved facings are witnesses for validation. Re-solving a saved numerical request is warm numerical optimization, not a cold route/passage reproduction, and any changed trajectory must still pass full scene validation. Reproduce cold search with the [obstacle CLI example](../../OBSTACLE_SEARCH.md) and the corresponding fixture under `../../fixtures`.

The 12-input examples reach the current graph's lower bound and report `minimumTicksProven` within the declared ordinary/stair/run-off avoidance family. The five-input run-off does not reach its four-input graph bound. None claims a globally optimal objective or a minimum over unimplemented intentional wall/ceiling-contact profiles. The native worker and facing constraints are unchanged; replay and TAS export are separate.
