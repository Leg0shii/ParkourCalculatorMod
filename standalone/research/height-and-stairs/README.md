# Different heights and short stair profiles

2026-10-26. Cold route/profile searches with fixed sprint-jump/sprint-45 policy and free facings. No intermediate order, air time, recorded yaw or per-tick positional table is supplied. Ceilings are not modeled in this pass.

| Fixture | Selected profile durations | Added runs | Total inputs | Search seconds |
| --- | --- | --- | ---: | ---: |
| height-route | 9, 9 | 0, 0 | 18 | .227 |
| lower-target | 14 | 0 | 14 | .199 |
| staircase | 2, 2 | 0, 1 | 5 | .019 |

The first route climbs two blocks through a selected +1 intermediate. The second lands one block lower, with an explicit earlier departure-floor check. The third chooses two half-block stair boosts: each consists of a grounded takeoff plus one air input. It discovers a run tick between them and retimes the second stair contact to state 4. Its Y trace is `[1,1.5,1.5,1.5,2,2]`.

Each `*-request.json` is a self-contained standalone numerical problem including the generated vertical configuration. `*-result.json` retains the original scene, candidate graph/profile alternatives, selected path, entry constraints, run allocation, contact checks and accepted X/Z/Y trajectories. `summary.json` records fixture hashes and both the original horizontal problem hash and the full request hash with Y included. All three full requests pass the existing independent scalar validator with zero violation, expected ground flags and strict support/contact checks.

The yaw in a saved request is the found witness for convenient validation. Re-solving that request is warm validation, not a reproduction of cold profile/route selection. Reproduce cold selection with the [block-route CLI](../../BLOCK_ROUTE_SEARCH.md) and the corresponding fixture under `../../fixtures`.

These are first-feasible routes under the stated profile family. In particular, the five-input staircase has one added run tick; the planner has not proved that its run count or elapsed time is globally optimal. An ordinary longer-air-time profile remains available for the same block pairs, and the default queue favors a lower possible flight-input count.
